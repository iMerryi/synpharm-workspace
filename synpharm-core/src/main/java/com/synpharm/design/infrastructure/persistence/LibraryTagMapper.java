package com.synpharm.design.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.design.domain.model.LibraryTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * library_tag 数据访问接口。
 *
 * @author SynPharm Team
 */
@Mapper
public interface LibraryTagMapper extends BaseMapper<LibraryTag> {
}
