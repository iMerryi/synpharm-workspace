package com.synpharm.design.api.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 指标定义（{@code metric_definition} 的对外投影）。
 *
 * <p>评价体系要把「评什么」讲清楚：指标码、中文名、单位、好坏方向、归一化区间、
 * 权重、适用分子形式、口径说明。这些都在表里，不在前端硬编码 ——
 * 前端写死一份清单，改一个指标就要发一次前端版本，且两份清单迟早对不上。
 *
 * @author SynPharm Team
 */
@Data
public class MetricDefinitionResponse {

    /** 指标码，如 MOL_WT / QED / DTI_CONFIDENCE */
    private String metricCode;

    /** 中文名 */
    private String metricName;

    /** CONSTRAINT/PROPERTY/DRUGLIKENESS/SAFETY/SYNTHESIS/DIVERSITY/ACTIVITY/SELECTIVITY/DOCKING/ADMET */
    private String category;

    /** 需求八维度；小分子基础指标没有维度归属，为 null */
    private String dimension;

    /** NUMERIC/BOOLEAN/GRADE/JSON */
    private String valueType;

    /** 单位，如 Da / kcal/mol；无量纲为空串 */
    private String unit;

    /** HIGHER 越大越好 / LOWER 越小越好 / NONE 无好坏方向（只做约束判定） */
    private String better;

    private BigDecimal minValue;

    private BigDecimal maxValue;

    /** 综合得分权重；0 表示仅展示不参与打分 */
    private BigDecimal weight;

    private Boolean enabled;

    private Integer sortNo;

    /** 口径说明（为什么这样定、口径坑在哪） */
    private String remark;

    /** 适用分子形式；空列表表示不限 */
    private List<String> appliesTo;
}
