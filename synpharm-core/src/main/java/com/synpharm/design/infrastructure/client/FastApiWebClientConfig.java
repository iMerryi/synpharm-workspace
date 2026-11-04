package com.synpharm.design.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 计算层（FastAPI）WebClient —— 设计域自用。
 *
 * <p><b>为什么这里要有一份</b>：这个 Bean 原先定义在 {@code platform} 包里
 * （{@code com.synpharm.platform.support.WebClientConfig}），但注入它的是设计域的
 * {@link ChemistryClient}。2026-11-04 把 platform 摘成独立服务时，这一点靠
 * 「搜 import」是看不见的 —— 两个包之间没有 import，耦合藏在 Spring 的按类型注入里，
 * 摘走之后 core 会以「no qualifying bean of type WebClient」启动失败。
 *
 * <p>正确做法不是把这个 Bean 塞进共享库：拆成两个进程之后，它们本来就是两个客户端，
 * 各自的 base-url、超时、API Key 都可能不同。共享一个 Bean 只会把两个服务的配置绑死。
 *
 * @author SynPharm Team
 */
@Configuration
public class FastApiWebClientConfig {

    @Value("${fastapi.base-url}")
    private String baseUrl;

    @Value("${fastapi.api-key:}")
    private String apiKey;

    @Bean
    public WebClient fastApiWebClient() {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader("X-API-Key", apiKey);
        }
        return builder.build();
    }
}
