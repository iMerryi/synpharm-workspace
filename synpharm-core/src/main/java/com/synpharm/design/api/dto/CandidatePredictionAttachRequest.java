package com.synpharm.design.api.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 回写预测结论的请求。
 *
 * <p>字段来自预测服务的响应，前端原样带回来即可。服务端只做校验与归属判定，
 * 不重算、不解释数值 —— 重算就等于在设计域再造一个预测器，两边结论迟早不一致。
 *
 * @author SynPharm Team
 */
@Data
public class CandidatePredictionAttachRequest {

    /** DTI/PPI/DDI，必填 */
    private String algoType;

    private String targetId;

    private String targetName;

    private String ligandSmiles;

    /** kcal/mol；PPI/DDI 无此值 */
    private BigDecimal bindingAffinity;

    /** 0~1 原始值，不要传百分数 */
    private BigDecimal confidenceScore;

    private String confidenceLevel;

    /** 相互作用残基明细（原样保存） */
    private Object interactions;

    /** 预测服务侧结果 id */
    private String predictResultId;

    /** 数据集/模型信息（原样保存） */
    private Object datasetInfo;

    private String note;
}
