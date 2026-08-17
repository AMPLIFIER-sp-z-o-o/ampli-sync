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

create table if not exists tenant_test.demo_customers (
    id uuid primary key default uuid_generate_v4(),
    name text not null,
    email text,
    city text,
    created_at timestamp default current_timestamp
    );

create table if not exists tenant_test.demo_products (
    id uuid primary key default uuid_generate_v4(),
    name text not null,
    sku text not null,
    unit_price numeric not null,
    is_active boolean default true,
    created_at timestamp default current_timestamp
    );

create table if not exists tenant_test.demo_orders (
    id uuid primary key default uuid_generate_v4(),
    customer_id uuid not null,
    order_number text not null,
    status text not null,
    total_amount numeric not null,
    created_at timestamp default current_timestamp
    );

create table if not exists tenant_test.demo_order_items (
    id uuid primary key default uuid_generate_v4(),
    order_id uuid not null,
    product_id uuid not null,
    quantity numeric not null,
    unit_price numeric not null,
    created_at timestamp default current_timestamp
    );

insert into tenant_test.demo_customers (id, name, email, city)
values
    ('5e7b16b0-0c2f-4e9d-9f74-2d7a8f4c0b21', 'Acme Retail', 'orders@acme.example', 'Warsaw'),
    ('0b8e9b8e-0fb5-4f2d-8d4c-3c57e7dc8e47', 'North Coast Shop', 'hello@northcoast.example', 'Gdansk'),
    ('8fb5f9c7-9929-4f87-8fcb-19f2092f0a5d', 'Green Market', 'contact@greenmarket.example', 'Poznan'),
    ('ad6510d7-c44f-4cfa-94c3-3f56a32a4c89', 'Metro Office', 'office@metro.example', 'Krakow')
    on conflict (id) do nothing;

insert into tenant_test.demo_products (id, name, sku, unit_price, is_active)
values
    ('1c94a79e-913f-4c8a-a694-85a99fcae4a1', 'Wireless Scanner', 'SCN-100', 299.00, true),
    ('d7a3a28a-f8b1-41f0-9eaa-2cdd19fbd678', 'Receipt Printer', 'PRN-200', 449.00, true),
    ('a2dd5c4f-19cb-4dfc-b902-4e463cfe5f60', 'Barcode Labels', 'LBL-300', 39.00, true),
    ('77c8a552-4461-486d-a6dc-3e47ae00c1c5', 'Cash Drawer', 'DRW-400', 189.00, true),
    ('b3da1d9d-0933-47fd-b172-4277ccde1a98', 'Tablet Stand', 'STD-500', 129.00, true)
    on conflict (id) do nothing;

insert into tenant_test.demo_orders (id, customer_id, order_number, status, total_amount)
values
    ('79574eb8-44c3-4733-9a85-71bfe2c60271', '5e7b16b0-0c2f-4e9d-9f74-2d7a8f4c0b21', 'ORD-1001', 'NEW', 748.00),
    ('06498ebd-0282-471f-9e1f-97de3d6bdf29', '0b8e9b8e-0fb5-4f2d-8d4c-3c57e7dc8e47', 'ORD-1002', 'PAID', 78.00),
    ('586fb265-f426-49eb-8552-256f54de61ef', '8fb5f9c7-9929-4f87-8fcb-19f2092f0a5d', 'ORD-1003', 'DRAFT', 318.00)
    on conflict (id) do nothing;

insert into tenant_test.demo_order_items (id, order_id, product_id, quantity, unit_price)
values
    ('c54d82ca-2f87-4a32-bc99-e4679ee090e3', '79574eb8-44c3-4733-9a85-71bfe2c60271', '1c94a79e-913f-4c8a-a694-85a99fcae4a1', 1, 299.00),
    ('06fd158b-7224-4d71-9a24-8b8fc6816b41', '79574eb8-44c3-4733-9a85-71bfe2c60271', 'd7a3a28a-f8b1-41f0-9eaa-2cdd19fbd678', 1, 449.00),
    ('e0a3fb7d-af35-4c7c-a7f2-c23017c19a7d', '06498ebd-0282-471f-9e1f-97de3d6bdf29', 'a2dd5c4f-19cb-4dfc-b902-4e463cfe5f60', 2, 39.00),
    ('a85726ea-90db-4fe7-9b85-00bbd2779e81', '586fb265-f426-49eb-8552-256f54de61ef', '77c8a552-4461-486d-a6dc-3e47ae00c1c5', 1, 189.00),
    ('fbd9601d-1b5f-4abf-9926-a6b02ad8ea5a', '586fb265-f426-49eb-8552-256f54de61ef', 'b3da1d9d-0933-47fd-b172-4277ccde1a98', 1, 129.00)
    on conflict (id) do nothing;

create table if not exists tenant_test.poc_customers (
                                                         id uuid primary key default uuid_generate_v4(),
    name text not null,
    assigned_user_uuid varchar(100) not null,
    rowid char(36) not null default uuid_generate_v4()
    );

create table if not exists tenant_test.poc_orders (
                                                      id uuid primary key default uuid_generate_v4(),
    customer_id uuid not null references tenant_test.poc_customers(id),
    order_number text not null,
    total_amount numeric not null,
    rowid char(36) not null default uuid_generate_v4()
    );

create table if not exists tenant_test.poc_order_items (
                                                           id uuid primary key default uuid_generate_v4(),
    order_id uuid not null references tenant_test.poc_orders(id),
    product_name text not null,
    quantity numeric not null,
    rowid char(36) not null default uuid_generate_v4()
    );

create or replace view tenant_test.vw_poc_customers_sync as
select
    c.rowid,
    c.id,
    c.name,
    c.assigned_user_uuid as uniquename
from tenant_test.poc_customers c;

create or replace view tenant_test.vw_poc_orders_sync as
select
    o.rowid,
    o.id,
    o.customer_id,
    o.order_number,
    o.total_amount,
    c.assigned_user_uuid as uniquename
from tenant_test.poc_orders o
         join tenant_test.poc_customers c on c.id = o.customer_id;

create or replace view tenant_test.vw_poc_order_items_sync as
select
    i.rowid,
    i.id,
    i.order_id,
    i.product_name,
    i.quantity,
    c.assigned_user_uuid as uniquename
from tenant_test.poc_order_items i
         join tenant_test.poc_orders o on o.id = i.order_id
         join tenant_test.poc_customers c on c.id = o.customer_id;

