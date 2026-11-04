<template>
  <section class="config">
    <!-- ① 这一步在做什么：先说人话，再说实现边界 -->
    <header class="config__head">
      <h3 class="config__title">这一步在做什么</h3>
      <ol class="config__howto">
        <li>你挑一批分子进来当「种子」—— 用左边的标签一层层收窄，勾选剩下的。</li>
        <li>系统按基线里冻结的硬约束逐条判定：分子量、LogP、可旋转键、PAINS 警示结构……</li>
        <li>通过的进入候选池，之后逐个评估打分；被筛掉的每一条都会留下原因。</li>
      </ol>
      <p class="config__hint">
        当前环境没有部署分子生成引擎，所以这一步是<b>导入后筛选</b>，不是「让 AI 凭空画新分子」：
        你做的是从已有分子里挑出值得做的 —— 这本身是研发里最常见的动作。
        这类批次会标「降级」，表示没有做结构创新。
      </p>
    </header>

    <!-- ② 种子从哪来 -->
    <div class="config__section">
      <p class="config__label">① 种子从哪来</p>
      <div class="seg">
        <button
          v-for="opt in SOURCE_OPTIONS"
          :key="opt.value"
          type="button"
          class="seg__btn"
          :class="{ 'seg__btn--on': source === opt.value }"
          @click="source = opt.value"
        >
          {{ opt.label }}
          <em v-if="opt.hint" class="seg__hint">{{ opt.hint }}</em>
        </button>
      </div>
    </div>

    <!-- 从库选：分面逐步收窄 -->
    <div v-if="source === 'library' || source === 'sequence'" class="picker">
      <div class="picker__bar">
        <label class="field">
          <span class="field__label">从哪个库选</span>
          <SpSelect v-model="datasetId" block :options="datasetOptions" @change="onDatasetChange" />
        </label>
        <label class="field field--grow">
          <span class="field__label">关键词（名称 / 分子式 / InChIKey）</span>
          <input
            v-model.trim="keyword"
            type="search"
            class="field__control"
            :placeholder="source === 'sequence' ? '按抗体名、靶点基因搜索' : '按名称、分子式、InChIKey 搜索'"
          />
        </label>
        <span class="picker__count">已选 {{ selected.length }} 条</span>
      </div>

      <!-- 已选：筛选变了也不丢，永远看得见 -->
      <div v-if="selected.length" class="tray">
        <span class="tray__label">已选</span>
        <button
          v-for="s in selected"
          :key="s.id"
          type="button"
          class="tray__chip"
          :title="'移出：' + s.title"
          @click="toggleById(s.id)"
        >
          {{ s.title }}
          <svg viewBox="0 0 12 12" width="8" height="8" fill="none" stroke="currentColor"
               stroke-width="2" stroke-linecap="round" aria-hidden="true">
            <path d="M2 2l8 8M10 2l-8 8" />
          </svg>
        </button>
        <button type="button" class="tray__clear" @click="clearSelection">清空</button>
        <span v-if="hiddenSelectedCount" class="tray__hidden">
          其中 {{ hiddenSelectedCount }} 条不在当前筛选结果里（仍会提交）
        </span>
      </div>

      <div class="picker__tools">
        <button type="button" class="btn btn--sm" :disabled="loadingItems || !items.length" @click="selectAllVisible">
          全选当前结果 {{ total }} 条
        </button>
        <button type="button" class="btn btn--sm" :disabled="!selectedTags.length && !keyword" @click="resetFilters">
          清空筛选
        </button>
        <button
          v-if="!selected.length && source === 'library'"
          type="button"
          class="btn btn--sm"
          :disabled="loadingItems || !items.length"
          @click="loadSample"
        >
          不知道选什么？载入示例
        </button>
      </div>

      <p v-if="itemError" class="banner banner--danger">{{ itemError }}</p>

      <div class="picker__body">
        <!-- 左：分面（逐步收窄）。计数随已选标签实时变化 -->
        <aside class="facet">
          <p class="facet__title">按标签收窄<em class="facet__hint">每个维度选一个</em></p>
          <p v-if="!facetGroups.length" class="facet__empty">这个库还没有可用的标签。</p>
          <div v-for="group in facetGroups" :key="group.key" class="facet__group">
            <p class="facet__group-label">
              {{ group.label }}
              <em v-if="group.readonly" title="按实际数据推导，不可手改">只读</em>
            </p>
            <div class="facet__tags">
              <button
                v-for="tag in group.tags"
                :key="tag.code"
                type="button"
                class="facet__tag"
                :class="{ 'facet__tag--on': selectedTags.includes(tag.code) }"
                @click="toggleTag(tag.code)"
              >
                {{ tag.label }}
                <em class="facet__num">{{ tag.usageCount }}</em>
              </button>
            </div>
          </div>
        </aside>

        <!-- 右：结果 -->
        <div class="picker__results">
          <div v-if="loadingItems && !items.length" class="picker__empty">加载库条目…</div>
          <div v-else-if="!items.length" class="picker__empty">
            当前条件下没有条目。清空筛选或换个关键词。
          </div>
          <template v-else>
            <ul class="picker__list">
              <li v-for="item in items" :key="item.id">
                <label class="row" :class="{ 'row--on': isSelected(item.id) }">
                  <input
                    type="checkbox"
                    class="row__box"
                    :checked="isSelected(item.id)"
                    @change="toggle(item)"
                  />
                  <span class="row__main">
                    <span class="row__title">{{ item.title }}</span>
                    <span v-if="item.subtitle" class="row__sub">{{ item.subtitle }}</span>
                  </span>
                  <span class="row__facts">
                    <span v-for="f in rowFacts(item)" :key="f" class="row__fact">{{ f }}</span>
                  </span>
                  <span v-if="mwWarning(item)" class="row__warn" :title="mwWarning(item) || ''">!</span>
                </label>
              </li>
            </ul>
            <div v-if="hasNext" class="picker__more">
              <button type="button" class="btn btn--sm" :disabled="loadingItems" @click="loadMore">
                {{ loadingItems ? '加载中…' : `显示更多（还剩 ${total - items.length} 条）` }}
              </button>
            </div>
          </template>
        </div>
      </div>

      <details v-if="source === 'sequence'" class="adv">
        <summary class="adv__summary">库里没有合适的？直接粘贴重链 / 轻链序列</summary>
        <label class="field field--block">
          <span class="field__label">重链（VH）</span>
          <textarea v-model="heavyText" rows="4" class="field__control field__control--mono" spellcheck="false" />
        </label>
        <label class="field field--block">
          <span class="field__label">轻链（VL，可留空）</span>
          <textarea v-model="lightText" rows="4" class="field__control field__control--mono" spellcheck="false" />
        </label>
        <p class="config__note config__note--warn">
          序列类分子目前没有可用的 RDKit 指标，粗筛阶段会记
          <code>SEQUENCE_METRICS_UNAVAILABLE</code> 并明确标注「未判定」——
          没检查不等于检查通过。
        </p>
      </details>
    </div>

    <!-- 粘贴 SMILES：专业用户路径 -->
    <div v-else class="config__section">
      <label class="field field--block">
        <span class="field__label">待筛选分子（一行一个 SMILES）</span>
        <textarea v-model="smilesText" rows="6" class="field__control field__control--mono" spellcheck="false" />
      </label>
      <div class="picker__tools">
        <button type="button" class="btn btn--sm" @click="fillSampleSmiles">载入 3 条示例</button>
        <span class="picker__count">{{ smilesList.length }} 条</span>
      </div>
      <p class="config__note">
        硬约束与 PAINS/BRENK 会在这里被真判定；被筛掉的分子逐条记原因，可在「逐分子去向」里查。
      </p>
    </div>

    <!-- ③ 高级选项：默认折叠 -->
    <details class="adv">
      <summary class="adv__summary">高级选项（不用改，只影响可复现性）</summary>
      <div class="config__grid">
        <label class="field">
          <span class="field__label">全局随机种子</span>
          <input v-model.number="seed" type="number" class="field__control" placeholder="留空则随机，事后可凭批次复现" />
        </label>
        <label class="field">
          <span class="field__label">幂等键</span>
          <input
            v-model.trim="idempotencyKey"
            type="text"
            maxlength="64"
            class="field__control"
            placeholder="同键重复提交只产生一个批次"
          />
        </label>
      </div>
      <p class="config__note">
        种子决定「同一次提交重跑是否得到同样的结果」；幂等键防止网络重试造成重复批次。日常两处都留空即可。
      </p>
    </details>

    <!-- ④ 提交 + 预检 -->
    <p v-if="!canSubmit" class="banner banner--danger">{{ blockReason }}</p>

    <div class="config__actions">
      <button type="button" class="btn btn--primary" :disabled="!canSubmit || submitting" @click="submit">
        {{ submitting ? '筛选中…' : '提交本批' }}
      </button>
      <span class="config__preview">{{ previewText }}</span>
    </div>
    <p v-if="riskText" class="config__note config__note--warn">{{ riskText }}</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import SpSelect from '@/components/ui/SpSelect.vue'
