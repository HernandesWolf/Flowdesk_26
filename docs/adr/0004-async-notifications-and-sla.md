# ADR 0004 — Asynchronous notifications and an idempotent SLA monitor

**Status:** accepted

## Decision
Services publish domain events (`Assigned`, `StatusChanged`, `SlaAlert`). `NotificationListener` handles them with `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`, so notifications only exist for changes that were committed and a failing e-mail can never roll back or slow down the business operation. Delivery creates an in-app notification and sends an e-mail through the `EmailGateway` port (default adapter just logs).

`SlaMonitor` runs on a schedule, finds orders about to miss or already past their due date and raises **one** alert per order: `due_soon_notified` / `breach_notified` flags make it idempotent. Changing an order's priority recalculates its due date and re-arms the flags.

## Consequences
- Fast, resilient request path; clear extension point for SMTP, WhatsApp, etc.
- In-process events are lost if the JVM dies between commit and handling. A transactional outbox plus a broker (RabbitMQ/Kafka) is the documented next step if delivery guarantees become a requirement.
- With several instances, the monitor would need a distributed lock (e.g. ShedLock) to avoid duplicate work.
