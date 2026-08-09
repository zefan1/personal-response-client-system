# Two Auxiliary Smart Sheet Projections

## Goal

Connect the API-created Smart Sheets `客资分配表（辅助）` and `新客到店衔接表（辅助）` through the configured WeCom server relay. The system writes display copies to them without accepting any reverse updates.

## Boundaries

- MariaDB remains the authoritative customer record.
- Smart Sheet changes never update customer data.
- Both destinations use the existing `RELAY` or `DIRECT` WeCom connection mode. In relay mode, only the relay server calls WeCom.
- A failed write is retried through the existing pending-write mechanism and must not block the originating customer operation.
- Existing single-table behavior remains unchanged; the two auxiliary projections are enabled only when their server identifiers are configured.

## Model

Add two fixed server configurations, one for each table. Each configuration contains only the API-created document, sheet, and view identifiers.

| Target | Source event | Initial projected data |
| --- | --- | --- |
| `LEAD_ASSIGNMENT` | Customer assignment changes | Customer ID, name, contact, lead type, assigned keeper, store, assignment status, update time |
| `NEW_CUSTOMER_ARRIVAL_HANDOFF` | New-customer appointment or arrival changes | Customer ID, name, contact, appointment data, arrival status, handoff keeper, update time |

The two new Smart Sheets are not imported as customer sources. They are outbound display projections only. There is no generic target registry, mapping editor, or dynamic table selection.

## Connection Flow

1. The server is configured once with the identifiers returned for each API-created table.
2. A customer assignment change queues an upsert to `客资分配表（辅助）`.
3. A new-customer appointment or arrival change queues an upsert to `新客到店衔接表（辅助）`.
4. The WeCom client sends the queued upsert through the chosen relay or direct endpoint.
5. The existing queue retries failures; the local customer transaction is already complete.

## Administration

No new target-management page is added. The existing connection-mode control remains global because it selects the network path, not a table. The two table identifiers stay in server deployment configuration.

## Error Handling

- Do not overwrite a field with an invalid option or formula value.
- Log relay authentication and trusted-IP failures with the fixed target name.

## Verification

- Unit tests cover the two event routes and their queue payloads.
- One integration test covers both targets through the existing relay contract.
- Live acceptance verifies each target independently: read, create a controlled record, update it, verify duplicate protection, and read back the result.