import { TAG_GROUP_LABEL, libraryApi } from '@/api/library'
import type { LibraryDataset, LibraryFacetResponse, LibraryItem, LibraryTag } from '@/api/library'
import type { GenerationCreateRequest, GenerationSeedInput } from '@/api/generation'

/**
 * 生成配置。
 *
 * 两条设计取向：
 *
 * 1. **默认路径零输入**。上一版把「粘贴 SMILES」当成唯一必填项 —— SMILES 是结构不是名字，
 *    没有生物信息背景的人第一步就走不下去。现在主路径是「从库勾选」。
 *
 * 2. **库大了不能一次列全**，所以是「标签分面逐步收窄」而不是「长列表 + 一个搜索框」。
 *    左侧每个标签带可用条数，且计数**随已选标签实时变化**：
 *    选了「肿瘤」之后，家族分面就只剩「肿瘤 ∩ 该家族」的条数。这是分面和普通筛选的区别 ——
 *    普通筛选只给结果，分面还要告诉用户下一步还能往哪走。
 *
 * 已选条目独立于当前筛选结果保存：筛完之后改条件不该把之前勾的丢掉，
 * 所以已选区常驻在列表上方，并提示「其中 N 条不在当前结果里（仍会提交）」。
 *
 * 采样量、批次大小、是否降级一律由后端 S2/S3 从基线快照算 —— 前端不提供
 * 「n_samples 填多少」的输入框，因为那正是设计里明确禁止的「人工拍脑袋定采样」。
 */
