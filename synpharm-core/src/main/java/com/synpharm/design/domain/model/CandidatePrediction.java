package com.synpharm.design.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * candidate_prediction · 候选的外部预测结论。
 *
 * <p>预测由 {@code synpharm-predict} 计算，本表只记录「结论属于哪个候选」。
 * 保留 {@code predictResultId} 是为了能回到预测域取原始明细，而不是把预测域的数据
 * 复制一份过来 —— 复制过来的那天起就有两个真相。
 *
 * @author SynPharm Team
 */
@Data
@TableName("candidate_prediction")
public class CandidatePrediction {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long candidateId;

    private Long userId;

    /** DTI/PPI/DDI */
    private String algoType;

    private String targetId;

    private String targetName;

    private String ligandSmiles;

    /** kcal/mol，越小越紧 */
    private BigDecimal bindingAffinity;

    /** 0~1 */
    private BigDecimal confidenceScore;

    /** HIGH/MEDIUM/LOW */
    private String confidenceLevel;

    /** JSON 数组文本：相互作用残基明细 */
    private String interactions;

    private String predictResultId;

    /** JSON 文本：预测所用数据集/模型信息 */
    private String datasetInfo;

    private String note;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
