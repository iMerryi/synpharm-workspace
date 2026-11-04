package com.synpharm.design.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 候选的外部预测结论。
 *
 * @author SynPharm Team
 */
@Data
public class CandidatePredictionResponse {

    private Long id;
    private Long projectId;
    private Long candidateId;
    private String algoType;
    private String targetId;
    private String targetName;
    private String ligandSmiles;
    private BigDecimal bindingAffinity;
    private BigDecimal confidenceScore;
    private String confidenceLevel;
    private Object interactions;
    private String predictResultId;
    private Object datasetInfo;
    private String note;
    private LocalDateTime createdAt;
}
