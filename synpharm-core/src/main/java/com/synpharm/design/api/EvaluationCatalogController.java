package com.synpharm.design.api;

import com.synpharm.common.api.Result;
import com.synpharm.design.api.dto.MetricDefinitionResponse;
import com.synpharm.design.application.EvaluationCatalogAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价体系 · 指标目录。
 *
 * <p>放在 {@code /api/evaluation} 而不是 {@code /api/design}：指标口径是评价体系的公共
 * 契约，不是某个设计项目的私有配置。项目里选的指标（{@code project_metric}）与
 * 这里的定义（{@code metric_definition}）是「实例」与「类型」的关系。
 *
 * @author SynPharm Team
 */
@RestController
@RequestMapping("/api/evaluation")
@RequiredArgsConstructor
@Tag(name = "评价体系 · 指标目录", description = "metric_definition 只读投影")
public class EvaluationCatalogController {

    private final EvaluationCatalogAppService evaluationCatalogAppService;

    @GetMapping("/metrics")
    @Operation(summary = "指标目录", description = """
            返回全部指标定义（含未启用项），按 category + sortNo 排序。
            weight 为原始权重，不归一化 —— 综合分是按「本次实际算出的指标」归一化的。""")
    public Result<List<MetricDefinitionResponse>> listMetrics() {
        return Result.success(evaluationCatalogAppService.listMetrics());
    }
}
