<template>
  <div class="cp">
    <div class="cp__picker">
      <div class="cp__picker-head">
        <span class="cp__picker-title">选择要对比的评价结果</span>
        <span class="cp__picker-hint">已选 {{ selected.length }} / 最多 {{ MAX_SELECT }}</span>
      </div>

      <div v-if="loading" class="cp__state">加载中…</div>
      <div v-else-if="loadError" class="cp__state cp__state--error">{{ loadError }}</div>
      <div v-else-if="!results.length" class="cp__state">
        还没有评价结果，先到「单次评价」跑一次。
      </div>
      <div v-else class="cp__list">
        <label
          v-for="r in results"
          :key="String(r.id)"
          class="cp__item"
          :class="{ 'cp__item--on': isSelected(r.id), 'cp__item--off': !isSelected(r.id) && selected.length >= MAX_SELECT }"
        >
          <input
            type="checkbox"
            :checked="isSelected(r.id)"
            :disabled="!isSelected(r.id) && selected.length >= MAX_SELECT"
            @change="toggle(r.id)"
          />
          <span class="cp__item-main">
            <span class="cp__item-name">{{ r.targetName || r.targetId || `#${r.id}` }}</span>
            <span class="cp__item-sub">{{ snippet(r.ligandSmiles) }}</span>
          </span>
          <span class="cp__item-metric">{{ affinityText(r) }}</span>
        </label>
      </div>
    </div>

    <div class="cp__result">
      <div v-if="selected.length < 2" class="cp__state">
        至少选 2 条才能对比 —— 单条不需要并排看。
      </div>
      <template v-else>
        <table class="cp__table">
          <thead>
            <tr>
              <th class="cp__th">维度</th>
              <th v-for="r in selected" :key="String(r.id)">
                {{ columnTitle(r) }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in ROWS" :key="row.key">
              <th class="cp__th">{{ row.label }}</th>
              <td v-for="r in selected" :key="String(r.id) + row.key" :class="row.cls">
                {{ row.value(r) }}
              </td>
            </tr>
            <tr>
              <th class="cp__th">相对最优</th>
              <td v-for="r in selected" :key="String(r.id) + '-best'">
                <span v-if="bestAffinityId === String(r.id)" class="cp__best">亲和力最高</span>
                <span v-else-if="bestConfidenceId === String(r.id)" class="cp__best cp__best--alt">置信度最高</span>
                <span v-else class="cp__dash">—</span>
              </td>
            </tr>
          </tbody>
        </table>

        <p class="cp__foot">
          只做并排陈列，不做排序推荐：亲和力越低越紧、置信度越高越可信，
          但两者同时最优往往不存在，选哪个是项目决策，不是这个页面能替用户决定的。
        </p>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 评价体系 · 结果对比。
 *
 * 为什么不做「综合排名」：把亲和力和置信度合成一个分数需要一个目的函数，
 * 而目的函数来自项目基线（权重、阈值），属于设计域而不是评价体系。
 * 这里只并列展示原值，把判断留给候选评估与决策。
 */
import { computed, onMounted, ref } from 'vue'
import { resultApi } from '@/api/predict'
import type { PredictionResult } from '@/types'

const MAX_SELECT = 4

const results = ref<PredictionResult[]>([])
const selectedIds = ref<string[]>([])
const loading = ref(false)
const loadError = ref('')

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    const page = await resultApi.getResultList(1, 100)
    results.value = page.list
  } catch (error: unknown) {
    loadError.value = error instanceof Error ? error.message : '加载评价结果失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

function isSelected(id: string | number): boolean {
  return selectedIds.value.includes(String(id))
}

function toggle(id: string | number): void {
  const key = String(id)
  if (selectedIds.value.includes(key)) {
    selectedIds.value = selectedIds.value.filter((x) => x !== key)
  } else if (selectedIds.value.length < MAX_SELECT) {
    selectedIds.value = [...selectedIds.value, key]
  }
}

const selected = computed(() =>
  selectedIds.value
    .map((id) => results.value.find((r) => String(r.id) === id))
    .filter((r): r is PredictionResult => !!r)
)

function affinityText(r: PredictionResult): string {
  return r.bindingAffinity != null ? `${r.bindingAffinity.toFixed(2)} kcal/mol` : '—'
}

/** 同一靶点下会有多条结果，列头必须带结果编号才能区分「哪一个 EGFR」 */
function columnTitle(r: PredictionResult): string {
  const name = r.targetName || r.targetId || '未命名'
  return `${name} · #${r.id}`
}

function snippet(value?: string): string {
  if (!value) return '—'
  return value.length > 34 ? `${value.slice(0, 34)}…` : value
}

const ROWS: Array<{ key: string; label: string; cls?: string; value: (r: PredictionResult) => string }> = [
  { key: 'targetId', label: '靶点编号', cls: 'cp__mono', value: (r) => r.targetId || '—' },
  { key: 'smiles', label: '配体 SMILES', cls: 'cp__mono cp__wrap', value: (r) => r.ligandSmiles || '—' },
  { key: 'affinity', label: '结合亲和力', value: (r) => affinityText(r) },
  {
    key: 'confidence',
    label: '置信度',
    value: (r) => `${Math.round((r.confidenceScore ?? 0) * 100)}%（${confidenceLabel(r.confidenceLevel)}）`
  },
  { key: 'interactions', label: '相互作用条数', value: (r) => String(r.interactions?.length ?? 0) },
  { key: 'dataset', label: '数据集', value: (r) => r.datasetInfo?.name || '—' },
  { key: 'createdAt', label: '时间', value: (r) => (r.createdAt || '').replace('T', ' ').slice(0, 19) || '—' }
]

function confidenceLabel(level?: string): string {
  const MAP: Record<string, string> = { high: '高', medium: '中', low: '低' }
  return level ? MAP[level] || level : '未分级'
}

/** 亲和力越小越紧（kcal/mol），空值不参与比较 */
const bestAffinityId = computed(() => {
  const candidates = selected.value.filter((r) => r.bindingAffinity != null)
  if (!candidates.length) return ''
  const best = candidates.reduce((a, b) => ((a.bindingAffinity ?? 0) <= (b.bindingAffinity ?? 0) ? a : b))
  return String(best.id)
})

const bestConfidenceId = computed(() => {
  if (!selected.value.length) return ''
  const best = selected.value.reduce((a, b) =>
    (a.confidenceScore ?? 0) >= (b.confidenceScore ?? 0) ? a : b
  )
  return String(best.id)
})
</script>

<style lang="scss" scoped>
$cp-warn: rgba(217, 119, 6, 0.12);

.cp {
  display: grid;
  grid-template-columns: minmax(280px, 340px) 1fr;
  gap: $spacing-lg;
  align-items: start;

  @media (max-width: 1000px) {
    grid-template-columns: 1fr;
  }
}

.cp__picker {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  border: 1px solid $color-border;
  border-radius: $radius-card;
  background: $color-surface;
  padding: $spacing-md;
  max-height: 70vh;
  overflow: auto;
}

.cp__picker-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $spacing-sm;
}

