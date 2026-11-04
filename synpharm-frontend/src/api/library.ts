import { request } from '@/utils/request'

/**
 * 数据资产库 API（`/api/library/**`）。
 *
 * 与 `requirement.ts` 里的知识库接口（MOA / MODALITY 字典）不是一个东西：
 * 那个是「可枚举的分类选项」，这个是「用户拥有的数据资产」。
 */

/** 数据集（界面上的一行「公共靶点库」「我的突变体库」） */
export interface LibraryDataset {
  id: number
  code: string
  name: string
  description?: string | null
  itemType: 'TARGET' | 'MOLECULE' | 'SEQUENCE' | string
  visibility: 'PUBLIC' | 'PRIVATE' | string
  ownerId?: number | null
  ownerName?: string | null
  source?: string | null
  itemCount: number
  tags?: string | null
  status: 'ACTIVE' | 'ARCHIVED' | string
  /** 当前用户是否属主 */
  isOwner: boolean
  /** 能否增改条目（属主且私有）——由后端算，前端不要自己判断 */
  canWrite: boolean
  /** 能否删库/授权（仅属主；被授权者一律不能） */
  canManage: boolean
}

/** 标签（DERIVED / CURATED 一律只读） */
export interface LibraryTag {
  code: string
  label: string
  tagGroup: string
  tagType: 'DERIVED' | 'CURATED' | 'USER' | string
  color?: string | null
  sortNo?: number
  /** 传 datasetId 时才有值：该数据集内被多少条目使用。0 表示没货，面板不显示 */
  usageCount?: number
  /** false 表示不可手改：QUALITY / READY 是基线预检判据 */
  editable: boolean
}

export interface LibraryTagChip {
  code: string
  label: string
  tagGroup: string
  color?: string | null
}

export interface LibraryItemQuality {
  level: string
  hasSequence: boolean
  hasStructure: boolean
  hasPocket: boolean
  missing: string[]
}

/** 库条目：三种类型共用一个形状，类型专属字段在 payload */
export interface LibraryItem {
  id: number
  datasetId: number
  datasetName?: string | null
  itemType: 'TARGET' | 'MOLECULE' | 'SEQUENCE' | string
  entityId: number
  title: string
  subtitle?: string | null
  /** 按标签组归集，直接按组渲染 */
  tags: Record<string, LibraryTagChip[]>
  quality: LibraryItemQuality
  readiness: string[]
  payload: Record<string, unknown>
  derivedFrom?: {
    itemId: number
    datasetId?: number | null
    datasetName?: string | null
  } | null
}

export interface LibraryQuery {
  q?: string
  /** 标签码多选 */
  tags?: string[]
  /** AND（默认）/ OR */
  tagMode?: 'AND' | 'OR'
  /** 限定组内匹配，避免跨组 AND 永远为空 */
  tagGroups?: string[]
  itemType?: string
  datasetId?: number
  /** ALL / PUBLIC / PRIVATE / SHARED */
  scope?: string
  qualityLevel?: string
  organism?: string
  mwMin?: number
  mwMax?: number
  logpMin?: number
  logpMax?: number
  sort?: string
  page?: number
  pageSize?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
  hasNext: boolean
}

/** 分面筛选结果：total 是当前全部条件下的命中数，tags 里是还能怎么继续收窄 */
export interface LibraryFacetResponse {
  total: number
  tags: LibraryTag[]
}

export const libraryApi = {
  listDatasets: (scope?: string, itemType?: string) =>
    request.get<LibraryDataset[]>('/api/library/datasets', { params: { scope, itemType } }),

  getDataset: (id: number) => request.get<LibraryDataset>(`/api/library/datasets/${id}`),

  /**
   * 跨类型条目检索。
   *
   * 标签用逗号拼接而不是 `tags[]`：网关与后端的 `@ModelAttribute` 对重复参数的处理
   * 依容器实现而异（Tomcat 对 `tags=a&tags=b` 的绑定行为在不同版本上不一致），
   * 拼成单值最稳。
   */
  queryItems: (query: LibraryQuery) => {
    const params: Record<string, unknown> = { ...query }
    if (query.tags && query.tags.length) params.tags = query.tags.join(',')
    else delete params.tags
    if (query.tagGroups && query.tagGroups.length) params.tagGroups = query.tagGroups.join(',')
    else delete params.tagGroups
    return request.get<PageResult<LibraryItem>>('/api/library/items', { params })
  },

  getItem: (id: number) => request.get<LibraryItem>(`/api/library/items/${id}`),

  /**
   * 分面筛选：当前条件下每个标签还能筛出多少条。
   *
   * 与 `listTags` 的区别：那个按整个数据集计数（展示「这个库有什么」），
   * 这个按当前筛选结果计数，且每个标签的计数扣掉它自己所在组的已选标签。
   */
  facets: (query: LibraryQuery) => {
    const params: Record<string, unknown> = { ...query }
    if (query.tags && query.tags.length) params.tags = query.tags.join(',')
    else delete params.tags
    if (query.tagGroups && query.tagGroups.length) params.tagGroups = query.tagGroups.join(',')
    else delete params.tagGroups
    return request.get<LibraryFacetResponse>('/api/library/facets', { params })
  },

  /**
   * 标签字典。
   *
   * 传 `datasetId` 才带 `usageCount` —— 筛选面板靠它只显示「有货」的标签：
   * 公共靶点库只有 3 条数据时，45 个内置标签里 37 个计数为 0，全列出来
   * 会把条目列表挤出首屏。
   */
  listTags: (itemType?: string, datasetId?: number) =>
    request.get<LibraryTag[]>('/api/library/tags', { params: { itemType, datasetId } })
}

/** 标签组中文名（顺序即筛选面板的展示顺序） */
export const TAG_GROUP_LABEL: Record<string, string> = {
  SOURCE: '来源',
  FAMILY: '靶点家族',
  DISEASE: '疾病领域',
  MODALITY: '成药模态',
  PHASE: '开发进程',
  SAFETY: '安全性',
  QUALITY: '数据完备度',
  READY: '可用能力',
  CUSTOM: '我的标签'
}

/** 只读组的提示：告诉用户为什么不能改，而不是给一个点了没反应的按钮 */
export const TAG_GROUP_READONLY_HINT: Record<string, string> = {
  QUALITY: '按实际结构推导，不允许手改 —— 基线预检的「无结构」判定读的就是它',
  READY: '按实际能力推导，不允许手改'
}

/** 条目类型中文名 */
export const ITEM_TYPE_LABEL: Record<string, string> = {
  TARGET: '靶点',
  MOLECULE: '分子',
  SEQUENCE: '抗体序列'
}

/** 数据完备度 → 展示（颜色与文案） */
export function qualityMeta(level?: string | null): {
  label: string
  tone: 'success' | 'warning' | 'danger' | 'neutral'
} {
  switch (level) {
    case 'EXPERIMENTAL_STRUCTURE':
      return { label: '有实验结构', tone: 'success' }
    case 'PREDICTED_ONLY':
      return { label: '仅预测结构', tone: 'warning' }
    case 'NO_STRUCTURE':
      return { label: '无结构', tone: 'danger' }
    // 分子与抗体不套用靶点的结构完备度：分子没有「实验/预测结构」这回事，
    // 硬套会让分子卡片上写着「无实验结构」，把两种语义混成一团
    case 'MOLECULE_READY':
      return { label: '结构已确证', tone: 'success' }
    case 'SEQUENCE_READY':
      return { label: '序列完整', tone: 'success' }
    default:
      return { label: '未知', tone: 'neutral' }
  }
}
