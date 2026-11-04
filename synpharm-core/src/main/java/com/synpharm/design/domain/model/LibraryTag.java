package com.synpharm.design.domain.model;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * library_tag · 标签字典。
 *
 * <p>{@code tagType} 是这套设计的核心边界，三个取值决定「谁能改」：
 *
 * <ul>
 *   <li>{@code DERIVED} —— 入库时按实际数据计算，可被重新清洗覆盖</li>
 *   <li>{@code CURATED} —— 平台人工审定，重灌<b>不可</b>覆盖</li>
 *   <li>{@code USER} —— 用户自建，重灌<b>绝对不能</b>覆盖</li>
 * </ul>
 *
 * <p>最后一条最容易被清洗脚本违反：一键重灌把用户三个月的标注全清空。所以重灌时
 * 必须按 {@code tagType} 只覆盖 {@code DERIVED} 部分。
 *
 * <p>{@code QUALITY} / {@code READY} 两组标签强制为 {@code DERIVED} 且不提供修改入口 ——
 * 它们是基线预检阻断项（{@code NO_STRUCTURE} / {@code NO_SEQUENCE}）的同源判据，
 * 允许用户手改等于允许伪造「可以立项」。
 *
 * @author SynPharm Team
 */
@Data
@TableName("library_tag")
public class LibraryTag {

    public static final String TYPE_DERIVED = "DERIVED";
    public static final String TYPE_CURATED = "CURATED";
    public static final String TYPE_USER = "USER";

    /** 用户自建标签固定归这一组 */
    public static final String GROUP_CUSTOM = "CUSTOM";

    @TableId(value = "code")
    private String code;

    private String label;

    /** SOURCE / FAMILY / DISEASE / MODALITY / PHASE / SAFETY / QUALITY / READY / CUSTOM */
    private String tagGroup;

    private String tagType;

    /** 逗号分隔，该标签可打给哪些条目类型 */
    private String itemTypes;

    private String color;

    private Integer sortNo;

    private Integer builtin;

    /** 非空 = 用户私有标签，只作用于自己的条目 */
    private Long ownerId;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
