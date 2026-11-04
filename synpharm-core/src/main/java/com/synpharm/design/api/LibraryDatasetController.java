package com.synpharm.design.api;

import com.synpharm.common.api.Result;
import com.synpharm.common.web.PageResult;
import com.synpharm.design.api.dto.LibraryDatasetItemResponse;
import com.synpharm.design.api.dto.LibraryDatasetResponse;
import com.synpharm.design.api.dto.LibraryFacetResponse;
import com.synpharm.design.api.dto.LibraryQueryRequest;
import com.synpharm.design.api.dto.LibraryTagResponse;
import com.synpharm.design.application.LibraryDatasetAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据资产库读端点（{@code /api/library/**}）。
 *
 * <p>与 {@code /api/design/library}（知识库字典：MOA / MODALITY / ASSAY）是两个不同的东西：
 * 那个是「可枚举的分类选项」，这个是「用户拥有的数据资产」。
 *
 * <p>路径刻意不放在 {@code /api/design} 下：库是跨产品线的基础数据，
 * 分子预测侧将来也要读它，挂在设计域下会误导后续的依赖方向。
 *
 * <p>写端点（建库 / 导入 / 编辑 / 授权 / 复制）在 {@code LibraryDatasetWriteController}。
 *
 * @author SynPharm Team
 */
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
@Validated
@Tag(name = "数据资产库", description = "公有/私有数据集的浏览、跨类型条目检索、标签字典")
public class LibraryDatasetController {

    private final LibraryDatasetAppService libraryDatasetAppService;

    @GetMapping("/datasets")
    @Operation(summary = "数据集列表", description = """
            按可见性过滤：公有数据集 + 我拥有的 + 被授权给我的。
            scope 收窄范围：ALL（默认）/ PUBLIC / PRIVATE / SHARED。
            非本人私有的数据集不会出现在结果里（也不会返回 403，避免暴露存在性）。""")
    public Result<List<LibraryDatasetResponse>> listDatasets(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String itemType) {
        return Result.success(libraryDatasetAppService.listDatasets(scope, itemType));
    }

    @GetMapping("/datasets/{id}")
    @Operation(summary = "数据集详情", description = "不可见返回 LIBRARY_DATASET_NOT_FOUND(40472)。")
    public Result<LibraryDatasetResponse> getDataset(@PathVariable Long id) {
        return Result.success(libraryDatasetAppService.getDataset(id));
    }

    @GetMapping("/items")
    @Operation(summary = "跨类型条目检索", description = """
            关键词（q）走 search_text —— 名称、中文名、基因名、UniProt、家族、描述一并命中，
            不是只匹配名称。标签（tags + tagMode）与结构化属性（organism / mw 区间）是
            两条独立通道：把数值范围做成标签会让标签字典爆炸。

            tagMode 默认 AND：默认 OR 的话「激酶 + 肿瘤」会返回全部激酶和全部肿瘤，
            用户会以为标签筛选没用。

            可能返回：LIBRARY_DATASET_NOT_FOUND(40472)、VALIDATION_FAILED(40000)。""")
    public Result<PageResult<LibraryDatasetItemResponse>> queryItems(
            @ModelAttribute LibraryQueryRequest request) {
        return Result.success(libraryDatasetAppService.queryItems(request));
    }

    @GetMapping("/items/{id}")
    @Operation(summary = "条目详情", description = "含完整 payload 与标签。不可见返回 LIBRARY_ENTRY_NOT_FOUND(40474)。")
    public Result<LibraryDatasetItemResponse> getItem(@PathVariable Long id) {
        return Result.success(libraryDatasetAppService.getItem(id));
    }

    @GetMapping("/tags")
    @Operation(summary = "标签字典", description = """
            返回内置标签 + 我自己的私有标签（别人的自定义标签不可见）。

            传 `datasetId` 时附带 `usageCount`（该数据集内被多少条目使用），
            前端据此只显示「有货」的标签 —— 公共靶点库只有 3 条数据时，
            45 个内置标签里 37 个计数为 0，全列出来会把条目列表挤出首屏。

            editable=false 的标签不可手改：QUALITY / READY 两组是基线预检
            「无结构 / 无序列」判定的同源数据，允许手改等于允许伪造「可以立项」。""")
    public Result<List<LibraryTagResponse>> listTags(
            @RequestParam(required = false) String itemType,
            @RequestParam(required = false) Long datasetId) {
        return Result.success(libraryDatasetAppService.listTags(itemType, datasetId));
    }

    @GetMapping("/facets")
    @Operation(summary = "分面筛选", description = """
            回答「在当前条件下，每个标签还能筛出多少条」—— 库大起来之后，
            「一次列全 + 一个关键词框」会退化成翻页地狱，用户看不出还能怎么收窄。

            与 `/tags` 的区别：那个按整个数据集计数（展示「这个库有什么」），
            这个按当前筛选结果计数，且每个标签的计数**扣掉它自己所在组的已选标签** ——
            选了「肿瘤」之后，家族分面应是「肿瘤 ∩ 该家族」的条数，
            否则用户点到某个家族才发现是 0 条。

            参数与 `/items` 同一套（datasetId 必填、q、tags、tagMode、itemType）。
            可能返回：LIBRARY_DATASET_NOT_FOUND(40472)。""")
    public Result<LibraryFacetResponse> facets(@ModelAttribute LibraryQueryRequest request) {
        return Result.success(libraryDatasetAppService.facets(request));
    }
}
