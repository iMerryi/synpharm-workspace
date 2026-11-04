package com.synpharm.design.application;

import com.synpharm.design.api.dto.CandidatePredictionAttachRequest;
import com.synpharm.design.api.dto.CandidatePredictionResponse;
import java.util.List;

/**
 * 候选 × 外部预测结论。
 *
 * <p>用途是「与设计项目打通」：候选在评价体系里跑一次预测，结论能回到候选评估页，
 * 而不是看完就丢在预测记录里。
 *
 * @author SynPharm Team
 */
public interface CandidatePredictionAppService {

    /** 把一条预测结论挂到候选上；同 {@code predictResultId} 重复挂返回已有记录 */
    CandidatePredictionResponse attach(Long candidateId, CandidatePredictionAttachRequest request);

    /** 新→旧列出该候选的预测结论 */
    List<CandidatePredictionResponse> list(Long candidateId);

    /** 解除挂接（逻辑删除）；不是候选本人的记录返回 404 而不是 403，避免探测 */
    void detach(Long candidateId, Long predictionId);
}
