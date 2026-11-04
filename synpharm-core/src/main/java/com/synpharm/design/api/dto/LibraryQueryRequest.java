package com.synpharm.design.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 资产库检索条件。
 *
 * <p>设计要点：<b>标签与结构化属性是两条独立通道，不能合并</b>。
 * 把「分子量 300~500」也做成标签会产生几万个动态标签，字典彻底失效；
 * 反过来把「所有激酶」做成属性字段又会遇到「一个靶点属于多个家族」。
 *
 * <p>{@code tagMode} 默认 AND：默认 OR 的话「激酶 + 肿瘤」会返回全部激酶和全部肿瘤，
 * 用户会得出「标签筛选没用」的结论。
 *
 * @author SynPharm Team
 */
@Data
public class LibraryQueryRequest {

    /** 关键词；命中 title / subtitle / searchText（含别名），非只查名称 */
    @Size(max = 200, message = "q 最长 200 字符")
    private String q;

    /** 标签码，多选；与 tagMode 配合 */
    private List<String> tags;

    /** AND=全部命中（默认）/ OR=任一命中 */
    private String tagMode = "AND";

    /** 限定只在这些标签组内匹配，避免跨组 AND 永远为空 */
    private List<String> tagGroups;

    /** TARGET / MOLECULE / SEQUENCE；为空则不限 */
    private String itemType;

    /** 只查某个数据集；为空则查当前用户可见的全部 */
    private Long datasetId;

    /** 数据集可见范围：ALL（默认）/ PUBLIC / PRIVATE / SHARED */
    private String scope;

    /** 数据完备度：EXPERIMENTAL_STRUCTURE / PREDICTED_ONLY / NO_STRUCTURE */
    private String qualityLevel;

    /** 精确过滤：物种 */
    @Size(max = 60)
    private String organism;

    /** 数值区间（仅 MOLECULE 有效；数值进属性，不进标签） */
    private BigDecimal mwMin;
    private BigDecimal mwMax;
    private BigDecimal logpMin;
    private BigDecimal logpMax;

    /** relevance（默认）/ title / updated_at */
    private String sort = "relevance";

    @Min(value = 1, message = "page 从 1 开始")
    private int page = 1;

    @Min(value = 1, message = "pageSize 最小 1")
    @Max(value = 200, message = "pageSize 最大 200")
    private int pageSize = 20;

    /** 标签是否要求全部命中（内部派生，不对外暴露为参数名） */
    public boolean requireAllTags() {
        return !"OR".equalsIgnoreCase(tagMode);
    }
}
