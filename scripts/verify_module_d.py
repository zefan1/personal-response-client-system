from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "dev-progress/01D_progress.md",
    "src/main/resources/db/migration/V4__module_d_customer_match.sql",
    "src/main/java/com/privateflow/modules/match/MatchType.java",
    "src/main/java/com/privateflow/modules/match/Confidence.java",
    "src/main/java/com/privateflow/modules/match/CustomerMatchService.java",
    "src/main/java/com/privateflow/modules/match/MatchRequest.java",
    "src/main/java/com/privateflow/modules/match/MatchResult.java",
    "src/main/java/com/privateflow/modules/match/CustomerSummary.java",
    "src/main/java/com/privateflow/modules/match/config/MatchConfig.java",
    "src/main/java/com/privateflow/modules/match/config/MatchConfigProvider.java",
    "src/main/java/com/privateflow/modules/match/service/MatchOrchestrator.java",
    "src/main/java/com/privateflow/modules/match/service/NicknamePrefixRemovalProcessor.java",
    "src/main/java/com/privateflow/modules/match/service/ConfidenceEvaluator.java",
    "src/main/java/com/privateflow/modules/match/service/CandidateRanker.java",
    "src/main/java/com/privateflow/modules/match/service/ExactMatcher.java",
    "src/main/java/com/privateflow/modules/match/service/FuzzyMatcher.java",
    "src/main/java/com/privateflow/modules/match/service/CustomerSearchService.java",
    "src/main/java/com/privateflow/modules/match/service/CustomerProfileService.java",
    "src/main/java/com/privateflow/modules/match/web/CustomerController.java",
]

match_keys = [
    "match.tag_removal_rules",
    "match.max_candidates",
    "match.fuzzy_search_timeout_ms",
    "match.confidence_ratio_threshold",
    "match.confidence_min_length",
]

errors = []

for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

match_type = read("src/main/java/com/privateflow/modules/match/MatchType.java")
for value in ["EXACT", "FUZZY", "MULTIPLE", "NONE"]:
    if value not in match_type:
        errors.append(f"MatchType missing {value}")

confidence = read("src/main/java/com/privateflow/modules/match/Confidence.java")
for value in ["HIGH", "MEDIUM"]:
    if value not in confidence:
        errors.append(f"Confidence missing {value}")

sql = read("src/main/resources/db/migration/V4__module_d_customer_match.sql")
for key in match_keys:
    if key not in sql:
        errors.append(f"V4 migration missing config key {key}")

app_yml = read("src/main/resources/application.yml")
for key in ["tag-removal-rules", "max-candidates", "fuzzy-search-timeout-ms", "confidence-ratio-threshold", "confidence-min-length"]:
    if key not in app_yml:
        errors.append(f"application.yml missing match.{key}")

customer_service = read("src/main/java/com/privateflow/modules/customer/CustomerQueryService.java")
if "searchByNickname(String nickname, int limit)" not in customer_service:
    errors.append("CustomerQueryService missing limit-aware nickname search overload")

orchestrator = read("src/main/java/com/privateflow/modules/match/service/MatchOrchestrator.java")
for token in [
    "exactMatcher.matchByPhone",
    "fuzzyMatcher.matchByNickname",
    "nicknamePrefixRemovalProcessor.clean",
    "Confidence.HIGH",
    "MatchType.EXACT",
    "MatchType.FUZZY",
    "MatchType.MULTIPLE",
    "MatchResult.none()",
]:
    if token not in orchestrator:
        errors.append(f"MatchOrchestrator missing {token}")
if orchestrator.find("exactMatcher.matchByPhone") > orchestrator.find("fuzzyMatcher.matchByNickname"):
    errors.append("MatchOrchestrator does not try exact phone match before fuzzy nickname match")

tag_processor = read("src/main/java/com/privateflow/modules/match/service/NicknamePrefixRemovalProcessor.java")
for token in ["comparingInt(String::length).reversed()", "toUpperCase(Locale.ROOT)", "configProvider.get().tagRemovalRules()"]:
    if token not in tag_processor:
        errors.append(f"NicknamePrefixRemovalProcessor missing {token}")

evaluator = read("src/main/java/com/privateflow/modules/match/service/ConfidenceEvaluator.java")
for token in ["Math.min", "Math.max", "confidenceRatioThreshold", "confidenceMinLength", "contains"]:
    if token not in evaluator:
        errors.append(f"ConfidenceEvaluator missing {token}")

ranker = read("src/main/java/com/privateflow/modules/match/service/CandidateRanker.java")
for token in ["ownCustomer", "lastFollowupAt", "nullsLast", "Confidence.HIGH", ".limit(limit)"]:
    if token not in ranker:
        errors.append(f"CandidateRanker missing {token}")
if "leadType" in ranker:
    errors.append("CandidateRanker must not sort by leadType")

fuzzy = read("src/main/java/com/privateflow/modules/match/service/FuzzyMatcher.java")
for token in ["customerQueryService.searchByNickname", "maxCandidates", "fuzzySearchTimeoutMs", "TimeoutException", "return List.of()"]:
    if token not in fuzzy:
        errors.append(f"FuzzyMatcher missing {token}")

search = read("src/main/java/com/privateflow/modules/match/service/CustomerSearchService.java")
for token in ["PhoneUtils.clean", "PhoneUtils.isValid", "customerQueryService.getByPhone", "customerQueryService.searchByNickname", "limit < 1 || limit > 50"]:
    if token not in search:
        errors.append(f"CustomerSearchService missing {token}")

profile = read("src/main/java/com/privateflow/modules/match/service/CustomerProfileService.java")
for token in ["PhoneUtils.clean", "PhoneUtils.isValid", "PhoneUtils.mask", "CUSTOMER_NOT_FOUND", "setVersion"]:
    if token not in profile:
        errors.append(f"CustomerProfileService missing {token}")

controller = read("src/main/java/com/privateflow/modules/match/web/CustomerController.java")
for token in ['@RequestMapping("/api/v1/customers")', '@GetMapping("/search")', '@GetMapping("/{phone}")', "BAD_REQUEST", "NOT_FOUND"]:
    if token not in controller:
        errors.append(f"CustomerController missing {token}")

progress = read("dev-progress/01D_progress.md")
for label in [f"SF-D{i:02d}" for i in range(1, 13)]:
    if label not in progress:
        errors.append(f"progress card missing {label}")

if errors:
    print("Module D verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module D static verification passed.")
print(f"Checked {len(required_files)} required files and {len(match_keys)} match config keys.")
