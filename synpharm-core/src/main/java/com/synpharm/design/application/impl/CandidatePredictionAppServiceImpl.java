package com.synpharm.design.application.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.synpharm.common.error.ErrorCode;
import com.synpharm.common.exception.BizException;
import com.synpharm.common.security.CurrentUser;
import com.synpharm.common.security.OwnershipGuard;
import com.synpharm.common.support.JsonCodec;
import com.synpharm.design.api.dto.CandidatePredictionAttachRequest;
import com.synpharm.design.api.dto.CandidatePredictionResponse;
import com.synpharm.design.application.CandidatePredictionAppService;
import com.synpharm.design.domain.model.CandidateMolecule;
import com.synpharm.design.domain.model.CandidatePrediction;
import com.synpharm.design.domain.model.DesignProject;
import com.synpharm.design.infrastructure.persistence.CandidateMoleculeMapper;
import com.synpharm.design.infrastructure.persistence.CandidatePredictionMapper;
import com.synpharm.design.infrastructure.persistence.DesignProjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 候选 × 外部预测结论实现。
 *
 * <p>两条纪律：
 * <ol>
 *   <li><b>不重算、不解释</b>：数值来自预测服务，这里只做格式与归属校验。
 *       在设计域重算一遍，等于承认「两个预测器」，结论不一致时无从判断谁对。</li>
 *   <li><b>同一预测结果重复挂接返回已有记录</b>：用户点两次「回写」不该得到两条记录，
 *       也不该报 409 让人以为失败 —— 结果已经在候选上了，这就是他想要的。</li>
 * </ol>
 *
 * @author SynPharm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidatePredictionAppServiceImpl implements CandidatePredictionAppService {

    private static final List<String> ALGO_TYPES = List.of("DTI", "PPI", "DDI");

    private final CandidatePredictionMapper predictionMapper;
    private final CandidateMoleculeMapper candidateMapper;
    private final DesignProjectMapper projectMapper;
    private final JsonCodec jsonCodec;

    @Override
    public CandidatePredictionResponse attach(Long candidateId, CandidatePredictionAttachRequest request) {
        Long userId = CurrentUser.requireUserId();
        CandidateMolecule candidate = loadOwnedCandidate(candidateId, userId);
        if (request == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "请求体不能为空");
        }
        String algoType = normalizeAlgoType(request.getAlgoType());

        if (StringUtils.hasText(request.getPredictResultId())) {
            CandidatePrediction existing = predictionMapper.selectOne(
                    Wrappers.lambdaQuery(CandidatePrediction.class)
                            .eq(CandidatePrediction::getCandidateId, candidateId)
                            .eq(CandidatePrediction::getPredictResultId, request.getPredictResultId())
                            .last("LIMIT 1"));
            if (existing != null) {
                return toResponse(existing);
            }
        }

        CandidatePrediction row = new CandidatePrediction();
        row.setProjectId(candidate.getProjectId());
        row.setCandidateId(candidateId);
        row.setUserId(userId);
        row.setAlgoType(algoType);
        row.setTargetId(trimToNull(request.getTargetId()));
        row.setTargetName(trimToNull(request.getTargetName()));
        row.setLigandSmiles(trimToNull(
                StringUtils.hasText(request.getLigandSmiles()) ? request.getLigandSmiles() : candidate.getSmiles()));
        row.setBindingAffinity(request.getBindingAffinity());
        row.setConfidenceScore(request.getConfidenceScore());
        row.setConfidenceLevel(trimToNull(request.getConfidenceLevel()));
        row.setInteractions(request.getInteractions() == null ? null : jsonCodec.write(request.getInteractions()));
        row.setPredictResultId(trimToNull(request.getPredictResultId()));
        row.setDatasetInfo(request.getDatasetInfo() == null ? null : jsonCodec.write(request.getDatasetInfo()));
        row.setNote(trimToNull(request.getNote()));
        row.setDeleted(0);
        predictionMapper.insert(row);
        log.info("预测结论已挂到候选: candidateId={}, algoType={}, predictionId={}",
                candidateId, algoType, row.getId());
        return toResponse(row);
    }

    @Override
    public List<CandidatePredictionResponse> list(Long candidateId) {
        Long userId = CurrentUser.requireUserId();
        loadOwnedCandidate(candidateId, userId);
        List<CandidatePrediction> rows = predictionMapper.selectList(
                Wrappers.lambdaQuery(CandidatePrediction.class)
                        .eq(CandidatePrediction::getCandidateId, candidateId)
                        .orderByDesc(CandidatePrediction::getId));
        List<CandidatePredictionResponse> result = new ArrayList<>(rows.size());
        for (CandidatePrediction row : rows) {
            result.add(toResponse(row));
        }
        return result;
    }

    @Override
    public void detach(Long candidateId, Long predictionId) {
        Long userId = CurrentUser.requireUserId();
        loadOwnedCandidate(candidateId, userId);
        CandidatePrediction row = predictionMapper.selectById(predictionId);
        if (row == null || !candidateId.equals(row.getCandidateId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND,
                    "预测结论不存在或不属于该候选：" + predictionId,
                    Map.of("candidateId", candidateId, "predictionId", predictionId));
        }
        predictionMapper.deleteById(predictionId);
    }

    private String normalizeAlgoType(String raw) {
        String algoType = raw == null ? "" : raw.trim().toUpperCase();
        if (!ALGO_TYPES.contains(algoType)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED,
                    "algoType 必须是 " + ALGO_TYPES + " 之一，收到：" + raw);
        }
        return algoType;
    }

    private CandidateMolecule loadOwnedCandidate(Long candidateId, Long userId) {
        CandidateMolecule candidate = candidateMapper.selectById(candidateId);
        if (candidate == null) {
            throw new BizException(ErrorCode.CANDIDATE_NOT_FOUND,
                    "候选不存在：" + candidateId, Map.of("candidateId", candidateId));
        }
        DesignProject project = projectMapper.selectById(candidate.getProjectId());
        OwnershipGuard.assertOwned(userId, project == null ? null : project.getUserId(),
                ErrorCode.CANDIDATE_NOT_FOUND, Map.of("candidateId", candidateId));
        return candidate;
    }

    private CandidatePredictionResponse toResponse(CandidatePrediction row) {
        CandidatePredictionResponse dto = new CandidatePredictionResponse();
        dto.setId(row.getId());
        dto.setProjectId(row.getProjectId());
        dto.setCandidateId(row.getCandidateId());
        dto.setAlgoType(row.getAlgoType());
        dto.setTargetId(row.getTargetId());
        dto.setTargetName(row.getTargetName());
        dto.setLigandSmiles(row.getLigandSmiles());
        dto.setBindingAffinity(row.getBindingAffinity());
        dto.setConfidenceScore(row.getConfidenceScore());
        dto.setConfidenceLevel(row.getConfidenceLevel());
        dto.setInteractions(readJsonQuietly(row.getInteractions()));
        dto.setPredictResultId(row.getPredictResultId());
        dto.setDatasetInfo(readJsonQuietly(row.getDatasetInfo()));
        dto.setNote(row.getNote());
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }

    /** 明细 JSON 解析失败时返回 null，不让一条脏数据把整页 500 */
    private Object readJsonQuietly(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return jsonCodec.read(json, Object.class);
        } catch (RuntimeException e) {
            log.warn("预测明细 JSON 解析失败，按空处理: {}", json);
            return null;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
