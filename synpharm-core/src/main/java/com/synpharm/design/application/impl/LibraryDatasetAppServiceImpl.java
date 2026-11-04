package com.synpharm.design.application.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.synpharm.common.error.ErrorCode;
import com.synpharm.common.exception.BizException;
import com.synpharm.common.security.CurrentUser;
import com.synpharm.common.web.PageResult;
import com.synpharm.design.api.dto.LibraryDatasetItemResponse;
import com.synpharm.design.api.dto.LibraryDatasetResponse;
import com.synpharm.design.api.dto.LibraryFacetResponse;
import com.synpharm.design.api.dto.LibraryQueryRequest;
import com.synpharm.design.api.dto.LibrarySeedPayload;
import com.synpharm.design.api.dto.LibraryTagResponse;
import com.synpharm.design.application.LibraryDatasetAppService;
import com.synpharm.design.domain.model.LibraryDataset;
import com.synpharm.design.domain.model.LibraryDatasetGrant;
import com.synpharm.design.domain.model.LibraryItem;
import com.synpharm.design.domain.model.LibraryItemTag;
import com.synpharm.design.domain.model.LibraryMolecule;
import com.synpharm.design.domain.model.LibraryTag;
import com.synpharm.design.domain.model.Target;
import com.synpharm.design.domain.model.TargetStructure;
import com.synpharm.design.infrastructure.persistence.LibraryDatasetGrantMapper;
import com.synpharm.design.infrastructure.persistence.LibraryDatasetMapper;
import com.synpharm.design.infrastructure.persistence.LibraryItemMapper;
import com.synpharm.design.infrastructure.persistence.LibraryMoleculeMapper;
import com.synpharm.design.infrastructure.persistence.LibraryTagMapper;
import com.synpharm.design.infrastructure.persistence.TargetMapper;
import com.synpharm.design.infrastructure.persistence.TargetStructureMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据资产库读侧实现。
 *
 * <p>三条刻意的设计：
 *
 * <ol>
 *   <li><b>不可见即 404</b>：非本人私有数据集返回 {@code LIBRARY_DATASET_NOT_FOUND} 而不是
 *       403。403 等于告诉调用方「这个 id 存在，只是不属于你」—— 私有库的名字本身就是信息。</li>
 *   <li><b>权限由后端算好给前端</b>（{@code canWrite} / {@code canManage}）。前端自己拼
 *       权限判断迟早会和后端不一致，表现是「按钮能点但请求失败」。</li>
 *   <li><b>payload 按类型分派而不是三个 VO</b>：三种条目共用 {@link LibraryDatasetItemResponse}，
 *       否则前端要么写三套类型分支、要么退化成 any。</li>
 * </ol>
 *
 * @author SynPharm Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryDatasetAppServiceImpl implements LibraryDatasetAppService {

    private final LibraryDatasetMapper datasetMapper;
    private final LibraryItemMapper itemMapper;
    private final LibraryTagMapper tagMapper;
    private final LibraryMoleculeMapper moleculeMapper;
    private final LibraryDatasetGrantMapper grantMapper;
    private final TargetMapper targetMapper;
    private final TargetStructureMapper targetStructureMapper;

    // ------------------------------------------------------------------ 数据集

    @Override
    public List<LibraryDatasetResponse> listDatasets(String scope, String itemType) {
        Long me = CurrentUser.requireUserId();
        List<LibraryDataset> all = datasetMapper.selectList(
                Wrappers.lambdaQuery(LibraryDataset.class)
                        .eq(LibraryDataset::getDeleted, 0)
                        .orderByAsc(LibraryDataset::getVisibility)
                        .orderByAsc(LibraryDataset::getId));

        Set<Long> sharedIds = grantedDatasetIds(me);

        List<LibraryDataset> visible = all.stream()
                .filter(d -> isVisible(d, me, sharedIds))
                .filter(d -> !StringUtils.hasText(itemType) || itemType.equalsIgnoreCase(d.getItemType()))
                .filter(d -> matchesScope(d, me, sharedIds, scope))
                .collect(Collectors.toList());

        Map<Long, String> ownerNames = ownerNamesOf(visible);
        return visible.stream().map(d -> toDatasetResponse(d, me, sharedIds, ownerNames)).collect(Collectors.toList());
    }

    @Override
    public LibraryDatasetResponse getDataset(Long datasetId) {
        Long me = CurrentUser.requireUserId();
        LibraryDataset dataset = requireVisibleDataset(datasetId, me);
        Set<Long> sharedIds = grantedDatasetIds(me);
        return toDatasetResponse(dataset, me, sharedIds, ownerNamesOf(List.of(dataset)));
    }

    // ------------------------------------------------------------------ 标签

    @Override
    public List<LibraryTagResponse> listTags(String itemType, Long datasetId) {
        Long me = CurrentUser.requireUserId();
        List<LibraryTag> tags = tagMapper.selectList(
                Wrappers.lambdaQuery(LibraryTag.class)
                        .eq(LibraryTag::getDeleted, 0)
                        .orderByAsc(LibraryTag::getTagGroup)
                        .orderByAsc(LibraryTag::getSortNo));

        // 传了 datasetId 就带上使用计数：前端据此只显示「有货」的标签，
        // 否则 45 个标签里绝大多数计数为 0，列表会变成一堵噪声墙
        Map<String, Long> usage = Map.of();
        if (datasetId != null) {
            requireVisibleDataset(datasetId, me);
            usage = itemMapper.countTagUsageByDataset(datasetId).stream()
                    .filter(r -> r.getTagCode() != null)
                    .collect(Collectors.toMap(
                            com.synpharm.design.infrastructure.persistence.LibraryItemMapper.TagUsageRow::getTagCode,
                            r -> r.getCnt() == null ? 0L : r.getCnt(),
                            (a, b) -> a));
        }

        final Map<String, Long> usageMap = usage;
        // 私有标签只对属主可见 —— 别人的自定义标签不该出现在我的筛选面板里
        return tags.stream()
                .filter(t -> t.getOwnerId() == null || t.getOwnerId().equals(me))
                .filter(t -> !StringUtils.hasText(itemType) || supports(t, itemType))
                .map(t -> {
                    LibraryTagResponse r = new LibraryTagResponse();
                    r.setCode(t.getCode());
                    r.setLabel(t.getLabel());
                    r.setTagGroup(t.getTagGroup());
                    r.setTagType(t.getTagType());
                    r.setColor(t.getColor());
                    r.setSortNo(t.getSortNo());
                    r.setUsageCount(usageMap.getOrDefault(t.getCode(), 0L));
                    // DERIVED / CURATED 一律只读：QUALITY、READY 是基线预检判据，
                    // 允许用户手改等于允许伪造「有实验结构」
                    r.setEditable(LibraryTag.TYPE_USER.equals(t.getTagType()) && me.equals(t.getOwnerId()));
                    return r;
                })
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ 条目

    @Override
    public PageResult<LibraryDatasetItemResponse> queryItems(LibraryQueryRequest request) {
        Long me = CurrentUser.requireUserId();
        long page = Math.max(1, request.getPage());
        long pageSize = Math.max(1, Math.min(200, request.getPageSize()));

        // 1) 可见数据集
        Set<Long> visibleDatasetIds = visibleDatasets(me, request.getScope());
        if (request.getDatasetId() != null) {
            if (!visibleDatasetIds.contains(request.getDatasetId())) {
                throw new BizException(ErrorCode.LIBRARY_DATASET_NOT_FOUND,
                        null, Map.of("datasetId", request.getDatasetId()));
            }
            visibleDatasetIds = new LinkedHashSet<>(List.of(request.getDatasetId()));
        }
        if (visibleDatasetIds.isEmpty()) {
            return PageResult.empty(page, pageSize);
        }

        // 2) 标签过滤先在关联表上收敛（避免整表扫完再过滤）
        Set<Long> tagFiltered = null;
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            List<Long> ids = itemMapper.selectItemIdsByTags(
                    request.getTags(), request.requireAllTags(), request.getTags().size());
            tagFiltered = new LinkedHashSet<>(ids);
            if (tagFiltered.isEmpty()) {
                return PageResult.empty(page, pageSize);
            }
        }

        // 3) 主查询
        var query = Wrappers.lambdaQuery(LibraryItem.class)
                .eq(LibraryItem::getDeleted, 0)
                .in(LibraryItem::getDatasetId, visibleDatasetIds);
        if (StringUtils.hasText(request.getItemType())) {
            query.eq(LibraryItem::getItemType, request.getItemType().trim().toUpperCase());
        }
        if (StringUtils.hasText(request.getQualityLevel())) {
            query.eq(LibraryItem::getQualityLevel, request.getQualityLevel().trim().toUpperCase());
        }
        if (StringUtils.hasText(request.getQ())) {
            // search_text 已包含名称 / 中文名 / 基因名 / UniProt / 家族 / 描述，
            // 用 title 单独匹配会漏掉「搜基因名查不到」这类情况
            String kw = request.getQ().trim();
            query.and(w -> w.like(LibraryItem::getTitle, kw)
                    .or().like(LibraryItem::getSubtitle, kw)
                    .or().like(LibraryItem::getSearchText, kw));
        }
        if (tagFiltered != null) {
            query.in(LibraryItem::getId, tagFiltered);
        }

        query.orderByAsc(LibraryItem::getId);

        IPage<LibraryItem> pageResult = itemMapper.selectPage(new Page<>(page, pageSize), query);

        // 4) 属性过滤（物种 / 分子量区间）在载荷侧完成：这些字段不在投影表上，
        //    放进去会让投影表变成"什么都存"的宽表，而投影表本该只装检索用的最小集
        List<LibraryItem> records = pageResult.getRecords();
        List<LibraryDatasetItemResponse> rows = assemble(records, me);
        rows = applyPayloadFilters(rows, request);

        return new PageResult<>(rows, pageResult.getTotal(), pageResult.getCurrent(),
                pageResult.getSize(), pageResult.getCurrent() < pageResult.getPages());
    }

    @Override
    public LibraryDatasetItemResponse getItem(Long itemId) {
        Long me = CurrentUser.requireUserId();
        LibraryItem item = itemMapper.selectById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(ErrorCode.LIBRARY_ENTRY_NOT_FOUND, null, Map.of("itemId", itemId));
        }
        // 可见性由所属数据集决定，不是条目自己
        requireVisibleDataset(item.getDatasetId(), me);
        List<LibraryDatasetItemResponse> one = assemble(List.of(item), me);
        return one.isEmpty() ? null : one.get(0);
    }

    // ------------------------------------------------------------------ 组装

    private List<LibraryDatasetItemResponse> assemble(List<LibraryItem> items, Long me) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = items.stream().map(LibraryItem::getId).toList();
        Set<Long> datasetIds = items.stream().map(LibraryItem::getDatasetId).collect(Collectors.toSet());

        // 批量取附属数据，避免 N+1（列表 20 条时 N+1 会变成 60+ 次查询）
        Map<Long, LibraryDataset> datasets = datasetMapper.selectBatchIds(datasetIds).stream()
                .collect(Collectors.toMap(LibraryDataset::getId, Function.identity(), (a, b) -> a));
        Map<String, LibraryTag> tags = tagMapper.selectList(
                        Wrappers.lambdaQuery(LibraryTag.class).eq(LibraryTag::getDeleted, 0))
                .stream().collect(Collectors.toMap(LibraryTag::getCode, Function.identity(), (a, b) -> a));
        Map<Long, List<LibraryItemTag>> tagsByItem =
                itemMapper.selectTagsByItemIds(itemIds).stream()
                        .collect(Collectors.groupingBy(LibraryItemTag::getItemId));

        // 类型专属载荷
        List<Long> targetIds = items.stream()
                .filter(i -> LibraryItem.TYPE_TARGET.equals(i.getItemType()))
                .map(LibraryItem::getEntityId).toList();
        Map<Long, Target> targetMap = targetIds.isEmpty() ? Map.of()
                : targetMapper.selectBatchIds(targetIds).stream()
                        .collect(Collectors.toMap(Target::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<TargetStructure>> structuresByTarget = targetIds.isEmpty() ? Map.of()
                : targetStructureMapper.selectList(Wrappers.lambdaQuery(TargetStructure.class)
                                .in(TargetStructure::getTargetId, targetIds)
                                .eq(TargetStructure::getDeleted, 0))
                        .stream().collect(Collectors.groupingBy(TargetStructure::getTargetId));

        List<Long> moleculeIds = items.stream()
                .filter(i -> !LibraryItem.TYPE_TARGET.equals(i.getItemType()))
                .map(LibraryItem::getEntityId).toList();
        Map<Long, LibraryMolecule> moleculeMap = moleculeIds.isEmpty() ? Map.of()
                : moleculeMapper.selectBatchIds(moleculeIds).stream()
                        .collect(Collectors.toMap(LibraryMolecule::getId, Function.identity(), (a, b) -> a));

        List<LibraryDatasetItemResponse> out = new ArrayList<>(items.size());
        for (LibraryItem item : items) {
            LibraryDatasetItemResponse vo = new LibraryDatasetItemResponse();
            vo.setId(item.getId());
            vo.setDatasetId(item.getDatasetId());
            LibraryDataset ds = datasets.get(item.getDatasetId());
            vo.setDatasetName(ds == null ? null : ds.getName());
            vo.setItemType(item.getItemType());
            vo.setEntityId(item.getEntityId());
            vo.setTitle(item.getTitle());
            vo.setSubtitle(item.getSubtitle());

            // 标签按组归集，前端直接按组渲染
            Map<String, List<LibraryDatasetItemResponse.TagChip>> grouped = new LinkedHashMap<>();
            List<String> readiness = new ArrayList<>();
            for (LibraryItemTag it : tagsByItem.getOrDefault(item.getId(), List.of())) {
                LibraryTag t = tags.get(it.getTagCode());
                if (t == null) {
                    continue;
                }
                grouped.computeIfAbsent(t.getTagGroup(), k -> new ArrayList<>())
                        .add(new LibraryDatasetItemResponse.TagChip(
                                t.getCode(), t.getLabel(), t.getTagGroup(), t.getColor()));
                if ("READY".equals(t.getTagGroup())) {
                    readiness.add(t.getLabel());
                }
            }
            vo.setTags(grouped);
            vo.setReadiness(readiness);

            if (LibraryItem.TYPE_TARGET.equals(item.getItemType())) {
                Target target = targetMap.get(item.getEntityId());
                List<TargetStructure> structures =
                        structuresByTarget.getOrDefault(item.getEntityId(), List.of());
                vo.setQuality(qualityOf(item, target, structures));
                vo.setPayload(targetPayload(target, structures));
            } else {
                LibraryMolecule molecule = moleculeMap.get(item.getEntityId());
                vo.setQuality(simpleQuality(item));
                vo.setPayload(moleculePayload(molecule));
            }

            if (item.getDerivedFromId() != null) {
                LibraryDatasetItemResponse.DerivedFrom df = new LibraryDatasetItemResponse.DerivedFrom();
                df.setItemId(item.getDerivedFromId());
                df.setDatasetId(item.getDerivedFromDatasetId());
                LibraryDataset origin = item.getDerivedFromDatasetId() == null ? null
                        : datasetMapper.selectById(item.getDerivedFromDatasetId());
                df.setDatasetName(origin == null ? null : origin.getName());
                vo.setDerivedFrom(df);
            }
            out.add(vo);
        }
        return out;
    }

    private LibraryDatasetItemResponse.Quality qualityOf(LibraryItem item, Target target,
                                                         List<TargetStructure> structures) {
        LibraryDatasetItemResponse.Quality q = new LibraryDatasetItemResponse.Quality();
        q.setLevel(item.getQualityLevel());

        boolean hasSeq = target != null && StringUtils.hasText(target.getSequence());
        boolean hasExpStructure = structures.stream()
                .anyMatch(s -> "PDB".equals(s.getSource()) || "USER_UPLOAD".equals(s.getSource()));
        boolean hasAnyStructure = !structures.isEmpty();

        q.setHasSequence(hasSeq);
        q.setHasStructure(hasExpStructure);
        q.setHasPocket(structures.stream().anyMatch(s -> s.getPocketCount() != null && s.getPocketCount() > 0));

        if (!hasExpStructure) {
            q.getMissing().add(hasAnyStructure ? "只有预测结构" : "无实验结构");
        }
        if (!hasSeq) {
            q.getMissing().add("无序列");
        } else if (target.getSequenceLength() != null && target.getSequenceLength() < 100) {
            q.getMissing().add("序列为片段");
        }
        return q;
    }

    private LibraryDatasetItemResponse.Quality simpleQuality(LibraryItem item) {
        LibraryDatasetItemResponse.Quality q = new LibraryDatasetItemResponse.Quality();
        q.setLevel(item.getQualityLevel());
        q.setHasSequence(true);
        q.setHasStructure(false);
        return q;
    }

    private Map<String, Object> targetPayload(Target t, List<TargetStructure> structures) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (t == null) {
            return p;
        }
        p.put("uniprotId", t.getUniprotId());
        p.put("geneName", t.getGeneName());
        p.put("name", t.getName());
        p.put("chineseName", t.getChineseName());
        p.put("organism", t.getOrganism());
        p.put("targetType", t.getTargetType());
        p.put("family", t.getFamily());
        p.put("sequenceLength", t.getSequenceLength());
        p.put("variantCode", t.getVariantCode());
        p.put("structureCount", structures.size());
        p.put("experimentalStructureCount",
                structures.stream().filter(s -> "PDB".equals(s.getSource())).count());
        p.put("pdbIds", structures.stream().map(TargetStructure::getPdbId)
                .filter(StringUtils::hasText).distinct().limit(5).toList());
        return p;
    }

    private Map<String, Object> moleculePayload(LibraryMolecule m) {
        Map<String, Object> p = new LinkedHashMap<>();
        if (m == null) {
            return p;
        }
        p.put("moleculeKind", m.getMoleculeKind());
        p.put("smiles", m.getSmiles());
        p.put("inchikey", m.getInchikey());
        p.put("mw", m.getMw());
        p.put("logp", m.getLogp());
        p.put("tpsa", m.getTpsa());
        p.put("hbd", m.getHbd());
        p.put("hba", m.getHba());
        p.put("rotatable", m.getRotatable());
        p.put("qed", m.getQed());
        p.put("painsHits", m.getPainsHits());
        p.put("sequenceLength", m.getSequenceLength());
        p.put("sequenceSource", m.getSequenceSource());
        return p;
    }

    /**
     * 载荷侧过滤（物种 / 分子量 / LogP 区间）。
     *
     * <p>为什么不在 SQL 里做：这些字段不在 {@code library_item} 投影表上。把它们加进投影
     * 会让这张表变成「什么都存」的宽表 —— 而投影表的职责只是承载检索用的最小集
     * （标题、搜索文本、质量等级）。代价是分页后过滤会使当前页条数少于 pageSize，
     * 这在带数值区间的检索里是可接受的（用户改小 pageSize 即可判断）。
     */
    private List<LibraryDatasetItemResponse> applyPayloadFilters(List<LibraryDatasetItemResponse> rows,
                                                                 LibraryQueryRequest req) {
        boolean needOrganism = StringUtils.hasText(req.getOrganism());
        boolean needRange = req.getMwMin() != null || req.getMwMax() != null
                || req.getLogpMin() != null || req.getLogpMax() != null;
        if (!needOrganism && !needRange) {
            return rows;
        }
        return rows.stream().filter(r -> {
            Map<String, Object> p = r.getPayload();
            if (needOrganism) {
                Object o = p.get("organism");
                if (o == null || !req.getOrganism().equalsIgnoreCase(String.valueOf(o))) {
                    return false;
                }
            }
            if (!inRange(p.get("mw"), req.getMwMin(), req.getMwMax())) {
                return false;
            }
            return inRange(p.get("logp"), req.getLogpMin(), req.getLogpMax());
        }).collect(Collectors.toList());
    }

    private boolean inRange(Object value, java.math.BigDecimal min, java.math.BigDecimal max) {
        if (min == null && max == null) {
            return true;
        }
        if (value == null) {
            return false;
        }
        java.math.BigDecimal v;
        try {
            v = new java.math.BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return false;
        }
        if (min != null && v.compareTo(min) < 0) {
            return false;
        }
        return max == null || v.compareTo(max) <= 0;
    }

    // ------------------------------------------------------------------ 权限

    /** 当前用户可见的数据集 id；scope 收敛后为空表示「没有可看的」 */
    private Set<Long> visibleDatasets(Long me, String scope) {
        List<LibraryDataset> all = datasetMapper.selectList(
                Wrappers.lambdaQuery(LibraryDataset.class).eq(LibraryDataset::getDeleted, 0));
        Set<Long> shared = grantedDatasetIds(me);
        return all.stream()
                .filter(d -> isVisible(d, me, shared))
                .filter(d -> matchesScope(d, me, shared, scope))
                .map(LibraryDataset::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isVisible(LibraryDataset d, Long me, Set<Long> sharedIds) {
        if (LibraryDataset.VISIBILITY_PUBLIC.equals(d.getVisibility())) {
            return true;
        }
        return me.equals(d.getOwnerId()) || sharedIds.contains(d.getId());
    }

    private boolean matchesScope(LibraryDataset d, Long me, Set<Long> sharedIds, String scope) {
        if (!StringUtils.hasText(scope) || "ALL".equalsIgnoreCase(scope)) {
            return true;
        }
        return switch (scope.toUpperCase()) {
            case "PUBLIC" -> LibraryDataset.VISIBILITY_PUBLIC.equals(d.getVisibility());
            case "PRIVATE" -> me.equals(d.getOwnerId());
            case "SHARED" -> !me.equals(d.getOwnerId()) && sharedIds.contains(d.getId());
            default -> true;
        };
    }

    /** 被授权（非属主）的数据集 id */
    private Set<Long> grantedDatasetIds(Long me) {
        return grantMapper.selectList(
                        Wrappers.lambdaQuery(LibraryDatasetGrant.class)
                                .eq(LibraryDatasetGrant::getGranteeUserId, me))
                .stream()
                .map(LibraryDatasetGrant::getDatasetId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ------------------------------------------------------------------ 分面

    @Override
    public LibraryFacetResponse facets(LibraryQueryRequest request) {
        Long me = CurrentUser.requireUserId();
        if (request.getDatasetId() == null) {
            throw new BizException(ErrorCode.LIBRARY_DATASET_NOT_FOUND, "分面筛选必须指定数据集",
                    Map.of("datasetId", "null"));
        }
        requireVisibleDataset(request.getDatasetId(), me);

        List<Long> baseIds = itemMapper.selectIdsForFacets(
                request.getDatasetId(), request.getItemType(), trimToNull(request.getQ()));
        Set<Long> base = new LinkedHashSet<>(baseIds);

        // 一次取全量 (item_id, tag_code)，在内存里逐组扣减。
        // 为什么不按组发 9 条聚合 SQL：每组的约束是「已选标签减去本组」，
        // HAVING 的下界随组变化，一条 GROUP BY 表达不出来。
        Map<Long, Set<String>> tagsByItem = new LinkedHashMap<>();
        for (LibraryItemMapper.ItemTagRow row : itemMapper.selectItemTagPairsByDataset(request.getDatasetId())) {
            if (!base.contains(row.getItemId())) {
                continue;
            }
            tagsByItem.computeIfAbsent(row.getItemId(), k -> new LinkedHashSet<>()).add(row.getTagCode());
        }

        List<String> selected = request.getTags() == null ? List.of()
                : request.getTags().stream().filter(StringUtils::hasText).toList();
        boolean requireAll = !"OR".equalsIgnoreCase(request.getTagMode());

        long total = countMatching(tagsByItem.values(), selected, requireAll);

        List<LibraryTag> allTags = tagMapper.selectList(
                Wrappers.lambdaQuery(LibraryTag.class)
                        .eq(LibraryTag::getDeleted, 0)
                        .orderByAsc(LibraryTag::getTagGroup)
                        .orderByAsc(LibraryTag::getSortNo));

        Map<String, String> groupOf = allTags.stream()
                .collect(Collectors.toMap(LibraryTag::getCode, LibraryTag::getTagGroup, (a, b) -> a));

        List<LibraryTagResponse> tags = new ArrayList<>();
        for (LibraryTag tag : allTags) {
            if (tag.getOwnerId() != null && !tag.getOwnerId().equals(me)) {
                continue;
            }
            if (StringUtils.hasText(request.getItemType()) && !supports(tag, request.getItemType())) {
                continue;
            }
            // 扣掉本组已选标签：选了「肿瘤」之后，家族分面应该是「肿瘤 ∩ 该家族」的条数
            List<String> constraints = selected.stream()
                    .filter(c -> !tag.getTagGroup().equals(groupOf.get(c)))
                    .toList();
            long count = countMatchingWith(tagsByItem.values(), constraints, requireAll, tag.getCode());

            LibraryTagResponse r = new LibraryTagResponse();
            r.setCode(tag.getCode());
            r.setLabel(tag.getLabel());
            r.setTagGroup(tag.getTagGroup());
            r.setTagType(tag.getTagType());
            r.setColor(tag.getColor());
            r.setSortNo(tag.getSortNo());
            r.setUsageCount(count);
            r.setEditable(LibraryTag.TYPE_USER.equals(tag.getTagType()) && me.equals(tag.getOwnerId()));
            tags.add(r);
        }

        LibraryFacetResponse response = new LibraryFacetResponse();
        response.setTotal(total);
        response.setTags(tags);
        return response;
    }

    private long countMatching(Collection<Set<String>> items, List<String> required, boolean requireAll) {
        if (required.isEmpty()) {
            return items.size();
        }
        return items.stream().filter(have -> matches(have, required, requireAll)).count();
    }

    /** 同时带上某个待评估标签：分面计数 = 「其余条件成立 且 有该标签」 */
    private long countMatchingWith(Collection<Set<String>> items, List<String> constraints,
                                   boolean requireAll, String candidateTag) {
        return items.stream()
                .filter(have -> have.contains(candidateTag) && matches(have, constraints, requireAll))
                .count();
    }

    private boolean matches(Set<String> have, List<String> required, boolean requireAll) {
        if (required.isEmpty()) {
            return true;
        }
        return requireAll ? have.containsAll(required) : required.stream().anyMatch(have::contains);
    }

    private String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    // ------------------------------------------------------------------ 种子解析

    @Override
    public LibrarySeedPayload resolveSeed(Long itemId) {
        Long me = CurrentUser.requireUserId();
        LibraryItem item = itemMapper.selectById(itemId);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw new BizException(ErrorCode.LIBRARY_ENTRY_NOT_FOUND, null, Map.of("itemId", itemId));
        }
        // 先过数据集可见性：条目的归属决定可见性，不是条目自己
        requireVisibleDataset(item.getDatasetId(), me);

        if (LibraryItem.TYPE_TARGET.equals(item.getItemType())) {
            throw new BizException(ErrorCode.LIBRARY_ENTRY_NOT_SEEDABLE,
                    "靶点条目不能当候选分子种子：请选择分子库或抗体序列库里的条目", Map.of("itemId", itemId));
        }

        LibraryMolecule molecule = moleculeMapper.selectById(item.getEntityId());
        if (molecule == null || Integer.valueOf(1).equals(molecule.getDeleted())) {
            throw new BizException(ErrorCode.LIBRARY_ENTRY_NOT_FOUND, null, Map.of("itemId", itemId));
        }

        LibrarySeedPayload payload = new LibrarySeedPayload();
        payload.setItemId(item.getId());
        payload.setDatasetId(item.getDatasetId());
        payload.setItemType(item.getItemType());
        payload.setName(StringUtils.hasText(molecule.getName()) ? molecule.getName() : item.getTitle());
        payload.setSubtitle(item.getSubtitle());
        if (StringUtils.hasText(molecule.getSmiles())) {
            // 用规范 SMILES：库里存的是原始式，「同一分子不同写法」在 S5 去重时才会暴露
            payload.setSmiles(StringUtils.hasText(molecule.getCanonicalSmiles())
                    ? molecule.getCanonicalSmiles() : molecule.getSmiles());
        } else {
            payload.setHeavyChain(molecule.getHeavyChain());
            payload.setLightChain(molecule.getLightChain());
        }
        return payload;
    }

    private LibraryDataset requireVisibleDataset(Long datasetId, Long me) {
        LibraryDataset d = datasetMapper.selectById(datasetId);
        if (d == null || Integer.valueOf(1).equals(d.getDeleted())) {
            throw new BizException(ErrorCode.LIBRARY_DATASET_NOT_FOUND, null, Map.of("datasetId", datasetId));
        }
        if (!isVisible(d, me, grantedDatasetIds(me))) {
            // 不可见按「不存在」处理，不返回 403
            throw new BizException(ErrorCode.LIBRARY_DATASET_NOT_FOUND, null, Map.of("datasetId", datasetId));
        }
        return d;
    }

    private Map<Long, String> ownerNamesOf(List<LibraryDataset> datasets) {
        // 属主昵称在 synpharm_auth.sys_user 里，core 不直连其它服务库（拆分铁律 R2）。
        // 本期不显示昵称，前端用「共享给我的库」分区 + ownerId 兜底；
        // 接 auth 的批量用户信息接口后再补，不在本层硬造。
        return Collections.emptyMap();
    }

    private LibraryDatasetResponse toDatasetResponse(LibraryDataset d, Long me, Set<Long> sharedIds,
                                                     Map<Long, String> ownerNames) {
        LibraryDatasetResponse r = new LibraryDatasetResponse();
        r.setId(d.getId());
        r.setCode(d.getCode());
        r.setName(d.getName());
        r.setDescription(d.getDescription());
        r.setItemType(d.getItemType());
        r.setVisibility(d.getVisibility());
        r.setOwnerId(d.getOwnerId());
        r.setOwnerName(d.getOwnerId() == null ? null : ownerNames.get(d.getOwnerId()));
        r.setSource(d.getSource());
        r.setItemCount(d.getItemCount());
        r.setTags(d.getTags());
        r.setStatus(d.getStatus());
        r.setCreatedAt(d.getCreatedAt());
        r.setUpdatedAt(d.getUpdatedAt());

        boolean isOwner = me.equals(d.getOwnerId());
        boolean isPublic = LibraryDataset.VISIBILITY_PUBLIC.equals(d.getVisibility());
        r.setIsOwner(isOwner);
        // 公共库由平台维护，界面不提供写入
        r.setCanWrite(!isPublic && isOwner);
        // 被授权者不能删库、不能再授权 —— 防权限扩散
        r.setCanManage(isOwner);
        return r;
    }

    private boolean supports(LibraryTag tag, String itemType) {
        if (!StringUtils.hasText(tag.getItemTypes())) {
            return false;
        }
        for (String t : tag.getItemTypes().split(",")) {
            if (t.trim().equalsIgnoreCase(itemType)) {
                return true;
            }
        }
        return false;
    }
}
