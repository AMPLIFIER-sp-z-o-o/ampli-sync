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
    ('11111111-1111-1111-1111-111111111111', 'Acme Retail', 'orders@acme.example', 'Warsaw'),
    ('22222222-2222-2222-2222-222222222222', 'North Coast Shop', 'hello@northcoast.example', 'Gdansk'),
    ('33333333-3333-3333-3333-333333333333', 'Green Market', 'contact@greenmarket.example', 'Poznan'),
    ('44444444-4444-4444-4444-444444444444', 'Metro Office', 'office@metro.example', 'Krakow')
    on conflict (id) do nothing;

insert into tenant_test.demo_products (id, name, sku, unit_price, is_active)
values
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Wireless Scanner', 'SCN-100', 299.00, true),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Receipt Printer', 'PRN-200', 449.00, true),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Barcode Labels', 'LBL-300', 39.00, true),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Cash Drawer', 'DRW-400', 189.00, true),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Tablet Stand', 'STD-500', 129.00, true)
    on conflict (id) do nothing;

insert into tenant_test.demo_orders (id, customer_id, order_number, status, total_amount)
values
    ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'ORD-1001', 'NEW', 748.00),
    ('66666666-6666-6666-6666-666666666666', '22222222-2222-2222-2222-222222222222', 'ORD-1002', 'PAID', 78.00),
    ('77777777-7777-7777-7777-777777777777', '33333333-3333-3333-3333-333333333333', 'ORD-1003', 'DRAFT', 318.00)
    on conflict (id) do nothing;

insert into tenant_test.demo_order_items (id, order_id, product_id, quantity, unit_price)
values
    ('88888888-8888-8888-8888-888888888888', '55555555-5555-5555-5555-555555555555', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 1, 299.00),
    ('99999999-9999-9999-9999-999999999999', '55555555-5555-5555-5555-555555555555', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 1, 449.00),
    ('10101010-1010-1010-1010-101010101010', '66666666-6666-6666-6666-666666666666', 'cccccccc-cccc-cccc-cccc-cccccccccccc', 2, 39.00),
    ('12121212-1212-1212-1212-121212121212', '77777777-7777-7777-7777-777777777777', 'dddddddd-dddd-dddd-dddd-dddddddddddd', 1, 189.00),
    ('13131313-1313-1313-1313-131313131313', '77777777-7777-7777-7777-777777777777', 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 1, 129.00)
    on conflict (id) do nothing;

