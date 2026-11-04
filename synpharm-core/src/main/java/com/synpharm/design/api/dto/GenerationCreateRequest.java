package com.synpharm.design.api.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.Data;

/**
 * 提交生成请求（06 §4.6）。
 *
 * @author SynPharm Team
 */
@Data
public class GenerationCreateRequest {

    /**
     * 要使用的基线快照 id。为空时取项目当前 ACTIVE 快照。
     *
     * <p>显式可传是必要的：重跑历史批次时用户可能想用旧基线复现，
     * 而不是被强制绑到最新版。
     */
    private Long baselineSnapshotId;

    /**
     * 幂等键。同键重复提交返回同一批次，不产生第二份候选集。
     *
     * <p>为空表示不做幂等（可以提交多次相同的生成）——
     * 这与「同键必须幂等」并不矛盾：幂等是<b>声明了同一意图</b>时才要求的语义。
     */
    @Size(max = 64, message = "idempotencyKey 最长 64 字符")
    private String idempotencyKey;

    /** 全局种子；为空则随机生成（生成后写入 run，可凭它复现） */
    private Long seed;

    /**
     * 显式提供的种子分子。为空时自动取项目内已有候选作为父本。
     *
     * <p>支持三种形态：纯 SMILES（小分子）、重/轻链（抗体）、extraChains（双抗）。
     *
     * <p><b>故意不加 {@code @NotEmpty}</b>：不传 / 空数组都是合法输入，语义是
     * 「用项目里已有的候选当父本」。真的两者皆空时由 S2 抛「搜索空间为空」，
     * 而不是在这里用参数校验拦掉——否则用户永远看不到那条可操作的提示。
     */
    private List<SeedInput> seeds;

    @Data
    public static class SeedInput {
        /** 调用方标识，用于日志追溯 */
        private String ref;
        private String smiles;
        private String heavyChain;
        private String lightChain;
        private List<String> extraChains;

        /**
         * 库条目 id（`library_item.id`）。
         *
         * <p>有它就不必再传 smiles / 重轻链：服务端按当前用户可见性解析出结构，
         * 并把库里的名字带进候选（`molecule_name`），这样「从库选」这条路
         * 对不懂 SMILES 的用户是零输入。
         *
         * <p>必须走服务端解析而不是前端把库里的 SMILES 读出来再回传：
         * 前端回传等于把「这个 id 属于你」的校验权交给了调用方。
         */
        private Long libraryItemId;

        /** 预留：单个种子的附加参数 */
        private Map<String, Object> params;
    }
}
