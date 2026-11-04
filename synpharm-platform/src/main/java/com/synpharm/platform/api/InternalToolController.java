package com.synpharm.platform.api;

import com.synpharm.api.platform.ToolRegistryItem;
import com.synpharm.common.api.Result;
import com.synpharm.platform.application.ToolAppService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工具注册表内网端点（供设计域的命令面板调用）。
 *
 * <p><b>为什么是 {@code /internal/**} 而不是 {@code /api/**}</b>：它不面向浏览器 ——
 * 网关没有 {@code /internal/**} 的路由，所以无法经网关到达；服务间通过服务名直连
 * （{@code @FeignClient(name = "synpharm-platform", path = "/internal/tools")}）。
 * 身份仍由 {@code X-Internal-Token} 守门（fail-closed），不因为「内网」就免鉴权。
 *
 * <p><b>为什么设计域不直接读 {@code design_tool} 表</b>：拆分铁律 R2 —— 每张表只有一个
 * 服务能写，读也只能走拥有者的接口。合并期两者同库，design 的命令面板确实可以直接 SELECT，
 * 但那正是「拆了又退回去」的路径。
 *
 * @author SynPharm Team
 */
@RestController
@RequestMapping("/internal/tools")
@RequiredArgsConstructor
public class InternalToolController {

    private final ToolAppService toolAppService;

    /**
     * 全量工具注册表（{@code available} 是能力事实的权威）。
     *
     * <p>不加登录校验：内网调用没有用户身份，鉴权由内部令牌完成；
     * 公开端点 {@code GET /api/platform/capabilities} 的登录校验在 controller 层。
     */
    @GetMapping("/registry")
    public Result<List<ToolRegistryItem>> registry() {
        return Result.success(toolAppService.registry());
    }
}
