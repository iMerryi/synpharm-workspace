<template>
  <div class="tp">
    <div class="tp__bar">
      <div class="tp__tabs">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          class="tp__tab"
          :class="{ 'tp__tab--active': activeTab === tab.value }"
          @click="activeTab = tab.value"
        >
          {{ tab.label }}
          <span v-if="tab.count > 0" class="tp__tab-count">{{ tab.count }}</span>
        </button>
      </div>
      <button type="button" class="tp__create" @click="goCreate">发起评价</button>
    </div>

    <div v-if="loading" class="tp__state">加载中…</div>
    <div v-else-if="loadError" class="tp__state tp__state--error">{{ loadError }}</div>
    <template v-else>
      <div v-for="task in filteredTasks" :key="task.id" class="tp__card">
        <div class="tp__card-head">
          <div class="tp__card-info">
            <span class="tp__card-name">{{ task.taskNo || task.name || task.id }}</span>
            <span class="tp__card-type">{{ typeLabel(task.predictType || task.type) }}</span>
          </div>
          <span class="tp__status" :class="`tp__status--${task.status}`">{{ statusLabel(task.status) }}</span>
        </div>

        <div v-if="task.status === 'running'" class="tp__progress">
          <div class="tp__progress-track">
            <div class="tp__progress-fill" :style="{ width: task.progress + '%' }"></div>
          </div>
          <span class="tp__progress-text">{{ task.progress }}%</span>
        </div>

        <div class="tp__card-foot">
          <span class="tp__date">{{ formatDate(task.createdAt) }}</span>
          <div class="tp__actions">
            <button type="button" class="tp__action" @click="handleView(task)">查看任务</button>
            <button
              v-if="task.status === 'running' || task.status === 'pending'"
              type="button"
              class="tp__action tp__action--danger"
              @click="handleCancel(task)"
            >
              取消任务
            </button>
          </div>
        </div>
      </div>

      <EmptyState
        v-if="!filteredTasks.length"
        icon="📋"
        :title="`暂无${tabLabel(activeTab)}任务`"
        description="任务在提交评价后生成，运行中的任务可以取消。"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 评价体系 · 评价任务。
 *
 * 与「评价结果」的区别：任务回答「跑完了没有」，结果回答「结论是什么」。
 * 一个任务最多对应一条结果，取消的任务没有结果 —— 所以两处都要留。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTasks, cancelTask } from '@/api/task'
import type { Task } from '@/types'
import EmptyState from '@/components/ui/EmptyState.vue'

const router = useRouter()

const activeTab = ref('all')
const tasks = ref<Task[]>([])
const loading = ref(false)
const loadError = ref('')

const TYPE_TEXT: Record<string, string> = {
  dti: '药物-靶点',
  ppi: '蛋白-蛋白',
  ddi: '药物-药物'
}

const STATUS_TEXT: Record<string, string> = {
  completed: '已完成',
  running: '运行中',
  pending: '待处理',
  failed: '失败',
  cancelled: '已取消'
}

const TAB_LABEL: Record<string, string> = {
  all: '',
  running: '运行中',
  pending: '待处理',
  completed: '已完成',
  failed: '失败'
}

function typeLabel(type?: string): string {
  if (!type) return '—'
  return TYPE_TEXT[type.toLowerCase()] || type
}

function statusLabel(status: string): string {
  return STATUS_TEXT[status] || status
}

function tabLabel(tab: string): string {
  return TAB_LABEL[tab] ?? ''
}

async function loadTasks(): Promise<void> {
  loading.value = true
  loadError.value = ''
  try {
    tasks.value = await getTasks()
  } catch (error: unknown) {
    loadError.value = error instanceof Error ? error.message : '加载任务失败'
  } finally {
    loading.value = false
  }
}

onMounted(loadTasks)

const tabs = computed(() => [
  { value: 'all', label: '全部', count: tasks.value.length },
  { value: 'running', label: '运行中', count: tasks.value.filter((t) => t.status === 'running').length },
  { value: 'pending', label: '待处理', count: tasks.value.filter((t) => t.status === 'pending').length },
  { value: 'completed', label: '已完成', count: tasks.value.filter((t) => t.status === 'completed').length },
  { value: 'failed', label: '失败', count: tasks.value.filter((t) => t.status === 'failed').length }
])

