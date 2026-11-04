package com.synpharm.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SynPharm 主业务服务（core）。
 *
 * <p>持有 {@code synpharm_core} schema，是这些表<b>唯一</b>的写入方：需求分析 / 生成 /
 * 编辑 / 决策四段全部落在这里。
 *
 * <p><b>2026-11-04 再次拆分</b>：{@code platform}（工具注册表 + 能力探测）与
 * {@code asset}（文件资产）已从本服务摘出，各自独立成 {@code synpharm-platform} /
 * {@code synpharm-asset}，各自独占一个 schema。它们与设计域<b>双向零 import</b>，
 * 摘出去是纯搬运；换来的收益是设计域不必再为大文件上传放开 200MB 上限、
 * 也不必跟着能力探测的定时任务一起重启。
 *
 * <p><b>本服务后续还会继续拆</b>：靶点与结构、生成与候选池、评估、编辑、决策、数据资产库
 * 各自成服务（证据见 {@code docs/architecture/SpringCloud微服务重构方案.md} §3）。
 * 每次拆分都以「全量 e2e 全绿」为验收条件。
 *
 * <p>鉴权边界：本服务<b>不</b>校验 JWT —— 网关已验签并注入 {@code X-User-Id} / {@code X-User-Role}。
 * 防伪造由 {@link com.synpharm.common.security.InternalRequestFilter} + 内部令牌负责。
 *
 * @author SynPharm Team
 */
// 扫描 com.synpharm：公共库（synpharm-common）里的过滤器 / 全局异常处理器 / MybatisPlusConfig 才会装配
@SpringBootApplication(scanBasePackages = "com.synpharm")
@EnableDiscoveryClient
@ConfigurationPropertiesScan
// 服务间 Feign 契约（synpharm-api）：工具注册表来自 synpharm-platform
@EnableFeignClients(basePackages = "com.synpharm.api")
@MapperScan("com.synpharm.design.infrastructure.persistence")
public class SynpharmCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(SynpharmCoreApplication.class, args);
    }
}
