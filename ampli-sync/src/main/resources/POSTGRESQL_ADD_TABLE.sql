create table if not exists {$table_schema}.mergecontent_{$table_name} (
	id serial primary key,
	subscriberid numeric not null,
	rowid char(36) not null,
	changedate timestamp  default current_timestamp not null,
	action numeric default 1 not null,
	syncid numeric null,
    record_has_changed boolean not null default false
)

kol_sc

alter table {$table_schema}.{$table_name} add if not exists rowid char(36) not null default uuid_generate_v4()

kol_sc

create index if not exists ix_subscriberid_{$table_name} on {$table_schema}.mergecontent_{$table_name} (subscriberid asc)

kol_sc

create index if not exists ix_rowid_subscriberid_{$table_name} on {$table_schema}.mergecontent_{$table_name} (rowid, subscriberid)

kol_sc

create index if not exists ixm_{$table_name}_rowid on {$table_schema}.{$table_name} (rowid asc)

kol_sc

update {$table_schema}.{$table_name} set rowid=uuid_generate_v4() where rowid is null

kol_sc

drop trigger if exists prmerge_{$table_name}update on {$table_schema}.{$table_name}

kol_sc

create trigger prmerge_{$table_name}_update
    before update
    on {$table_schema}.{$table_name}
    for each row execute function {$table_schema}.rowver_increment();

kol_sc

insert into {$table_schema}.mergetablestosync (tablename,tableschema,tablefilter,readonly,identityrange,identitytrashold) values ('{$table_name}','{$table_schema}','',0,50000,80) on conflict on constraint ix_mergetablestosync do nothing;
