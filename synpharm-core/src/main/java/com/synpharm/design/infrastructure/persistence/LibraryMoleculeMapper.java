package com.synpharm.design.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.design.domain.model.LibraryMolecule;
import org.apache.ibatis.annotations.Mapper;

/**
 * library_molecule 数据访问接口。
 *
 * @author SynPharm Team
 */
@Mapper
public interface LibraryMoleculeMapper extends BaseMapper<LibraryMolecule> {
}
