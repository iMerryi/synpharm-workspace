package com.synpharm.design.application.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.synpharm.common.support.JsonCodec;
import com.synpharm.design.api.dto.MetricDefinitionResponse;
import com.synpharm.design.application.EvaluationCatalogAppService;
import com.synpharm.design.domain.model.MetricDefinition;
import com.synpharm.design.infrastructure.persistence.MetricDefinitionMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 指标目录实现。
 *
 * <p>权重口径：{@code weight} 之和不必为 1，综合分按「本次实际算出的指标」的权重
 * 归一化（见 06 §5.1）。所以这里原样回传权重，不在服务端做任何换算 ——
 * 换算后的数字会让人误以为「权重加起来是 100%」。
 *
 * @author SynPharm Team
 */
@Service
@RequiredArgsConstructor
public class EvaluationCatalogAppServiceImpl implements EvaluationCatalogAppService {

    private final MetricDefinitionMapper metricDefinitionMapper;
    private final JsonCodec jsonCodec;

    @Override
    public List<MetricDefinitionResponse> listMetrics() {
        List<MetricDefinition> rows = metricDefinitionMapper.selectList(
                Wrappers.lambdaQuery(MetricDefinition.class)
                        .orderByAsc(MetricDefinition::getCategory)
                        .orderByAsc(MetricDefinition::getSortNo));
        List<MetricDefinitionResponse> result = new ArrayList<>(rows.size());
        for (MetricDefinition row : rows) {
            result.add(toResponse(row));
        }
        return result;
    }

    private MetricDefinitionResponse toResponse(MetricDefinition row) {
        MetricDefinitionResponse dto = new MetricDefinitionResponse();
        dto.setMetricCode(row.getMetricCode());
        dto.setMetricName(row.getMetricName());
        dto.setCategory(row.getCategory());
        dto.setDimension(row.getDimension());
        dto.setValueType(row.getValueType());
        dto.setUnit(row.getUnit() == null ? "" : row.getUnit());
        dto.setBetter(row.getBetter());
        dto.setMinValue(row.getMinValue());
        dto.setMaxValue(row.getMaxValue());
        dto.setWeight(row.getWeight());
        dto.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        dto.setSortNo(row.getSortNo());
        dto.setRemark(row.getRemark());
        dto.setAppliesTo(parseAppliesTo(row.getAppliesTo()));
        return dto;
    }

    /** {@code applies_to} 是 JSON 数组文本；解析失败按「不限」处理，不因此让整页 500 */
    private List<String> parseAppliesTo(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            String[] parsed = jsonCodec.read(raw, String[].class);
            return parsed == null ? List.of() : Arrays.asList(parsed);
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