const props = defineProps<{
  /** 项目是否已冻结基线；未冻结时不允许提交 */
  hasBaseline: boolean
  /** 是否已开启轮次；生成必须挂在某一轮上 */
  hasRound: boolean
  submitting: boolean
}>()

const emit = defineEmits<{
  (e: 'submit', body: GenerationCreateRequest): void
}>()

type Source = 'library' | 'smiles' | 'sequence'

const SOURCE_OPTIONS: { value: Source; label: string; hint?: string }[] = [
  { value: 'library', label: '从库选分子', hint: '推荐' },
  { value: 'smiles', label: '粘贴 SMILES', hint: '已有结构式' },
  { value: 'sequence', label: '抗体序列', hint: '重链 / 轻链' }
]

const source = ref<Source>('library')

/* ------------------------------------------------------------------ 库选择 */
const PAGE_SIZE = 50

const datasets = ref<LibraryDataset[]>([])
const datasetId = ref<number | null>(null)
const items = ref<LibraryItem[]>([])
const selected = ref<{ id: number; title: string; mw?: number }[]>([])
const keyword = ref('')
const selectedTags = ref<string[]>([])
const facets = ref<LibraryFacetResponse | null>(null)
const total = ref(0)
const page = ref(1)
const hasNext = ref(false)
const loadingItems = ref(false)
const itemError = ref('')

