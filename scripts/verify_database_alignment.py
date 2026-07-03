#!/usr/bin/env python3
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "db"
DB_NAME = os.environ.get("SMOKE_DB_NAME", "private_domain_assistant_smoke")
DB_USER = os.environ.get("SMOKE_DB_USER", "pda_smoke")
DB_PASSWORD = os.environ.get("SMOKE_DB_PASSWORD", "pda_smoke_pwd")

REQUIRED_COLUMNS = {
    "accounts": {
        "id", "phone", "username", "password_hash", "display_name", "role", "leader_id",
        "is_enabled", "last_login_at", "created_at", "updated_at",
    },
    "datasources": {
        "id", "name", "sheet_id", "source_table", "description", "is_enabled",
        "created_by", "created_at", "updated_at",
    },
    "datasource_field_mappings": {
        "id", "source_table", "source_field", "target_field", "transform_rule",
        "is_enabled", "created_at", "updated_at",
    },
    "datasource_mapping_versions": {
        "id", "datasource_id", "version", "mappings_json", "mapping_count",
        "change_summary", "changed_by", "created_at",
    },
    "customer_import_log": {
        "id", "file_name", "total_rows", "created_count", "updated_count",
        "skipped_count", "error_detail", "imported_by", "created_at",
    },
    "customers": {
        "id", "phone", "nickname", "source_channel", "lead_type", "assigned_keeper",
        "customer_stage", "next_followup_at", "last_followup_at", "source_table",
        "source_row_id", "synced_at", "version", "created_at", "updated_at",
    },
    "system_configs": {"config_key", "config_value", "description", "updated_by", "created_at", "updated_at"},
    "skill_scene_bindings": {
        "id", "skill_id", "skill_name", "scene", "lead_type", "priority",
        "enabled", "last_tested_at", "created_at", "updated_at",
    },
    "quick_search_items": {
        "id", "content_type", "scene", "lead_type", "title", "shortcut_code",
        "content", "image_url", "sort_order", "is_enabled", "created_by", "created_at", "updated_at",
    },
    "system_notices": {
        "id", "notice_id", "title", "content", "level", "source", "status",
        "is_stopped", "publish_at", "pushed_at", "expire_at", "stopped_at",
        "created_by", "created_at", "updated_at",
    },
    "desktop_versions": {
        "id", "version", "platform", "status", "update_strategy", "gradual_percent",
        "download_url", "file_size", "changelog", "revoked_at", "revoke_reason",
        "alternative_version", "published_at", "created_by", "created_at", "updated_at",
    },
    "audit_logs": {
        "id", "action", "operator", "target_type", "target_id", "detail", "created_at",
    },
    "tag_categories": {
        "id", "category_key", "category_name", "bound_field", "is_builtin",
        "is_enabled", "sort_order", "created_at", "updated_at",
    },
    "tag_values": {
        "id", "category_id", "tag_value", "display_name", "is_enabled",
        "sort_order", "created_at", "updated_at",
    },
}


def mysql_query(sql: str) -> str:
    cmd = [
        "wsl", "-d", "Ubuntu", "--", "bash", "-lc",
        "mysql --batch --raw --skip-column-names "
        f"-u{DB_USER} -p{DB_PASSWORD} {DB_NAME} -e {json.dumps(sql)}",
    ]
    completed = subprocess.run(cmd, cwd=ROOT, text=True, encoding="utf-8", errors="replace", capture_output=True)
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip())
    return completed.stdout


def load_schema() -> dict[str, dict[str, dict[str, str]]]:
    rows = mysql_query("""
        SELECT table_name, column_name, column_type, is_nullable, COALESCE(column_default, '')
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
        ORDER BY table_name, ordinal_position
    """)
    schema: dict[str, dict[str, dict[str, str]]] = {}
    for line in rows.splitlines():
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) != 5:
            continue
        table, column, column_type, nullable, default = parts
        schema.setdefault(table, {})[column] = {
            "type": column_type,
            "nullable": nullable,
            "default": default,
        }
    return schema


def main() -> int:
    schema = load_schema()
    missing: dict[str, list[str]] = {}
    for table, columns in REQUIRED_COLUMNS.items():
        actual = set(schema.get(table, {}))
        absent = sorted(columns - actual)
        if absent:
            missing[table] = absent
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "database": DB_NAME,
        "tables": len(schema),
        "requiredTables": len(REQUIRED_COLUMNS),
        "missing": missing,
        "schema": schema,
    }
    report_path = REPORT_DIR / "schema_alignment_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"schema_alignment_report={report_path}")
    print(f"tables={len(schema)} required={len(REQUIRED_COLUMNS)} missing_tables={len(missing)}")
    if missing:
        for table, columns in missing.items():
            print(f"missing {table}: {', '.join(columns)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
