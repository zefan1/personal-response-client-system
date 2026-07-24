package com.privateflow.modules.api.chat;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.image.config.ImageConfigProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps preprocessed recognition images only while a recognition job is active.
 * No image payload is written to a business or audit table.
 */
@Component
public class TemporaryRecognitionImageStore {

  static final String ROOT_CONFIG_KEY = "chat.recognition_temp_root";
  static final String TTL_CONFIG_KEY = "chat.recognition_temp_ttl_seconds";
  static final String MAX_TOTAL_BYTES_CONFIG_KEY = "chat.recognition_temp_max_total_bytes";

  private static final String DEFAULT_ROOT = "active";
  private static final int DEFAULT_TTL_SECONDS = 600;
  private static final long DEFAULT_MAX_TOTAL_BYTES = 100L * 1024 * 1024;
  private static final long HARD_MAX_IMAGE_BYTES = 5L * 1024 * 1024;
  private static final Path DEFAULT_APPLICATION_TEMP_ROOT = Path.of("uploads", "temporary-recognition")
      .toAbsolutePath()
      .normalize();

  private final SystemConfigRepository configRepository;
  private final ImageConfigProvider imageConfigProvider;
  private final Clock clock;
  private final Path applicationTempRoot;
  private final Map<String, Path> activePaths = new ConcurrentHashMap<>();
  private final Object capacityLock = new Object();

  public TemporaryRecognitionImageStore(
      SystemConfigRepository configRepository,
      ImageConfigProvider imageConfigProvider) {
    this(configRepository, imageConfigProvider, Clock.systemUTC());
  }

  TemporaryRecognitionImageStore(
      SystemConfigRepository configRepository,
      ImageConfigProvider imageConfigProvider,
      Clock clock) {
    this(configRepository, imageConfigProvider, clock, DEFAULT_APPLICATION_TEMP_ROOT);
  }

  TemporaryRecognitionImageStore(
      SystemConfigRepository configRepository,
      ImageConfigProvider imageConfigProvider,
      Clock clock,
      Path applicationTempRoot) {
    this.configRepository = configRepository;
    this.imageConfigProvider = imageConfigProvider;
    this.clock = clock;
    this.applicationTempRoot = applicationTempRoot.toAbsolutePath().normalize();
  }

