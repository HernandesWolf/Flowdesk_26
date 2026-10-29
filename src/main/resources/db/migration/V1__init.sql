-- FlowDesk initial schema.
-- Multi-tenancy: every business table carries company_id and every query is scoped by it.

create table companies (
    id                uuid primary key,
    name              varchar(150) not null,
    next_order_number bigint       not null default 1,
    created_at        timestamptz  not null
);

create table users (
    id            uuid primary key,
    company_id    uuid         not null references companies (id),
    name          varchar(120) not null,
    email         varchar(180) not null,
    password_hash varchar(100) not null,
    role          varchar(20)  not null,
    active        boolean      not null default true,
    created_at    timestamptz  not null,
    constraint uq_users_email unique (email),
    constraint ck_users_role check (role in ('ADMIN', 'MANAGER', 'TECHNICIAN'))
);
create index idx_users_company on users (company_id);

create table customers (
    id         uuid primary key,
    company_id uuid         not null references companies (id),
    name       varchar(150) not null,
    email      varchar(180),
    phone      varchar(30),
    document   varchar(20),
    address    varchar(300),
    created_at timestamptz  not null
);
create index idx_customers_company_name on customers (company_id, name);

create table service_orders (
    id                  uuid primary key,
    company_id          uuid         not null references companies (id),
    order_number        bigint       not null,
    title               varchar(200) not null,
    description         text,
    customer_id         uuid         not null references customers (id),
    assignee_id         uuid references users (id),
    created_by_id       uuid         not null references users (id),
    priority            varchar(10)  not null,
    status              varchar(20)  not null,
    diagnosis           text,
    resolution          text,
    cancellation_reason text,
    due_at              timestamptz  not null,
    due_soon_notified   boolean      not null default false,
    breach_notified     boolean      not null default false,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    completed_at        timestamptz,
    version             bigint       not null default 0,
    constraint uq_orders_company_number unique (company_id, order_number),
    constraint ck_orders_priority check (priority in ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    constraint ck_orders_status check (status in
        ('OPEN', 'IN_DIAGNOSIS', 'WAITING_APPROVAL', 'IN_EXECUTION', 'COMPLETED', 'CANCELLED'))
);
create index idx_orders_company_status on service_orders (company_id, status);
create index idx_orders_company_assignee on service_orders (company_id, assignee_id);
create index idx_orders_company_created on service_orders (company_id, created_at);
create index idx_orders_status_due on service_orders (status, due_at);

create table service_records (
    id               uuid primary key,
    company_id       uuid        not null references companies (id),
    service_order_id uuid        not null references service_orders (id),
    author_id        uuid        not null references users (id),
    content          text        not null,
    minutes_spent    integer,
    created_at       timestamptz not null
);
create index idx_records_order on service_records (service_order_id, created_at);

create table attachments (
    id                uuid primary key,
    company_id        uuid         not null references companies (id),
    service_order_id  uuid         not null references service_orders (id),
    uploaded_by_id    uuid         not null references users (id),
    original_filename varchar(255) not null,
    content_type      varchar(120) not null,
    size_bytes        bigint       not null,
    storage_key       varchar(300) not null,
    created_at        timestamptz  not null,
    constraint uq_attachments_storage_key unique (storage_key)
);
create index idx_attachments_order on attachments (service_order_id);

-- Append-only audit trail: the application never updates or deletes rows here.
create table audit_logs (
    id               uuid primary key,
    company_id       uuid         not null references companies (id),
    service_order_id uuid         not null references service_orders (id),
    actor_id         uuid references users (id),
    actor_name       varchar(120) not null,
    action           varchar(30)  not null,
    field_name       varchar(60),
    old_value        text,
    new_value        text,
    created_at       timestamptz  not null
);
create index idx_audit_order_created on audit_logs (service_order_id, created_at);

create table notifications (
    id               uuid primary key,
    company_id       uuid         not null references companies (id),
    recipient_id     uuid         not null references users (id),
    type             varchar(30)  not null,
    message          varchar(500) not null,
    service_order_id uuid references service_orders (id),
    is_read          boolean      not null default false,
    created_at       timestamptz  not null
);
create index idx_notifications_recipient on notifications (recipient_id, is_read, created_at);
