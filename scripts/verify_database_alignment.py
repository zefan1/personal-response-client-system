#!/usr/bin/env python3
import json
import os
import re
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

MIGRATION_DIR = ROOT / "src" / "main" / "resources" / "db" / "migration"
JAVA_DIR = ROOT / "src" / "main" / "java"

SQL_TABLE_PATTERN = re.compile(
    r"\b(?:FROM|JOIN|UPDATE|INTO|DELETE\s+FROM)\s+`?([a-zA-Z][a-zA-Z0-9_]*)`?",
    re.IGNORECASE,
)
IGNORED_SQL_WORDS = {
    "SELECT", "WHERE", "SET", "VALUES", "DATABASE", "DUAL", "SKIP",
    "version", "nickname", "sent_at", "config",
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


def migration_tables() -> set[str]:
    tables: set[str] = set()
    for path in MIGRATION_DIR.glob("*.sql"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in re.finditer(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?", text, re.IGNORECASE):
            tables.add(match.group(1))
    return tables


def migration_config_keys() -> set[str]:
    keys: set[str] = set()
    for path in MIGRATION_DIR.glob("*.sql"):
        text = path.read_text(encoding="utf-8", errors="replace")
        for match in re.finditer(r"['\"]([a-zA-Z][a-zA-Z0-9_.-]+)['\"]\s*,", text):
            key = match.group(1)
            if "." in key and not key.startswith("http"):
                keys.add(key)
    return keys


def live_config_keys() -> set[str]:
    rows = mysql_query("SELECT config_key FROM system_configs")
    return {line.strip() for line in rows.splitlines() if line.strip()}


def repository_table_references() -> dict[str, list[str]]:
    refs: dict[str, set[str]] = {}
    for path in JAVA_DIR.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "JdbcTemplate" not in text and "jdbcTemplate" not in text:
            continue
        found: set[str] = set()
        for match in SQL_TABLE_PATTERN.finditer(text):
            table = match.group(1)
            if table.lower() not in {word.lower() for word in IGNORED_SQL_WORDS}:
                found.add(table)
        if found:
            refs[str(path.relative_to(ROOT))] = found
    return {path: sorted(tables) for path, tables in sorted(refs.items())}


def main() -> int:
    schema = load_schema()
    missing: dict[str, list[str]] = {}
    for table, columns in REQUIRED_COLUMNS.items():
        actual = set(schema.get(table, {}))
        absent = sorted(columns - actual)
        if absent:
            missing[table] = absent
    expected_tables = migration_tables()
    missing_tables = sorted(expected_tables - set(schema))
    expected_configs = migration_config_keys()
    actual_configs = live_config_keys()
    missing_configs = sorted(expected_configs - actual_configs)
    repository_refs = repository_table_references()
    referenced_tables = sorted({table for tables in repository_refs.values() for table in tables})
    missing_repository_tables = sorted(set(referenced_tables) - set(schema))
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "database": DB_NAME,
        "tables": len(schema),
        "requiredTables": len(REQUIRED_COLUMNS),
        "migrationTables": len(expected_tables),
        "missing": missing,
        "missingMigrationTables": missing_tables,
        "migrationConfigKeys": len(expected_configs),
        "missingConfigKeys": missing_configs,
        "repositoryReferencedTables": len(referenced_tables),
        "missingRepositoryTables": missing_repository_tables,
        "repositoryReferences": repository_refs,
        "schema": schema,
    }
    report_path = REPORT_DIR / "schema_alignment_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"schema_alignment_report={report_path}")
    print(
        f"tables={len(schema)} required={len(REQUIRED_COLUMNS)} migration_tables={len(expected_tables)} "
        f"missing_required_tables={len(missing)} missing_migration_tables={len(missing_tables)} "
        f"missing_config_keys={len(missing_configs)} missing_repository_tables={len(missing_repository_tables)}"
    )
    if missing or missing_tables or missing_configs or missing_repository_tables:
        for table, columns in missing.items():
            print(f"missing {table}: {', '.join(columns)}", file=sys.stderr)
        for table in missing_tables:
            print(f"missing migration table: {table}", file=sys.stderr)
        for key in missing_configs:
            print(f"missing config key: {key}", file=sys.stderr)
        for table in missing_repository_tables:
            print(f"missing repository table: {table}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
