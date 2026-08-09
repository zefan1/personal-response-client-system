# Two Auxiliary Smart Sheet Projections

## Goal

Connect the API-created Smart Sheets `客资分配表（辅助）` and `新客到店衔接表（辅助）` through the configured WeCom server relay. The system writes display copies to them without accepting any reverse updates.

## Boundaries

- MariaDB remains the authoritative customer record.
- Smart Sheet changes never update customer data.
- Both destinations use the existing `RELAY` or `DIRECT` WeCom connection mode. In relay mode, only the relay server calls WeCom.
- A failed write is retried through the existing pending-write mechanism and must not block the originating customer operation.
- Existing single-table behavior remains available until an administrator enables a target.

## Model

Store two independently enabled projection targets. Each target contains the API-created document, sheet, and view identifiers; its table URL; its source event; and its field mapping.

| Target | Source event | Initial projected data |
| --- | --- | --- |
| `LEAD_ASSIGNMENT` | Customer assignment changes | Customer ID, name, contact, lead type, assigned keeper, store, assignment status, update time |
| `NEW_CUSTOMER_ARRIVAL_HANDOFF` | New-customer appointment or arrival changes | Customer ID, name, contact, appointment data, arrival status, handoff keeper, update time |

The two new Smart Sheets are not imported as customer sources. They are outbound display projections only.

## Connection Flow

1. An administrator pastes an API-created Smart Sheet URL and selects its projection type.
2. The backend validates the URL through the configured WeCom endpoint, discovers its Smart Sheet, grid view, and writable fields, then stores that target independently.
3. On the matching customer event, the projection service resolves the target mapping and queues an upsert.
4. The WeCom client sends the queued upsert through the chosen relay or direct endpoint.
5. The queue records retries and exposes a per-target failure state; the local customer transaction is already complete.

## Administration

The admin console shows a compact target list with the projection type, connected table name, enabled state, last write status, and actions to test, edit, or disable each target. The existing connection-mode control remains global because it selects the network path, not a table.

## Error Handling

- Reject URLs that are not API-created Smart Sheets or that cannot expose a writable grid view.
- Reject duplicate targets for the same projection type.
- Do not overwrite a field with an invalid option or formula value.
- Report relay authentication and trusted-IP failures as connection failures for the specific target.

## Verification

- Unit tests cover URL validation, duplicate prevention, event routing, mappings, and queue payloads.
- Integration tests cover both targets through the existing relay contract.
- Live acceptance verifies each target independently: read, create a controlled record, update it, verify duplicate protection, and read back the result.
