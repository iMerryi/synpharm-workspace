-- ===========================================================================
-- 48_library.sql —— 数据资产库（Library）：把「靶点库」升级为通用数据资产中心
-- ===========================================================================
-- 适用范围：synpharm_core
-- 幂等：全部走 information_schema 守卫，可重复执行
-- 执行账号：**必须 root**（建表 + 改唯一键）
-- 设计文档：docs/design/detailed/11-数据资产库设计.md
--
-- 要解决的问题：现在的 target 表没有属主/可见性概念，用户无法拥有自己的库；
-- uk_uniprot 全局唯一又让用户连「我自己的 EGFR 突变体」都建不出来。
--
-- 分层：08/09 是数据工厂（来源/批次/别名/证据），本脚本是其下游的「数据商店」——
-- 用户看到的是 library_dataset，不感知上游有几个 release。
-- ===========================================================================

USE synpharm_core;

DROP PROCEDURE IF EXISTS sp_add_column_if_missing;
DELIMITER $$
CREATE PROCEDURE sp_add_column_if_missing(IN tbl VARCHAR(64), IN col VARCHAR(64), IN col_ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) THEN
        SET @ddl = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', col_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------------
-- 1. library_dataset：用户可见的「库」容器
--
-- 一个数据集只装一种 item_type。混装会让字段、导入模板、校验全部退化成 if-else，
-- 用户要装两类就建两个库，界面上用「库」统一称呼。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `library_dataset` (
    `id`          bigint       NOT NULL AUTO_INCREMENT,
    `code`        varchar(64)  NOT NULL COMMENT '稳定编码，如 PUBLIC_TARGET',
    `name`        varchar(200) NOT NULL COMMENT '展示名',
    `description` varchar(500) DEFAULT NULL,
    `item_type`   varchar(20)  NOT NULL COMMENT 'TARGET / MOLECULE / SEQUENCE',
    `visibility`  varchar(16)  NOT NULL DEFAULT 'PRIVATE' COMMENT 'PUBLIC 全体可读 / PRIVATE 仅属主与被授权者',
    `owner_id`    bigint       DEFAULT NULL COMMENT '公有数据集为 NULL',
    `source`      varchar(20)  NOT NULL DEFAULT 'USER_MANUAL' COMMENT 'SYSTEM / USER_MANUAL / USER_IMPORT',
    `item_count`  int          NOT NULL DEFAULT 0 COMMENT '由 library_item 维护，不做实时 count',
    `tags`        varchar(500) DEFAULT NULL COMMENT '数据集级标签，逗号分隔，用于列表分区',
    `status`      varchar(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED（停用不删，已绑定的项目仍可读）',
    `deleted`     tinyint      NOT NULL DEFAULT 0,
    `created_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_library_dataset_code` (`code`),
    KEY `idx_library_dataset_owner` (`owner_id`, `visibility`),
    KEY `idx_library_dataset_type` (`item_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据资产库：库容器（公有/私有）';

-- ---------------------------------------------------------------------------
-- 2. library_dataset_grant：授权（笼统版：只有 READ / WRITE 两档）
--
-- 刻意不做团队/机构授权 —— sys_user.institution/lab 是自由文本，不能作权限主体，
-- 要让「分享给某个实验室」真正生效，必须先有 org 实体表，属独立议题。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `library_dataset_grant` (
    `id`              bigint      NOT NULL AUTO_INCREMENT,
    `dataset_id`      bigint      NOT NULL,
    `grantee_user_id` bigint      NOT NULL,
    `permission`      varchar(16) NOT NULL DEFAULT 'READ' COMMENT 'READ 只读 / WRITE 可增改条目（不能删数据集、不能再授权）',
    `granted_by`      bigint      NOT NULL,
    `created_at`      datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_library_grant` (`dataset_id`, `grantee_user_id`),
    KEY `idx_library_grant_user` (`grantee_user_id`),
    CONSTRAINT `fk_library_grant_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `library_dataset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私有库授权（个人级）';

-- ---------------------------------------------------------------------------
-- 3. library_tag：标签字典
--
-- tag_type 是这次最关键的一条边界：
--   DERIVED 由入库计算得出（可被重灌覆盖）
--   CURATED 人工审定（重灌不可覆盖）
--   USER    用户自建（重灌绝对不能覆盖）
-- 重灌数据冲掉用户三个月的标注，是清洗脚本最常犯的错。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `library_tag` (
    `code`       varchar(64)  NOT NULL COMMENT '标签码，如 FAMILY_KINASE',
    `label`      varchar(100) NOT NULL COMMENT '展示名，如 激酶',
    `tag_group`  varchar(32)  NOT NULL COMMENT 'SOURCE/FAMILY/DISEASE/MODALITY/PHASE/SAFETY/QUALITY/READY/CUSTOM',
    `tag_type`   varchar(16)  NOT NULL DEFAULT 'DERIVED' COMMENT 'DERIVED / CURATED / USER',
    `item_types` varchar(64)  NOT NULL DEFAULT 'TARGET,MOLECULE,SEQUENCE' COMMENT '该标签可打给哪些条目类型',
    `color`      varchar(16)  DEFAULT NULL,
    `sort_no`    int          NOT NULL DEFAULT 0,
    `builtin`    tinyint      NOT NULL DEFAULT 0,
    `owner_id`   bigint       DEFAULT NULL COMMENT '非空 = 用户私有标签，只作用于自己的条目',
    `deleted`    tinyint      NOT NULL DEFAULT 0,
    `created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`code`),
    KEY `idx_library_tag_group` (`tag_group`, `sort_no`),
    KEY `idx_library_tag_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签字典（含用户自建）';

-- ---------------------------------------------------------------------------
-- 4. library_item：跨类型检索投影
--
-- 为什么不把标签挂在 target / library_molecule 各自表上：跨类型查询要
-- UNION ALL 三种形状，标签过滤写三遍，且分页语义会错（每类各取 20 条
-- ≠ 全局前 20 条）。冗余一张投影表，写入时同事务双写，换一条 SQL 查全。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `library_item` (
    `id`                       bigint       NOT NULL AUTO_INCREMENT,
    `dataset_id`               bigint       NOT NULL,
    `item_type`                varchar(20)  NOT NULL COMMENT 'TARGET / MOLECULE / SEQUENCE',
    `entity_id`                bigint       NOT NULL COMMENT '指向 target.id 或 library_molecule.id',
    `title`                    varchar(200) NOT NULL COMMENT '统一展示名：靶点=基因名，分子=通用名',
    `subtitle`                 varchar(300) DEFAULT NULL COMMENT '靶点=中文名，分子=分子式',
    `search_text`              varchar(1000) DEFAULT NULL COMMENT '名称+别名+基因名+UniProt+描述 拼接，供模糊查询',
    `quality_level`            varchar(32)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'EXPERIMENTAL_STRUCTURE / PREDICTED_ONLY / NO_STRUCTURE / UNKNOWN',
    `derived_from_id`          bigint       DEFAULT NULL COMMENT '复制血缘：源 item id',
    `derived_from_dataset_id`  bigint       DEFAULT NULL,
    `deleted`                  tinyint      NOT NULL DEFAULT 0,
    `created_at`               datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_library_item_entity` (`item_type`, `entity_id`),
    KEY `idx_library_item_dataset` (`dataset_id`, `item_type`),
    KEY `idx_library_item_quality` (`quality_level`),
    KEY `idx_library_item_title` (`title`),
    CONSTRAINT `fk_library_item_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `library_dataset` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库条目检索投影（跨类型统一查询入口）';

-- ---------------------------------------------------------------------------
-- 5. library_item_tag：条目 ↔ 标签
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `library_item_tag` (
    `id`         bigint      NOT NULL AUTO_INCREMENT,
    `item_id`    bigint      NOT NULL,
    `tag_code`   varchar(64) NOT NULL,
    `created_at` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_library_item_tag` (`item_id`, `tag_code`),
    KEY `idx_library_item_tag_code` (`tag_code`, `item_id`),
    CONSTRAINT `fk_library_item_tag_item` FOREIGN KEY (`item_id`) REFERENCES `library_item` (`id`),
    CONSTRAINT `fk_library_item_tag_tag` FOREIGN KEY (`tag_code`) REFERENCES `library_tag` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库条目标签关联';

-- ---------------------------------------------------------------------------
-- 6. library_molecule：分子与抗体序列载荷
--
-- 分子与抗体同表：candidate_molecule 已是这个形状（smiles 与 heavy_chain/
-- sequence_hash 字段互补，用 molecule_kind 区分）。拆两张表会让「一个库里
-- 同时有小分子和抗体」表达不出来。
--
-- dedup_key 统一取 InChIKey（小分子）或 sequence_hash（抗体），
-- 一张表一条去重规则，避免多列唯一键的 NULL 语义陷阱。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `library_molecule` (
    `id`                   bigint        NOT NULL AUTO_INCREMENT,
    `dataset_id`           bigint        NOT NULL,
    `molecule_kind`        varchar(20)   NOT NULL DEFAULT 'SMALL_MOLECULE' COMMENT 'SMALL_MOLECULE / ANTIBODY / PEPTIDE / OLIGO',
    `dedup_key`            varchar(64)   NOT NULL COMMENT '小分子=InChIKey，抗体=sequence_hash',
    `name`                 varchar(200)  DEFAULT NULL,
    `smiles`               varchar(2000) DEFAULT NULL,
    `canonical_smiles`     varchar(2000) DEFAULT NULL,
    `inchikey`             char(27)      DEFAULT NULL,
    `sequence_hash`        char(64)      DEFAULT NULL,
    `heavy_chain`          mediumtext    DEFAULT NULL,
    `light_chain`          mediumtext    DEFAULT NULL,
    `extra_chains`         json          DEFAULT NULL,
    `sequence_length`      int           DEFAULT NULL,
    `sequence_source`      varchar(20)   DEFAULT NULL COMMENT 'PUBLIC / ILLUSTRATIVE / IMGT / PDB_DERIVED',
    `mw`                   decimal(9,3)  DEFAULT NULL,
    `logp`                 decimal(7,3)  DEFAULT NULL,
    `tpsa`                 decimal(7,3)  DEFAULT NULL,
    `hbd`                  int           DEFAULT NULL,
    `hba`                  int           DEFAULT NULL,
    `rotatable`            int           DEFAULT NULL,
    `qed`                  decimal(5,4)  DEFAULT NULL,
    `pains_hits`           int           DEFAULT NULL,
    `structure_asset_id`   bigint        DEFAULT NULL COMMENT 'file_asset.id，私有文件按 owner_id 隔离',
    `note`                 varchar(500)  DEFAULT NULL,
    `deleted`              tinyint       NOT NULL DEFAULT 0,
    `created_at`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_library_molecule_dedup` (`dataset_id`, `dedup_key`),
    KEY `idx_library_molecule_kind` (`molecule_kind`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库内分子/抗体载荷';

-- ---------------------------------------------------------------------------
-- 7. target 加归属与派生血缘
-- ---------------------------------------------------------------------------
CALL sp_add_column_if_missing('target', 'dataset_id',
    'dataset_id BIGINT NULL COMMENT ''所属库；迁移后置 NOT NULL''');
CALL sp_add_column_if_missing('target', 'variant_code',
    'variant_code VARCHAR(40) NOT NULL DEFAULT '''' COMMENT ''派生体标识（如 L858R）；同 UniProt 的多个变体靠它区分''');
CALL sp_add_column_if_missing('target', 'derived_from_id',
    'derived_from_id BIGINT NULL COMMENT ''复制血缘：源靶点 id''');
CALL sp_add_column_if_missing('target', 'derived_from_dataset_id',
    'derived_from_dataset_id BIGINT NULL COMMENT ''复制血缘：源数据集 id''');

-- ---------------------------------------------------------------------------
-- 8. project_target 加来源数据集快照
--
-- 记录「这个靶点当时来自哪个库」。之后即使数据集改名/停用，历史项目仍能解释
-- 自己用了什么 —— 与决策域「名额必须落库」同一条理由：派生值不落库，
-- 事后就只能按当前配置重算，历史解释被悄悄改写。
-- ---------------------------------------------------------------------------
CALL sp_add_column_if_missing('project_target', 'source_dataset_id',
    'source_dataset_id BIGINT NULL COMMENT ''绑定时该靶点所属数据集快照''');

DROP PROCEDURE sp_add_column_if_missing;

-- ---------------------------------------------------------------------------
-- 9. 公共数据集 + 现有靶点归属
-- ---------------------------------------------------------------------------
INSERT INTO `library_dataset` (`code`, `name`, `description`, `item_type`, `visibility`, `owner_id`, `source`, `status`)
SELECT 'PUBLIC_TARGET', '公共靶点库', '平台维护的靶点参考数据，全体可读；只经数据治理管道更新，界面不提供写入',
       'TARGET', 'PUBLIC', NULL, 'SYSTEM', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM `library_dataset` WHERE `code` = 'PUBLIC_TARGET');

INSERT INTO `library_dataset` (`code`, `name`, `description`, `item_type`, `visibility`, `owner_id`, `source`, `status`)
SELECT 'PUBLIC_MOLECULE', '公共分子库', '平台维护的上市药物分子参考集，全体可读',
       'MOLECULE', 'PUBLIC', NULL, 'SYSTEM', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM `library_dataset` WHERE `code` = 'PUBLIC_MOLECULE');

INSERT INTO `library_dataset` (`code`, `name`, `description`, `item_type`, `visibility`, `owner_id`, `source`, `status`)
SELECT 'PUBLIC_SEQUENCE', '公共抗体序列库', '平台维护的治疗性抗体序列参考集（序列为示意值，不可用于生产）',
       'SEQUENCE', 'PUBLIC', NULL, 'SYSTEM', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM `library_dataset` WHERE `code` = 'PUBLIC_SEQUENCE');

-- 现有靶点全部归入公共靶点库
UPDATE `target`
SET `dataset_id` = (SELECT `id` FROM `library_dataset` WHERE `code` = 'PUBLIC_TARGET')
WHERE `dataset_id` IS NULL;

-- project_target 快照回填：现有绑定都来自公共靶点库
UPDATE `project_target` pt
JOIN `target` t ON t.`id` = pt.`target_id`
SET pt.`source_dataset_id` = t.`dataset_id`
WHERE pt.`source_dataset_id` IS NULL;

-- ---------------------------------------------------------------------------
-- 10. 唯一键：uk_uniprot → uk_dataset_target(dataset_id, uniprot_id, variant_code)
--
-- 让「公共库的 EGFR」与「我的库里的 EGFR L858R」共存 —— 复制公共条目
-- 依赖它，否则复制必然撞键。
--
-- 同时解决一个隐患：旧 uk_uniprot 不含 deleted，逻辑删掉一个靶点后就再也
-- 建不回来（与已修的 uk_project_target 同类问题）。私有条目改为物理删除，
-- 公共条目禁止删除，因此不再需要把 deleted 纳入唯一键。
-- ---------------------------------------------------------------------------
SET @has_old_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'target'
                      AND INDEX_NAME = 'uk_uniprot');
SET @has_new_uk := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'target'
                      AND INDEX_NAME = 'uk_dataset_target');
SET @need_rebuild := (@has_old_uk > 0);

-- dataset_id 必须先填满才能建 NOT NULL 唯一键
SET @null_ds := (SELECT COUNT(*) FROM `target` WHERE `dataset_id` IS NULL);
SET @sql := IF(@null_ds = 0, 'ALTER TABLE `target` MODIFY COLUMN `dataset_id` BIGINT NOT NULL COMMENT ''所属库''', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@need_rebuild AND @has_new_uk = 0 AND @null_ds = 0,
    'ALTER TABLE `target` ADD UNIQUE KEY `uk_dataset_target` (`dataset_id`, `uniprot_id`, `variant_code`)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@has_old_uk > 0,
    'ALTER TABLE `target` DROP INDEX `uk_uniprot`',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- dataset_id 外键
SET @has_fk := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'target'
                  AND CONSTRAINT_NAME = 'fk_target_dataset');
SET @sql := IF(@has_fk = 0,
    'ALTER TABLE `target` ADD CONSTRAINT `fk_target_dataset` FOREIGN KEY (`dataset_id`) REFERENCES `library_dataset` (`id`)',
    'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------------------
-- 11. 标签字典：内置标签（只有 DERIVED/CURATED，用户标签不预置）
-- ---------------------------------------------------------------------------
INSERT INTO `library_tag` (`code`, `label`, `tag_group`, `tag_type`, `item_types`, `sort_no`, `builtin`)
SELECT * FROM (
    SELECT 'SOURCE_UNIPROT'   AS code, '来自 UniProt'  AS label, 'SOURCE'   AS tag_group, 'DERIVED' AS tag_type, 'TARGET' AS item_types, 10 AS sort_no, 1 AS builtin
    UNION ALL SELECT 'SOURCE_PDB',      '来自 PDB',        'SOURCE',   'DERIVED', 'TARGET',           11, 1
    UNION ALL SELECT 'SOURCE_CHEMBL',   '来自 ChEMBL',     'SOURCE',   'DERIVED', 'TARGET,MOLECULE',  12, 1
    UNION ALL SELECT 'SOURCE_USER',     '用户导入',        'SOURCE',   'DERIVED', 'TARGET,MOLECULE,SEQUENCE', 13, 1
    UNION ALL SELECT 'FAMILY_KINASE',   '激酶',            'FAMILY',   'CURATED', 'TARGET',           20, 1
    UNION ALL SELECT 'FAMILY_GPCR',     'GPCR',            'FAMILY',   'CURATED', 'TARGET',           21, 1
    UNION ALL SELECT 'FAMILY_PROTEASE', '蛋白酶',          'FAMILY',   'CURATED', 'TARGET',           22, 1
    UNION ALL SELECT 'FAMILY_IONCHAN',  '离子通道',        'FAMILY',   'CURATED', 'TARGET',           23, 1
    UNION ALL SELECT 'FAMILY_NUCLEAR',  '核受体',          'FAMILY',   'CURATED', 'TARGET',           24, 1
    UNION ALL SELECT 'FAMILY_CHECKPOINT','免疫检查点',     'FAMILY',   'CURATED', 'TARGET',           25, 1
    UNION ALL SELECT 'FAMILY_CYTOKINE', '细胞因子',        'FAMILY',   'CURATED', 'TARGET',           26, 1
    UNION ALL SELECT 'FAMILY_OTHER',    '其他',            'FAMILY',   'CURATED', 'TARGET',           29, 1
    UNION ALL SELECT 'DISEASE_ONCOLOGY','肿瘤',            'DISEASE',  'CURATED', 'TARGET',           30, 1
    UNION ALL SELECT 'DISEASE_CARDIO',  '心血管',          'DISEASE',  'CURATED', 'TARGET',           31, 1
    UNION ALL SELECT 'DISEASE_METABOLIC','代谢',           'DISEASE',  'CURATED', 'TARGET',           32, 1
    UNION ALL SELECT 'DISEASE_NEURO',   '神经',            'DISEASE',  'CURATED', 'TARGET',           33, 1
    UNION ALL SELECT 'DISEASE_INFECTION','感染',           'DISEASE',  'CURATED', 'TARGET',           34, 1
    UNION ALL SELECT 'DISEASE_IMMUNE',  '免疫',            'DISEASE',  'CURATED', 'TARGET',           35, 1
    UNION ALL SELECT 'DISEASE_INFLAMM', '炎症',            'DISEASE',  'CURATED', 'TARGET',           36, 1
    UNION ALL SELECT 'DISEASE_RARE',    '罕见病',          'DISEASE',  'CURATED', 'TARGET',           37, 1
    UNION ALL SELECT 'DISEASE_OCULAR',  '眼科',            'DISEASE',  'CURATED', 'TARGET',           38, 1
    UNION ALL SELECT 'DISEASE_RESPIR',  '呼吸',            'DISEASE',  'CURATED', 'TARGET',           39, 1
    UNION ALL SELECT 'MODALITY_SMALL',  '小分子可及',      'MODALITY', 'CURATED', 'TARGET',           40, 1
    UNION ALL SELECT 'MODALITY_BIO',    '生物药可及',      'MODALITY', 'CURATED', 'TARGET',           41, 1
    UNION ALL SELECT 'MODALITY_BOTH',   '两者皆可',        'MODALITY', 'CURATED', 'TARGET',           42, 1
    UNION ALL SELECT 'MODALITY_NONE',   '无成药先例',      'MODALITY', 'CURATED', 'TARGET',           43, 1
    UNION ALL SELECT 'PHASE_APPROVED',  '已上市',          'PHASE',    'DERIVED', 'TARGET,MOLECULE',  50, 1
    UNION ALL SELECT 'PHASE_3',         'III 期',          'PHASE',    'DERIVED', 'TARGET,MOLECULE',  51, 1
    UNION ALL SELECT 'PHASE_2',         'II 期',           'PHASE',    'DERIVED', 'TARGET,MOLECULE',  52, 1
    UNION ALL SELECT 'PHASE_1',         'I 期',            'PHASE',    'DERIVED', 'TARGET,MOLECULE',  53, 1
    UNION ALL SELECT 'PHASE_PRECLIN',   '临床前',          'PHASE',    'DERIVED', 'TARGET,MOLECULE',  54, 1
    UNION ALL SELECT 'PHASE_NONE',      '未开发',          'PHASE',    'DERIVED', 'TARGET',           55, 1
    UNION ALL SELECT 'SAFETY_HERG',     '心脏安全性(hERG)','SAFETY',   'CURATED', 'TARGET',           60, 1
    UNION ALL SELECT 'SAFETY_HEPATO',   '肝毒性关注',      'SAFETY',   'CURATED', 'TARGET,MOLECULE',  61, 1
    UNION ALL SELECT 'SAFETY_OFFTARGET','脱靶风险',        'SAFETY',   'CURATED', 'TARGET',           62, 1
    UNION ALL SELECT 'SAFETY_IMMUNO',   '免疫原性',        'SAFETY',   'CURATED', 'TARGET,SEQUENCE',  63, 1
    UNION ALL SELECT 'QUALITY_EXP_STRUCT','有实验结构',    'QUALITY',  'DERIVED', 'TARGET',           70, 1
    UNION ALL SELECT 'QUALITY_PRED_STRUCT','仅预测结构',   'QUALITY',  'DERIVED', 'TARGET',           71, 1
    UNION ALL SELECT 'QUALITY_NO_STRUCT','无结构',         'QUALITY',  'DERIVED', 'TARGET',           72, 1
    UNION ALL SELECT 'QUALITY_FULL_SEQ', '有全长序列',     'QUALITY',  'DERIVED', 'TARGET,SEQUENCE',  73, 1
    UNION ALL SELECT 'QUALITY_FRAG_SEQ', '序列为片段',     'QUALITY',  'DERIVED', 'TARGET',           74, 1
    UNION ALL SELECT 'READY_DTI',       '可做 DTI',        'READY',    'DERIVED', 'TARGET,MOLECULE',  80, 1
    UNION ALL SELECT 'READY_PPI',       '可做 PPI',        'READY',    'DERIVED', 'TARGET,SEQUENCE',  81, 1
    UNION ALL SELECT 'READY_POCKET',    '可做口袋对接',    'READY',    'DERIVED', 'TARGET',           82, 1
    UNION ALL SELECT 'READY_GENERATE',  '可做生成',        'READY',    'DERIVED', 'TARGET',           83, 1
) AS t
WHERE NOT EXISTS (SELECT 1 FROM `library_tag` lt WHERE lt.`code` = t.`code`);

-- ---------------------------------------------------------------------------
-- 12. 回填 library_item（现有 3 个靶点）
--
-- quality_level 按结构来源推导：有 PDB → EXPERIMENTAL_STRUCTURE；
-- 只有 ALPHAFOLD → PREDICTED_ONLY；无结构 → NO_STRUCTURE。
-- 这是 QUALITY / READY 标签的来源，基线预检的 NO_STRUCTURE 判定与它同源。
-- ---------------------------------------------------------------------------
INSERT INTO `library_item` (`dataset_id`, `item_type`, `entity_id`, `title`, `subtitle`, `search_text`, `quality_level`)
SELECT t.`dataset_id`,
       'TARGET',
       t.`id`,
       COALESCE(t.`gene_name`, t.`name`),
       CONCAT_WS(' · ', t.`chinese_name`, t.`uniprot_id`),
       TRIM(BOTH ' ' FROM CONCAT_WS(' ',
           t.`name`, t.`chinese_name`, t.`gene_name`, t.`uniprot_id`,
           t.`family`, t.`disease_area`, LEFT(t.`description`, 120))),
       CASE
           WHEN EXISTS (SELECT 1 FROM `target_structure` s
                        WHERE s.`target_id` = t.`id` AND s.`deleted` = 0
                          AND s.`source` IN ('PDB', 'USER_UPLOAD')) THEN 'EXPERIMENTAL_STRUCTURE'
           WHEN EXISTS (SELECT 1 FROM `target_structure` s
                        WHERE s.`target_id` = t.`id` AND s.`deleted` = 0) THEN 'PREDICTED_ONLY'
           ELSE 'NO_STRUCTURE'
       END
FROM `target` t
WHERE t.`deleted` = 0
  AND t.`dataset_id` IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM `library_item` li
                  WHERE li.`item_type` = 'TARGET' AND li.`entity_id` = t.`id`);

-- ---------------------------------------------------------------------------
-- 13. 回填标签（DERIVED 组，按实际数据推导）
-- ---------------------------------------------------------------------------
-- 来源标签
INSERT INTO `library_item_tag` (`item_id`, `tag_code`)
SELECT li.`id`, CONCAT('SOURCE_', UPPER(t.`source`))
FROM `library_item` li
JOIN `target` t ON t.`id` = li.`entity_id`
WHERE li.`item_type` = 'TARGET'
  AND CONCAT('SOURCE_', UPPER(t.`source`)) IN (SELECT `code` FROM `library_tag`)
  AND NOT EXISTS (SELECT 1 FROM `library_item_tag` x
                  WHERE x.`item_id` = li.`id` AND x.`tag_code` = CONCAT('SOURCE_', UPPER(t.`source`)));

-- QUALITY 标签
INSERT INTO `library_item_tag` (`item_id`, `tag_code`)
SELECT li.`id`,
       CASE li.`quality_level`
           WHEN 'EXPERIMENTAL_STRUCTURE' THEN 'QUALITY_EXP_STRUCT'
           WHEN 'PREDICTED_ONLY' THEN 'QUALITY_PRED_STRUCT'
           ELSE 'QUALITY_NO_STRUCT'
       END
FROM `library_item` li
WHERE li.`item_type` = 'TARGET'
  AND NOT EXISTS (SELECT 1 FROM `library_item_tag` x
                  WHERE x.`item_id` = li.`id`
                    AND x.`tag_code` LIKE 'QUALITY_%');

-- 序列完备度：sequence_length 明显短于真实长度时标「序列为片段」
INSERT INTO `library_item_tag` (`item_id`, `tag_code`)
SELECT li.`id`, 'QUALITY_FRAG_SEQ'
FROM `library_item` li
JOIN `target` t ON t.`id` = li.`entity_id`
WHERE li.`item_type` = 'TARGET'
  AND t.`sequence` IS NOT NULL
  AND CHAR_LENGTH(t.`sequence`) > 0
  AND CHAR_LENGTH(t.`sequence`) < 100
  AND NOT EXISTS (SELECT 1 FROM `library_item_tag` x
                  WHERE x.`item_id` = li.`id` AND x.`tag_code` = 'QUALITY_FRAG_SEQ');

-- READY 标签：有序列可做 DTI；有实验结构可做口袋对接
INSERT INTO `library_item_tag` (`item_id`, `tag_code`)
SELECT li.`id`, 'READY_DTI'
FROM `library_item` li
JOIN `target` t ON t.`id` = li.`entity_id`
WHERE li.`item_type` = 'TARGET'
  AND t.`sequence` IS NOT NULL AND CHAR_LENGTH(t.`sequence`) >= 20
  AND NOT EXISTS (SELECT 1 FROM `library_item_tag` x
                  WHERE x.`item_id` = li.`id` AND x.`tag_code` = 'READY_DTI');

INSERT INTO `library_item_tag` (`item_id`, `tag_code`)
SELECT li.`id`, 'READY_POCKET'
FROM `library_item` li
WHERE li.`item_type` = 'TARGET'
  AND li.`quality_level` = 'EXPERIMENTAL_STRUCTURE'
  AND NOT EXISTS (SELECT 1 FROM `library_item_tag` x
                  WHERE x.`item_id` = li.`id` AND x.`tag_code` = 'READY_POCKET');

-- ---------------------------------------------------------------------------
-- 14. 同步 item_count
-- ---------------------------------------------------------------------------
UPDATE `library_dataset` d
SET d.`item_count` = (SELECT COUNT(*) FROM `library_item` li
                      WHERE li.`dataset_id` = d.`id` AND li.`deleted` = 0)
WHERE d.`item_type` = 'TARGET';

-- ---------------------------------------------------------------------------
-- 15. 验收查询
-- ---------------------------------------------------------------------------
SELECT '--- 数据集 ---' AS section;
SELECT `code`, `name`, `item_type`, `visibility`, `owner_id`, `item_count`, `status`
FROM `library_dataset` WHERE `deleted` = 0 ORDER BY `id`;

SELECT '--- 目标表归属 ---' AS section;
SELECT COUNT(*) AS total,
       SUM(`dataset_id` IS NULL) AS null_dataset,
       SUM(`variant_code` = '') AS empty_variant
FROM `target` WHERE `deleted` = 0;

SELECT '--- 检索投影 ---' AS section;
SELECT li.`id`, li.`title`, li.`subtitle`, li.`quality_level`,
       (SELECT GROUP_CONCAT(t.`code` ORDER BY t.`sort_no`)
          FROM `library_item_tag` it JOIN `library_tag` t ON t.`code` = it.`tag_code`
         WHERE it.`item_id` = li.`id`) AS tags
FROM `library_item` li WHERE li.`deleted` = 0 ORDER BY li.`id`;

SELECT '--- 项目绑定未被破坏 ---' AS section;
SELECT COUNT(*) AS bindings,
       SUM(`source_dataset_id` IS NULL) AS null_source
FROM `project_target` WHERE `deleted` = 0;

SELECT '--- 唯一键 ---' AS section;
SELECT `INDEX_NAME`, `COLUMN_NAME`, `SEQ_IN_INDEX`
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'target' AND `NON_UNIQUE` = 0
ORDER BY `INDEX_NAME`, `SEQ_IN_INDEX`;
