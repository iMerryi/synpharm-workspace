-- =============================================================================
-- 2026-11-04 · 把 file_asset 从 synpharm_core 拆回 synpharm_asset
--
-- 背景同 platform 那份：2026-10-22 的三模块合并把 file_asset 带进了 synpharm_core，
-- 现在随 synpharm-asset 重新独立。本脚本只处理**已存在的库**。
--
-- 全新部署（数据卷为空）不需要本脚本：
--   31_file_asset.sql 已改为 USE synpharm_asset
--   00_create_database.sql 已建 synpharm_asset
--   00_create_accounts.sh 已创建 synpharm_asset 账号
--
-- ⚠️ 与 platform 那份同样必须幂等：用 IF EXISTS 守卫，重复执行只输出 SKIP。
-- =============================================================================

DROP PROCEDURE IF EXISTS synpharm_asset.synpharm_resplit_file_asset;

DELIMITER $$
CREATE PROCEDURE synpharm_asset.synpharm_resplit_file_asset()
BEGIN
    DECLARE v_has_core INT DEFAULT 0;
    DECLARE v_has_asset INT DEFAULT 0;

    SELECT COUNT(*) INTO v_has_core
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'synpharm_core' AND TABLE_NAME = 'file_asset';

    SELECT COUNT(*) INTO v_has_asset
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'synpharm_asset' AND TABLE_NAME = 'file_asset';

    IF v_has_asset = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'synpharm_asset.file_asset 不存在：请先执行 sql/31_file_asset.sql';
    END IF;

    IF v_has_core = 0 THEN
        SELECT 'SKIP: synpharm_core.file_asset 不存在，迁移已完成' AS result;
    ELSE
        DELETE FROM synpharm_asset.file_asset;

        INSERT INTO synpharm_asset.file_asset
          (id, sha256, file_name, content_type, size_bytes, storage_type, bucket, object_key, storage_uri,
           ref_count, owner_id, deleted, created_at, updated_at)
        SELECT
           id, sha256, file_name, content_type, size_bytes, storage_type, bucket, object_key, storage_uri,
           ref_count, owner_id, deleted, created_at, updated_at
        FROM synpharm_core.file_asset;

        DROP TABLE synpharm_core.file_asset;
        SELECT 'DONE: 已搬迁并删除 synpharm_core.file_asset' AS result;
    END IF;
END$$
DELIMITER ;

-- -----------------------------------------------------------------------------
-- 前置校验：不应有任何外键指向 file_asset（历史上 3 条已在阶段 3 拆库时删除）
-- -----------------------------------------------------------------------------
SELECT CONCAT('FK_TO_FILE_ASSET=', COUNT(*)) AS check_fk_before
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_NAME = 'file_asset';

CALL synpharm_asset.synpharm_resplit_file_asset();

DROP PROCEDURE synpharm_asset.synpharm_resplit_file_asset;

-- -----------------------------------------------------------------------------
-- 校验
-- -----------------------------------------------------------------------------
SELECT CONCAT('CORE_GONE=', COUNT(*)) AS check_core_gone
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'synpharm_core' AND TABLE_NAME = 'file_asset';

SELECT CONCAT('ASSET_ROWS=', COUNT(*)) AS check_rows
FROM synpharm_asset.file_asset;

SELECT CONCAT('ALL_FK_CROSS_SCHEMA=', COUNT(*)) AS check_cross_schema
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_NAME IS NOT NULL AND REFERENCED_TABLE_SCHEMA <> TABLE_SCHEMA;
