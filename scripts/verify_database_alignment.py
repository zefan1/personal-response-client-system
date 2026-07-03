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

EXPECTED_COLUMN_PROPERTIES = {
    "accounts": {
        "username": {"nullable": "NO"},
        "password_hash": {"nullable": "NO"},
        "display_name": {"nullable": "NO"},
        "role": {"nullable": "NO"},
        "is_enabled": {"nullable": "NO", "default": "1"},
    },
    "customers": {
        "phone": {"nullable": "NO"},
        "lead_type": {"nullable": "YES"},
        "version": {"nullable": "NO", "default": "0"},
    },
    "datasources": {
        "name": {"nullable": "NO"},
        "sheet_id": {"nullable": "NO"},
        "source_table": {"nullable": "NO"},
        "is_enabled": {"nullable": "NO", "default": "1"},
    },
    "datasource_field_mappings": {
        "source_table": {"nullable": "NO"},
        "source_field": {"nullable": "NO"},
        "target_field": {"nullable": "NO"},
        "is_enabled": {"nullable": "NO", "default": "1"},
    },
    "skill_scene_bindings": {
        "skill_id": {"nullable": "NO"},
        "scene": {"nullable": "NO"},
        "lead_type": {"nullable": "NO"},
        "priority": {"nullable": "NO", "default": "0"},
        "enabled": {"nullable": "NO", "default": "1"},
    },
    "quick_search_items": {
        "content_type": {"nullable": "NO"},
        "lead_type": {"nullable": "NO", "default": "GENERAL"},
        "title": {"nullable": "NO"},
        "shortcut_code": {"nullable": "NO"},
        "content": {"nullable": "NO"},
        "sort_order": {"nullable": "NO", "default": "0"},
        "is_enabled": {"nullable": "NO", "default": "1"},
    },
    "desktop_versions": {
        "version": {"nullable": "NO"},
        "platform": {"nullable": "NO"},
        "status": {"nullable": "NO", "default": "DRAFT"},
        "update_strategy": {"nullable": "NO", "default": "OPTIONAL"},
        "gradual_percent": {"nullable": "YES"},
    },
    "system_notices": {
        "notice_id": {"nullable": "NO"},
        "title": {"nullable": "NO"},
        "content": {"nullable": "NO"},
        "level": {"nullable": "NO", "default": "INFO"},
        "source": {"nullable": "NO", "default": "MANUAL"},
        "status": {"nullable": "NO", "default": "PUBLISHED"},
        "is_stopped": {"nullable": "NO", "default": "0"},
        "publish_at": {"nullable": "NO"},
        "expire_at": {"nullable": "NO"},
        "created_by": {"nullable": "NO"},
    },
    "audit_log_exports": {
        "export_id": {"nullable": "NO"},
        "status": {"nullable": "NO", "default": "PROCESSING"},
        "filters_json": {"nullable": "NO"},
        "total_count": {"nullable": "NO", "default": "0"},
        "expire_at": {"nullable": "NO"},
        "created_by": {"nullable": "NO"},
    },
}

ENUM_COLUMNS = {
    ("accounts", "role"): {"ADMIN", "LEADER", "KEEPER"},
    ("customers", "lead_type"): {"TUAN_GOU", "XIAN_SUO", "PENDING"},
    ("skill_scene_bindings", "scene"): {"CHAT_RECOGNIZE", "ACTIVE_REPLY", "REGENERATE", "PROFILE_EXTRACT", "OPENING"},
    ("skill_scene_bindings", "lead_type"): {"TUAN_GOU", "XIAN_SUO", "PENDING"},
    ("quick_search_items", "content_type"): {"TEMPLATE", "KNOWLEDGE", "LOCATION", "IMAGE", "MINI_PROGRAM"},
    ("quick_search_items", "lead_type"): {"TUAN_GOU", "XIAN_SUO", "GENERAL"},
    ("desktop_versions", "platform"): {"WINDOWS", "MAC"},
    ("desktop_versions", "status"): {"DRAFT", "PUBLISHED", "REVOKED"},
    ("desktop_versions", "update_strategy"): {"FORCED", "OPTIONAL", "GRADUAL"},
    ("system_notices", "level"): {"INFO", "WARN", "ERROR"},
    ("system_notices", "source"): {"MANUAL", "AUTO"},
    ("system_notices", "status"): {"PUBLISHED", "SCHEDULED"},
    ("audit_log_exports", "status"): {"PROCESSING", "COMPLETED", "FAILED"},
}

MIGRATION_DIR = ROOT / "src" / "main" / "resources" / "db" / "migration"
JAVA_DIR = ROOT / "src" / "main" / "java"

