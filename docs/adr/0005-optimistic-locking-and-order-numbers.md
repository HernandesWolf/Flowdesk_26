# ADR 0005 — Concurrency: optimistic locking and per-company order numbers

**Status:** accepted

## Decision
- `ServiceOrder` has a `@Version` column: two people moving the same order at once cannot silently overwrite each other; the loser gets HTTP 409.
- Human-friendly numbers (`OS-000042`) are sequential **per company**. Creating an order takes a pessimistic row lock on the company (`findByIdForUpdate`) to hand out the next number, guaranteeing no gaps from races and no duplicates (also enforced by a unique constraint on `(company_id, order_number)`).

## Consequences
Order creation within one company is serialised for a few milliseconds, which is fine for this domain. A database sequence per company would scale further but is harder to manage.
