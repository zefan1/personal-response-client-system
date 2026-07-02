from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "dev-progress/01G_progress.md",
    "src/main/resources/db/migration/V7__module_g_table_write.sql",
    "src/main/java/com/privateflow/modules/tablewrite/TableWriteErrorCodes.java",
    "src/main/java/com/privateflow/modules/tablewrite/TableWriteException.java",
    "src/main/java/com/privateflow/modules/tablewrite/TableWriteActionType.java",
    "src/main/java/com/privateflow/modules/tablewrite/TableWriteStatus.java",
    "src/main/java/com/privateflow/modules/tablewrite/ManualSaveRequest.java",
    "src/main/java/com/privateflow/modules/tablewrite/ManualSaveResult.java",
    "src/main/java/com/privateflow/modules/tablewrite/PendingTableWrite.java",
    "src/main/java/com/privateflow/modules/tablewrite/PendingWritePayload.java",
    "src/main/java/com/privateflow/modules/tablewrite/config/TableConfig.java",
    "src/main/java/com/privateflow/modules/tablewrite/config/TableConfigProvider.java",
    "src/main/java/com/privateflow/modules/tablewrite/config/TableWriteModuleConfiguration.java",
    "src/main/java/com/privateflow/modules/tablewrite/client/WecomTableClient.java",
    "src/main/java/com/privateflow/modules/tablewrite/client/MockWecomTableClient.java",
    "src/main/java/com/privateflow/modules/tablewrite/client/UnavailableWecomTableClient.java",
    "src/main/java/com/privateflow/modules/tablewrite/infra/TableFieldMappingResolver.java",
    "src/main/java/com/privateflow/modules/tablewrite/infra/PendingTableWriteRepository.java",
    "src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java",
    "src/main/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdater.java",
    "src/main/java/com/privateflow/modules/tablewrite/service/WriteQueueManager.java",
    "src/main/java/com/privateflow/modules/tablewrite/service/TableWriteOrchestrator.java",
    "src/main/java/com/privateflow/modules/tablewrite/service/QueueRetryManager.java",
    "src/main/java/com/privateflow/modules/tablewrite/service/ManualSaveHandler.java",
]

config_keys = [
    "table.write_timeout_ms",
    "table.retry_max_count",
    "table.retry_interval_s",
    "table.alert_failure_hours",
    "table.alert_notify_target",
    "table.queue_warn_threshold",
    "table.queue_alert_threshold",
]

errors = []

for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

