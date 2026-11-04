<template>
  <div class="er">
    <Sidebar />
    <main class="er__main">
      <div class="page">
        <PageHeader
          icon="🗂️"
          title="评价记录"
          subtitle="结果、任务与并排对比：评价跑过什么、结论是什么、不同分子差在哪"
        />

        <TabBar v-model="currentTab" :tabs="TABS" />

        <RecordsPanel v-if="activeTab === 'records'" />
        <TasksPanel v-else-if="activeTab === 'tasks'" />
        <ComparePanel v-else-if="activeTab === 'compare'" />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
/**
 * 评价体系 · 评价记录。
 *
 * 与「评价工作台」分开放：工作台是**做评价**的地方（表单、指标口径、能力边界），
 * 记录是**看结果**的地方。两者混在一页会让「我只是想查一条历史结果」也要先
 * 跨过一张发起表单。
 */
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Sidebar from '@/components/Sidebar.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import TabBar from '@/components/ui/TabBar.vue'
import RecordsPanel from '@/components/evaluation/RecordsPanel.vue'
import TasksPanel from '@/components/evaluation/TasksPanel.vue'
import ComparePanel from '@/components/evaluation/ComparePanel.vue'

const TABS = [
  { key: 'records', label: '评价结果', icon: '📈' },
  { key: 'tasks', label: '评价任务', icon: '📋' },
  { key: 'compare', label: '结果对比', icon: '⚖️' }
]

const TAB_KEYS = TABS.map((t) => t.key)

const route = useRoute()
const router = useRouter()

function resolveTab(): string {
  const raw = route.query.tab
  const key = typeof raw === 'string' ? raw : ''
  return TAB_KEYS.includes(key) ? key : 'records'
}

const activeTab = ref(resolveTab())

watch(
  () => route.query.tab,
  () => {
    const next = resolveTab()
    if (next !== activeTab.value) activeTab.value = next
  }
)

const currentTab = computed({
  get: () => activeTab.value,
  set: (value: string) => {
    activeTab.value = value
    if (resolveTab() !== value) {
      router.replace({ path: route.path, query: { ...route.query, tab: value } })
    }
  }
})
</script>

<style lang="scss" scoped>
.er {
  display: flex;
  min-height: 100vh;
  background: $bg-secondary;
}

.er__main {
  flex: 1;
  padding: $spacing-lg $spacing-xl $spacing-2xl;
  min-width: 0;
}

.page {
  display: flex;
  flex-direction: column;
  max-width: $content-max-width;
  margin: 0 auto;
}
</style>
