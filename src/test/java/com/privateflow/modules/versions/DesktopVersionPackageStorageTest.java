package com.privateflow.modules.versions;

import static org.assertj.core.api.Assertions.assertThat;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class DesktopVersionPackageStorageTest {

  @TempDir
  Path tempDir;

  @Test
  void storeWritesInstallerAndReturnsDownloadUrl() throws Exception {
    SystemConfigRepository configRepository = org.mockito.Mockito.mock(SystemConfigRepository.class);
    org.mockito.Mockito.when(configRepository.findValue("version.storage.root")).thenReturn(Optional.of(tempDir.toString()));
    org.mockito.Mockito.when(configRepository.findValue("version.storage.public_base_url")).thenReturn(Optional.of("/downloads/desktop-releases"));
    DesktopVersionPackageStorage storage = new DesktopVersionPackageStorage(
        configRepository,
        tempDir.toString(),
        "/downloads/desktop-releases");

    VersionUploadResponse response = storage.store(new MockMultipartFile(
        "file",
        "Private Domain Assistant 1.2.3.exe",
        "application/octet-stream",
        new byte[] {1, 2, 3, 4}),
        DesktopPlatform.WINDOWS);

    assertThat(response.downloadUrl()).startsWith("/downloads/desktop-releases/").endsWith(".exe");
    assertThat(response.downloadUrl()).contains("WINDOWS-");
    assertThat(response.fileSize()).isEqualTo(4);
    assertThat(Files.walk(tempDir).filter(Files::isRegularFile).count()).isEqualTo(1);
    Path stored = Files.walk(tempDir).filter(Files::isRegularFile).findFirst().orElseThrow();
    assertThat(Files.readAllBytes(stored)).containsExactly(1, 2, 3, 4);
  }
}
