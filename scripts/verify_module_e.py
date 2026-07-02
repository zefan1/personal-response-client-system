from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "dev-progress/01E_progress.md",
    "src/main/resources/db/migration/V5__module_e_profile_update.sql",
    "src/main/java/com/privateflow/common/events/CustomerMessageSentEvent.java",
    "src/main/java/com/privateflow/common/events/ProfileSuggestionsReadyEvent.java",
    "src/main/java/com/privateflow/common/events/ProfileUpdatedEvent.java",
    "src/main/java/com/privateflow/modules/profile/config/ProfileConfig.java",
    "src/main/java/com/privateflow/modules/profile/config/ProfileConfigProvider.java",
    "src/main/java/com/privateflow/modules/profile/config/ProfileModuleConfiguration.java",
    "src/main/java/com/privateflow/modules/profile/infra/ProfileFieldRegistry.java",
    "src/main/java/com/privateflow/modules/profile/infra/ProfileWriter.java",
    "src/main/java/com/privateflow/modules/profile/infra/SuggestionRepository.java",
    "src/main/java/com/privateflow/modules/profile/infra/AuditLogRepository.java",
    "src/main/java/com/privateflow/modules/profile/service/EventDeduplicator.java",
    "src/main/java/com/privateflow/modules/profile/service/ProfileExtractionClient.java",
    "src/main/java/com/privateflow/modules/profile/service/ConfidenceRouter.java",
    "src/main/java/com/privateflow/modules/profile/service/ProfileUpdateOrchestrator.java",
    "src/main/java/com/privateflow/modules/profile/service/SuggestionQueueManager.java",
    "src/main/java/com/privateflow/modules/profile/service/ManualEditHandler.java",
]

profile_keys = [
    "profile.extract_fields",
    "profile.extract_timeout_ms",
    "profile.send_confirm_window_s",
    "profile.suggestion_expire_days",
    "profile.suggestion_cleanup_cron",
    "profile.suggestion_max_per_customer",
    "profile.dedup_window_s",
    "profile.fallback_summary_chars",
]

errors = []

for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

sql = read("src/main/resources/db/migration/V5__module_e_profile_update.sql")
for token in ["CREATE TABLE IF NOT EXISTS profile_update_suggestions", "idx_phone_status", "idx_created", "idx_phone_field_status", "CREATE TABLE IF NOT EXISTS audit_logs"]:
    if token not in sql:
        errors.append(f"V5 migration missing {token}")
for key in profile_keys:
    if key not in sql:
        errors.append(f"V5 migration missing config key {key}")

event = read("src/main/java/com/privateflow/common/events/CustomerMessageSentEvent.java")
for token in ["conversationSummary", "rawMessages", "sentText", "selectedDirection", "followupSuggest"]:
    if token not in event:
        errors.append(f"CustomerMessageSentEvent missing {token}")

updated = read("src/main/java/com/privateflow/common/events/ProfileUpdatedEvent.java")
for token in ["List<String> updatedFields", "public ProfileUpdatedEvent(String phone)"]:
    if token not in updated:
        errors.append(f"ProfileUpdatedEvent missing {token}")

config = read("src/main/java/com/privateflow/modules/profile/config/ProfileConfigProvider.java")
for token in ["profile.extract_fields", "profile.extract_timeout_ms", "profile.suggestion_expire_days", "profile.dedup_window_s", "ConfigChangedEvent"]:
    if token not in config:
        errors.append(f"ProfileConfigProvider missing {token}")

registry = read("src/main/java/com/privateflow/modules/profile/infra/ProfileFieldRegistry.java")
for field in [
    "postpartumMonths", "parity", "deliveryMethod", "breastfeeding", "lochiaPeriod",
    "bodyConcerns", "diastasisRecti", "urineLeakage", "pubicLumbago", "prevRepairExp",
    "postpartumCheck", "exerciseHabits", "worries", "intentLevel", "personalityType",
    "nextFollowupAt", "nextFollowupDir", "followupNotes", "lastFollowupAt"
]:
    if field not in registry:
        errors.append(f"ProfileFieldRegistry missing {field}")
