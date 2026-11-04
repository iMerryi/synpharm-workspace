package com.synpharm.api.config;

import com.synpharm.common.constant.InternalHeaders;
import com.synpharm.common.security.CurrentUser;
import com.synpharm.common.security.SynpharmInternalProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

/**
 * 给所有 Feign 调用补上内部身份头。
 *
 * <p><b>为什么必须统一在这做</b>：{@code /internal/**} 端点由
 * {@code InternalRequestFilter} 以 fail-closed 方式守门 —— 没带正确
 * {@code X-Internal-Token} 的请求一律 401。如果让每个调用方自己记得加头，
 * 迟早漏一个，而漏掉的表现是「某个功能突然 500」，排查要从业务一路看到过滤器。
 *
 * <p>另一个转发的是 {@code X-User-Id}：下游做归属校验需要知道「这次调用代表谁」。
 * 但 Feign 调用不一定发生在请求线程里（例如定时任务），所以取不到身份时**不加**该头，
 * 由下游按「无用户上下文」处理（多数内部端点是系统级调用，本就不需要用户身份）。
 *
 * @author SynPharm Team
 */
@RequiredArgsConstructor
public class InternalTokenFeignInterceptor implements RequestInterceptor {

    private final Supplier<SynpharmInternalProperties> propertiesSupplier;

    @Override
    public void apply(RequestTemplate template) {
        SynpharmInternalProperties properties = propertiesSupplier.get();
        if (properties != null && StringUtils.hasText(properties.getToken())) {
            template.header(InternalHeaders.INTERNAL_TOKEN, properties.getToken());
        }

        CurrentUser.Identity identity = CurrentUser.get();
        if (identity != null) {
            if (identity.userId() != null) {
                template.header(InternalHeaders.USER_ID, String.valueOf(identity.userId()));
            }
            if (StringUtils.hasText(identity.role())) {
                template.header(InternalHeaders.USER_ROLE, identity.role());
            }
            if (StringUtils.hasText(identity.tokenId())) {
                template.header(InternalHeaders.TOKEN_ID, identity.tokenId());
            }
        }
    }
}
