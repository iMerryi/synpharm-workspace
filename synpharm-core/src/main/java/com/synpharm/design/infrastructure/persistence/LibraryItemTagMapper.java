package com.synpharm.design.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.design.domain.model.LibraryItemTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * library_item_tag 数据访问接口。
 *
 * @author SynPharm Team
 */
@Mapper
public interface LibraryItemTagMapper extends BaseMapper<LibraryItemTag> {

    /**
     * 按条目 + 标签类型删除关联。
     *
     * <p>重新清洗时只清 {@code DERIVED} 标签 —— 用户标签与人工审定标签必须留住，
     * 否则一键重灌会把用户几个月的标注抹掉。类型过滤交给调用方拼 tag 子查询。
     */
    @Delete("DELETE it FROM library_item_tag it "
            + "JOIN library_tag t ON t.code = it.tag_code "
            + "WHERE it.item_id = #{itemId} AND t.tag_type = #{tagType}")
    int deleteByItemAndTagType(@Param("itemId") Long itemId, @Param("tagType") String tagType);
}
