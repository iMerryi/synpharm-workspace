package com.synpharm.api.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具注册表条目 —— <b>跨服务契约</b>。
 *
 * <p>放在 {@code synpharm-api} 而不是 platform 自己的 {@code api.dto} 里：设计域的命令面板
 * 需要按 {@code category} 分组、用 {@code name} 做按钮文案，还要 {@code available}
 * 决定按钮是否置灰。只要它被两个服务同时使用，就必须有一份**唯一**的定义，
 * 否则两边各自演化，最终会出现「字段名一样、语义不同」的隐性不兼容。
 *
 * <p>为什么不复用 platform 的能力矩阵 DTO：能力矩阵是给前端画<b>状态灯</b>的，
 * 只需要「谁可用、为什么不可用」；命令面板要的是「怎么分组、按钮叫什么」。
 * 二者字段集与演化节奏不同，混用会让公开契约被内部需求牵着走。
 *
 * @author SynPharm Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolRegistryItem {

    private String toolCode;

    private String name;

    private String category;

    /** 真实性边界，命令面板需要据此提示（红线 R1） */
    private String realness;

    private Boolean enabled;

    /** 能力事实（权威），只由探测回写 */
    private Boolean available;

    /** 不可用原因，UI 直接展示（红线 R2） */
    private String probeMsg;

    private Boolean agentExposed;

    private String agentToolName;
}
