<template>
  <div class="mp">
    <div class="mp__intro">
      <p class="mp__lead">
        指标是评价体系的「尺子」。下表全部来自后端指标定义表，不做前端硬编码 ——
        权重的总和不要求等于 100%：综合分是按「本次实际算出来的指标」重新归一化的。
        某个指标算不出来时它进「缺失」而不是记 0，因为 LogP=0 和「LogP 算不出」在决策阶段含义完全不同。
      </p>
      <div class="mp__summary">
        <span class="mp__pill">共 {{ metrics.length }} 项</span>
        <span class="mp__pill mp__pill--ok">已启用 {{ enabledCount }}</span>
        <span class="mp__pill mp__pill--warn">仅展示 {{ zeroWeightCount }}</span>
        <span class="mp__pill">权重合计 {{ weightSum.toFixed(2) }}</span>
      </div>
    </div>

    <div v-if="loading" class="mp__state">加载中…</div>
    <div v-else-if="loadError" class="mp__state mp__state--error">{{ loadError }}</div>
    <template v-else>
      <section v-for="group in groups" :key="group.category" class="mp__group">
        <header class="mp__group-head">
          <h3 class="mp__group-title">{{ categoryLabel(group.category) }}</h3>
          <span class="mp__group-meta">{{ group.items.length }} 项 · 权重合计 {{ group.weight.toFixed(2) }}</span>
        </header>

        <div class="mp__grid">
          <article v-for="m in group.items" :key="m.metricCode" class="mp__card">
            <div class="mp__card-head">
              <span class="mp__name">{{ m.metricName }}</span>
              <span class="mp__code">{{ m.metricCode }}</span>
            </div>

            <div class="mp__tags">
              <span class="mp__tag">
                {{ directionLabel(m.better) }}<template v-if="m.unit"> · {{ m.unit }}</template>
              </span>
              <span class="mp__tag" :class="m.weight > 0 ? 'mp__tag--weight' : 'mp__tag--idle'">
                权重 {{ m.weight.toFixed(2) }}
              </span>
              <span v-if="m.dimension" class="mp__tag mp__tag--dim">{{ dimensionLabel(m.dimension) }}</span>
              <span v-if="!m.enabled" class="mp__tag mp__tag--off">未启用</span>
            </div>

            <p v-if="m.appliesTo.length" class="mp__scope">
              适用：{{ m.appliesTo.join(' / ') }}
            </p>
            <p v-else class="mp__scope mp__scope--all">适用：全部分子形式</p>

            <p v-if="m.remark" class="mp__remark">{{ m.remark }}</p>
            <p v-if="m.minValue != null || m.maxValue != null" class="mp__range">
              归一化区间 {{ m.minValue ?? '—' }} ~ {{ m.maxValue ?? '—' }}
            </p>
          </article>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 评价体系 · 指标总览。
 *
 * 按 category 分组，而不是按「小分子 / 抗体」分组：分类是表里已有的稳定维度，
 * 分子形式则是一个指标可能同时适用多种（applies_to 是数组），按形式分组会重复展示。
 */
import { computed, onMounted, ref } from 'vue'
import { evaluationCatalogApi, dimensionLabel, type MetricDefinition } from '@/api/evaluation'

const metrics = ref<MetricDefinition[]>([])
const loading = ref(false)
const loadError = ref('')

const CATEGORY_TEXT: Record<string, string> = {
  CONSTRAINT: '约束判定',
  ACTIVITY: '活性',
  PROPERTY: '理化性质',
  DRUGLIKENESS: '类药性',
  SYNTHESIS: '可合成性',
  SAFETY: '安全性',
  SELECTIVITY: '选择性',
  DOCKING: '对接打分',
  DIVERSITY: '多样性',
  DEVELOPABILITY: '可开发性',
  IMMUNOGENICITY: '免疫原性',
  ADMET: 'ADMET'
}

/** 展示顺序：按「评什么 → 能不能吃 → 好不好做 → 安不安全」，不按字母表 */
const CATEGORY_ORDER = [
  'ACTIVITY',
  'SELECTIVITY',
  'DOCKING',
  'PROPERTY',
  'DRUGLIKENESS',
  'ADMET',
  'DEVELOPABILITY',
  'STABILITY',
  'IMMUNOGENICITY',
  'SAFETY',
  'SYNTHESIS',
  'DIVERSITY',
  'CONSTRAINT'
]