for unsafe in ["leadType", "sourceChannel", "assignedKeeper"]:
    if f'register("{unsafe}"' in registry:
        errors.append(f"ProfileFieldRegistry must not allow AI/manual write to {unsafe}")

writer = read("src/main/java/com/privateflow/modules/profile/infra/ProfileWriter.java")
for token in ["version = version + 1", "WHERE phone = ?", "AND version = ?", "ProfileUpdatedEvent", "fieldRegistry.supports"]:
    if token not in writer:
        errors.append(f"ProfileWriter missing {token}")

dedup = read("src/main/java/com/privateflow/modules/profile/service/EventDeduplicator.java")
for token in ["MD5", "dedupWindowS", "ConcurrentHashMap", "MAX_SIZE = 200", "@PreDestroy"]:
    if token not in dedup:
        errors.append(f"EventDeduplicator missing {token}")

extractor = read("src/main/java/com/privateflow/modules/profile/service/ProfileExtractionClient.java")
for token in ["skillGatewayService.extractProfile", "ProfileExtractRequest", "fieldRegistry.toProfileMap", "ProfileUpdates.empty"]:
    if token not in extractor:
        errors.append(f"ProfileExtractionClient missing {token}")

router = read("src/main/java/com/privateflow/modules/profile/service/ConfidenceRouter.java")
for token in ['"HIGH"', '"MEDIUM"', "targetFields.contains", "fieldRegistry.supports"]:
    if token not in router:
        errors.append(f"ConfidenceRouter missing {token}")

orchestrator = read("src/main/java/com/privateflow/modules/profile/service/ProfileUpdateOrchestrator.java")
for token in ["@Async(\"profileUpdateExecutor\")", "@EventListener", "customerQueryService.getByPhone", "extractionClient.extract", "confidenceRouter.route", "lastFollowupAt", "followupNotes", "suggestionQueueManager.enqueue"]:
    if token not in orchestrator:
        errors.append(f"ProfileUpdateOrchestrator missing {token}")

queue = read("src/main/java/com/privateflow/modules/profile/service/SuggestionQueueManager.java")
for token in ["ProfileSuggestionsReadyEvent", "batchResolve", "ResolveAction.REJECT", "ResolveAction.CONFIRM", "CONFLICT_SKIPPED", "@Scheduled"]:
    if token not in queue:
        errors.append(f"SuggestionQueueManager missing {token}")

manual = read("src/main/java/com/privateflow/modules/profile/service/ManualEditHandler.java")
for token in ["version() == null", "VERSION_CONFLICT", "profileWriter.write", "UPDATE_PROFILE"]:
    if token not in manual:
        errors.append(f"ManualEditHandler missing {token}")

controller = read("src/main/java/com/privateflow/modules/match/web/CustomerController.java")
for token in ['@PutMapping("/{phone}")', '@PostMapping("/{phone}/suggestions/batch-resolve")', "pendingSuggestions", "ProfileUpdateException", "HttpStatus.CONFLICT"]:
    if token not in controller and token != "pendingSuggestions":
        errors.append(f"CustomerController missing {token}")
profile_view = read("src/main/java/com/privateflow/modules/profile/CustomerProfileView.java")
if "pendingSuggestions" not in profile_view:
    errors.append("CustomerProfileView missing pendingSuggestions")

progress = read("dev-progress/01E_progress.md")
for label in [f"SF-E{i:02d}" for i in range(1, 16)]:
    if label not in progress:
        errors.append(f"progress card missing {label}")

if errors:
    print("Module E verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module E static verification passed.")
print(f"Checked {len(required_files)} required files and {len(profile_keys)} profile config keys.")
