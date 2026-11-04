package com.synpharm.design.domain.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * library_molecule · 库内分子 / 抗体序列载荷。
 *
 * <p><b>分子与抗体同表</b>的依据：现有 {@code candidate_molecule} 已是这个形状
 * （{@code smiles} 与 {@code heavyChain}/{@code sequenceHash} 字段互补，用
 * {@code moleculeKind} 区分），且有 232 条真实数据在跑。拆成两张表会让
 * 「一个库里同时装小分子和抗体」这种真实场景表达不出来。
 *
 * <p>{@code dedupKey} 统一取 InChIKey（小分子）或 {@code sequenceHash}（抗体），
 * 一张表一条去重规则，避免多列唯一键的 NULL 语义陷阱（MySQL 唯一键允许多个 NULL，
 * 用「(dataset, inchikey) 或 (dataset, seqHash)」两列会让去重在某些组合下静默失效）。
 *
 * <p><b>理化性质字段一律服务端实算</b>，不接受前端传入 —— 前端能填就能造出
 * 「看起来对其实是另一个分子」的条目。
 *
 * @author SynPharm Team
 */
@Data
@TableName("library_molecule")
public class LibraryMolecule {

    public static final String KIND_SMALL_MOLECULE = "SMALL_MOLECULE";
    public static final String KIND_ANTIBODY = "ANTIBODY";
    public static final String KIND_PEPTIDE = "PEPTIDE";
    public static final String KIND_OLIGO = "OLIGO";

    /** 序列来源：公开序列 */
    public static final String SEQ_SOURCE_PUBLIC = "PUBLIC";
    /** 序列来源：示意值，不可用于生产 */
    public static final String SEQ_SOURCE_ILLUSTRATIVE = "ILLUSTRATIVE";

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long datasetId;

    /** SMALL_MOLECULE / ANTIBODY / PEPTIDE / OLIGO */
    private String moleculeKind;

    /** 小分子=InChIKey，抗体=sequenceHash */
    private String dedupKey;

    private String name;

    private String smiles;

    private String canonicalSmiles;

    private String inchikey;

    private String sequenceHash;

    private String heavyChain;

    private String lightChain;

    /** JSON 数组：额外链 */
    private String extraChains;

    private Integer sequenceLength;

    /** PUBLIC / ILLUSTRATIVE / IMGT / PDB_DERIVED */
    private String sequenceSource;

    private BigDecimal mw;

    private BigDecimal logp;

    private BigDecimal tpsa;

    private Integer hbd;

    private Integer hba;

    private Integer rotatable;

    private BigDecimal qed;

    private Integer painsHits;

    /** file_asset.id；私有文件按 owner_id 隔离 */
    private Long structureAssetId;

    private String note;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
