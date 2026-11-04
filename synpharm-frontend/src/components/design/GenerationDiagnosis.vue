<template>
  <section class="diag" :class="`diag--${meta.tone}`">
    <header class="diag__head">
      <div class="diag__verdict">
        <span class="diag__badge">{{ meta.label }}</span>
        <span class="diag__hint">{{ meta.hint }}</span>
      </div>
      <span class="diag__ready" :class="readyClass">
        {{ readyText }}
      </span>
    </header>

    <p v-if="recommendedNext" class="diag__next">
      <strong>建议下一步：</strong>{{ recommendedNext }}
    </p>

    <ul v-if="findings.length" class="diag__findings">
      <li
        v-for="(finding, index) in findings"
        :key="`${finding.level}-${index}`"
        class="finding"
        :class="`finding--${levelTone(finding.level)}`"
      >
        <span class="finding__level">{{ finding.level }}</span>
        <span class="finding__msg">{{ finding.message }}</span>
        <span v-if="finding.suggestion" class="finding__sug">{{ finding.suggestion }}</span>
      </li>
    </ul>
    <p v-else class="diag__clean">没有发现需要处理的问题。</p>

    <p class="diag__note">
      结论由后端代码按「候选数 / 有效簇数」阈值算出，文案只负责解释 —— 避免同一批数据
      在不同时间被解释成不同结论。
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { verdictMeta } from '@/api/generation'
import type { GenerationDiagnosis, GenerationVerdict } from '@/api/generation'

/**
 * S9 诊断面板。
 *
 * verdict 的判定权在后端，这里只翻译与配色；不提供任何「手工判定通过」的入口。
 *
 * 「闸门结果」与「能不能往下走」分开显示：候选数低于基线要求时仍然允许进入编辑
 * （否则用户会被卡死在这里），但结论必须写成「未达标」而不是绿色的「通过」——
 * 一个页面不能同时说「通过」和「覆盖不足」。
 */
const props = defineProps<{
  diagnosis?: GenerationDiagnosis | null
  /** 后端 verdict 缺失时回退到批次状态，避免面板空白 */
  fallbackVerdict?: GenerationVerdict | null
}>()

const GATE_META: Record<string, { label: string; tone: 'success' | 'warning' | 'danger'; hint: string }> = {
  OK: { label: '通过', tone: 'success', hint: '候选数与有效簇均达标，可进入编辑' },
  BELOW_MIN_CANDIDATES: {
    label: '未达标 · 候选数不足',
    tone: 'warning',
    hint: '候选数低于基线要求的最小值，允许进入编辑但需要重跑补足'
  },
  BELOW_DIVERSITY: {
    label: '未达标 · 多样性不足',
    tone: 'warning',
    hint: '候选数量够，但挤在少数几个结构簇里，覆盖不足'
  },
  EMPTY: { label: '未通过 · 无候选', tone: 'danger', hint: '一个候选都没产出，禁止进入编辑' },
  ALL_DUPLICATE: {
    label: '未通过 · 无新增候选',
    tone: 'warning',
    hint: '这批分子都已在候选池里（跨批次去重），已有候选不受影响'
  }
}

const meta = computed(() => {
  const gate = props.diagnosis?.gate
  if (gate && GATE_META[gate]) return GATE_META[gate]
  const fallback = verdictMeta(props.diagnosis?.verdict ?? props.fallbackVerdict ?? undefined)
  return { label: fallback.label, tone: fallback.tone, hint: fallback.hint }
})

const findings = computed(() => props.diagnosis?.findings ?? [])
const recommendedNext = computed(() => props.diagnosis?.recommendedNext ?? '')
const readyForEdit = computed(() => props.diagnosis?.readyForEdit ?? false)

/** 能进编辑 ≠ 达标：未达标时用琥珀色并写明原因，不用绿色 */
const gateOk = computed(() => (props.diagnosis?.gate ?? 'OK') === 'OK')

const readyText = computed(() => {
  if (!readyForEdit.value) return '禁止进入编辑'
  return gateOk.value ? '可进入编辑' : '可进入编辑（未达标）'
})

const readyClass = computed(() => {
  if (!readyForEdit.value) return 'diag__ready--no'
  return gateOk.value ? 'diag__ready--ok' : 'diag__ready--warn'
})

function levelTone(level: string): 'danger' | 'warning' | 'neutral' {
  const upper = level.toUpperCase()
  if (upper === 'ERROR' || upper === 'HARD') return 'danger'
  if (upper === 'WARN' || upper === 'WARNING') return 'warning'
  return 'neutral'
}
</script>

<style lang="scss" scoped>
.diag {
  padding: $spacing-md;
  border: 1px solid $color-border;
  border-radius: $radius-control;
  background: $color-surface;

  @each $tone, $color in (success: $success-color, warning: $warning-color, danger: $error-color, neutral: $text-muted) {
    &--#{$tone} {
      border-left: 4px solid $color;

      .diag__badge {
        color: $color;
        background: rgba($color, 0.12);
      }
    }
  }

  &__head {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-sm;
    align-items: center;
    justify-content: space-between;
  }

  &__verdict {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-sm;
    align-items: center;
  }

  &__badge {
    padding: 3px 12px;
    border-radius: $radius-pill;
    font-size: $font-size-sm;
    font-weight: $font-weight-semibold;
  }

  &__hint {
    font-size: $font-size-sm;
    color: $color-text-soft;
  }

  &__ready {
    padding: 3px 10px;
    border-radius: $radius-pill;
    font-size: $font-size-xs;
    font-weight: $font-weight-medium;

    &--ok {
      color: $success-color;
      background: rgba($success-color, 0.12);
    }

    &--no {
      color: $error-color;
      background: rgba($error-color, 0.12);
    }

    &--warn {
      color: $warning-color;
      background: rgba($warning-color, 0.14);
    }
  }

  &__next {
    margin: $spacing-sm 0 0;
    font-size: $font-size-sm;
    line-height: 1.6;
    color: $color-text-soft;
  }

  &__findings {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin: $spacing-md 0 0;
    padding: 0;
    list-style: none;
  }

  &__clean {
    margin: $spacing-sm 0 0;
    font-size: $font-size-sm;
    color: $color-text-faint;
  }

  &__note {
    margin: $spacing-md 0 0;
    font-size: $font-size-xs;
    line-height: 1.6;
    color: $color-text-faint;
  }
}

.finding {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  align-items: baseline;
  padding: 8px 10px;
  border-radius: $radius-control;
  background: $color-surface-sunken;
  font-size: $font-size-sm;

  @each $tone, $color in (danger: $error-color, warning: $warning-color, neutral: $text-muted) {
    &--#{$tone} .finding__level {
      color: $color;
    }
  }

  &__level {
    font-family: monospace;
    font-size: $font-size-xs;
    font-weight: $font-weight-semibold;
  }

  &__msg {
    flex: 1 1 260px;
    color: $color-text;
  }

  &__sug {
    flex: 1 1 100%;
    font-size: $font-size-xs;
    color: $color-text-faint;
  }
}
</style>