SQL_TABLE_PATTERN = re.compile(
    r"\b(?:FROM|JOIN|UPDATE|INTO|DELETE\s+FROM)\s+`?([a-zA-Z][a-zA-Z0-9_]*)`?",
    re.IGNORECASE,
)
SQL_TEXT_BLOCK_PATTERN = re.compile(r'"""(.*?)"""', re.DOTALL)
SQL_QUOTED_PATTERN = re.compile(r'"([^"\n]*(?:SELECT|INSERT|UPDATE|DELETE|FROM|JOIN|INTO)[^"\n]*)"', re.IGNORECASE)
INSERT_COLUMNS_PATTERN = re.compile(
    r"\bINSERT\s+INTO\s+`?([a-zA-Z][a-zA-Z0-9_]*)`?\s*\((.*?)\)",
    re.IGNORECASE | re.DOTALL,
)
UPDATE_SET_PATTERN = re.compile(
    r"\bUPDATE\s+`?([a-zA-Z][a-zA-Z0-9_]*)`?\s+SET\s+(.*?)(?:\bWHERE\b|\bON\b|\Z)",
    re.IGNORECASE | re.DOTALL,
)
ALIAS_PATTERN = re.compile(
    r"\b(?:FROM|JOIN)\s+`?([a-zA-Z][a-zA-Z0-9_]*)`?(?:\s+(?:AS\s+)?([a-zA-Z][a-zA-Z0-9_]*))?",
    re.IGNORECASE,
)
QUALIFIED_COLUMN_PATTERN = re.compile(r"\b([a-zA-Z][a-zA-Z0-9_]*)\.([a-zA-Z][a-zA-Z0-9_]*)\b")
IGNORED_SQL_WORDS = {
    "SELECT", "WHERE", "SET", "VALUES", "DATABASE", "DUAL", "SKIP",
    "version", "nickname", "sent_at", "config",
}
IGNORED_SQL_ALIASES = {
    "ON", "WHERE", "LEFT", "RIGHT", "INNER", "OUTER", "JOIN", "ORDER", "GROUP",
    "LIMIT", "FOR", "SET", "VALUES", "DUPLICATE", "KEY", "UPDATE",
}
IGNORED_QUALIFIED_PREFIXES = {
    "com", "privateflow", "modules", "java", "time", "util", "org", "springframework",
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


def java_sql_fragments(text: str) -> list[str]:
    fragments = SQL_TEXT_BLOCK_PATTERN.findall(text)
    fragments.extend(match.group(1) for match in SQL_QUOTED_PATTERN.finditer(text))
    return [fragment for fragment in fragments if re.search(r"\b(SELECT|INSERT|UPDATE|DELETE|FROM|JOIN|INTO)\b", fragment, re.IGNORECASE)]


def split_sql_columns(raw: str) -> list[str]:
    columns: list[str] = []
    for item in raw.split(","):
        column = item.strip()
        column = re.sub(r"--.*$", "", column).strip()
        column = column.strip("` ")
        if column and re.match(r"^[a-zA-Z][a-zA-Z0-9_]*$", column):
            columns.append(column)
    return columns


def repository_column_references(schema: dict[str, dict[str, dict[str, str]]]) -> list[dict[str, str]]:
    violations: list[dict[str, str]] = []
    for path in JAVA_DIR.rglob("*.java"):
        text = path.read_text(encoding="utf-8", errors="replace")
        if "JdbcTemplate" not in text and "jdbcTemplate" not in text:
            continue
        for fragment in java_sql_fragments(text):
            sql = re.sub(r"\s+", " ", fragment).strip()
            for table, raw_columns in INSERT_COLUMNS_PATTERN.findall(sql):
                if table not in schema:
                    continue
                for column in split_sql_columns(raw_columns):
                    if column not in schema[table]:
                        violations.append({
                            "file": str(path.relative_to(ROOT)),
                            "table": table,
                            "column": column,
                            "context": "insert",
                        })
            for table, raw_set in UPDATE_SET_PATTERN.findall(sql):
                if table not in schema:
                    continue
                for column in re.findall(r"`?([a-zA-Z][a-zA-Z0-9_]*)`?\s*=", raw_set):
                    if column not in schema[table]:
                        violations.append({
                            "file": str(path.relative_to(ROOT)),
                            "table": table,
                            "column": column,
                            "context": "update",
                        })
            alias_to_table: dict[str, str] = {}
            for table, alias in ALIAS_PATTERN.findall(sql):
                if table not in schema:
                    continue
                alias_to_table[table] = table
                if alias and alias.upper() not in IGNORED_SQL_ALIASES:
                    alias_to_table[alias] = table
            for alias, column in QUALIFIED_COLUMN_PATTERN.findall(sql):
                if alias in IGNORED_QUALIFIED_PREFIXES:
                    continue
                table = alias_to_table.get(alias)
                if not table:
                    continue
                if column not in schema[table]:
                    violations.append({
                        "file": str(path.relative_to(ROOT)),
                        "table": table,
                        "column": column,
                        "context": f"qualified:{alias}",
                    })
    unique: list[dict[str, str]] = []
    seen: set[tuple[str, str, str, str]] = set()
    for violation in violations:
        key = (violation["file"], violation["table"], violation["column"], violation["context"])
        if key not in seen:
            unique.append(violation)
            seen.add(key)
    return unique


def normalize_default(value: str) -> str:
    stripped = (value or "").strip()
    if len(stripped) >= 2 and stripped[0] == "'" and stripped[-1] == "'":
        return stripped[1:-1]
    return stripped


def validate_column_properties(schema: dict[str, dict[str, dict[str, str]]]) -> list[dict[str, str]]:
    violations: list[dict[str, str]] = []
    for table, columns in EXPECTED_COLUMN_PROPERTIES.items():
        for column, expected in columns.items():
            actual = schema.get(table, {}).get(column)
            if not actual:
                continue
            expected_nullable = expected.get("nullable")
            if expected_nullable and actual["nullable"] != expected_nullable:
                violations.append({
                    "table": table,
                    "column": column,
                    "property": "nullable",
                    "expected": expected_nullable,
                    "actual": actual["nullable"],
                })
            if "default" in expected:
                actual_default = normalize_default(actual["default"])
                expected_default = expected["default"]
                if actual_default != expected_default:
                    violations.append({
                        "table": table,
                        "column": column,
                        "property": "default",
                        "expected": expected_default,
                        "actual": actual_default,
                    })
    return violations


def invalid_enum_values(schema: dict[str, dict[str, dict[str, str]]]) -> list[dict[str, object]]:
    invalid: list[dict[str, object]] = []
    for (table, column), allowed in ENUM_COLUMNS.items():
        if column not in schema.get(table, {}):
            continue
        quoted = ", ".join("'" + value.replace("'", "''") + "'" for value in sorted(allowed))
        rows = mysql_query(
            f"SELECT DISTINCT {column} FROM {table} "
            f"WHERE {column} IS NOT NULL AND {column} NOT IN ({quoted})"
        )
        values = sorted(line.strip() for line in rows.splitlines() if line.strip())
        if values:
            invalid.append({
                "table": table,
                "column": column,
                "allowed": sorted(allowed),
                "invalidValues": values,
            })
    return invalid


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
    column_property_violations = validate_column_properties(schema)
    enum_violations = invalid_enum_values(schema)
    repository_column_violations = repository_column_references(schema)
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
        "repositoryColumnViolations": repository_column_violations,
        "columnPropertyViolations": column_property_violations,
        "enumViolations": enum_violations,
        "schema": schema,
    }
    report_path = REPORT_DIR / "schema_alignment_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"schema_alignment_report={report_path}")
    print(
        f"tables={len(schema)} required={len(REQUIRED_COLUMNS)} migration_tables={len(expected_tables)} "
        f"missing_required_tables={len(missing)} missing_migration_tables={len(missing_tables)} "
        f"missing_config_keys={len(missing_configs)} missing_repository_tables={len(missing_repository_tables)} "
        f"repository_column_violations={len(repository_column_violations)} "
        f"column_property_violations={len(column_property_violations)} enum_violations={len(enum_violations)}"
    )
    if missing or missing_tables or missing_configs or missing_repository_tables or repository_column_violations or column_property_violations or enum_violations:
        for table, columns in missing.items():
            print(f"missing {table}: {', '.join(columns)}", file=sys.stderr)
        for table in missing_tables:
            print(f"missing migration table: {table}", file=sys.stderr)
        for key in missing_configs:
            print(f"missing config key: {key}", file=sys.stderr)
        for table in missing_repository_tables:
            print(f"missing repository table: {table}", file=sys.stderr)
        for violation in repository_column_violations:
            print(
                "repository column mismatch "
                f"{violation['file']} {violation['table']}.{violation['column']} "
                f"({violation['context']})",
                file=sys.stderr,
            )
        for violation in column_property_violations:
            print(
                "column property mismatch "
                f"{violation['table']}.{violation['column']} {violation['property']}: "
                f"expected {violation['expected']} actual {violation['actual']}",
                file=sys.stderr,
            )
        for violation in enum_violations:
            print(
                f"invalid enum values {violation['table']}.{violation['column']}: "
                f"{', '.join(violation['invalidValues'])}",
                file=sys.stderr,
            )
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