const datasetOptions = computed(() =>
  datasets.value.map((d) => ({
    value: d.id,
    label: `${d.name}（${d.itemCount} 条 · ${d.visibility === 'PUBLIC' ? '公共' : '私有'}）`
  }))
)

/** 分子库与抗体序列库是两个数据集类型，切页签要换数据源 */
const wantedType = computed(() => (source.value === 'sequence' ? 'SEQUENCE' : 'MOLECULE'))

/** 分面：只显示「有货」或「已选中」的标签；空的标签点进去只会得到 0 条 */
const facetGroups = computed(() => {
  const tags = facets.value?.tags ?? []
  const byGroup = new Map<string, LibraryTag[]>()
  for (const t of tags) {
    const on = selectedTags.value.includes(t.code)
    if (!on && (t.usageCount ?? 0) <= 0) continue
    const arr = byGroup.get(t.tagGroup) ?? []
    arr.push(t)
    byGroup.set(t.tagGroup, arr)
  }
  const ORDER = Object.keys(TAG_GROUP_LABEL)
  return [...byGroup.entries()]
    .map(([key, list]) => ({
      key,
      label: TAG_GROUP_LABEL[key] ?? key,
      readonly: list.some((t) => t.tagGroup === 'QUALITY' || t.tagGroup === 'READY'),
      tags: [...list].sort((a, b) => (a.sortNo ?? 0) - (b.sortNo ?? 0))
    }))
    .sort((a, b) => ORDER.indexOf(a.key) - ORDER.indexOf(b.key))
})

/** 已选里有多少条被当前筛选挡在列表外 —— 不提示会让人以为它们被取消了 */
const hiddenSelectedCount = computed(() => {
  const visible = new Set(items.value.map((i) => i.id))
  return selected.value.filter((s) => !visible.has(s.id)).length
})

async function loadDatasets() {
  try {
    datasets.value = await libraryApi.listDatasets(undefined, wantedType.value)
    if (!datasets.value.some((d) => d.id === datasetId.value)) {
      datasetId.value = datasets.value[0]?.id ?? null
    }
    await refresh()
  } catch {
    // 库读不到不该挡住「粘贴 SMILES」这条路：只让「从库选」自己空着并说明
    datasets.value = []
    items.value = []
    facets.value = null
    itemError.value = '数据资产库暂时读不到，可以改用「粘贴 SMILES」继续。'
  }
}

async function loadFacets() {
  if (datasetId.value == null) {
    facets.value = null
    return
  }
  try {
    facets.value = await libraryApi.facets({
      datasetId: datasetId.value,
      itemType: wantedType.value,
      q: keyword.value || undefined,
      tags: selectedTags.value.length ? selectedTags.value : undefined,
      tagMode: 'AND'
    })
  } catch {
    facets.value = null
  }
}

async function loadItems(reset = true) {
  if (datasetId.value == null) {
    items.value = []
    total.value = 0
    hasNext.value = false
    return
  }
  if (reset) {
    page.value = 1
  }
  loadingItems.value = true
  itemError.value = ''
  try {
    const res = await libraryApi.queryItems({
      datasetId: datasetId.value,
      itemType: wantedType.value,
      q: keyword.value || undefined,
      tags: selectedTags.value.length ? selectedTags.value : undefined,
      tagMode: 'AND',
      page: page.value,
      pageSize: PAGE_SIZE
    })
    items.value = reset ? res.list : [...items.value, ...res.list]
    total.value = res.total
    hasNext.value = res.hasNext
  } catch {
    items.value = []
    total.value = 0
    hasNext.value = false
    itemError.value = '读取库条目失败，可以改用「粘贴 SMILES」继续。'
  } finally {
    loadingItems.value = false
  }
}

