from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "pom.xml",
    "src/main/java/com/privateflow/PrivateDomainAssistantApplication.java",
    "src/main/java/com/privateflow/modules/customer/Customer.java",
    "src/main/java/com/privateflow/modules/customer/CustomerQueryService.java",
    "src/main/java/com/privateflow/modules/customer/service/CustomerQueryServiceImpl.java",
    "src/main/java/com/privateflow/modules/customer/service/CustomerMergeEngine.java",
    "src/main/java/com/privateflow/modules/customer/infra/CustomerCacheManager.java",
    "src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java",
    "src/main/java/com/privateflow/modules/customer/sync/FieldMappingResolver.java",
    "src/main/resources/db/migration/V1__module_a_customer_cache.sql",
    "dev-progress/01A_progress.md",
]

customer_fields = [
    "phone", "nickname", "sourceChannel", "leadType", "personalityType",
    "assignedKeeper", "intendedStore", "intendedProject", "purchasedProject",
    "postpartumMonths", "parity", "deliveryMethod", "breastfeeding",
    "lochiaPeriod", "pregnancyWeight", "currentWeight", "bodyConcerns",
    "diastasisRecti", "urineLeakage", "pubicLumbago", "prevRepairExp",
    "postpartumCheck", "exerciseHabits", "intentLevel", "worries",
    "customerStage", "lastFollowupAt", "followupNotes", "nextFollowupAt",
    "nextFollowupDir", "appointmentDate", "appointmentStore",
    "appointmentItem", "arrived", "sourceTable", "sourceRowId", "syncedAt",
]

config_keys = [
    "cache.sync_cron",
    "cache.ttl_seconds",
    "cache.load_batch_size",
    "cache.sync_timeout_ms",
    "cache.max_sync_rows_per_round",
    "cache.lock_spin_max",
    "cache.lock_spin_interval_ms",
    "cache.lock_ttl_s",
]

sql_tables = [
    "customers",
    "system_configs",
    "datasource_field_mappings",
    "sync_failure_log",
]

errors = []

for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

customer_java = (ROOT / "src/main/java/com/privateflow/modules/customer/Customer.java").read_text(encoding="utf-8")
for field in customer_fields:
    if not re.search(rf"\b{re.escape(field)}\b", customer_java):
        errors.append(f"Customer.java missing field: {field}")

service_java = (ROOT / "src/main/java/com/privateflow/modules/customer/CustomerQueryService.java").read_text(encoding="utf-8")
for method in ["getByPhone", "searchByNickname", "scanActiveCustomers", "refreshCache"]:
    if method not in service_java:
        errors.append(f"CustomerQueryService missing method: {method}")

sql = (ROOT / "src/main/resources/db/migration/V1__module_a_customer_cache.sql").read_text(encoding="utf-8")
for table in sql_tables:
    if f"CREATE TABLE IF NOT EXISTS {table}" not in sql:
        errors.append(f"migration missing table: {table}")

for index in ["idx_phone", "idx_nickname", "idx_assigned_keeper", "idx_next_followup", "idx_last_followup", "idx_lead_type"]:
    if index not in sql:
        errors.append(f"migration missing index: {index}")

for key in config_keys:
    if key not in sql:
        errors.append(f"migration missing config key seed: {key}")

events_text = "\n".join(
    (ROOT / rel).read_text(encoding="utf-8")
    for rel in [
        "src/main/java/com/privateflow/common/events/NewLeadEvent.java",
        "src/main/java/com/privateflow/common/events/ProfileUpdatedEvent.java",
        "src/main/java/com/privateflow/common/events/ConfigChangedEvent.java",
        "src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java",
        "src/main/java/com/privateflow/modules/customer/service/CustomerQueryServiceImpl.java",
    ]
)
for token in ["NewLeadEvent", "ProfileUpdatedEvent", "ConfigChangedEvent"]:
    if token not in events_text:
        errors.append(f"event contract not wired: {token}")

sync_java = (ROOT / "src/main/java/com/privateflow/modules/customer/sync/CustomerSyncScheduler.java").read_text(encoding="utf-8")
order_index = [sync_java.find(name) for name in ["推广组客资登记表", "私域客资管理表", "新客管理衔接表"]]
if any(i < 0 for i in order_index) or order_index != sorted(order_index):
    errors.append("sync table order is not promo -> private -> arrival")

if "MOCK_EXTERNALS" not in (ROOT / "src/main/resources/application.yml").read_text(encoding="utf-8"):
    errors.append("application.yml missing MOCK_EXTERNALS switch")

if errors:
    print("Module A verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module A static verification passed.")
print(f"Checked {len(required_files)} required files, {len(customer_fields)} customer fields, {len(config_keys)} config keys.")
