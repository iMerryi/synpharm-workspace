package com.synpharm.design.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * library_item_tag · 条目与标签的关联。
 *
 * <p>不放 {@code deleted}：解绑即物理删除。标签关联是纯查询投影，没有审计价值 ——
 * 真正的审计在 {@code library_item} 与底层实体的 {@code created_at} 上。
 *
 * @author SynPharm Team
 */
@Data
@TableName("library_item_tag")
public class LibraryItemTag {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long itemId;

    private String tagCode;

    private LocalDateTime createdAt;
}
