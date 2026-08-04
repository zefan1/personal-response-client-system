# Admin Demo V1 Style Migration

## Goal

Apply the visual language of `admin-complete-system-demo.html` to the complete
desktop management console while preserving every existing feature and its
backend contract.

## Scope

- Update the management-console shell, grouped navigation, toolbar, metric
  cards, panels, tables, filters, forms, drawers, notices, and responsive
  behavior.
- Cover every existing management section: configuration, data and content,
  organization and rules, tags, analytics, version management, notices, audit
  logs, and system health.
- Preserve the existing section keys, request paths, request payloads,
  loading/error states, dialogs, file uploads, CSV exports, and access rules.
- Keep the tag-manager-only view limited to the customer-tag section and its
  tag endpoints.

## Visual Direction

The console will use the demo's warm neutral canvas, paper panels, restrained
orange primary action color, green/warning status colors, compact typography,
thin borders, and dense operational layout. The sidebar will present section
groups with compact page links. The main area will retain the existing content
but use the demo's header, metric row, table density, action hierarchy, and
responsive breakpoints.

## Implementation Boundary

The migration is presentation-only. Existing Vue state, computed data,
event handlers, API helpers, tests, and data-testid attributes stay intact.
Markup changes are limited to semantic wrappers and stable class names needed
to apply the shared visual system. No backend code, database schema, API
contract, role policy, or business rule changes are in scope.

## Error Handling And Safety

Existing loading, disabled, validation, confirmation, and failure states
remain visible after the restyle. Destructive actions continue to use their
current confirmation flow. Responsive changes must retain usable navigation,
tables, forms, drawers, and action buttons at desktop and narrow widths.

## Verification

- Run the focused admin console tests, including navigation and tag-only
  authorization coverage.
- Run desktop type checking and the production build.
- Start the local management console and inspect the rendered admin route.
- Verify representative configuration, tag, analytics, audit, and health
  sections without changing remote or production data.
