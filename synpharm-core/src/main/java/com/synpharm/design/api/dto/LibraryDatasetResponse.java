package com.synpharm.design.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 库容器（契约 {@code LibraryDataset}）。
 *
 * <p>{@code canWrite} / {@code isOwner} 由后端算好给前端，不让前端自己拼权限判断 ——
 * 前端算权限迟早会和后端不一致，表现就是「按钮能点但请求 403」。
 *
 * @author SynPharm Team
 */
@Data
public class LibraryDatasetResponse {

    private Long id;

    private String code;

    private String name;

    private String description;

    /** TARGET / MOLECULE / SEQUENCE */
    private String itemType;

    /** PUBLIC / PRIVATE */
    private String visibility;

    private Long ownerId;

    /** 属主昵称；公有为 null。列表里显示「张三的库」比显示 ownerId 有用 */
    private String ownerName;

    /** SYSTEM / USER_MANUAL / USER_IMPORT */
    private String source;

    private Integer itemCount;

    private String tags;

    /** ACTIVE / ARCHIVED */
    private String status;

    /** 当前用户是否属主 */
    private Boolean isOwner;

    /** 当前用户能否增改条目（属主，或被授予 WRITE） */
    private Boolean canWrite;

    /** 当前用户能否删除/授权（仅属主；被授权者一律不能，防权限扩散） */
    private Boolean canManage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
