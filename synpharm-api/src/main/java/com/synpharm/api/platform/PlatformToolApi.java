package com.synpharm.api.platform;

import com.synpharm.common.api.Result;
import com.synpharm.common.constant.ServiceNames;
import com.synpharm.common.error.ErrorCode;
import com.synpharm.common.exception.BizException;
import feign.FeignException;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 平台治理服务（{@code synpharm-platform}）的服务间契约。
 *
 * <p>端点走 {@code /internal/**}：网关不路由该前缀，所以调用方必须经服务发现直连
 * （Feign + LoadBalancer），并带上 {@code X-Internal-Token}
 * （由 {@code InternalTokenFeignInterceptor} 自动注入，见 {@code SynpharmApiAutoConfiguration}）。
 *
 * <p><b>三种失败必须都翻成 503，不能有任何一种退化成空清单</b>：
 * <ol>
 *   <li>下游不可达 / 超时 → Feign 抛 {@code RetryableException}（{@code FeignException} 子类）</li>
 *   <li>下游返回 HTTP 非 2xx（例如内部令牌不匹配的 401）→ {@code FeignException}</li>
 *   <li>下游返回 HTTP 200 但业务 code 非 0 → 信封里带错误</li>
 * </ol>
 * 命令面板靠这个区分「引擎确实没有这个能力」（available=false，正常展示置灰）
 * 与「平台服务挂了」（503，页面该报错）。混成 500 或空列表都会让用户看到错误的结论。
 *
 * @author SynPharm Team
 */
@FeignClient(name = ServiceNames.PLATFORM, path = "/internal/tools")
public interface PlatformToolApi {

    /** 原始信封（保留给需要读 message / errorCode 的调用方） */
    @GetMapping("/registry")
    Result<List<ToolRegistryItem>> registryEnvelope();

    /**
     * 工具注册表全量。
     *
     * @throws BizException {@code DEPENDENCY_UNAVAILABLE} —— 下游不可达、HTTP 非 2xx 或业务 code 非 0
     */
    default List<ToolRegistryItem> registry() {
        Result<List<ToolRegistryItem>> envelope;
        try {
            envelope = registryEnvelope();
        } catch (FeignException e) {
            // 连接被拒 / 超时 / 401 内部令牌不符 / 5xx —— 一律是「下游不可用」
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "平台治理服务不可用：" + e.status() + " " + e.getMessage());
        }
        if (envelope == null) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "平台治理服务返回空响应：无法读取工具注册表");
        }
        if (envelope.getCode() == null || envelope.getCode() != Result.SUCCESS_CODE) {
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "平台治理服务返回错误：" + envelope.getMessage());
        }
        return envelope.getData() == null ? List.of() : envelope.getData();
    }
}
