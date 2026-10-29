# ADR 0003 — Append-only audit trail written in the same transaction

**Status:** accepted

## Decision
Every meaningful change (creation, edits, assignment, priority, diagnosis/resolution, status, records, attachments, SLA alerts) writes an `audit_logs` row with actor, action, field, old and new value. `AuditService.record` uses `Propagation.MANDATORY`, so it can only run inside the transaction of the change it describes: either both are committed or neither is. The application never updates or deletes audit rows. System actions (the SLA monitor) are recorded as actor "System".

## Consequences
- A trustworthy history for customers, disputes and productivity analysis (`GET /service-orders/{id}/timeline`).
- Explicit calls in services are easy to read and test, at the cost of remembering to call them. Hibernate Envers was considered but gives less control over human-readable entries.
