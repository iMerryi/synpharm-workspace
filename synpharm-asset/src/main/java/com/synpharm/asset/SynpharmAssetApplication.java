package com.synpharm.asset;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 文件资产服务。
 *
 * <p>职责单一：{@code file_asset} 的唯一写入方 —— 内容寻址（SHA256 去重）+ 引用计数。
 *
 * <p><b>为什么独立成服务</b>：① 与设计域双向零 import，拆分零改造；
 * ② 它是唯一需要"大文件 + 本地卷"的服务，2026-10-22 合并后设计域被拖着
 * 一起放开了 200MB 的 multipart 上限，这是不必要的攻击面；
 * ③ 存储后端将来换 S3/MinIO 时，只影响这一个服务。
 *
 * <p><b>不暴露到网关</b>：端点前缀是 {@code /internal/files/**}，属服务间专用路径。
 * 网关不路由它，{@link com.synpharm.common.security.InternalRequestFilter} 会校验内部令牌。
 *
 * @author SynPharm Team
 */
@SpringBootApplication(scanBasePackages = "com.synpharm")
@EnableDiscoveryClient
@ConfigurationPropertiesScan
@MapperScan("com.synpharm.asset.infrastructure.persistence")
public class SynpharmAssetApplication {

    public static void main(String[] args) {
        SpringApplication.run(SynpharmAssetApplication.class, args);
    }
}
