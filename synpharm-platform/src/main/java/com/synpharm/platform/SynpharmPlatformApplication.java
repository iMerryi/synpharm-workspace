package com.synpharm.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 平台治理服务。
 *
 * <p>职责单一：维护 {@code design_tool}（工具注册表）与算法引擎能力矩阵，是
 * {@code design_tool.available} 的<b>唯一</b>写入方。设计域只读，改能力走这里。
 *
 * <p><b>为什么独立成服务</b>：它与设计域<b>零代码依赖</b>（双向无 import），
 * 而它的变更节奏完全不同 —— 引擎加一个算子就要动它，与"项目/候选/决策"无关。
 * 合成一个进程时，一次能力探测失败会把整个设计域的线程池拖住。
 *
 * <p>鉴权边界：本服务<b>不</b>校验 JWT —— 网关已验签并注入 {@code X-User-Id}。
 * 防伪造由 {@link com.synpharm.common.security.InternalRequestFilter} + 内部令牌负责。
 *
 * @author SynPharm Team
 */
@SpringBootApplication(scanBasePackages = "com.synpharm")
@EnableDiscoveryClient
// 能力矩阵定时探测（红线 R1：design_tool.available 的唯一数据源）
@EnableScheduling
@ConfigurationPropertiesScan
@MapperScan("com.synpharm.platform.infrastructure.persistence")
public class SynpharmPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SynpharmPlatformApplication.class, args);
    }
}
