package com.synpharm.api.platform;

import com.synpharm.common.api.Result;
import com.synpharm.common.error.ErrorCode;
import com.synpharm.common.exception.BizException;
import feign.FeignException;
import feign.Request;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PlatformToolApi#registry()} 的失败翻译。
 *
 * <p>这些用例守同一个原则：<b>下游出问题时必须报 503，不能返回空清单</b>。
 * 空清单在 UI 上表现为「所有工具都不可用」，用户会以为是引擎缺能力，
 * 而不是「平台服务挂了」—— 两种结论的处置方式完全不同。
 *
 * @author SynPharm Team
 */
class PlatformToolApiTest {

    private static final Request REQUEST = Request.create(
            Request.HttpMethod.GET, "/internal/tools/registry", Map.of(), null,
            StandardCharsets.UTF_8, null);

    /** 只重写 registryEnvelope，其余走 default 实现 */
    private static PlatformToolApi apiReturning(Result<List<ToolRegistryItem>> envelope) {
        return () -> envelope;
    }

    @Test
    @DisplayName("正常：code=0 时返回 data")
    void returnsDataOnSuccess() {
        ToolRegistryItem item = ToolRegistryItem.builder().toolCode("mol_properties").build();
        assertThat(apiReturning(Result.success(List.of(item))).registry())
                .extracting(ToolRegistryItem::getToolCode)
                .containsExactly("mol_properties");
    }

    @Test
    @DisplayName("data 为 null 时返回空列表，不抛错")
    void emptyDataBecomesEmptyList() {
        assertThat(apiReturning(Result.success(null)).registry()).isEmpty();
    }

    @Test
    @DisplayName("业务 code 非 0：翻成 DEPENDENCY_UNAVAILABLE，而不是空列表")
    void nonZeroCodeBecomesDependencyUnavailable() {
        Result<List<ToolRegistryItem>> failure = Result.failure(ErrorCode.INTERNAL_ERROR, "平台内部错误");
        assertThatThrownBy(() -> apiReturning(failure).registry())
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    @DisplayName("HTTP 401（内部令牌不符）：翻成 DEPENDENCY_UNAVAILABLE")
    void feignClientErrorBecomesDependencyUnavailable() {
        PlatformToolApi api = () -> {
            throw new FeignException.Unauthorized("401", REQUEST, null, Map.of());
        };
        assertThatThrownBy(api::registry)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }

    @Test
    @DisplayName("下游不可达（连接被拒/超时）：翻成 DEPENDENCY_UNAVAILABLE")
    void transportFailureBecomesDependencyUnavailable() {
        PlatformToolApi api = () -> {
            throw new RetryableException(503, "Connection refused", Request.HttpMethod.GET, (Long) null, REQUEST);
        };
        assertThatThrownBy(api::registry)
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
    }
}