const filteredTasks = computed(() =>
  activeTab.value === 'all' ? tasks.value : tasks.value.filter((t) => t.status === activeTab.value)
)

function goCreate(): void {
  router.push({ path: '/evaluation', query: { tab: 'single' } })
}

function handleView(task: Task): void {
  router.push(`/tasks/${task.id}`)
}

async function handleCancel(task: Task): Promise<void> {
  try {
    await ElMessageBox.confirm('取消后该任务不再产出结果，确认取消？', '取消任务', {
      type: 'warning',
      confirmButtonText: '取消任务',
      cancelButtonText: '再想想'
    })
  } catch {
    return
  }
  try {
    await cancelTask(task.id)
    ElMessage.success('任务已取消')
    await loadTasks()
  } catch (error: unknown) {
    ElMessage.error(error instanceof Error ? error.message : '取消失败')
  }
}

function formatDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<style lang="scss" scoped>
.tp {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.tp__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.tp__tabs {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  flex-wrap: wrap;
}

.tp__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px $spacing-md;
  border: 1px solid $color-border;
  border-radius: $radius-pill;
  background: $color-surface;
  font-size: $font-size-sm;
  font-family: inherit;
  color: $color-text-soft;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }

  &--active {
    background: $color-brand;
    border-color: $color-brand;
    color: $color-on-dark;
  }
}

.tp__tab-count {
  min-width: 18px;
  padding: 0 5px;
  border-radius: $radius-pill;
  background: $color-surface-sunken;
  color: $color-text-faint;
  font-size: $font-size-xs;
  line-height: 18px;
  text-align: center;
}

.tp__tab--active .tp__tab-count {
  background: rgba(255, 255, 255, 0.22);
  color: $color-on-dark;
}

.tp__create {
  padding: 7px $spacing-md;
  border: none;
  border-radius: $radius-control;
  background: $color-brand;
  color: $color-on-dark;
  font-size: $font-size-sm;
  font-family: inherit;
  cursor: pointer;

  &:hover {
    background: $color-brand-strong;
  }
}

.tp__card {
  border: 1px solid $color-border;
  border-radius: $radius-card;
  background: $color-surface;
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.tp__card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
}

.tp__card-info {
  display: flex;
  align-items: baseline;
  gap: $spacing-sm;
  min-width: 0;
}

.tp__card-name {
  font-size: $font-size-sm;
  font-weight: $font-weight-medium;
  color: $color-text;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tp__card-type {
  font-size: $font-size-xs;
  color: $color-text-faint;
}

.tp__status {
  padding: 2px 10px;
  border-radius: $radius-pill;
  background: $color-surface-sunken;
  color: $color-text-soft;
  font-size: $font-size-xs;

  &--running { background: rgba(8, 145, 178, 0.12); color: $info-color; }
  &--pending { background: rgba(217, 119, 6, 0.12); color: $warning-color; }
  &--completed { background: rgba(5, 150, 105, 0.12); color: $success-color; }
  &--failed { background: rgba(220, 38, 38, 0.1); color: $error-color; }
  &--cancelled { background: $color-surface-sunken; color: $color-text-faint; }
}

.tp__progress {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.tp__progress-track {
  flex: 1;
  height: 6px;
  border-radius: $radius-pill;
  background: $color-surface-sunken;
  overflow: hidden;
}

.tp__progress-fill {
  height: 100%;
  background: $color-brand;
  transition: width $transition-normal;
}

.tp__progress-text {
  font-size: $font-size-xs;
  color: $color-text-faint;
  font-variant-numeric: tabular-nums;
}

.tp__card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
}

.tp__date {
  font-size: $font-size-xs;
  color: $color-text-faint;
}

.tp__actions {
  display: flex;
  gap: $spacing-xs;
}

.tp__action {
  padding: 4px $spacing-sm;
  border: 1px solid $color-border;
  border-radius: $radius-control;
  background: transparent;
  font-size: $font-size-xs;
  font-family: inherit;
  color: $color-text-soft;
  cursor: pointer;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }

  &--danger:hover {
    border-color: $error-color;
    color: $error-color;
  }
}

.tp__state {
  padding: $spacing-xl;
  text-align: center;
  color: $color-text-faint;
  font-size: $font-size-sm;
}

.tp__state--error {
  color: $error-color;
}
</style>
