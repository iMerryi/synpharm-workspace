package com.synpharm.api.config;

import com.synpharm.common.security.SynpharmInternalProperties;
import feign.RequestInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * {@code synpharm-api} 的自动装配。
 *
 * <p>用 Spring Boot 3 的 {@code AutoConfiguration.imports} 而不是 {@code @Component}：
 * 调用方（core / 后续拆出的各服务）只声明 {@code @EnableFeignClients(basePackages = "com.synpharm.api")}，
 * 不该再被迫记住「还要 import 一个配置类才能让内部令牌生效」。
 *
 * <p>条件装配：只有 classpath 上有 Feign 时才生效。将来若有服务只依赖
 * {@code synpharm-api} 的 DTO 而不做 Feign 调用，也不会被强加一个用不上的 Bean。
 *
 * @author SynPharm Team
 */
@AutoConfiguration
@ConditionalOnClass(RequestInterceptor.class)
public class SynpharmApiAutoConfiguration {

    /**
     * 用 {@code ObjectProvider} 而不是直接注入：{@code SynpharmInternalProperties} 由
     * 各服务的 {@code @ConfigurationPropertiesScan} 装配，若某服务没扫描到它，
     * 直接注入会让启动失败；这里退化为「没有令牌就不加头」，行为仍然明确
     * （下游会以 401 明确拒绝，而不是静默放行）。
     */
    @Bean
    public InternalTokenFeignInterceptor internalTokenFeignInterceptor(
            ObjectProvider<SynpharmInternalProperties> propertiesProvider) {
        return new InternalTokenFeignInterceptor(propertiesProvider::getIfAvailable);
    }
}
