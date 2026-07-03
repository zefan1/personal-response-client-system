#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
SRC_DIR = ROOT / "src" / "main" / "java"
ACCEPTANCE_FILES = [
    ROOT / "scripts" / "acceptance_backend_api.py",
    ROOT / "scripts" / "acceptance_real_external_local.py",
]

METHODS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
}

INTENTIONAL_GAPS = {
    ("POST", "/admin/api/v1/skill-prompt/{type}/restore"): "Covered by concrete prompt type restore; path matcher does not generalize type placeholders.",
}


def class_prefix(text: str) -> str:
    match = re.search(r'@RequestMapping\("([^"]+)"\)', text)
    return match.group(1) if match else ""


def normalize_path(path: str) -> str:
    path = re.sub(r"\{([^}:]+):[^}]+\}", r"{\1}", path)
    path = re.sub(r"\?.*$", "", path)
    return path.rstrip("/") or "/"


def java_mappings() -> set[tuple[str, str]]:
    mappings: set[tuple[str, str]] = set()
    pattern = re.compile(r"@(GetMapping|PostMapping|PutMapping|DeleteMapping)\(([^)]*)\)")
    for path in SRC_DIR.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        prefix = class_prefix(text)
        for annotation, args in pattern.findall(text):
            method = METHODS[annotation]
            path_match = re.search(r'"([^"]+)"', args)
            if not path_match:
                continue
            raw = path_match.group(1)
            full = raw if raw.startswith("/") else prefix.rstrip("/") + "/" + raw
            if prefix and raw.startswith("/") and not raw.startswith("/api/") and not raw.startswith("/admin/"):
                full = prefix.rstrip("/") + raw
            mappings.add((method, normalize_path(full)))
    return mappings


def acceptance_paths() -> set[tuple[str, str]]:
    text = "\n".join(path.read_text(encoding="utf-8", errors="replace") for path in ACCEPTANCE_FILES if path.exists())
    paths: set[tuple[str, str]] = set()
    for match in re.finditer(r'api\.request\([^,]+,\s*"([A-Z]+)",\s*f?["\']([^"\']+)["\']', text):
        method, path = match.groups()
        path = re.sub(r"\{[^}]+\}", "{id}", path)
        path = re.sub(r"\?.*$", "", path)
        path = path.replace("{account_id}", "{id}").replace("{skill_id}", "{id}").replace("{env_id}", "{id}")
        path = path.replace("{ds_id}", "{id}").replace("{item_id}", "{id}").replace("{rule_id}", "{id}")
        path = path.replace("{cat_id}", "{id}").replace("{value_id}", "{id}").replace("{notice_id}", "{id}")
        path = path.replace("{scheduled_id}", "{id}").replace("{version_id}", "{id}")
        paths.add((method, normalize_path(path)))
    for match in re.finditer(r'api\.request\([^,]+,\s*"([A-Z]+)",\s*f?["\']([^"\']*\{[^"\']+(?:/admin)?/api/v1/[^"\']+)["\']', text):
        method, path = match.groups()
        path = re.sub(r"\{[^}]+\}", "{id}", path)
        path = re.sub(r"\?.*$", "", path)
        paths.add((method, normalize_path(path)))
    method_window = re.compile(r'"(GET|POST|PUT|DELETE)"\s*,\s*f?["\']([^"\']*(?:/admin)?/api/v1/[^"\']+)["\']')
    for method, path in method_window.findall(text):
        path = re.sub(r"\{[^}]+\}", "{id}", path)
        path = re.sub(r"\?.*$", "", path)
        paths.add((method, normalize_path(path)))
    for path in re.findall(r'["\']((?:/admin)?/api/v1/[^"\']+)["\']', text):
        if "{kind}" in path:
            for kind in ("skill", "image"):
                expanded = path.replace("{kind}", kind)
                expanded = re.sub(r"\{[^}]+\}", "{id}", expanded)
                expanded = re.sub(r"\?.*$", "", expanded)
                for method in ("GET", "POST", "PUT", "DELETE"):
                    paths.add((method, normalize_path(expanded)))
            continue
        normalized = re.sub(r"\{[^}]+\}", "{id}", re.sub(r"\?.*$", "", path))
        paths.add(("GET", normalize_path(normalized)))
    return paths


def path_matches(mapping: tuple[str, str], covered: set[tuple[str, str]]) -> bool:
    method, path = mapping
    if (method, path) in covered:
        return True
    path_regex = "^" + re.escape(path).replace(re.escape("{id}"), r"[^/]+").replace(r"\{phone\}", r"[^/]+").replace(r"\{type\}", r"[^/]+").replace(r"\{key\}", r"[^/]+").replace(r"\{exportId\}", r"[^/]+") + "$"
    normalized_path = re.sub(r"\{[^}]+\}", "{id}", path)
    if (method, normalized_path) in covered:
        return True
    return any(method == candidate_method and re.match(path_regex, candidate_path) for candidate_method, candidate_path in covered)


def main() -> int:
    mappings = java_mappings()
    covered = acceptance_paths()
    missing = sorted(item for item in mappings if not path_matches(item, covered))
    unclassified = [item for item in missing if item not in INTENTIONAL_GAPS]
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "mappingCount": len(mappings),
        "coveredOrMatched": len(mappings) - len(missing),
        "missingCount": len(missing),
        "unclassifiedMissingCount": len(unclassified),
        "missing": [{"method": method, "path": path, "reason": INTENTIONAL_GAPS.get((method, path))} for method, path in missing],
    }
    path = REPORT_DIR / "api_mapping_coverage.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"api_mapping_coverage_report={path}")
    print(
        f"mappings={report['mappingCount']} covered={report['coveredOrMatched']} "
        f"missing={report['missingCount']} unclassified_missing={report['unclassifiedMissingCount']}"
    )
    if unclassified:
        for method, path in unclassified:
            print(f"unclassified missing: {method} {path}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
