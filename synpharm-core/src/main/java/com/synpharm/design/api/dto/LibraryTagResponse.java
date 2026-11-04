package com.synpharm.design.api.dto;

import lombok.Data;

/**
 * 标签（契约 {@code LibraryTag}）。
 *
 * <p>{@code editable} 由后端给出：{@code DERIVED} / {@code CURATED} 一律 false。
 * 前端据此把标签显示成只读 —— <b>QUALITY / READY 两组若允许用户手改，基线预检的
 * 「无结构」判定就能被伪造</b>，等于放行一个根本没有结构的靶点。
 *
 * @author SynPharm Team
 */
@Data
public class LibraryTagResponse {

    private String code;

    private String label;

    /** SOURCE / FAMILY / DISEASE / MODALITY / PHASE / SAFETY / QUALITY / READY / CUSTOM */
    private String tagGroup;

    /** DERIVED / CURATED / USER */
    private String tagType;

    private String color;

    private Integer sortNo;

    /** 用户能否修改/删除该标签定义 */
    private Boolean editable;

    /** 该标签当前被多少条目引用 */
    private Long usageCount;
}
