# Enterprise WeCom Smart Sheet Connection Design

## Goal

Connect the current refactored private-domain assistant to one configured
Enterprise WeCom Smart Sheet. The application must read records, create one
deduplicated test record, update that record, and refuse to write formula or
other read-only fields.

## Scope and Boundaries

- Change only `C:\Users\85314\Desktop\私域辅助系统\私域辅助系统`.
- Do not modify, reset, or run source code under `C:\Users\85314\Desktop\私域工具`.
- Preserve the current refactor's recognition queue, supervision, templates,
  and desktop workbench changes.
- Replace the current generic `table.api_base_url` / `table.api_key` gateway
  adapter for real mode. Keep the existing mock-external behavior for local
  non-provider tests.
- Restore the manual-save rule that a request may write only the source table
  row bound to the authorized customer. Client supplied table and row values
  are validation inputs, never write authority.

## Architecture

`HttpWecomTableClient` remains the existing application-facing adapter for
`WecomTableClient` and `SheetClient`. In real mode it delegates to a focused
Smart Sheet record client rather than constructing generic gateway URLs.

The Smart Sheet client chain has five responsibilities:

1. `WecomSmartSheetConfig` reads and validates the fixed `WECOM_*`
   environment variables.
2. `WecomAccessTokenProvider` obtains and caches the Enterprise WeCom access
   token without logging the secret or token.
3. `WecomSmartSheetApiClient` sends bounded requests to the official WeCom
   Smart Sheet endpoints and normalizes provider failures.
4. `WecomSmartSheetFieldCatalog` loads visible field metadata and identifies
   writable fields. Formula and other read-only fields are excluded before a
   write request is created.
5. `WecomSmartSheetRecordClient` performs paginated reads, exact unique-field
   duplicate lookup, create/update confirmation, and value encoding.

The existing field mapping, queue, customer-sync, and profile-save layers keep
their current public contracts. They receive the same `WecomTableClient`
interface and do not receive credentials.

## Configuration and Security

The sole runtime configuration route is process environment variables:

- `WECOM_CORP_ID`
- `WECOM_APP_SECRET`
- `WECOM_SMARTSHEET_DOC_ID`
- `WECOM_SMARTSHEET_SHEET_ID`
- `WECOM_SMARTSHEET_VIEW_ID`
- `WECOM_SMARTSHEET_SOURCE_TABLE`
- `WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE`

Credentials are supplied once outside Git and outside chat. No secret is added
to source code, database seed data, command arguments, screenshots, or logs.
The configuration validator reports only missing variable names.

## Correctness Rules

- A provider request has a finite deadline and no unbounded retry loop.
- A read follows official pagination metadata and rejects malformed or
  inconsistent pages.
- A create first performs an exact unique-field lookup. Concurrent creates
  for the same unique value coordinate locally and return the existing record
  when found.
- An update must target the configured document and source table.
- Field metadata is loaded before writing. Only visible writable fields are
  encoded; formula/read-only fields cause a controlled failure rather than a
  write attempt.
- Manual profile save compares the request table/row with the authorized
  customer's persisted source table/row before mapping or provider calls.

## Verification Gates

1. Add or restore focused unit tests first, run them red, then implement each
   behavior until green.
2. Run the Smart Sheet Spring wiring test, the table-write regression suite,
   and package the backend with no real WeCom credentials.
3. Before any provider request, independently verify that the configured
   database exists, credentials are available in the fixed environment route,
   and the application starts with fake WeCom values.
4. After the local gates pass and the WeCom application has the required Smart
   Sheet permission and trusted egress IP, run exactly one controlled live
   sequence: query, create a dedicated test record, update it, repeat the
   create, and reread it while checking formula protection. Do not delete
   production data or create a formal customer from recognition data.

## Completion Definition

The feature is complete only when the controlled live sequence returns
evidence for all five operations. Passing tests, package creation, or a valid
access token alone are local-readiness evidence, not completion.
