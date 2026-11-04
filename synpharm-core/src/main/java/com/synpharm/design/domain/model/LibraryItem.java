package com.synpharm.design.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * library_item · 库条目的跨类型检索投影。
 *
 * <p><b>为什么要有这张冗余表</b>：标签若挂在 {@code target} / {@code library_molecule}
 * 各自的表上，跨类型查询就得 {@code UNION ALL} 三种形状，标签过滤要重复写三遍，
 * 而且分页会错 —— 每类各取 20 条 ≠ 全局前 20 条。冗余一张投影表（写入时同事务双写），
 * 换来一条 SQL 完成「跨类型 + 标签 + 关键词 + 分页」，且 {@code item_count}
 * 不用实时 count 三张表。
 *
 * <p>{@code qualityLevel} 是 {@code QUALITY} / {@code READY} 两组标签的来源，也是基线
 * 预检 {@code NO_STRUCTURE} 判定的同源数据。<b>它必须按实际结构推导，不接受用户输入</b>：
 * 用户若能把自己标成「有实验结构」，基线就会放行一个根本没有结构的靶点，
 * 然后崩在生成准备环节，事后极难追查。
 *
 * @author SynPharm Team
 */
@Data
@TableName("library_item")
public class LibraryItem {

    /** 有条目类型：靶点 */
    public static final String TYPE_TARGET = "TARGET";

    /** 条目类型：小分子 */
    public static final String TYPE_MOLECULE = "MOLECULE";

    /** 条目类型：抗体/多肽序列 */
    public static final String TYPE_SEQUENCE = "SEQUENCE";

    public static final String QUALITY_EXPERIMENTAL = "EXPERIMENTAL_STRUCTURE";
    public static final String QUALITY_PREDICTED = "PREDICTED_ONLY";
    public static final String QUALITY_NONE = "NO_STRUCTURE";
    public static final String QUALITY_UNKNOWN = "UNKNOWN";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    private String itemType;

    /** 指向 target.id 或 library_molecule.id */
    private Long entityId;

    /** 统一展示名：靶点=基因名，分子=通用名 */
    private String title;

    /** 副标题：靶点=中文名 · UniProt，分子=分子式 */
    private String subtitle;

    /** 名称+别名+基因名+UniProt+描述 拼接，供模糊查询 */
    private String searchText;

    /** EXPERIMENTAL_STRUCTURE / PREDICTED_ONLY / NO_STRUCTURE / UNKNOWN */
    private String qualityLevel;

    /** 复制血缘：源 item id；从零新建为 null */
    private Long derivedFromId;

    private Long derivedFromDatasetId;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
