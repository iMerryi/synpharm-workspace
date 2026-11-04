package com.synpharm.design.application;

import com.synpharm.common.web.PageResult;
import com.synpharm.design.api.dto.LibraryDatasetItemResponse;
import com.synpharm.design.api.dto.LibraryDatasetResponse;
import com.synpharm.design.api.dto.LibraryFacetResponse;
import com.synpharm.design.api.dto.LibraryQueryRequest;
import com.synpharm.design.api.dto.LibrarySeedPayload;
import com.synpharm.design.api.dto.LibraryTagResponse;

import java.util.List;

/**
 * 数据资产库用例（`/api/library/**`）。
 *
 * <p>与 {@link LibraryAppService}（知识库字典：MOA / MODALITY / ASSAY）是两个不同的东西：
 * 那个管的是「可枚举的分类选项」，这个管的是「用户拥有的数据资产（靶点/分子/序列）」。
 *
 * @author SynPharm Team
 */
public interface LibraryDatasetAppService {

    /**
     * 数据集列表。
     *
     * @param scope ALL / PUBLIC / PRIVATE / SHARED
     * @param itemType 为空则不限
     */
    List<LibraryDatasetResponse> listDatasets(String scope, String itemType);

    /** 数据集详情；不可见返回 404（不是 403，不暴露存在性） */
    LibraryDatasetResponse getDataset(Long datasetId);

    /** 跨类型条目检索：关键词 + 标签 + 属性 + 分页 */
    PageResult<LibraryDatasetItemResponse> queryItems(LibraryQueryRequest request);

    /**
     * 标签字典（含用户自建）。
     *
     * @param itemType  为空则不限
     * @param datasetId 传了就带上「该数据集内被多少条目使用」的计数，
     *                  前端据此只显示有货的标签
     */
    List<LibraryTagResponse> listTags(String itemType, Long datasetId);

    /** 单条详情（含完整 payload） */
    LibraryDatasetItemResponse getItem(Long itemId);

    /**
     * 把库条目解析成生成种子。
     *
     * <p>存在意义：不让前端把库里的 SMILES 读出来再回传。回传等于把
     * 「这个条目属于你」的校验权交给调用方，私有库的分子就能被借 id 塞进别人的批次。
     * 这里统一按数据集可见性判定，不可见一律 404。
     *
     * <p>只接受 {@code MOLECULE} / {@code SEQUENCE} 两类条目：靶点不是候选分子，
     * 拿它当种子在 S4 会产出空结构。
     */
    LibrarySeedPayload resolveSeed(Long itemId);

    /**
     * 分面筛选：在当前条件下，每个标签还能筛出多少条。
     *
     * <p>用于「数据一多就不能一次列全」的场景（生成阶段挑种子）。
     * 与 {@link #listTags} 的区别：那个按整个数据集计数（用于展示「这个库有什么」），
     * 这个按<b>当前筛选结果</b>计数并扣掉本组已选标签（用于回答「下一步还能怎么收窄」）。
     */
    LibraryFacetResponse facets(LibraryQueryRequest request);
}
