/**
 * 项目流程上下文（需求分析 → 候选生成 → 评估与编辑 → 决策）
 * ============================================================================
 * 为什么需要一个跨页 store：这四个步骤原来由页面里的横向流程条（`FlowSteps`）承载，
 * 而流程条要显示「当前在第几步」就必须知道 projectId、可评估的 candidateId、
 * 基线是否冻结。这些信息分散在四个页面各自的响应里，页面之间又不共享。
 *
 * 现在步骤条搬到了左侧导航 —— 导航是常驻组件，跨路由不重建，更需要一个明确的
 * 数据来源。约定：**页面负责写，侧栏负责读**。页面加载完自己的数据后调用
 * `enter()` / `setReady()` / `setCandidate()`，侧栏不做任何请求。
 *
 * 这样做的好处是侧栏不产生额外网络请求（原来每个页面都要为流程条多查一次），
 * 代价是页面必须先渲染一次 —— 侧栏在数据到位前显示「未加载」而不是假装就绪。
 */
import { defineStore } from 'pinia'

export const useFlowStore = defineStore('flow', {
  state: () => ({
    /** 当前项目；为空表示没在项目里（侧栏不显示流程组） */
    projectId: null as number | null,
    projectName: '',
    /** 可进入评估的候选 id；为空时「评估与编辑」置灰 */
    candidateId: null as number | null,
    /** 基线是否已冻结（步骤 1 的前提） */
    baselineReady: false,
    /** 是否已开启轮次（步骤 2 的前提） */
    roundReady: false
  }),

  getters: {
    inProject: (state) => state.projectId != null,
    evaluationReady: (state) => state.projectId != null && state.candidateId != null,
    /** 组标题右侧的副标题：项目名，太长由 CSS 截断 */
    contextLabel: (state) => state.projectName || (state.projectId != null ? `项目 #${state.projectId}` : '')
  },

  actions: {
    /** 进入某个项目。换了项目就清掉上一个项目的候选，避免跨项目串上下文 */
    enter(projectId: number | null, projectName = '') {
      if (projectId !== this.projectId) {
        this.candidateId = recallCandidate(projectId)
        this.baselineReady = false
        this.roundReady = false
      }
      this.projectId = projectId
      if (projectName) this.projectName = projectName
    },

    /** 页面把自己的就绪状态同步给侧栏（不传表示保持原值） */
    setReady(baselineReady?: boolean, roundReady?: boolean) {
      if (baselineReady !== undefined) this.baselineReady = baselineReady
      if (roundReady !== undefined) this.roundReady = roundReady
    },

    setCandidate(candidateId: number | null) {
      this.candidateId = candidateId
      rememberCandidate(this.projectId, candidateId)
    },

    /** 离开项目语境（如进入分子精修台但没选项目） */
    leave() {
      this.projectId = null
      this.projectName = ''
      this.candidateId = null
      this.baselineReady = false
      this.roundReady = false
    }
  }
})

/**
 * 候选 id 按项目记在 sessionStorage 里。
 *
 * 为什么需要：决策页 / 库页刷新后 store 是空的，而 projectId 只能从 URL 拿到，
 * 于是「评估与编辑」这一步会以「还没有候选可以评估」的灰色出现 —— 可这个项目
 * 明明有候选。记住最后一个打开过的候选，刷新后这一步就还是亮的。
 *
 * 只活到标签页关闭：候选属于项目内数据，不该跨会话残留。
 */
const KEY_PREFIX = 'flow_candidate_'

function rememberCandidate(projectId: number | null, candidateId: number | null) {
  if (projectId == null) return
  try {
    if (candidateId == null) sessionStorage.removeItem(`${KEY_PREFIX}${projectId}`)
    else sessionStorage.setItem(`${KEY_PREFIX}${projectId}`, String(candidateId))
  } catch {
    // 隐私模式下 sessionStorage 可能不可用：纯体验降级，不影响功能
  }
}

function recallCandidate(projectId: number | null): number | null {
  if (projectId == null) return null
  try {
    const raw = sessionStorage.getItem(`${KEY_PREFIX}${projectId}`)
    const n = raw == null ? NaN : Number(raw)
    return Number.isFinite(n) && n > 0 ? n : null
  } catch {
    return null
  }
}
