package com.synpharm.design.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.synpharm.design.domain.model.CandidatePrediction;
import org.apache.ibatis.annotations.Mapper;

/**
 * candidate_prediction 数据访问接口。
 *
 * @author SynPharm Team
 */
@Mapper
public interface CandidatePredictionMapper extends BaseMapper<CandidatePrediction> {
}
