package com.synpharm.platform.api;

import com.synpharm.platform.api.dto.AlgorithmHealthResponse;
import com.synpharm.platform.infrastructure.client.EngineHealthClient;
import com.synpharm.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统管理控制器（阶段 4 从单体迁入；单体侧同名类已删除）。
 *
 * <p>算法引擎健康检查：主动探测 FastAPI {@code GET /health/}，带响应超时、有限重试与熔断。
 * 方案 §4 把「能力探测」归 platform 域（`design_tool.available` 是能力的唯一权威），
 * 本端点即该职责的入口。
 *
 * <p>路径 2026-11-04 由 {@code /api/system/**} 改为 {@code /api/platform/**}：
 * {@code /api/system} 是单体时代的通用前缀，一个服务独占它已无意义；
 * 拆成多服务后每个前缀必须能唯一对应一个目标服务。
 *
 * @author SynPharm Team
 * @version 3.0.0
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
@Tag(name = "系统管理", description = "系统级接口（健康检查）")
public class SystemController {

    private final EngineHealthClient engineHealthClient;

    @GetMapping("/algorithm-health")
    @Operation(summary = "算法引擎健康检查", description = "探测 FastAPI 算法引擎 /health/，带超时、有限重试与熔断")
    public Result<AlgorithmHealthResponse> algorithmHealth() {
        return Result.success(engineHealthClient.health());
    }
}