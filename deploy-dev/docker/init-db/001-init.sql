create extension if not exists "uuid-ossp";

create table if not exists public.tenants_tenant (
    id bigint primary key,
    schema_name varchar(100) not null
    );

create table if not exists public.users_customuser (
    id bigint primary key,
    default_tenant_id bigint not null references public.tenants_tenant(id)
);

create schema if not exists tenant_test;

insert into public.tenants_tenant (id, schema_name)
values (1, 'tenant_test')
on conflict (id) do nothing;

insert into public.users_customuser (id, default_tenant_id)
values (1, 1)
on conflict (id) do nothing;

create table if not exists tenant_test.document_headers (
    id uuid primary key default uuid_generate_v4(),
    title text not null,
    amount numeric,
    created_at timestamp default current_timestamp
);

insert into tenant_test.document_headers (title, amount)
values
('Test document 1', 100),
('Test document 2', 200);
