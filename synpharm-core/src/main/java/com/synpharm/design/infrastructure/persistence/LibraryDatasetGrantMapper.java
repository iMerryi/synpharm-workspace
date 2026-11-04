package com.synpharm.design.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.design.domain.model.LibraryDatasetGrant;
import org.apache.ibatis.annotations.Mapper;

/**
 * library_dataset_grant 数据访问接口。
 *
 * @author SynPharm Team
 */
@Mapper
public interface LibraryDatasetGrantMapper extends BaseMapper<LibraryDatasetGrant> {
}
