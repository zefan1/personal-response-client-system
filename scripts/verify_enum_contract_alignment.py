#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path

import verify_database_alignment

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "contracts"

EXPECTED = {
    "account.roles": {"ADMIN", "LEADER", "KEEPER"},
    "customer.leadTypes": {"TUAN_GOU", "XIAN_SUO", "PENDING"},
    "skill.scenes": {"CHAT_RECOGNIZE", "ACTIVE_REPLY", "REGENERATE", "PROFILE_EXTRACT", "OPENING"},
    "skill.leadTypes": {"TUAN_GOU", "XIAN_SUO", "PENDING"},
    "quickSearch.contentTypes": {"TEMPLATE", "KNOWLEDGE", "LOCATION", "IMAGE", "MINI_PROGRAM"},
    "quickSearch.leadTypes": {"TUAN_GOU", "XIAN_SUO", "GENERAL"},
    "desktopVersion.platforms": {"WINDOWS", "MAC"},
    "desktopVersion.statuses": {"DRAFT", "PUBLISHED", "REVOKED"},
    "desktopVersion.updateStrategies": {"FORCED", "OPTIONAL", "GRADUAL"},
    "notice.levels": {"INFO", "WARN", "ERROR"},
    "notice.sources": {"MANUAL", "AUTO"},
    "notice.statuses": {"PUBLISHED", "SCHEDULED"},
    "notice.publishTypes": {"IMMEDIATE", "SCHEDULED"},
    "auditExport.statuses": {"PROCESSING", "COMPLETED", "FAILED"},
}

DB_ENUM_MAP = {
    ("accounts", "role"): "account.roles",
    ("customers", "lead_type"): "customer.leadTypes",
    ("skill_scene_bindings", "scene"): "skill.scenes",
    ("skill_scene_bindings", "lead_type"): "skill.leadTypes",
    ("quick_search_items", "content_type"): "quickSearch.contentTypes",
    ("quick_search_items", "lead_type"): "quickSearch.leadTypes",
    ("desktop_versions", "platform"): "desktopVersion.platforms",
    ("desktop_versions", "status"): "desktopVersion.statuses",
    ("desktop_versions", "update_strategy"): "desktopVersion.updateStrategies",
    ("system_notices", "level"): "notice.levels",
    ("system_notices", "source"): "notice.sources",
    ("system_notices", "status"): "notice.statuses",
    ("audit_log_exports", "status"): "auditExport.statuses",
}

JAVA_ENUM_SOURCES = {
    "account.roles": ROOT / "src/main/java/com/privateflow/modules/api/Role.java",
    "skill.scenes": ROOT / "src/main/java/com/privateflow/modules/skill/Scene.java",
    "quickSearch.contentTypes": ROOT / "src/main/java/com/privateflow/modules/quicksearch/ContentType.java",
    "desktopVersion.platforms": ROOT / "src/main/java/com/privateflow/modules/versions/DesktopPlatform.java",
    "desktopVersion.statuses": ROOT / "src/main/java/com/privateflow/modules/versions/VersionStatus.java",
    "desktopVersion.updateStrategies": ROOT / "src/main/java/com/privateflow/modules/versions/UpdateStrategy.java",
    "notice.levels": ROOT / "src/main/java/com/privateflow/modules/notices/NoticeLevel.java",
    "notice.sources": ROOT / "src/main/java/com/privateflow/modules/notices/NoticeSource.java",
    "notice.statuses": ROOT / "src/main/java/com/privateflow/modules/notices/NoticeStatus.java",
    "notice.publishTypes": ROOT / "src/main/java/com/privateflow/modules/notices/PublishType.java",
    "auditExport.statuses": ROOT / "src/main/java/com/privateflow/modules/api/audit/AuditExportStatus.java",
}

JAVA_CONSTANT_SOURCES = {
    "customer.leadTypes": ROOT / "src/main/java/com/privateflow/modules/customer/LeadTypes.java",
}

