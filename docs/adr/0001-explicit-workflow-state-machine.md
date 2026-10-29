# ADR 0001 — Explicit workflow state machine

**Status:** accepted

## Context
A service order must follow a process (diagnosis → approval → execution → completion). Scattering `if (status == ...)` checks across services makes the rules hard to find, test and change.

## Decision
All transition rules live in one framework-free class, `ServiceOrderWorkflow`. It receives a plain `TransitionContext` (current/target state, actor role, whether the actor is the assignee, and the data already filled in) and returns a `TransitionCheck` with one of four outcomes: `OK`, `NOT_ALLOWED` (structurally invalid → HTTP 409), `FORBIDDEN` (role/ownership → 403) or `RULE_VIOLATION` (missing data such as diagnosis → 422).

## Consequences
- Rules are unit-tested without Spring or a database (`ServiceOrderWorkflowTest`).
- The same class powers `GET /allowed-transitions`, so a UI can show only valid buttons.
- Changing the process means editing one table, not hunting through the codebase.
- Trade-off: the workflow is fixed in code. Per-company custom workflows would need the table moved to the database.
