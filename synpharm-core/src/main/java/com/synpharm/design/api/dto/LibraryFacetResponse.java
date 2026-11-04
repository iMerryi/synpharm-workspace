package com.synpharm.design.api.dto;

import java.util.List;
import lombok.Data;

/**
 * 分面筛选结果（标签逐步收窄）。
 *
 * <p>为什么需要它：数据集里只有几条时，「一次列出全部条目 + 一个关键词框」够用；
 * 几千条之后同一个界面就退化成翻页地狱 —— 用户不知道自己要找的在第几页，
 * 也看不出「现在还剩哪些维度可收窄」。
 *
 * <p>分面计数按<b>扣掉本组已选标签</b>计算：选了「肿瘤」之后，「靶点家族」组里
 * 各家族的计数应该是「肿瘤 ∩ 该家族」的数量，否则用户点到家族时会发现是 0 条。
 * 这是分面筛选与普通筛选的区别 —— 普通筛选只给结果，分面还要告诉你怎么走下去。
 *
 * <p>{@code total} 是当前<b>全部</b>筛选条件下的命中数；{@code tags} 里每个标签的
 * {@code usageCount} 是该标签在「除自己所在组外其余条件都成立」时的可用条目数。
 *
 * @author SynPharm Team
 */
@Data
public class LibraryFacetResponse {

    /** 当前筛选条件下的条目总数（含标签条件） */
    private long total;

    /** 可作为进一步收窄的标签；usageCount=0 的是「选了也只会空」，前端据此隐藏 */
    private List<LibraryTagResponse> tags;
}
