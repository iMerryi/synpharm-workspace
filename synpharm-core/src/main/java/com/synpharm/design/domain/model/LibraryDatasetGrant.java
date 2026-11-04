package com.synpharm.design.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * library_dataset_grant · 私有库授权（个人级）。
 *
 * <p>刻意只有两档权限，且 {@code WRITE} <b>不包含</b>删库与再授权：
 * 一次 {@code WRITE} 授权如果连带「可再授权」，权限就会沿着人际关系链无限扩散，
 * 而库所有者完全不知道自己的数据被转授给了谁。
 *
 * <p>不做团队/机构授权：{@code sys_user.institution} / {@code lab} 是自由文本，
 * 不能作权限主体。要让「分享给某个实验室」真正生效，必须先有 {@code org} 实体表。
 *
 * @author SynPharm Team
 */
@Data
@TableName("library_dataset_grant")
public class LibraryDatasetGrant {

    public static final String PERMISSION_READ = "READ";
    public static final String PERMISSION_WRITE = "WRITE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private Long granteeUserId;

    /** READ / WRITE */
    private String permission;

    private Long grantedBy;

    private LocalDateTime createdAt;
}
