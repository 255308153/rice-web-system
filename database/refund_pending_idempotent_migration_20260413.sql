-- 退款并发幂等迁移：
-- 目标：同一 order_id + user_id 只允许存在 1 条 status=0（待处理）记录
-- 方式：增加生成列 pending_guard，并建立唯一索引

SET @has_pending_guard_column := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'refund_request'
    AND COLUMN_NAME = 'pending_guard'
);

SET @add_pending_guard_column_sql := IF(
  @has_pending_guard_column = 0,
  'ALTER TABLE `refund_request`
     ADD COLUMN `pending_guard` VARCHAR(64)
     GENERATED ALWAYS AS (
       CASE
         WHEN `status` = 0 THEN CONCAT(`order_id`, ''_'', `user_id`)
         ELSE NULL
       END
     ) STORED COMMENT ''待处理退款唯一约束辅助列'' AFTER `status`',
  'SELECT 1'
);

PREPARE stmt_add_column FROM @add_pending_guard_column_sql;
EXECUTE stmt_add_column;
DEALLOCATE PREPARE stmt_add_column;

SET @has_pending_guard_unique := (
  SELECT COUNT(*)
  FROM INFORMATION_SCHEMA.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'refund_request'
    AND INDEX_NAME = 'uk_pending_guard'
);

SET @add_pending_guard_unique_sql := IF(
  @has_pending_guard_unique = 0,
  'ALTER TABLE `refund_request` ADD UNIQUE KEY `uk_pending_guard` (`pending_guard`)',
  'SELECT 1'
);

PREPARE stmt_add_unique FROM @add_pending_guard_unique_sql;
EXECUTE stmt_add_unique;
DEALLOCATE PREPARE stmt_add_unique;