/** 分面与结果一起刷：只刷一个会出现「左边说 3 条、右边列 5 条」 */
async function refresh() {
  await Promise.all([loadFacets(), loadItems(true)])
}

function loadMore() {
  page.value += 1
  void loadItems(false)
}

function onDatasetChange() {
  selectedTags.value = []
  keyword.value = ''
  void refresh()
}

function toggleTag(code: string) {
  const group = tagGroupOf(code)
  if (selectedTags.value.includes(code)) {
    selectedTags.value = selectedTags.value.filter((c) => c !== code)
  } else if (group) {
    // 每个维度只选一个：这些字典实际都是「一条条目只属于一个家族/一个阶段」，
    // 同组选两个在 AND 语义下必然是空集。换组内另一个标签是「改选」而不是「叠加」。
    selectedTags.value = [...selectedTags.value.filter((c) => tagGroupOf(c) !== group), code]
  } else {
    selectedTags.value = [...selectedTags.value, code]
  }
  void refresh()
}

/** 标签属于哪个维度（从分面字典里查，字典本身不随筛选变化） */
function tagGroupOf(code: string): string | null {
  return (facets.value?.tags ?? []).find((t) => t.code === code)?.tagGroup ?? null
}

function resetFilters() {
  selectedTags.value = []
  keyword.value = ''
  void refresh()
}

/* 关键词输入做个 300ms 防抖：每敲一个字就打一次接口会把分面刷成闪烁的噪声 */
let keywordTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => void refresh(), 300)
})
onBeforeUnmount(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})

function isSelected(id: number) {
  return selected.value.some((s) => s.id === id)
}

function toggle(item: LibraryItem) {
  if (isSelected(item.id)) {
    selected.value = selected.value.filter((s) => s.id !== item.id)
    return
  }
  const mw = Number(item.payload?.mw ?? 0) || undefined
  selected.value = [...selected.value, { id: item.id, title: item.title, mw }]
}

function toggleById(id: number) {
  selected.value = selected.value.filter((s) => s.id !== id)
}

function selectAllVisible() {
  for (const item of items.value) {
    if (!isSelected(item.id)) toggle(item)
  }
}

function clearSelection() {
  selected.value = []
}

/** 示例优先挑分子量适中、大家听过的分子，让第一次跑就有东西可看 */
function loadSample() {
  const preferred = ['阿司匹林', '布洛芬', '厄洛替尼']
  const picked = preferred
    .map((n) => items.value.find((i) => i.title === n))
    .filter((i): i is LibraryItem => !!i)
  const target = picked.length ? picked : items.value.slice(0, 3)
  for (const item of target) {
    if (!isSelected(item.id)) toggle(item)
  }
}

watch(source, async () => {
  selected.value = []
  selectedTags.value = []
  keyword.value = ''
  await loadDatasets()
})

function rowFacts(item: LibraryItem): string[] {
  const p = item.payload ?? {}
  const facts: string[] = []
  if (p.mw) facts.push(`MW ${Number(p.mw).toFixed(1)}`)
  if (p.logp !== undefined && p.logp !== null) facts.push(`LogP ${Number(p.logp).toFixed(2)}`)
  if (p.sequenceLength) facts.push(`${p.sequenceLength} aa`)
  return facts
}

/** 只提示不拦截：硬约束在基线里，前端并不知道这个项目启用了哪几条 */
function mwWarning(item: LibraryItem): string | null {
  const mw = Number(item.payload?.mw ?? 0)
  if (!mw) return null
  if (mw < 150) return `分子量 ${mw.toFixed(1)} 低于常见的 150 下限，若基线启用了分子量约束会在粗筛被淘汰`
  if (mw > 600) return `分子量 ${mw.toFixed(1)} 高于常见的 600 上限，若基线启用了分子量约束会在粗筛被淘汰`
  return null
}

