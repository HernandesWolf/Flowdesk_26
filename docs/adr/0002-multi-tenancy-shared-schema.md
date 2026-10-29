# ADR 0002 — Multi-tenancy with a shared schema and `company_id`

**Status:** accepted

## Context
Many companies share one deployment. One company must never see another's data.

## Decision
Shared database, shared schema; every business table has `company_id`. The tenant is **never** read from the request: it comes from the authenticated principal (`CurrentUser`), which is rebuilt from the database on every request. Every repository lookup includes `companyId` (`findByIdAndCompanyId`, `ServiceOrderSpecifications.forCompany`, …). A foreign id behaves exactly like a missing one and returns **404**, so even the existence of other tenants' data is not revealed. Technicians are additionally restricted to orders assigned to them.

## Consequences
- Simple operations and cheap onboarding of new companies.
- Isolation depends on discipline in the service layer; `MultiTenancyIntegrationTest` attacks every route with a second tenant to guard it.
- The only intentional cross-tenant code is the system `SlaMonitor`, and each event it emits carries the order's own `companyId`.
- Possible hardening: PostgreSQL Row Level Security as a second line of defence.
