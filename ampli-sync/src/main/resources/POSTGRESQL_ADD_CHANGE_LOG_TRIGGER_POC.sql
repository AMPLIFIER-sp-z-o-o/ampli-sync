drop trigger if exists trg_{$table_name}_change_log on {$table_schema}.{$table_name}

kol_sc

create trigger trg_{$table_name}_change_log
after insert or update or delete on {$table_schema}.{$table_name}
for each row execute function {$table_schema}.write_poc_change_log();