/* ------------------------------------------------ 粘贴 SMILES / 粘贴序列 */
const smilesText = ref('')
const heavyText = ref('')
const lightText = ref('')

const SAMPLE_SMILES = [
  'CC(=O)Oc1ccccc1C(=O)O',
  'CC(C)Cc1ccc(cc1)C(C)C(=O)O',
  'COc1cc2ncnc(Nc3cccc(Cl)c3F)c2cc1OCCCN1CCOCC1'
]

function fillSampleSmiles() {
  smilesText.value = SAMPLE_SMILES.join('\n')
}

const smilesList = computed(() =>
  smilesText.value
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean)
)

const seed = ref<number | undefined>(undefined)
const idempotencyKey = ref('')

const pastedSequence = computed(() => heavyText.value.trim() || lightText.value.trim())

const canSubmit = computed(() => {
  if (!props.hasBaseline || props.hasRound === false) return false
  if (props.submitting) return false
  if (source.value === 'smiles') return smilesList.value.length > 0
  if (source.value === 'sequence') return selected.value.length > 0 || !!pastedSequence.value
  return selected.value.length > 0
})

const blockReason = computed(() => {
  if (!props.hasBaseline) return '项目还没有冻结基线。生成只读快照，请先在需求分析里冻结。'
  if (props.hasRound === false) return '项目还没有开启任何轮次。生成必须挂在某一轮上，请先开启一轮。'
  if (source.value === 'library' && !selected.value.length) {
    return '请至少勾选一条分子。不知道选什么可以点「载入示例」。'
  }
  if (source.value === 'sequence' && !selected.value.length && !pastedSequence.value) {
    return '请从库里勾选抗体，或展开底部粘贴一条重链/轻链序列。'
  }
  if (source.value === 'smiles' && !smilesList.value.length) return '请至少填入一个 SMILES。'
  return ''
})

const previewText = computed(() => {
  if (source.value === 'smiles') return `${smilesList.value.length} 个 SMILES`
  if (source.value === 'sequence' && pastedSequence.value && !selected.value.length) return '1 条粘贴的序列'
  const risky = selected.value.filter((s) => s.mw && (s.mw < 150 || s.mw > 600)).length
  return `已选 ${selected.value.length} 条${risky ? `，其中 ${risky} 条有约束风险` : ''}`
})

const riskText = computed(() => {
  const risky = selected.value.filter((s) => s.mw && (s.mw < 150 || s.mw > 600))
  if (!risky.length) return ''
  const names = risky.map((r) => `${r.title}（MW ${r.mw?.toFixed(1)}）`).join('、')
  return `${names} 的分子量不在常见的 150–600 区间。若基线启用了分子量硬约束，它们会在粗筛被淘汰并记下原因 —— 这本身是想让你看到的结果，不是错误。`
})

onMounted(loadDatasets)

function submit() {
  if (!canSubmit.value) return

  let seeds: GenerationSeedInput[]
  if (source.value === 'smiles') {
    seeds = smilesList.value.map((smiles, i) => ({ ref: `SEED-${i + 1}`, smiles }))
  } else if (source.value === 'sequence' && !selected.value.length) {
    seeds = [
      {
        ref: 'PARENT-1',
        heavyChain: heavyText.value.trim() || undefined,
        lightChain: lightText.value.trim() || undefined
      }
    ]
  } else {
    // 只传 itemId：结构由后端按可见性解析，前端不搬运（也拿不到）别人的私有结构
    seeds = selected.value.map((s) => ({ ref: `LIB-${s.id}`, libraryItemId: s.id, name: s.title }))
  }

  emit('submit', {
    seeds,
    seed: seed.value === undefined || seed.value === null ? undefined : Number(seed.value),
    idempotencyKey: idempotencyKey.value || undefined
  })
}
</script>