  public String put(byte[] jpegBytes) {
    if (jpegBytes == null || jpegBytes.length == 0) {
      throw new IllegalArgumentException("recognition image is required");
    }
    long configuredImageLimit = imageConfigProvider.get().maxSizeBytes();
    long effectiveImageLimit = Math.min(configuredImageLimit, HARD_MAX_IMAGE_BYTES);
    if (jpegBytes.length > effectiveImageLimit) {
      String message = configuredImageLimit >= HARD_MAX_IMAGE_BYTES
          ? "recognition image exceeds 5MB hard limit"
          : "recognition image exceeds configured image limit";
      throw new IllegalArgumentException(message);
    }

    Path root = root();
    String token = UUID.randomUUID().toString();
    Path target = pathFor(root, token);
    synchronized (capacityLock) {
      try {
        Files.createDirectories(root);
        cleanupDirectory(root, clock.instant().minusSeconds(ttlSeconds()));
        if (totalBytes(root) + jpegBytes.length > maxTotalBytes()) {
          throw new IllegalStateException("temporary recognition image capacity is full");
        }
        Files.write(target, jpegBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        activePaths.put(token, target);
        return token;
      } catch (IOException ex) {
        deleteQuietly(target);
        throw new IllegalStateException("unable to store temporary recognition image", ex);
      }
    }
  }

  public byte[] read(String token) {
    Path path = knownOrCurrentPath(token);
    try {
      return Files.readAllBytes(path);
    } catch (IOException ex) {
      activePaths.remove(token, path);
      throw new IllegalStateException("temporary recognition image is unavailable", ex);
    }
  }

  public void delete(String token) {
    Path path = knownOrCurrentPath(token);
    deleteQuietly(path);
    activePaths.remove(token, path);
  }

  public boolean exists(String token) {
    try {
      return Files.exists(knownOrCurrentPath(token));
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  public void cleanupExpired(Instant now) {
    Instant cutoff = now.minusSeconds(ttlSeconds());
    Path currentRoot = root();
    cleanupDirectory(currentRoot, cutoff);
    activePaths.values().stream()
        .map(Path::getParent)
        .filter(Objects::nonNull)
        .filter(path -> !path.equals(currentRoot))
        .distinct()
        .forEach(path -> cleanupDirectory(path, cutoff));
  }

  @Scheduled(fixedDelay = 60_000)
  public void cleanupExpired() {
    cleanupExpired(clock.instant());
  }

  private void cleanupDirectory(Path root, Instant cutoff) {
    if (!Files.isDirectory(root)) {
      return;
    }
    try (Stream<Path> files = Files.list(root)) {
      files.filter(Files::isRegularFile)
          .filter(this::isManagedJpeg)
          .filter(path -> modifiedAt(path).compareTo(cutoff) <= 0)
          .forEach(path -> {
            deleteQuietly(path);
            activePaths.entrySet().removeIf(entry -> entry.getValue().equals(path));
          });
    } catch (IOException ignored) {
      // Recognition jobs delete their own image in a finally block; scheduled cleanup is a fallback.
    }
  }

  private boolean isManagedJpeg(Path path) {
    String fileName = path.getFileName().toString();
    if (!fileName.endsWith(".jpg")) {
      return false;
    }
    try {
      UUID.fromString(fileName.substring(0, fileName.length() - 4));
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private Instant modifiedAt(Path path) {
    try {
      return Files.getLastModifiedTime(path).toInstant();
    } catch (IOException ex) {
      return Instant.MAX;
    }
  }

  private Path knownOrCurrentPath(String token) {
    requireToken(token);
    Path known = activePaths.get(token);
    return known == null ? pathFor(root(), token) : known;
  }

  private Path root() {
    String configured = configRepository.findValue(ROOT_CONFIG_KEY)
        .filter(value -> !value.isBlank())
        .orElse(DEFAULT_ROOT);
    String candidate = configured.trim();
    if (candidate.isBlank() || candidate.startsWith("/") || candidate.startsWith("\\")
        || isWindowsDrivePath(candidate) || hasTraversalSegment(candidate)) {
      throw new IllegalArgumentException(
          "temporary recognition image directory must be a controlled relative directory");
    }
    try {
      Path relative = Path.of(candidate);
      if (relative.isAbsolute()) {
        throw new IllegalArgumentException(
            "temporary recognition image directory must be a controlled relative directory");
      }
      Path root = applicationTempRoot.resolve(relative).normalize();
      if (!root.startsWith(applicationTempRoot)) {
        throw new IllegalArgumentException(
            "temporary recognition image directory must be a controlled relative directory");
      }
      return root;
    } catch (java.nio.file.InvalidPathException ex) {
      throw new IllegalArgumentException(
          "temporary recognition image directory must be a controlled relative directory", ex);
    }
  }

  private boolean isWindowsDrivePath(String value) {
    return value.length() >= 2
        && Character.isLetter(value.charAt(0))
        && value.charAt(1) == ':';
  }

  private boolean hasTraversalSegment(String value) {
    return Stream.of(value.replace('\\', '/').split("/"))
        .anyMatch(".."::equals);
  }

  private Path pathFor(Path root, String token) {
    requireToken(token);
    Path path = root.resolve(token + ".jpg").normalize();
    if (!path.startsWith(root)) {
      throw new IllegalArgumentException("invalid recognition image token");
    }
    return path;
  }

  private void requireToken(String token) {
    try {
      UUID.fromString(token);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("invalid recognition image token", ex);
    }
  }

  private long totalBytes(Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      return 0;
    }
    try (Stream<Path> files = Files.list(root)) {
      return files.filter(Files::isRegularFile)
          .filter(this::isManagedJpeg)
          .mapToLong(this::fileSize)
          .sum();
    }
  }

  private long fileSize(Path path) {
    try {
      return Files.size(path);
    } catch (IOException ex) {
      return 0;
    }
  }

  private int ttlSeconds() {
    return configuredInteger(TTL_CONFIG_KEY, DEFAULT_TTL_SECONDS);
  }

  private long maxTotalBytes() {
    return configuredLong(MAX_TOTAL_BYTES_CONFIG_KEY, DEFAULT_MAX_TOTAL_BYTES);
  }

  private int configuredInteger(String key, int fallback) {
    try {
      return configRepository.findValue(key).map(Integer::parseInt).orElse(fallback);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private long configuredLong(String key, long fallback) {
    try {
      return configRepository.findValue(key).map(Long::parseLong).orElse(fallback);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Cleanup is retried by the scheduled expiration sweep.
    }
  }
}
