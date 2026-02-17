drop table MergeContent_{$TABLE_NAME}

KOL_SC

DROP TRIGGER prMerge_{$TABLE_NAME}Update ON {$TABLE_NAME}

KOL_SC

DROP TRIGGER prMerge_{$TABLE_NAME}Insert ON {$TABLE_NAME}

KOL_SC

ALTER TABLE {$TABLE_NAME} DROP COLUMN RowId

KOL_SC

delete from MergeTablesToSync where TableName='{$TABLE_NAME}'