<style lang="scss" scoped>
.config {
  &__head {
    margin-bottom: $spacing-md;
  }

  &__title {
    margin: 0 0 8px;
    font-size: $font-size-lg;
    font-weight: $font-weight-semibold;
    color: $color-text;
  }

  &__howto {
    max-width: 820px;
    margin: 0 0 8px;
    padding-left: 20px;
    font-size: $font-size-sm;
    line-height: 1.8;
    color: $color-text-soft;
  }

  &__hint {
    max-width: 820px;
    margin: 0;
    font-size: $font-size-sm;
    line-height: 1.6;
    color: $color-text-faint;
  }

  &__section {
    margin-bottom: $spacing-md;
  }

  &__label {
    margin: 0 0 6px;
    font-size: $font-size-xs;
    font-weight: $font-weight-semibold;
    color: $color-text-soft;
  }

  &__grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
    gap: $spacing-sm;
    margin-bottom: $spacing-sm;
  }

  &__note {
    margin: $spacing-sm 0 0;
    font-size: $font-size-xs;
    line-height: 1.6;
    color: $color-text-faint;

    &--warn {
      color: $warning-color;
    }

    code {
      font-family: monospace;
      font-size: $font-size-xs;
    }
  }

  &__actions {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-md;
    align-items: center;
    margin-top: $spacing-md;
    padding-top: $spacing-md;
    border-top: 1px solid $color-border;
  }

  &__preview {
    font-size: $font-size-xs;
    color: $color-text-faint;
  }
}

.seg {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 3px;
  border: 1px solid $color-border;
  border-radius: $radius-control;
  background: $color-surface-sunken;

  &__btn {
    display: inline-flex;
    align-items: baseline;
    gap: 5px;
    padding: 6px 14px;
    border: none;
    border-radius: $border-radius-sm;
    background: none;
    font-family: inherit;
    font-size: $font-size-sm;
    color: $color-text-soft;
    cursor: pointer;
    transition: background $transition-fast, color $transition-fast;

    &:hover {
      color: $color-text;
    }

    &--on {
      background: $color-surface;
      color: $color-brand-strong;
      font-weight: $font-weight-semibold;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
    }
  }

  &__hint {
    font-size: 10px;
    font-style: normal;
    color: $color-text-faint;
  }
}

.picker {
  margin-bottom: $spacing-md;

  &__bar {
    display: grid;
    grid-template-columns: minmax(180px, 0.8fr) minmax(220px, 1.4fr) auto;
    gap: $spacing-sm;
    align-items: end;
    margin-bottom: $spacing-sm;
  }

  &__tools {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: center;
    margin-bottom: $spacing-sm;
  }

  &__count {
    padding-bottom: 8px;
    font-size: $font-size-xs;
    color: $color-text-faint;
    white-space: nowrap;
  }

  &__body {
    display: grid;
    grid-template-columns: 196px minmax(0, 1fr);
    gap: $spacing-md;
    align-items: start;
  }

  &__results {
    min-width: 0;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 4px;
    max-height: 380px;
    margin: 0;
    padding: 2px;
    overflow-y: auto;
    list-style: none;
  }

  &__more {
    padding: 8px 0 0;
    text-align: center;
  }

  &__empty {
    padding: $spacing-lg;
    text-align: center;
    font-size: $font-size-sm;
    color: $color-text-faint;
  }
}

.tray {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-bottom: $spacing-sm;
  padding: 7px 10px;
  border: 1px solid rgba($accent-color, 0.28);
  border-radius: $radius-control;
  background: $color-brand-softer;

  &__label {
    font-size: $font-size-xs;
    color: $color-text-faint;
  }

  &__chip {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 2px 8px;
    border: none;
    border-radius: $radius-pill;
    background: $color-surface;
    font-family: inherit;
    font-size: $font-size-xs;
    color: $color-text;
    cursor: pointer;

    &:hover {
      color: $error-color;
    }
  }

  &__clear {
    border: none;
    background: none;
    font-family: inherit;
    font-size: $font-size-xs;
    color: $color-text-faint;
    cursor: pointer;
    text-decoration: underline;
  }

  &__hidden {
    margin-left: auto;
    font-size: 10px;
    color: $color-text-faint;
  }
}

