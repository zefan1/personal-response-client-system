#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "external"

CHECKS = [
    {
        "name": "skill_real_http_client",
        "path": "src/main/java/com/privateflow/modules/skill/client/DefaultSkillHttpClient.java",
        "required": ["HttpClient", "Authorization", "/v1/chat/completions"],
        "blocking": True,
    },
    {
        "name": "image_real_http_client",
        "path": "src/main/java/com/privateflow/modules/image/client/HttpImageRecognitionClient.java",
        "required": ["HttpClient", "Authorization", "application/json", "image_url"],
        "blocking": True,
    },
    {
        "name": "wecom_sheet_real_client",
        "path": "src/main/java/com/privateflow/modules/tablewrite/client/HttpWecomTableClient.java",
        "required": ["implements WecomTableClient, SheetClient", "fetchIncrementalRows", "table.api_base_url", "table.api_key"],
        "blocking": True,
    },
    {
        "name": "wecom_table_write_real_client",
        "path": "src/main/java/com/privateflow/modules/tablewrite/client/HttpWecomTableClient.java",
        "required": ["createRow", "updateRow", "HttpClient", "Authorization"],
        "blocking": True,
    },
    {
        "name": "llm_real_http_client",
        "path": "src/main/java/com/privateflow/modules/llm/HttpLlmClient.java",
        "required": ["HttpClient", "Authorization", "/v1/chat/completions", "OPENAI_COMPATIBLE"],
        "blocking": True,
    },
    {
        "name": "llm_scene_routing_runtime",
        "path": "src/main/java/com/privateflow/modules/llm/LlmRoutingService.java",
        "required": ["findEnabled", "findActive", "AiEnvironmentType.LLM", "configProvider.get()"],
        "blocking": True,
    },
    {
        "name": "llm_business_scene_enum",
        "path": "src/main/java/com/privateflow/modules/llm/LlmScene.java",
        "required": ["REPLY_GENERATION", "PROFILE_EXTRACTION", "FOLLOWUP_SUGGESTION", "ABNORMAL_DETECTION", "SUMMARY"],
        "blocking": True,
    },
    {
        "name": "llm_call_logging",
        "path": "src/main/java/com/privateflow/modules/llm/LlmService.java",
        "required": ["callLogger.logCall", "routingService.resolve", "client.generate"],
        "blocking": True,
    },
]

CONFIG_KEYS = {
    "skill.api_base_url": ["src/main/java/com/privateflow/modules/skill/config/SkillConfigProvider.java", "src/main/resources/db/migration"],
    "skill.api_key": ["src/main/java/com/privateflow/modules/skill/config/SkillConfigProvider.java", "src/main/resources/db/migration"],
    "image.api_base_url": ["src/main/java/com/privateflow/modules/image/config/ImageConfigProvider.java", "src/main/resources/db/migration"],
    "image.api_key": ["src/main/java/com/privateflow/modules/image/config/ImageConfigProvider.java", "src/main/resources/db/migration"],
    "table.api_base_url": ["src/main/java/com/privateflow/modules/tablewrite/config/TableConfigProvider.java", "src/main/resources/db/migration"],
    "table.api_key": ["src/main/java/com/privateflow/modules/tablewrite/config/TableConfigProvider.java", "src/main/resources/db/migration"],
    "llm.api_base_url": ["src/main/java/com/privateflow/modules/llm/LlmConfigProvider.java", "src/main/resources/db/migration"],
    "llm.api_key": ["src/main/java/com/privateflow/modules/llm/LlmConfigProvider.java", "src/main/resources/db/migration"],
    "llm.model": ["src/main/java/com/privateflow/modules/llm/LlmConfigProvider.java", "src/main/resources/db/migration"],
    "llm.reply_generation.enabled": ["src/main/java/com/privateflow/modules/llm/LlmReplyGenerationService.java", "src/main/resources/db/migration"],
    "llm.profile_extraction.enabled": ["src/main/java/com/privateflow/modules/llm/LlmProfileExtractionService.java", "src/main/resources/db/migration"],
    "llm.followup_suggestion.enabled": ["src/main/java/com/privateflow/modules/llm/LlmFollowupSuggestionService.java", "src/main/resources/db/migration"],
    "llm.abnormal_detection.enabled": ["src/main/java/com/privateflow/modules/llm/LlmAbnormalDetectionService.java", "src/main/resources/db/migration"],
    "llm.summary.enabled": ["src/main/java/com/privateflow/modules/llm/LlmSummaryService.java", "src/main/resources/db/migration"],
}

FORBIDDEN_PATHS = [
    "src/main/java/com/privateflow/modules/customer/sync/UnavailableSheetClient.java",
    "src/main/java/com/privateflow/modules/tablewrite/client/UnavailableWecomTableClient.java",
]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        return ""
    return target.read_text(encoding="utf-8", errors="replace")


def check_source() -> list[dict[str, object]]:
    results: list[dict[str, object]] = []
    for item in CHECKS:
        text = read(item["path"])
        missing = [token for token in item.get("required", []) if token not in text]
        forbidden_present = [token for token in item.get("forbidden", []) if token in text]
        ok = bool(text) and not missing and not forbidden_present
        results.append({
            "name": item["name"],
            "path": item["path"],
            "ok": ok,
            "blocking": item["blocking"],
            "missing": missing,
            "forbiddenPresent": forbidden_present,
        })
    return results


def check_config_keys() -> list[dict[str, object]]:
    results: list[dict[str, object]] = []
    for key, paths in CONFIG_KEYS.items():
        text = ""
        for path in paths:
            target = ROOT / path
            if target.is_dir():
                text += "\n".join(file.read_text(encoding="utf-8", errors="replace") for file in target.glob("*.sql"))
            else:
                text += read(path)
        results.append({
            "name": f"config_key:{key}",
            "path": ",".join(paths),
            "ok": re.search(rf"['\"]{re.escape(key)}['\"]", text) is not None,
            "blocking": True,
        })
    return results


def main() -> int:
    checks = check_source() + check_config_keys()
    for path in FORBIDDEN_PATHS:
        checks.append({
            "name": f"forbidden_path:{path}",
            "path": path,
            "ok": not (ROOT / path).exists(),
            "blocking": True,
        })
    blockers = [item for item in checks if item["blocking"] and not item["ok"]]
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "mockExternalsFalseReady": len(blockers) == 0,
        "blockerCount": len(blockers),
        "checks": checks,
        "notes": [
            "This verifies source/config readiness only; real-provider acceptance still requires valid external credentials and live endpoint tests.",
            "UnavailableSheetClient and UnavailableWecomTableClient are production blockers for MOCK_EXTERNALS=false.",
        ],
    }
    path = REPORT_DIR / "real_external_readiness.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"real_external_readiness_report={path}")
    print(f"mockExternalsFalseReady={str(report['mockExternalsFalseReady']).lower()} blockers={len(blockers)}")
    for blocker in blockers:
        print(f"blocker {blocker['name']} path={blocker['path']}", file=sys.stderr)
    return 1 if blockers else 0


if __name__ == "__main__":
    raise SystemExit(main())