.cp__picker-title {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $color-text;
}

.cp__picker-hint {
  font-size: $font-size-xs;
  color: $color-text-faint;
  font-variant-numeric: tabular-nums;
}

.cp__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cp__item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 6px $spacing-sm;
  border-radius: $radius-control;
  cursor: pointer;

  &:hover {
    background: $color-surface-sunken;
  }

  &--on {
    background: $color-brand-soft;
  }

  &--off {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

.cp__item-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.cp__item-name {
  font-size: $font-size-xs;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cp__item-sub {
  font-family: $font-family-mono;
  font-size: 10px;
  color: $color-text-faint;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cp__item-metric {
  font-size: 10px;
  color: $color-text-soft;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.cp__result {
  border: 1px solid $color-border;
  border-radius: $radius-card;
  background: $color-surface;
  padding: $spacing-md;
  overflow-x: auto;
}

.cp__table {
  width: 100%;
  border-collapse: collapse;
  font-size: $font-size-xs;

  th,
  td {
    padding: $spacing-sm;
    border-bottom: 1px solid $color-border-soft;
    text-align: left;
    vertical-align: top;
  }

  thead th {
    font-size: $font-size-sm;
    color: $color-text;
    font-weight: $font-weight-medium;
    border-bottom: 1px solid $color-border;
  }
}

.cp__th {
  width: 130px;
  color: $color-text-faint;
  font-weight: $font-weight-normal;
}

.cp__mono {
  font-family: $font-family-mono;
}

.cp__wrap {
  word-break: break-all;
  max-width: 260px;
}

.cp__best {
  display: inline-block;
  padding: 1px 8px;
  border-radius: $radius-pill;
  background: rgba(5, 150, 105, 0.12);
  color: $success-color;
  font-size: 10px;

  &--alt {
    background: $cp-warn;
    color: $warning-color;
  }
}

.cp__dash {
  color: $color-text-faint;
}

.cp__state {
  padding: $spacing-xl;
  text-align: center;
  color: $color-text-faint;
  font-size: $font-size-sm;
}

.cp__state--error {
  color: $error-color;
}

.cp__foot {
  margin: $spacing-md 0 0;
  font-size: 11px;
  line-height: 1.7;
  color: $color-text-faint;
}
</style>