SERVICE_CONTRACTS = [
    {
        "key": "customer.leadTypes",
        "source": ROOT / "src/main/java/com/privateflow/modules/analytics/AnalyticsService.java",
        "kind": "string_tokens",
    },
    {
        "key": "skill.leadTypes",
        "source": ROOT / "src/main/java/com/privateflow/modules/skill/admin/SkillAdminService.java",
        "kind": "list_of_strings",
        "name": "LEAD_TYPES",
    },
    {
        "key": "skill.scenes",
        "source": ROOT / "src/main/java/com/privateflow/modules/skill/admin/SkillAdminService.java",
        "kind": "scene_list",
        "name": "SCENES",
    },
    {
        "key": "quickSearch.leadTypes",
        "source": ROOT / "src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminService.java",
        "kind": "set_of_strings",
        "name": "LEAD_TYPES",
    },
    {
        "key": "quickSearch.contentTypes",
        "source": ROOT / "src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminService.java",
        "kind": "content_type_list",
    },
]

FRONTEND_CONTRACTS = [
    {
        "key": "customer.leadTypes",
        "source": ROOT / "desktop/src/renderer/modules/workbench/WorkbenchPanel.vue",
        "kind": "visible_tokens",
        "required": {"TUAN_GOU", "XIAN_SUO", "PENDING"},
    },
    {
        "key": "customer.leadTypes",
        "source": ROOT / "desktop/src/renderer/modules/batch-template/BatchTemplateOverlay.vue",
        "kind": "visible_tokens",
        "required": {"TUAN_GOU", "XIAN_SUO", "PENDING"},
    },
    {
        "key": "quickSearch.leadTypes",
        "source": ROOT / "desktop/src/renderer/modules/quick-search/QuickSearchOverlay.vue",
        "kind": "visible_tokens",
        "required": {"TUAN_GOU", "XIAN_SUO", "GENERAL"},
    },
    {
        "key": "quickSearch.contentTypes",
        "source": ROOT / "desktop/src/renderer/modules/quick-search/QuickSearchOverlay.vue",
        "kind": "visible_tokens",
        "required": {"TEMPLATE", "KNOWLEDGE", "LOCATION", "IMAGE", "MINI_PROGRAM"},
    },
    {
        "key": "skill.scenes",
        "source": ROOT / "desktop/src/renderer/modules/admin/AdminConsole.vue",
        "kind": "visible_tokens",
        "required": {"OPENING"},
        "allowedMissing": {"CHAT_RECOGNIZE", "ACTIVE_REPLY", "REGENERATE", "PROFILE_EXTRACT"},
        "reason": "Admin console currently exposes JSON action defaults; full scene picker is not implemented.",
    },
    {
        "key": "skill.leadTypes",
        "source": ROOT / "desktop/src/renderer/modules/admin/AdminConsole.vue",
        "kind": "visible_tokens",
        "required": {"PENDING"},
        "allowedMissing": {"TUAN_GOU", "XIAN_SUO"},
        "reason": "Admin console currently exposes JSON action defaults; full lead-type picker is not implemented.",
    },
    {
        "key": "desktopVersion.platforms",
        "source": ROOT / "desktop/src/renderer/modules/admin/AdminConsole.vue",
        "kind": "visible_tokens",
        "required": {"WINDOWS", "MAC"},
    },
    {
        "key": "desktopVersion.updateStrategies",
        "source": ROOT / "desktop/src/renderer/modules/admin/AdminConsole.vue",
        "kind": "visible_tokens",
        "required": {"OPTIONAL"},
        "allowedMissing": {"FORCED", "GRADUAL"},
        "reason": "Admin console currently exposes JSON action defaults; strategy alternatives are accepted by backend but not listed as controls.",
    },
    {
        "key": "notice.levels",
        "source": ROOT / "desktop/src/renderer/modules/admin/AdminConsole.vue",
        "kind": "visible_tokens",
        "required": {"INFO", "WARN"},
        "allowedMissing": {"ERROR"},
        "reason": "Admin console default examples do not create ERROR notices; backend validation accepts ERROR.",
    },
    {
        "key": "notice.publishTypes",
        "source": ROOT / "desktop/src/renderer/modules/admin/AdminConsole.vue",
        "kind": "visible_tokens",
        "required": {"IMMEDIATE", "SCHEDULED"},
    },
]


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def java_enum_values(path: Path) -> set[str]:
    text = read(path)
    match = re.search(r"enum\s+\w+\s*\{(?P<body>.*?)\}", text, re.S)
    if not match:
        raise ValueError(f"enum not found in {path}")
    return set(re.findall(r"\b([A-Z][A-Z0-9_]*)\b", match.group("body")))


def lead_type_constants(path: Path) -> set[str]:
    text = read(path)
    return set(re.findall(r'public\s+static\s+final\s+String\s+\w+\s*=\s*"([A-Z0-9_]+)"', text))