if not errors:
    sql = read("src/main/resources/db/migration/V7__module_g_table_write.sql")
    for token in ["CREATE TABLE IF NOT EXISTS pending_table_writes", "action_type", "payload", "retry_count", "PENDING", "RESOLVED", "FAILED", "idx_status_retry", "idx_phone"]:
        if token not in sql:
            errors.append(f"V7 migration missing {token}")
    for key in config_keys:
        if key not in sql:
            errors.append(f"V7 migration missing config key {key}")

    app = read("src/main/resources/application.yml")
    for token in ["table:", "write-timeout-ms: 10000", "retry-max-count: 5", "retry-interval-s: 60", "alert-notify-target: ADMIN"]:
        if token not in app:
            errors.append(f"application.yml missing {token}")

    codes = read("src/main/java/com/privateflow/modules/tablewrite/TableWriteErrorCodes.java")
    for code in ["70-10001", "70-10002", "80-10001"]:
        if code not in codes:
            errors.append(f"TableWriteErrorCodes missing {code}")

    config = read("src/main/java/com/privateflow/modules/tablewrite/config/TableConfigProvider.java")
    for token in config_keys + ["ConfigChangedEvent", "5000, 20000", "3, 10", "30, 300", "ADMIN", "LEADER", "BOTH"]:
        if token not in config:
            errors.append(f"TableConfigProvider missing {token}")

    module_config = read("src/main/java/com/privateflow/modules/tablewrite/config/TableWriteModuleConfiguration.java")
    for token in ["tableWriteExecutor", "setCorePoolSize(2)", "setMaxPoolSize(4)", "setQueueCapacity(200)", "CallerRunsPolicy", "setAwaitTerminationSeconds(30)"]:
        if token not in module_config:
            errors.append(f"TableWriteModuleConfiguration missing {token}")

    client = read("src/main/java/com/privateflow/modules/tablewrite/client/WecomTableClient.java")
    for token in ["createRow", "updateRow", "Duration"]:
        if token not in client:
            errors.append(f"WecomTableClient missing {token}")

    mapping = read("src/main/java/com/privateflow/modules/tablewrite/infra/TableFieldMappingResolver.java")
    for token in ["datasource_field_mappings", "target_field", "source_field", "toSourceFields", "datasource.field_mappings"]:
        if token not in mapping:
            errors.append(f"TableFieldMappingResolver missing {token}")

    repo = read("src/main/java/com/privateflow/modules/tablewrite/infra/PendingTableWriteRepository.java")
    for token in ["enqueue", "due", "markResolved", "markRetry", "markFailed", "countPending", "countStaleFailed"]:
        if token not in repo:
            errors.append(f"PendingTableWriteRepository missing {token}")

    orchestrator = read("src/main/java/com/privateflow/modules/tablewrite/service/TableWriteOrchestrator.java")
    for token in ["CustomerMessageSentEvent", "@EventListener", "@Async(\"tableWriteExecutor\")", "customerQueryService.getByPhone", "withOneImmediateRetry", "queueManager.enqueue"]:
        if token not in orchestrator:
            errors.append(f"TableWriteOrchestrator missing {token}")

    new_customer = read("src/main/java/com/privateflow/modules/tablewrite/service/NewCustomerRowCreator.java")
    for token in ["createRow", "CustomerRepository", "ProfileUpdatedEvent", "customerRepository.upsert", "eventPublisher.publishEvent", "待联系"]:
        if token not in new_customer:
            errors.append(f"NewCustomerRowCreator missing {token}")

    existing = read("src/main/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdater.java")
    for token in ["updateRow", "followupFields", "getSourceTable", "getSourceRowId"]:
        if token not in existing:
            errors.append(f"ExistingCustomerUpdater missing {token}")
    if "CustomerRepository" in existing or "customerRepository" in existing:
        errors.append("ExistingCustomerUpdater must not update MySQL customer records")

    retry = read("src/main/java/com/privateflow/modules/tablewrite/service/QueueRetryManager.java")
    for token in ["@Scheduled", "retryIntervalS", "retryMaxCount", "markFailed", "markRetry", "markResolved", "alertNotifyTarget", "CustomerQueryService", "insertCustomerAfterQueuedCreate", "resolveExistingRow"]:
        if token not in retry:
            errors.append(f"QueueRetryManager missing {token}")

    manual = read("src/main/java/com/privateflow/modules/tablewrite/service/ManualSaveHandler.java")
    for token in ["ManualSaveRequest", "ManualSaveResult", "updateRow", "TABLE_WRITE_FAILED", "BAD_REQUEST"]:
        if token not in manual:
            errors.append(f"ManualSaveHandler missing {token}")
    if "queueManager" in manual or "pending_table_writes" in manual:
        errors.append("ManualSaveHandler must not enqueue manual save failures")

    controller = read("src/main/java/com/privateflow/modules/match/web/CustomerController.java")
    for token in ["save-to-table", "ManualSaveHandler", "TableWriteException", "TOO_MANY_REQUESTS"]:
        if token not in controller:
            errors.append(f"CustomerController missing {token}")

    progress = read("dev-progress/01G_progress.md")
    for label in [f"SF-G{i:02d}" for i in range(1, 16)]:
        if label not in progress:
            errors.append(f"progress card missing {label}")

if errors:
    print("Module G verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module G static verification passed.")
print(f"Checked {len(required_files)} required files and {len(config_keys)} table config keys.")
