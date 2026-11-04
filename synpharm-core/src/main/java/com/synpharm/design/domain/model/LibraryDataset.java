package com.synpharm.design.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * library_dataset · 数据资产库容器。
 *
 * <p>这是<b>用户在界面上看到的那一行</b>「公共靶点库」「我的突变体库」，与
 * {@code target_dataset_release}（上游发布版本，见 08 数据仓库设计）是两个抽象层：
 * 用户不感知上游有几个 release，只看到库里有多少条。
 *
 * <p>一个数据集只装一种 {@code itemType}：混装会让字段、导入模板、校验全部退化成
 * if-else。用户要装两类就建两个库，界面上统一叫「库」，不逼他理解类型。
 *
 * @author SynPharm Team
 */
@Data
@TableName("library_dataset")
public class LibraryDataset {

    /** 可见性：全体可读，平台维护，界面不提供写入 */
    public static final String VISIBILITY_PUBLIC = "PUBLIC";

    /** 可见性：仅属主与被授权者 */
    public static final String VISIBILITY_PRIVATE = "PRIVATE";

    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 停用不删：已绑定该库靶点的项目仍要能读，只是不再出现在可选列表 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 稳定编码，如 PUBLIC_TARGET */
    private String code;

    private String name;

    private String description;

    /** TARGET / MOLECULE / SEQUENCE */
    private String itemType;

    /** PUBLIC / PRIVATE */
    private String visibility;

    /** 公有数据集为 NULL */
    private Long ownerId;

    /** SYSTEM / USER_MANUAL / USER_IMPORT */
    private String source;

    /** 由 library_item 维护，不做实时 count */
    private Integer itemCount;

    private String tags;

    /** ACTIVE / ARCHIVED */
    private String status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