.facet {
  max-height: 380px;
  padding-right: 6px;
  overflow-y: auto;

  &__title {
    margin: 0 0 6px;
    font-size: $font-size-xs;
    font-weight: $font-weight-semibold;
    color: $color-text-soft;
  }

  &__hint {
    margin-left: 6px;
    font-size: 10px;
    font-style: normal;
    font-weight: $font-weight-normal;
    color: $color-text-faint;
  }

  &__empty {
    margin: 0;
    font-size: $font-size-xs;
    color: $color-text-faint;
  }

  &__group {
    margin-bottom: 10px;
  }

  &__group-label {
    margin: 0 0 4px;
    font-size: 10px;
    letter-spacing: 0.02em;
    color: $color-text-faint;

    em {
      font-style: normal;
      opacity: 0.75;
    }
  }

  &__tags {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
  }

  &__tag {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 2px 7px;
    border: 1px solid $color-border;
    border-radius: $radius-pill;
    background: $color-surface;
    font-family: inherit;
    font-size: 11px;
    color: $color-text-soft;
    cursor: pointer;
    transition: border-color $transition-fast, background $transition-fast, color $transition-fast;

    &:hover {
      border-color: rgba($accent-color, 0.45);
      color: $color-text;
    }

    &--on {
      border-color: rgba($accent-color, 0.6);
      background: $color-brand-soft;
      color: $color-brand-strong;
      font-weight: $font-weight-medium;
    }
  }

  &__num {
    font-style: normal;
    font-size: 10px;
    color: $color-text-faint;
  }
}

.row {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid $color-border;
  border-radius: $radius-control;
  background: $color-surface;
  cursor: pointer;
  transition: border-color $transition-fast, background $transition-fast;

  &:hover {
    border-color: rgba($accent-color, 0.4);
  }

  &--on {
    border-color: rgba($accent-color, 0.55);
    background: $color-brand-softer;
  }

  &__box {
    flex: 0 0 auto;
    width: 14px;
    height: 14px;
    accent-color: $accent-color;
    cursor: pointer;
  }

  &__main {
    display: flex;
    flex-direction: column;
    gap: 1px;
    min-width: 0;
    flex: 1;
  }

  &__title {
    font-size: $font-size-sm;
    color: $color-text;
  }

  &__sub {
    font-size: $font-size-xs;
    color: $color-text-faint;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  &__facts {
    display: flex;
    flex: 0 0 auto;
    gap: 8px;
    font-size: $font-size-xs;
    color: $color-text-faint;
  }

  &__warn {
    flex: 0 0 auto;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 15px;
    height: 15px;
    border-radius: 50%;
    background: rgba($warning-color, 0.16);
    font-size: 10px;
    font-weight: $font-weight-semibold;
    color: $warning-color;
  }
}

.adv {
  margin-top: $spacing-md;
  padding-top: $spacing-sm;
  border-top: 1px dashed $color-border;

  &__summary {
    font-size: $font-size-xs;
    color: $color-text-faint;
    cursor: pointer;

    &:hover {
      color: $color-text-soft;
    }
  }
}

.field {
  display: flex;
  flex-direction: column;
  gap: 4px;

  &--grow {
    min-width: 0;
  }

  &--block {
    margin-bottom: $spacing-sm;
  }

  &__label {
    font-size: $font-size-xs;
    color: $color-text-faint;
  }

  &__control {
    padding: 7px 9px;
    border: 1px solid $color-border;
    border-radius: $radius-control;
    font-size: $font-size-sm;
    color: $color-text;
    background: $color-surface;

    &--mono {
      font-family: monospace;
      font-size: $font-size-xs;
      line-height: 1.6;
      resize: vertical;
    }
  }
}
</style>
