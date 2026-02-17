create extension if not exists "uuid-ossp";

kol_sc

CREATE OR REPLACE FUNCTION {$table_schema}.rowver_increment()
    RETURNS trigger
AS $function$
BEGIN
EXECUTE format('UPDATE {$table_schema}.mergecontent_%s t SET record_has_changed=true FROM (SELECT id FROM {$table_schema}.mergecontent_%s WHERE rowid=''%s'' ORDER BY {$table_schema}.mergecontent_%s.id FOR UPDATE) src WHERE t.id = src.id;', TG_TABLE_NAME, TG_TABLE_NAME, new.rowid, TG_TABLE_NAME);
RETURN NEW;
END;
$function$
LANGUAGE plpgsql;

kol_sc

create or replace function {$table_schema}.rowid_generator()
    returns trigger
    language 'plpgsql'
    cost 100
    volatile not leakproof
as $body$
begin
 new.rowid = uuid_generate_v4();
return new;

end;
$body$;


kol_sc

create table if not exists {$table_schema}.mergeidentity (
        id serial primary key,
        tableid numeric not null,
        subscriberid numeric not null,
        rev numeric default 1 not null,
        identitystart numeric not null,
        identityend numeric not null,
        identitycurrent numeric not null,
        rowid varchar(64) unique not null,
        mergeinsertsource numeric  default 1 null,
        rowver numeric default 1 not null
    );

kol_sc

create table if not exists {$table_schema}.mergecontent_mergeidentity (
        id serial primary key,
        tableid numeric not null,
        subscriberid numeric not null,
        rowid char(36) not null,
        rowver numeric default 1 not null,
        changedate timestamp  default current_timestamp not null,
        action numeric default 1 not null,
        syncid numeric null
    );
kol_sc
create index if not exists ix_mergecontent_mi_1 on {$table_schema}.mergecontent_mergeidentity (tableid,subscriberid,rowid,rowver);
kol_sc
create index if not exists ix_mergecontent_mi_2 on {$table_schema}.mergecontent_mergeidentity (syncid,rowver,tableid,subscriberid);
kol_sc
create index if not exists ix_mergecontent_subid_rowid on {$table_schema}.mergecontent_mergeidentity (subscriberid,rowid);
kol_sc
create index if not exists ix_mergecontent_subscriberid on {$table_schema}.mergecontent_mergeidentity (subscriberid);
kol_sc
create index if not exists ix_mergecontent_subscriberid1 on {$table_schema}.mergecontent_mergeidentity (tableid,subscriberid);
kol_sc
drop trigger if exists mergeidentity_rowver on {$table_schema}.mergeidentity;
kol_sc
create trigger mergeidentity_rowver
    before update
    on {$table_schema}.mergeidentity
    for each row
    execute procedure {$table_schema}.rowver_increment();

kol_sc
drop trigger if exists mergeidentity_rowid on {$table_schema}.mergeidentity;
kol_sc
create trigger mergeidentity_rowid
    before insert
    on {$table_schema}.mergeidentity
    for each row
    execute procedure {$table_schema}.rowid_generator();
kol_sc
create table if not exists {$table_schema}.mergesubscribers (
    subscriberid serial primary key,
    name varchar(200) not null,
    uniquename varchar(100) not null,
    deviceuuid varchar(100) not null,
    isenabled numeric default 1 not null);
kol_sc
create index if not exists ix_mergesubscribers on {$table_schema}.mergesubscribers (uniquename);
kol_sc
create table if not exists {$table_schema}.mergesync (
     syncid serial primary key,
     subscriberid numeric not null,
     syncstart timestamp  default current_timestamp not null,
     syncend timestamp,
     syncobject varchar(100),
    tableid numeric
    );
kol_sc
create table if not exists {$table_schema}.mergetablestosync (
    tableid serial primary key,
    tablename varchar(510) null,
    tableschema varchar(510) null,
    tablefilter varchar(4000) null,
    readonly numeric default 0 not null,
    identityrange numeric  default 100000 not null,
    identitytrashold numeric default 80 null
    );
kol_sc
alter table {$table_schema}.mergetablestosync add constraint ix_mergetablestosync unique (tablename);

kol_sc
create table if not exists {$table_schema}.mergetablesschemaupdates (
    id serial primary key,
    query varchar(1000) null,
    enabled boolean default true not null,
    subscriberid numeric null,
    tablename varchar(510) null
    );
kol_sc
create table if not exists {$table_schema}.mergetablesschemaupdateresults (
    id serial primary key,
    schemaupdateid numeric not null,
    result varchar(1000) null,
    subscriberid numeric null,
    executiondate timestamp  default current_timestamp not null
    );