def assignment_body(text: str, name: str) -> str:
    match = re.search(rf"{re.escape(name)}\s*=\s*(?P<body>[^;]+);", text, re.S)
    if not match:
        raise ValueError(f"assignment {name} not found")
    return match.group("body")


def extract_service_values(contract: dict[str, object]) -> set[str]:
    text = read(contract["source"])
    kind = contract["kind"]
    if kind in {"list_of_strings", "set_of_strings"}:
        return set(re.findall(r'"([A-Z0-9_]+)"', assignment_body(text, contract["name"])))
    if kind == "scene_list":
        body = assignment_body(text, contract["name"])
        return set(re.findall(r"Scene\.([A-Z0-9_]+)", body))
    if kind == "content_type_list":
        return set(re.findall(r"ContentType\.([A-Z0-9_]+)", text))
    if kind == "string_tokens":
        expected = EXPECTED[contract["key"]]
        return {value for value in expected if f'"{value}"' in text}
    raise ValueError(f"unsupported service contract kind {kind}")


def visible_tokens(path: Path, expected: set[str]) -> set[str]:
    text = read(path)
    return {value for value in expected if value in text}


def compare(name: str, expected: set[str], actual: set[str], source: str, mismatches: list[dict[str, object]]) -> dict[str, object]:
    missing = sorted(expected - actual)
    extra = sorted(actual - expected)
    result = {
        "name": name,
        "source": source,
        "expected": sorted(expected),
        "actual": sorted(actual),
        "missing": missing,
        "extra": extra,
    }
    if missing or extra:
        mismatches.append(result)
    return result


def main() -> int:
    mismatches: list[dict[str, object]] = []
    checks: list[dict[str, object]] = []
    exceptions: list[dict[str, object]] = []

    for key, path in JAVA_ENUM_SOURCES.items():
        checks.append(compare(f"java.{key}", EXPECTED[key], java_enum_values(path), str(path.relative_to(ROOT)), mismatches))

    for key, path in JAVA_CONSTANT_SOURCES.items():
        checks.append(compare(f"java.{key}", EXPECTED[key], lead_type_constants(path), str(path.relative_to(ROOT)), mismatches))

    for db_key, key in DB_ENUM_MAP.items():
        actual = verify_database_alignment.ENUM_COLUMNS.get(db_key, set())
        checks.append(compare(f"databaseVerifier.{db_key[0]}.{db_key[1]}", EXPECTED[key], set(actual), "scripts/verify_database_alignment.py", mismatches))

    for contract in SERVICE_CONTRACTS:
        key = contract["key"]
        checks.append(compare(
            f"service.{key}",
            EXPECTED[key],
            extract_service_values(contract),
            str(contract["source"].relative_to(ROOT)),
            mismatches,
        ))

    for contract in FRONTEND_CONTRACTS:
        key = contract["key"]
        expected = EXPECTED[key]
        required = set(contract["required"])
        allowed_missing = set(contract.get("allowedMissing", set()))
        actual = visible_tokens(contract["source"], expected)
        missing_required = sorted(required - actual)
        unexpected = sorted(actual - expected)
        missing_optional = sorted(expected - actual - allowed_missing - (expected - required - allowed_missing))
        result = {
            "name": f"frontend.{key}",
            "source": str(contract["source"].relative_to(ROOT)),
            "expected": sorted(expected),
            "required": sorted(required),
            "actual": sorted(actual),
            "missingRequired": missing_required,
            "unexpected": unexpected,
            "allowedMissing": sorted(allowed_missing),
        }
        checks.append(result)
        if allowed_missing:
            exceptions.append({
                "name": result["name"],
                "source": result["source"],
                "allowedMissing": sorted(allowed_missing),
                "reason": contract.get("reason", "documented frontend exposure exception"),
            })
        if missing_required or unexpected or missing_optional:
            mismatches.append(result)

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "checkedContracts": len(checks),
        "mismatches": mismatches,
        "intentionalExceptions": exceptions,
        "checks": checks,
    }
    report_path = REPORT_DIR / "enum_contract_alignment.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"enum_contract_alignment_report={report_path}")
    print(
        f"checked_contracts={len(checks)} mismatches={len(mismatches)} "
        f"intentional_exceptions={len(exceptions)}"
    )
    if mismatches:
        for mismatch in mismatches:
            print(json.dumps(mismatch, ensure_ascii=False), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
