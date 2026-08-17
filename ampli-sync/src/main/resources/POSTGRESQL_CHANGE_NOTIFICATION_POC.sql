create table if not exists {$table_schema}.poc_change_log (
    seq bigserial primary key,
    table_name text not null,
    rowid char(36) not null,
    operation text not null,
    changed_at timestamp not null default current_timestamp
);

kol_sc

create table if not exists {$table_schema}.poc_device_sync_state (
    subscriberid numeric not null,
    deviceuuid varchar(100) not null,
    last_confirmed_seq numeric not null default 0,
    pending_latest_seq numeric,
    has_pending_changes boolean not null default false,
    updated_at timestamp not null default current_timestamp,
    primary key (subscriberid, deviceuuid)
);

kol_sc

create or replace function {$table_schema}.write_poc_change_log()
returns trigger as $$
begin
    if (tg_op = 'DELETE') then
        insert into {$table_schema}.poc_change_log (table_name, rowid, operation)
        values (tg_table_name, old.rowid, lower(tg_op));
        return old;
    end if;

    insert into {$table_schema}.poc_change_log (table_name, rowid, operation)
    values (tg_table_name, new.rowid, lower(tg_op));
    return new;
end;
$$ language plpgsql;