function categoryLabel(category: string): string {
  return CATEGORY_TEXT[category] || category
}

function directionLabel(better: string): string {
  if (better === 'HIGHER') return '越大越好'
  if (better === 'LOWER') return '越小越好'
  return '无好坏方向'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    metrics.value = await evaluationCatalogApi.metrics()
  } catch (error: unknown) {
    loadError.value = error instanceof Error ? error.message : '加载指标目录失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** 按 category 分组，组内保持后端给的 sortNo 顺序；组间按 CATEGORY_ORDER 排 */
const groups = computed(() => {
  const map = new Map<string, { category: string; items: MetricDefinition[]; weight: number }>()
  for (const m of metrics.value) {
    const key = m.category || 'OTHER'
    if (!map.has(key)) {
      map.set(key, { category: key, items: [], weight: 0 })
    }
    const group = map.get(key)!
    group.items.push(m)
    group.weight += m.weight ?? 0
  }
  const rank = (c: string) => {
    const i = CATEGORY_ORDER.indexOf(c)
    return i === -1 ? CATEGORY_ORDER.length : i
  }
  return [...map.values()].sort((a, b) => rank(a.category) - rank(b.category))
})

const enabledCount = computed(() => metrics.value.filter((m) => m.enabled).length)

const zeroWeightCount = computed(() =>
  metrics.value.filter((m) => m.enabled && (m.weight ?? 0) === 0).length
)

const weightSum = computed(() => metrics.value.reduce((sum, m) => sum + (m.weight ?? 0), 0))
</script>

<style lang="scss" scoped>
$mp-ok: rgba(5, 150, 105, 0.12);
$mp-warn: rgba(217, 119, 6, 0.12);
$mp-err: rgba(220, 38, 38, 0.1);

.mp {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.mp__intro {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  padding: $spacing-md $spacing-lg;
  border: 1px solid $color-border;
  border-left: 3px solid $color-brand;
  border-radius: $radius-card;
  background: $color-surface;
}

.mp__lead {
  margin: 0;
  font-size: $font-size-sm;
  line-height: 1.75;
  color: $color-text-soft;
}

.mp__summary {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.mp__pill {
  padding: 2px 10px;
  border-radius: $radius-pill;
  background: $color-surface-sunken;
  color: $color-text-soft;
  font-size: $font-size-xs;
  font-variant-numeric: tabular-nums;

  &--ok { background: $mp-ok; color: $success-color; }
  &--warn { background: $mp-warn; color: $warning-color; }
}

.mp__group {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.mp__group-head {
  display: flex;
  align-items: baseline;
  gap: $spacing-sm;
  padding-bottom: $spacing-xs;
  border-bottom: 1px solid $color-border;
}

.mp__group-title {
  font-size: $font-size-base;
  font-weight: $font-weight-semibold;
  color: $color-text;
}

.mp__group-meta {
  font-size: $font-size-xs;
  color: $color-text-faint;
  font-variant-numeric: tabular-nums;
}

.mp__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: $spacing-sm;
}

.mp__card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: $spacing-md;
  border: 1px solid $color-border;
  border-radius: $radius-card;
  background: $color-surface;
}

.mp__card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $spacing-sm;
}

.mp__name {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $color-text;
}

.mp__code {
  font-family: $font-family-mono;
  font-size: 10px;
  color: $color-text-faint;
  white-space: nowrap;
}

.mp__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.mp__tag {
  padding: 1px 8px;
  border-radius: $radius-pill;
  background: $color-surface-sunken;
  color: $color-text-soft;
  font-size: 10px;

  &--weight { background: $color-brand-soft; color: $color-brand-strong; }
  &--idle { background: $color-surface-sunken; color: $color-text-faint; }
  &--dim { background: rgba(8, 145, 178, 0.12); color: $info-color; }
  &--off { background: $mp-err; color: $error-color; }
}

.mp__scope,
.mp__range {
  margin: 0;
  font-size: 11px;
  color: $color-text-faint;
}

.mp__scope--all {
  color: $color-text-faint;
  opacity: 0.8;
}

.mp__remark {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: $color-text-soft;
}

.mp__state {
  padding: $spacing-xl;
  text-align: center;
  color: $color-text-faint;
  font-size: $font-size-sm;
}

.mp__state--error {
  color: $error-color;
}
</style>
