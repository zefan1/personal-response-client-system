#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_PATH = ROOT / ".tools" / "contracts" / "admin_product_surface.json"

SOURCE_TARGETS = [
    ROOT / "desktop" / "src" / "renderer" / "App.vue",
    ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue",
]

FORBIDDEN_PATTERNS = [
    ("raw_request_body", re.compile(r"请求体\s*JSON")),
    ("target_id", re.compile(r"目标\s*ID", re.IGNORECASE)),
    ("admin_get_path", re.compile(r"\bGET\s+/admin", re.IGNORECASE)),
    ("admin_post_path", re.compile(r"\bPOST\s+/admin", re.IGNORECASE)),
    ("admin_put_path", re.compile(r"\bPUT\s+/admin", re.IGNORECASE)),
    ("admin_delete_path", re.compile(r"\bDELETE\s+/admin", re.IGNORECASE)),
    ("api_execute_copy", re.compile(r"(执行接口|接口路径|发送\s*JSON|复制\s*id|复制\s*ID)")),
]

REQUIRED_MARKERS = [
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "ops-admin-shell"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "配置中心"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "Skill 场景管理"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "数据源与内容"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "客户数据对接"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "速搜内容管理"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "组织与规则"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "账号与权限"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "跟进规则引擎配置"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "客户标签与分层"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "分析与系统"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "运营分析看板"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "版本管理"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "系统公告"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "操作审计日志"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "系统健康监控"),
    (ROOT / "desktop" / "src" / "renderer" / "modules" / "admin" / "AdminConsole.vue", "runtimeMode"),
    (ROOT / "desktop" / "src" / "renderer" / "App.vue", "#/admin/dev-console"),
    (ROOT / "desktop" / "src" / "renderer" / "App.vue", "#/desktop"),
    (ROOT / "desktop" / "src" / "renderer" / "App.vue", "isElectronRuntime"),
    (ROOT / "desktop" / "src" / "renderer" / "App.vue", "return '#/admin'"),
    (ROOT / "desktop" / "src" / "renderer" / "App.vue", "normalizeInitialHash"),
    (ROOT / "desktop" / "src" / "renderer" / "App.vue", "hasDesktopBridge()"),
    (ROOT / "desktop" / "src" / "renderer" / "App.vue", "window.history.replaceState(null, '', '#/admin')"),
]


def scan_file(path: Path) -> list[dict[str, object]]:
    text = path.read_text(encoding="utf-8")
    violations: list[dict[str, object]] = []
    for name, pattern in FORBIDDEN_PATTERNS:
        for match in pattern.finditer(text):
            line = text.count("\n", 0, match.start()) + 1
            violations.append({
                "file": str(path.relative_to(ROOT)),
                "line": line,
                "rule": name,
                "match": match.group(0),
            })
    return violations


def main() -> int:
    violations: list[dict[str, object]] = []
    missing: list[dict[str, str]] = []

    for path in SOURCE_TARGETS:
        if not path.exists():
            missing.append({"file": str(path.relative_to(ROOT)), "marker": "<file>"})
            continue
        violations.extend(scan_file(path))

    for path, marker in REQUIRED_MARKERS:
        if not path.exists():
            missing.append({"file": str(path.relative_to(ROOT)), "marker": marker})
            continue
        if marker not in path.read_text(encoding="utf-8"):
            missing.append({"file": str(path.relative_to(ROOT)), "marker": marker})

    report = {
        "passed": not violations and not missing,
        "scannedFiles": [str(path.relative_to(ROOT)) for path in SOURCE_TARGETS],
        "excludedDebugConsole": "desktop/src/renderer/modules/admin/AdminDevConsole.vue",
        "violations": violations,
        "missingMarkers": missing,
    }
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"admin_product_surface_report={REPORT_PATH}")
    print(f"passed={str(report['passed']).lower()} violations={len(violations)} missingMarkers={len(missing)}")
    if violations or missing:
        print(json.dumps({"violations": violations[:20], "missingMarkers": missing[:20]}, ensure_ascii=False), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
