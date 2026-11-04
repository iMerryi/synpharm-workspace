package com.synpharm.design.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 资产库条目（契约 {@code LibraryDatasetItem}）。
 *
 * <p>类名带 {@code Dataset} 是为了与知识库字典的 {@code LibraryItemResponse}
 * （MOA / MODALITY / ASSAY 那套）区分 —— 两者都叫 "library item"，但一个是
 * 可枚举的字典项、一个是用户拥有的数据资产，混名会让后来的人分不清该用哪个。
 *
 * <p><b>三种条目类型共用这一个形状</b>，类型专属字段放 {@link #payload}：
 * 若拆成 Target/Molecule/Sequence 三个 Response，前端要么写三套类型分支，
 * 要么退化成 {@code any} —— 两种情况都比一个 Map 更糟。前端按 {@code itemType} 渲染。
 *
 * <p>{@code quality} / {@code readiness} 不是新数据，是
 * {@code library_item.quality_level} 与 QUALITY/READY 标签的投影，与基线预检
 * {@code NO_STRUCTURE} 判定同源。放 VO 里是为了让前端不必自己再算
 * 「有结构但没有全长序列」这类组合。
 *
 * @author SynPharm Team
 */
@Data
public class LibraryDatasetItemResponse {

    private Long id;

    private Long datasetId;

    /** 前端分区显示「来自：我的突变体库」 */
    private String datasetName;

    /** TARGET / MOLECULE / SEQUENCE */
    private String itemType;

    /** 指向 target.id 或 library_molecule.id；复制/绑定需要 */
    private Long entityId;

    /** 统一展示名：靶点=基因名，分子=通用名 */
    private String title;

    /** 副标题：靶点=中文名 · UniProt */
    private String subtitle;

    /** 已打标签，按 tagGroup 分组，前端直接渲染标签条 */
    private Map<String, List<TagChip>> tags = new LinkedHashMap<>();

    /** 数据完备度 */
    private Quality quality;

    /** 可用能力：可做 DTI / PPI / 口袋对接 / 生成 */
    private List<String> readiness = new ArrayList<>();

    /** 类型专属字段 */
    private Map<String, Object> payload = new LinkedHashMap<>();

    /** 复制血缘；公共条目或从零新建为 null */
    private DerivedFrom derivedFrom;

    /** 标签芯片 */
    @Data
    public static class TagChip {
        private String code;
        private String label;
        private String tagGroup;
        private String color;

        public TagChip() {
        }

        public TagChip(String code, String label, String tagGroup, String color) {
            this.code = code;
            this.label = label;
            this.tagGroup = tagGroup;
            this.color = color;
        }
    }

    /** 数据完备度快照 */
    @Data
    public static class Quality {
        /** EXPERIMENTAL_STRUCTURE / PREDICTED_ONLY / NO_STRUCTURE / UNKNOWN */
        private String level;
        private boolean hasSequence;
        private boolean hasStructure;
        private boolean hasPocket;
        /** 缺失项，直接展示：["无实验结构", "序列为片段"] */
        private List<String> missing = new ArrayList<>();
    }

    /** 复制来源 */
    @Data
    public static class DerivedFrom {
        private Long itemId;
        private Long datasetId;
        private String datasetName;
    }
}
