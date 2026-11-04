package com.synpharm.design.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.design.domain.model.LibraryItem;
import com.synpharm.design.domain.model.LibraryItemTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * library_item 数据访问接口。
 *
 * <p>跨类型检索与标签过滤走注解 SQL：条件构造器拼不出「命中全部标签」这种
 * {@code GROUP BY ... HAVING COUNT = n} 的形状，硬拼只会得到一条既难读又容易出错
 * 的动态 SQL。
 *
 * @author SynPharm Team
 */
@Mapper
public interface LibraryItemMapper extends BaseMapper<LibraryItem> {

    /**
     * 按标签过滤条目 id。
     *
     * @param tags       标签码
     * @param requireAll true=AND（全部命中），false=OR（任一命中）
     * @param tagCount   标签个数；AND 模式下作为 HAVING 的精确下界
     * @return 命中的 item id
     */
    @Select("""
            <script>
            SELECT it.item_id
            FROM library_item_tag it
            WHERE it.tag_code IN
            <foreach collection="tags" item="t" open="(" separator="," close=")">#{t}</foreach>
            GROUP BY it.item_id
            <if test="requireAll">
              HAVING COUNT(DISTINCT it.tag_code) = #{tagCount}
            </if>
            </script>
            """)
    List<Long> selectItemIdsByTags(@Param("tags") List<String> tags,
                                   @Param("requireAll") boolean requireAll,
                                   @Param("tagCount") int tagCount);

    /** 批量取条目标签，避免列表页 N+1 */
    @Select("""
            <script>
            SELECT it.id, it.item_id, it.tag_code
            FROM library_item_tag it
            WHERE it.item_id IN
            <foreach collection="itemIds" item="i" open="(" separator="," close=")">#{i}</foreach>
            </script>
            """)
    List<LibraryItemTag> selectTagsByItemIds(@Param("itemIds") List<Long> itemIds);

    /** 数据集内条目数（回填 / 校验用；常态读 library_dataset.item_count） */
    @Select("SELECT COUNT(*) FROM library_item WHERE dataset_id = #{datasetId} AND deleted = 0")
    long countByDataset(@Param("datasetId") Long datasetId);

    /**
     * 分面用的候选条目 id：只应用「数据集 + 类型 + 关键词」，**不应用标签**。
     *
     * <p>标签约束在内存里逐组扣减（见 {@code LibraryDatasetAppServiceImpl#facets}），
     * 因为「某一组的分面计数要排除该组自己已选的标签」这种语义在一条 GROUP BY 里
     * 表达不出来：HAVING 的下界随组变化。9 个标签组就要 9 条 SQL 才等价。
     */
    @Select("""
            <script>
            SELECT li.id
            FROM library_item li
            WHERE li.dataset_id = #{datasetId} AND li.deleted = 0
            <if test="itemType != null and itemType != ''">
              AND li.item_type = #{itemType}
            </if>
            <if test="keyword != null and keyword != ''">
              AND li.search_text LIKE CONCAT('%', #{keyword}, '%')
            </if>
            </script>
            """)
    List<Long> selectIdsForFacets(@Param("datasetId") Long datasetId,
                                  @Param("itemType") String itemType,
                                  @Param("keyword") String keyword);

    /**
     * 数据集内全部 {@code (item_id, tag_code)} 对。
     *
     * <p>一次 JOIN 取全量，让分面计数在内存里完成 —— 库规模到几万条之前，
     * 这比「每个组一条聚合 SQL」更省也更好读。上限写在调用方，另附说明。
     */
    @Select("""
            SELECT it.item_id AS itemId, it.tag_code AS tagCode
            FROM library_item_tag it
            JOIN library_item li ON li.id = it.item_id
            WHERE li.dataset_id = #{datasetId} AND li.deleted = 0
            """)
    List<ItemTagRow> selectItemTagPairsByDataset(@Param("datasetId") Long datasetId);

    /** (item_id, tag_code) 行 */
    class ItemTagRow {
        private Long itemId;
        private String tagCode;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getTagCode() {
            return tagCode;
        }

        public void setTagCode(String tagCode) {
            this.tagCode = tagCode;
        }
    }

    /**
     * 某个数据集内每个标签被多少条目使用。
     *
     * <p>筛选面板只显示 {@code count > 0} 的标签 —— 公共靶点库只有 3 条数据时，
     * 45 个内置标签里 37 个的计数是 0，全列出来只是一堵噪声墙，还会把条目列表
     * 挤出首屏。一次 {@code GROUP BY} 拿全量，避免 45 次 count 查询。
     */
    @Select("""
            SELECT it.tag_code AS tagCode, COUNT(*) AS cnt
            FROM library_item_tag it
            JOIN library_item li ON li.id = it.item_id
            WHERE li.dataset_id = #{datasetId} AND li.deleted = 0
            GROUP BY it.tag_code
            """)
    List<TagUsageRow> countTagUsageByDataset(@Param("datasetId") Long datasetId);

    /**
     * 物理删除投影行。
     *
     * <p>不走 {@code deleteById}：{@code deleted} 是 {@code @TableLogic} 列，逻辑删除
     * 会留下占位行，而唯一键 {@code uk_library_item_entity(item_type, entity_id)}
     * 不含 {@code deleted} —— 删完重建同一实体必然撞键（与 {@code project_target}
     * 踩过的坑完全一样）。
     */
    @Delete("DELETE FROM library_item WHERE item_type = #{itemType} AND entity_id = #{entityId}")
    int hardDeleteByEntity(@Param("itemType") String itemType, @Param("entityId") Long entityId);

    /** 标签使用计数行 */
    class TagUsageRow {
        private String tagCode;
        private Long cnt;

        public String getTagCode() {
            return tagCode;
        }

        public void setTagCode(String tagCode) {
            this.tagCode = tagCode;
        }

        public Long getCnt() {
            return cnt;
        }

        public void setCnt(Long cnt) {
            this.cnt = cnt;
        }
    }
}
