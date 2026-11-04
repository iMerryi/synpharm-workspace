package com.synpharm.design.application;

import com.synpharm.design.api.dto.MetricDefinitionResponse;
import java.util.List;

/**
 * 评价体系 · 指标目录（只读）。
 *
 * <p>与 {@link EvaluationFreezeAppService} 的分工：那边算分并冻结，这边只回答
 * 「系统里定义了哪些指标、口径是什么」。前端「指标」页签靠它渲染，
 * 不再自己维护一份指标清单。
 *
 * @author SynPharm Team
 */
public interface EvaluationCatalogAppService {

    /**
     * 全部指标定义，按 category + sortNo 排序。
     *
     * <p>包含 {@code enabled=0} 的条目：它们「存在但未启用」，与「不存在」是两件事，
     * 用户需要看到为什么某个指标这次没参与打分。
     */
    List<MetricDefinitionResponse> listMetrics();
}
