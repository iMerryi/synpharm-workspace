-- =============================================================================
-- 2026-11-04 · 把 design_tool 从 synpharm_core 拆回 synpharm_platform
--
-- 背景：2026-10-22 的服务收敛把 design / asset / platform 三个模块合并成 synpharm-core，
-- 三张表也随之搬进 synpharm_core。现在按「微服务做到底」重新拆开，本脚本处理**已存在的库**。
--
-- 全新部署（数据卷为空）不需要本脚本：
--   32_design_tool.sql 已改为 USE synpharm_platform
--   37_design_seed_data.sql 的种子已写 synpharm_platform.design_tool
--   00_create_database.sql 已建 synpharm_platform
--
-- ⚠️ 数据方向：以 synpharm_core 为准（收敛期间 available 由 core 的探测回写，
--   synpharm_platform 里那份是 2026-10-22 之前的遗留，实测 available 全为 0）。
--
-- ⚠️ 本脚本**必须幂等**。这不是洁癖：「先删后插再删源表」的写法多跑一次就会把刚搬过去的
--   数据删干净、再因源表已不存在而报错 —— 实测踩过一次，只能靠重放种子恢复。
--   所以整段包在存储过程里，用 IF EXISTS 守卫。
-- =============================================================================

DROP PROCEDURE IF EXISTS synpharm_platform.synpharm_resplit_design_tool;

DELIMITER $$
CREATE PROCEDURE synpharm_platform.synpharm_resplit_design_tool()
BEGIN
    DECLARE v_has_core INT DEFAULT 0;
    DECLARE v_has_platform INT DEFAULT 0;

    SELECT COUNT(*) INTO v_has_core
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'synpharm_core' AND TABLE_NAME = 'design_tool';

    SELECT COUNT(*) INTO v_has_platform
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = 'synpharm_platform' AND TABLE_NAME = 'design_tool';

    IF v_has_platform = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'synpharm_platform.design_tool 不存在：请先执行 sql/32_design_tool.sql';
    END IF;

    IF v_has_core = 0 THEN
        -- 已经搬完了（或本来就是全新部署）：什么都不做，重复执行安全
        SELECT 'SKIP: synpharm_core.design_tool 不存在，迁移已完成' AS result;
    ELSE
        -- 显式列名而不是 SELECT *：两表若因历史迁移留下列顺序差异，SELECT * 会静默错位
        DELETE FROM synpharm_platform.design_tool;

        INSERT INTO synpharm_platform.design_tool
          (id, tool_code, name, description, category, type, provider, endpoint, http_method,
           input_schema, output_schema, async_execution, timeout_ms, requires_engine, capability_key,
           realness, enabled, available, probe_msg, last_probe_at, agent_exposed, agent_tool_name,
           version, deleted, created_at, updated_at)
        SELECT
           id, tool_code, name, description, category, type, provider, endpoint, http_method,
           input_schema, output_schema, async_execution, timeout_ms, requires_engine, capability_key,
           realness, enabled, available, probe_msg, last_probe_at, agent_exposed, agent_tool_name,
           version, deleted, created_at, updated_at
        FROM synpharm_core.design_tool;

        DROP TABLE synpharm_core.design_tool;
        SELECT 'DONE: 已搬迁并删除 synpharm_core.design_tool' AS result;
    END IF;
END$$
DELIMITER ;

-- -----------------------------------------------------------------------------
-- 前置校验：不应有任何外键指向 design_tool（非 0 请先删，本项目已声明「永久不建」）
-- -----------------------------------------------------------------------------
SELECT CONCAT('FK_TO_DESIGN_TOOL=', COUNT(*)) AS check_fk_before
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_NAME = 'design_tool';

CALL synpharm_platform.synpharm_resplit_design_tool();

DROP PROCEDURE synpharm_platform.synpharm_resplit_design_tool;

-- -----------------------------------------------------------------------------
-- 校验
-- -----------------------------------------------------------------------------
-- 设计域应已没有这张表（预期 0）
SELECT CONCAT('CORE_GONE=', COUNT(*)) AS check_core_gone
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'synpharm_core' AND TABLE_NAME = 'design_tool';

-- 平台库应有 12 行（种子数）
SELECT CONCAT('PLATFORM_ROWS=', COUNT(*)) AS check_rows
FROM synpharm_platform.design_tool;

-- 全库跨 schema 外键必须为 0（拆分铁律 R2）
SELECT CONCAT('ALL_FK_CROSS_SCHEMA=', COUNT(*)) AS check_cross_schema
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_NAME IS NOT NULL AND REFERENCED_TABLE_SCHEMA <> TABLE_SCHEMA;
