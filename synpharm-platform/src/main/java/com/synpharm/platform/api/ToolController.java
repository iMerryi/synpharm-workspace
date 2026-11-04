package com.synpharm.platform.api;

import com.synpharm.common.api.Result;
import com.synpharm.common.security.CurrentUser;
import com.synpharm.common.web.PageResult;
import com.synpharm.platform.api.dto.CapabilitiesResponse;
import com.synpharm.platform.api.dto.ToolResponse;
import com.synpharm.platform.api.dto.ToolUpsertRequest;
import com.synpharm.platform.application.ToolAppService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具与能力端点（契约 4.3.11，6 个操作）。
 *
 * <p><b>路径已改为 {@code /api/platform/**}（2026-11-04）</b>：原先它挂在
 * {@code /api/design/**} 之下，靠网关的一条「必须排在 core 之前」的路由分流 ——
 * 那是拆分铁律 R1 说的「按文档章节/前端功能域切」的典型症状：路径表达的是前端功能域，
 * 数据所有权却在另一个服务，于是只能靠路由声明顺序维持正确性，顺序一改就静默 404。
 *
 * <p>现在路径与服务一一对应：{@code /api/platform/**} 只有一个目标。设计域若需要
 * 工具清单，走 Feign 读本服务的 {@code /internal/tools}，不再共享 URL 前缀。
 *
 * @author SynPharm Team
 */
@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class ToolController {

    private final ToolAppService toolAppService;

    /** 工具列表 */
    @GetMapping("/tools")
    public Result<PageResult<ToolResponse>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(toolAppService.list(category, enabled, page, pageSize));
    }

    /** 注册工具；{@code realness} 必填，{@code available} 不接受传入 */
    @PostMapping("/tools")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ToolResponse> create(@Valid @RequestBody ToolUpsertRequest request) {
        return Result.success(toolAppService.create(request));
    }

    /** 工具详情 */
    @GetMapping("/tools/{id}")
    public Result<ToolResponse> get(@PathVariable Long id) {
        return Result.success(toolAppService.get(id));
    }

    /** 更新工具（启用/禁用/暴露给智能体等）；不传的字段保持不变 */
    @PatchMapping("/tools/{id}")
    public Result<ToolResponse> patch(@PathVariable Long id,
                                      @RequestBody ToolUpsertRequest request) {
        return Result.success(toolAppService.patch(id, request));
    }

    /** 能力探测（回写 available / probe_msg）；探测本身失败返回 502 */
    @PostMapping("/tools/{id}/probe")
    public Result<ToolResponse> probe(@PathVariable Long id) {
        return Result.success(toolAppService.probe(id));
    }

    /**
     * 当前能力矩阵（前端能力状态灯）。
     *
     * <p><b>登录校验为什么在 controller 而不是 AppService</b>：同一个
     * {@link ToolAppService#capabilities()} 还被 {@code InternalToolController} 的
     * {@code /internal/tools/capabilities} 复用，那条是 design 的服务间调用、**没有用户身份**。
     * 把这行校验下沉到 AppService 会直接打断 design 的命令注册表（见问题总账 H-27）。
     */
    @GetMapping("/capabilities")
    public Result<CapabilitiesResponse> capabilities() {
        CurrentUser.requireUserId();
        return Result.success(toolAppService.capabilities());
    }
}