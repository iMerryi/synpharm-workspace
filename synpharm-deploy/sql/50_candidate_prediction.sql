-- =============================================
-- AI 药物分子设计平台 · 候选 × 外部预测结果
-- 版本：v1.0  依赖：20_candidate_molecule.sql、11_design_project.sql
--
-- 「评价体系」里的分子预测（DTI/PPI/DDI）是独立服务（synpharm-predict）算的，
-- 结果原本只能留在预测域，设计域的候选看不到。本表把「某条预测结论属于哪个候选」
-- 这个关系落到设计域，于是候选评估页能显示「亲和力 7.42 kcal/mol、置信度 0.86」，
-- 而不是只理化指标。
--
-- 为什么不复用 evaluation_result：
--   evaluation_result 挂在 evaluation_run 下，跑一次评估会批量覆盖候选的指标集，
--   而预测是「一次一条、可重复、带模型版本」的外部结论，两者生命周期不同。
--   混在一起会出现「重跑一次理化评估把上次预测的亲和力冲掉」这种事故。
--
-- 幂等：可重复执行。
-- =============================================
USE synpharm_core;

CREATE TABLE IF NOT EXISTS candidate_prediction (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id        BIGINT        NOT NULL COMMENT '冗余，便于按项目查询',
    candidate_id      BIGINT        NOT NULL COMMENT '候选分子',
    user_id           BIGINT        NOT NULL COMMENT '发起预测的用户（越权校验用）',
    algo_type         VARCHAR(10)   NOT NULL COMMENT 'DTI/PPI/DDI',
    target_id         VARCHAR(120)           COMMENT 'DTI 的靶点（UniProt/PDB/靶点名）',
    target_name       VARCHAR(200)           COMMENT '靶点展示名',
    ligand_smiles     VARCHAR(2000)          COMMENT '送进预测的结构（便于回溯「当时预测的到底是什么」）',
    binding_affinity  DECIMAL(10,4)          COMMENT 'kcal/mol；越小越紧。PPI/DDI 无此值',
    confidence_score  DECIMAL(6,5)           COMMENT '0~1，原样保存不做百分数换算',
    confidence_level  VARCHAR(20)            COMMENT 'HIGH/MEDIUM/LOW',
    interactions      JSON                   COMMENT '相互作用残基明细',
    predict_result_id VARCHAR(64)            COMMENT '预测服务侧结果 id，用于回溯原始记录',
    dataset_info      JSON                   COMMENT '预测所用数据集/模型信息',
    note              VARCHAR(500)           COMMENT '用户备注',
    deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    KEY idx_candidate (candidate_id, created_at),
    KEY idx_project (project_id, created_at),
    KEY idx_predict_result (predict_result_id),
    CONSTRAINT fk_candpred_candidate FOREIGN KEY (candidate_id) REFERENCES candidate_molecule(id),
    CONSTRAINT fk_candpred_project FOREIGN KEY (project_id) REFERENCES design_project(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候选的外部预测结论（分子预测服务回写）';
