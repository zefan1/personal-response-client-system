from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "src/main/java/com/privateflow/modules/image/Source.java",
    "src/main/java/com/privateflow/modules/image/RecognitionResult.java",
    "src/main/java/com/privateflow/modules/image/Message.java",
    "src/main/java/com/privateflow/modules/image/ImageRecognitionService.java",
    "src/main/java/com/privateflow/modules/image/ImageRecognitionException.java",
    "src/main/java/com/privateflow/modules/image/ImageFormatException.java",
    "src/main/java/com/privateflow/modules/image/config/ImageConfig.java",
    "src/main/java/com/privateflow/modules/image/config/ImageConfigProvider.java",
    "src/main/java/com/privateflow/modules/image/processing/ImageValidator.java",
    "src/main/java/com/privateflow/modules/image/processing/ImagePreprocessor.java",
    "src/main/java/com/privateflow/modules/image/client/ImageRecognitionClient.java",
    "src/main/java/com/privateflow/modules/image/client/HttpImageRecognitionClient.java",
    "src/main/java/com/privateflow/modules/image/client/MockImageRecognitionClient.java",
    "src/main/java/com/privateflow/modules/image/parser/RecognitionResultParser.java",
    "src/main/java/com/privateflow/modules/image/health/ImageServiceHealthMonitor.java",
    "src/main/java/com/privateflow/modules/image/service/ImageRecognitionServiceImpl.java",
    "src/main/java/com/privateflow/common/events/ImageServiceStatusEvent.java",
    "src/main/resources/db/migration/V2__module_c_image_configs.sql",
    "src/main/resources/db/migration/V72__cross_platform_image_recognition_prompt.sql",
    "dev-progress/01C_progress.md",
]

image_keys = [
    "image.api_base_url",
    "image.api_key",
    "image.timeout_ms",
    "image.max_size_bytes",
    "image.max_dimension_px",
    "image.compress_quality",
    "image.recognition_prompt",
    "image.consecutive_failures_alert",
]

errors = []
for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

source = (ROOT / "src/main/java/com/privateflow/modules/image/Source.java").read_text(encoding="utf-8")
for value in ["BUTTON_CLICK", "CLIPBOARD_SCREENSHOT"]:
    if value not in source:
        errors.append(f"Source enum missing {value}")

result = (ROOT / "src/main/java/com/privateflow/modules/image/RecognitionResult.java").read_text(encoding="utf-8")
for field in ["nickname", "phone", "messages", "timestamp", "customerIdentifier", "platform", "confidence"]:
    if field not in result:
        errors.append(f"RecognitionResult missing {field}")

validator = (ROOT / "src/main/java/com/privateflow/modules/image/processing/ImageValidator.java").read_text(encoding="utf-8")
for token in ["isPng", "isJpeg", "isWebp", "maxSizeBytes", "ImageFormatException"]:
    if token not in validator:
        errors.append(f"ImageValidator missing {token}")

parser = (ROOT / "src/main/java/com/privateflow/modules/image/parser/RecognitionResultParser.java").read_text(encoding="utf-8")
for token in ["client", "keeper", "replaceAll(\"[-\\\\s]\", \"\")", "\\\\d{11}", "messages.isEmpty", "UNABLE_TO_DETERMINE", "failureReason"]:
    if token not in parser:
        errors.append(f"RecognitionResultParser missing {token}")

client = (ROOT / "src/main/java/com/privateflow/modules/image/client/HttpImageRecognitionClient.java").read_text(encoding="utf-8")
for token in ["/v1/chat/completions", "application/json", "image_url", "Authorization", "HttpTimeoutException"]:
    if token not in client:
        errors.append(f"HttpImageRecognitionClient missing {token}")
if "retry" in client.lower():
    errors.append("HttpImageRecognitionClient appears to contain retry logic")

health = (ROOT / "src/main/java/com/privateflow/modules/image/health/ImageServiceHealthMonitor.java").read_text(encoding="utf-8")
for token in ["AtomicInteger", "compareAndSet", "ImageServiceStatusEvent", "\"DOWN\"", "\"UP\""]:
    if token not in health:
        errors.append(f"ImageServiceHealthMonitor missing {token}")

service = (ROOT / "src/main/java/com/privateflow/modules/image/service/ImageRecognitionServiceImpl.java").read_text(encoding="utf-8")
for token in ["validator.validate", "preprocessor.preprocess", "client.recognize", "parser.parse", "workingImage = null"]:
    if token not in service:
        errors.append(f"ImageRecognitionServiceImpl missing {token}")

sql = (ROOT / "src/main/resources/db/migration/V2__module_c_image_configs.sql").read_text(encoding="utf-8")
for key in image_keys:
    if key not in sql:
        errors.append(f"V2 migration missing config key {key}")

cross_platform_prompt_sql = (ROOT / "src/main/resources/db/migration/V72__cross_platform_image_recognition_prompt.sql").read_text(encoding="utf-8") if (ROOT / "src/main/resources/db/migration/V72__cross_platform_image_recognition_prompt.sql").exists() else ""
for token in [
    "微信",
    "企业微信",
    "抖音网页后台",
    "UNKNOWN",
    "UNABLE_TO_DETERMINE",
    "customerIdentifier",
    "failureReason",
    "ON DUPLICATE KEY UPDATE",
]:
    if token not in cross_platform_prompt_sql:
        errors.append(f"V72 migration missing {token}")

progress = (ROOT / "dev-progress/01C_progress.md").read_text(encoding="utf-8")
for label in ["SF-C01", "SF-C02", "SF-C03", "SF-C04", "SF-C05", "SF-C06", "SF-C07", "SF-C08"]:
    if label not in progress:
        errors.append(f"progress card missing {label}")

if errors:
    print("Module C verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module C static verification passed.")
print(f"Checked {len(required_files)} required files and {len(image_keys)} image config keys.")
