package com.synpharm.design.api.dto;

import lombok.Data;

/**
 * 库条目 → 生成种子的解析结果。
 *
 * <p>为什么单独一个 VO 而不是复用 {@link LibraryDatasetItemResponse}：列表 VO 的
 * {@code payload} 是按类型分派的松散 Map，取重链/轻链要 `(String) map.get(...)` 强转，
 * 而生成侧需要的是明确的字段。更重要的是<b>完整链序列不该出现在列表接口里</b>
 * （一条抗体 400+ 字符，20 条列表就是 8KB 噪声），只有真要拿它当种子时才取。
 *
 * @author SynPharm Team
 */
@Data
public class LibrarySeedPayload {

    /** library_item.id */
    private Long itemId;

    private Long datasetId;

    /** TARGET / MOLECULE / SEQUENCE */
    private String itemType;

    /** 展示名：优先库里的中文名，没有就退回条目 title */
    private String name;

    /** 小分子结构；抗体为 null */
    private String smiles;

    /** 抗体重链/轻链；小分子为 null */
    private String heavyChain;

    private String lightChain;

    /** 库内副标题（分子式 / 靶点基因），用于回显「你选的是哪一条」 */
    private String subtitle;
}
