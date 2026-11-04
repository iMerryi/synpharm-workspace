package com.synpharm.design.api;

import com.synpharm.common.api.Result;
import com.synpharm.design.api.dto.CandidatePredictionAttachRequest;
import com.synpharm.design.api.dto.CandidatePredictionResponse;
import com.synpharm.design.application.CandidatePredictionAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设计域 · 候选的外部预测结论（评价体系回写）。
 *
 * <p>用裸路径而不是 {@code :} 后缀：这里操作的是一个**集合**（一个候选可以有多条预测），
 * 不是像 {@code evaluation:freeze} 那样的单次动作。
 *
 * @author SynPharm Team
 */
@RestController
@RequestMapping("/api/design/candidates")
@RequiredArgsConstructor
@Tag(name = "设计域 · 候选预测回写", description = "把评价体系里的分子预测结论挂到候选上")
public class CandidatePredictionController {

    private final CandidatePredictionAppService candidatePredictionAppService;

    @PostMapping("/{candidateId}/predictions")
    @Operation(summary = "挂接一条预测结论",
            description = """
                    把预测服务返回的结论记到候选上。同 predictResultId 重复挂接返回已有记录（幂等）。
                    可能返回：CANDIDATE_NOT_FOUND(40443)、VALIDATION_FAILED(40000，algoType 非法)。""")
    public Result<CandidatePredictionResponse> attach(
            @PathVariable Long candidateId,
            @RequestBody CandidatePredictionAttachRequest request) {
        return Result.success(candidatePredictionAppService.attach(candidateId, request));
    }

    @GetMapping("/{candidateId}/predictions")
    @Operation(summary = "候选的预测结论列表", description = "新→旧；用于评估页展示「外部预测」区块")
    public Result<List<CandidatePredictionResponse>> list(@PathVariable Long candidateId) {
        return Result.success(candidatePredictionAppService.list(candidateId));
    }

    @DeleteMapping("/{candidateId}/predictions/{predictionId}")
    @Operation(summary = "解除挂接", description = "逻辑删除；不影响预测域里的原始记录")
    public Result<Void> detach(@PathVariable Long candidateId, @PathVariable Long predictionId) {
        candidatePredictionAppService.detach(candidateId, predictionId);
        return Result.success(null);
    }
}
