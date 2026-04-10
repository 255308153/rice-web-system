<template>
  <div class="admin-posts">
    <div class="hero">
      <h2>内容审核</h2>
      <p>帖子已切换为“正常 / 异常 / 下架”三态审核，其中异常通常来自 AI 预审核命中风险。</p>
    </div>

    <div class="tabs">
      <button :class="{ active: tab === 'post' }" @click="switchTab('post')">帖子审核</button>
      <button :class="{ active: tab === 'comment' }" @click="switchTab('comment')">评论审核</button>
    </div>

    <div class="filters">
      <input v-model.trim="keyword" placeholder="搜索内容关键词" @keyup.enter="reloadFirstPage" />
      <select v-model.number="statusFilter" @change="reloadFirstPage">
        <option :value="-1">全部状态</option>
        <option v-for="item in currentStatusOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <button class="btn-search" @click="reloadFirstPage">查询</button>
      <button class="btn-refresh" @click="loadCurrent">刷新</button>
    </div>

    <div v-if="tab === 'post'" class="list">
      <div v-for="item in postList" :key="item.id" class="card">
        <div class="main">
          <div class="title">{{ item.title }}</div>
          <div class="meta">
            <span>帖子ID：{{ item.id }}</span>
            <span>作者：{{ item.username || `用户#${item.userId}` }}</span>
            <span>分类：{{ item.category || '综合交流' }}</span>
            <span>时间：{{ formatTime(item.createTime) }}</span>
          </div>
          <div class="content">{{ item.content }}</div>
          <div class="meta">
            <span class="status" :class="`status-${item.status ?? 1}`">{{ getPostStatusText(item.status) }}</span>
            <span v-if="item.auditRemark">AI审核建议：{{ item.auditRemark }}</span>
          </div>
        </div>
        <div class="actions">
          <button v-if="(item.status ?? 1) !== 1" class="btn-on" @click="updatePostStatus(item, 1)">设为正常</button>
          <button v-if="(item.status ?? 1) !== 0" class="btn-warn" @click="updatePostStatus(item, 0)">设为异常</button>
          <button v-if="(item.status ?? 1) !== 2" class="btn-off" @click="updatePostStatus(item, 2)">下架</button>
        </div>
      </div>
      <div v-if="postList.length === 0" class="empty">暂无帖子数据</div>
    </div>

    <div v-if="tab === 'comment'" class="list">
      <div v-for="item in commentList" :key="item.id" class="card">
        <div class="main">
          <div class="title">评论 #{{ item.id }}</div>
          <div class="meta">
            <span>帖子：{{ item.postTitle || `帖子#${item.postId}` }}</span>
            <span>评论人：{{ item.username || `用户#${item.userId}` }}</span>
            <span>时间：{{ formatTime(item.createTime) }}</span>
          </div>
          <div class="content">{{ item.content }}</div>
          <div class="meta">
            <span class="status" :class="'status-comment-' + (item.status ?? 1)">{{ (item.status ?? 1) === 1 ? '正常' : '下架' }}</span>
            <span v-if="item.auditRemark">备注：{{ item.auditRemark }}</span>
          </div>
        </div>
        <div class="actions">
          <button
            :class="(item.status ?? 1) === 1 ? 'btn-off' : 'btn-on'"
            @click="toggleCommentStatus(item)"
          >
            {{ (item.status ?? 1) === 1 ? '下架' : '恢复' }}
          </button>
        </div>
      </div>
      <div v-if="commentList.length === 0" class="empty">暂无评论数据</div>
    </div>

    <div class="pager">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ totalPages }} 页（{{ total }} 条）</span>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '../../utils/request'

const tab = ref('post')
const keyword = ref('')
const statusFilter = ref(-1)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const postList = ref([])
const commentList = ref([])

const postStatusOptions = [
  { value: 1, label: '正常' },
  { value: 0, label: '异常' },
  { value: 2, label: '下架' }
]

const commentStatusOptions = [
  { value: 1, label: '正常' },
  { value: 0, label: '下架' }
]

const currentStatusOptions = computed(() => (tab.value === 'post' ? postStatusOptions : commentStatusOptions))

const totalPages = computed(() => {
  const pages = Math.ceil(total.value / size.value)
  return pages > 0 ? pages : 1
})

const formatTime = (time) => {
  if (!time) return '-'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) return parsed.toLocaleString('zh-CN')
  return String(time)
}

const getPostStatusText = (status) => {
  if (Number(status) === 0) return '异常'
  if (Number(status) === 2) return '下架'
  return '正常'
}

const loadPostAudits = async () => {
  const query = new URLSearchParams({
    page: String(page.value),
    size: String(size.value),
    status: String(statusFilter.value)
  })
  if (keyword.value) query.append('keyword', keyword.value)
  const res = await request.get(`/admin/content/posts?${query.toString()}`)
  if (res.code === 200) {
    postList.value = res.data.records || []
    total.value = res.data.total || postList.value.length
  }
}

const loadCommentAudits = async () => {
  const query = new URLSearchParams({
    page: String(page.value),
    size: String(size.value),
    status: String(statusFilter.value)
  })
  if (keyword.value) query.append('keyword', keyword.value)
  const res = await request.get(`/admin/content/comments?${query.toString()}`)
  if (res.code === 200) {
    commentList.value = res.data.records || []
    total.value = res.data.total || commentList.value.length
  }
}

const loadCurrent = async () => {
  if (tab.value === 'post') {
    await loadPostAudits()
    return
  }
  await loadCommentAudits()
}

const reloadFirstPage = async () => {
  page.value = 1
  await loadCurrent()
}

const changePage = async (next) => {
  page.value = next
  await loadCurrent()
}

const switchTab = async (nextTab) => {
  tab.value = nextTab
  statusFilter.value = -1
  page.value = 1
  total.value = 0
  await loadCurrent()
}

const updatePostStatus = async (item, nextStatus) => {
  const labelMap = { 0: '设为异常', 1: '设为正常', 2: '下架' }
  const remarkPlaceholderMap = {
    0: item.auditRemark || 'AI预审命中风险，待人工复核',
    1: '人工复核通过，允许发布',
    2: '管理员下架处理'
  }
  if (!confirm(`确认将帖子「${item.title}」${labelMap[nextStatus]}吗？`)) return
  const remark = prompt('审核备注（可选）', remarkPlaceholderMap[nextStatus]) || ''
  const res = await request.put(`/admin/content/posts/${item.id}/status`, {
    status: nextStatus,
    remark
  })
  if (res.code === 200) {
    await loadCurrent()
    return
  }
  alert(res.message || `${labelMap[nextStatus]}失败`)
}

const toggleCommentStatus = async (item) => {
  const nextStatus = (item.status ?? 1) === 1 ? 0 : 1
  const actionText = nextStatus === 0 ? '下架' : '恢复'
  if (!confirm(`确认${actionText}该评论吗？`)) return
  const remark = prompt('审核备注（可选）', '') || ''
  const res = await request.put(`/admin/content/comments/${item.id}/status`, {
    status: nextStatus,
    remark
  })
  if (res.code === 200) {
    await loadCurrent()
    return
  }
  alert(res.message || `${actionText}失败`)
}

onMounted(loadCurrent)
</script>

<style scoped>
.admin-posts {
  max-width: 1180px;
  margin: 0 auto;
}

h2 {
  margin-bottom: 4px;
  font-size: 30px;
  color: #0f172a;
}

.hero {
  margin-bottom: 12px;
}

.hero p {
  color: #667085;
  font-size: 14px;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.tabs button {
  padding: 9px 16px;
  border: 1px solid var(--line-soft);
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  color: #475467;
}

.tabs button.active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

.filters {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.filters input,
.filters select {
  padding: 9px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
}

.filters input {
  min-width: 260px;
}

.btn-search,
.btn-refresh {
  padding: 9px 16px;
  border: none;
  border-radius: 8px;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
}

.btn-search {
  background: var(--brand);
}

.btn-refresh {
  background: #64748b;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.card {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  padding: 14px;
  display: flex;
  gap: 12px;
  justify-content: space-between;
}

.main {
  flex: 1;
}

.title {
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 6px;
}

.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.content {
  line-height: 1.7;
  color: #475467;
  margin-bottom: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.actions {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  flex-shrink: 0;
  flex-wrap: wrap;
}

.btn-off,
.btn-on,
.btn-warn {
  border: none;
  border-radius: 8px;
  color: #fff;
  font-weight: 700;
  cursor: pointer;
  padding: 8px 14px;
}

.btn-off {
  background: #ef4444;
}

.btn-on {
  background: #10b981;
}

.btn-warn {
  background: #f59e0b;
}

.status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-1 {
  color: #166534;
  background: #dcfce7;
}

.status-0 {
  color: #92400e;
  background: #fef3c7;
}

.status-2 {
  color: #991b1b;
  background: #fee2e2;
}

.status-comment-1 {
  color: #166534;
  background: #dcfce7;
}

.status-comment-0 {
  color: #991b1b;
  background: #fee2e2;
}

.empty {
  text-align: center;
  padding: 24px;
  color: #98a2b3;
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  background: #fbfdff;
}

.pager {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  align-items: center;
  color: #64748b;
  font-size: 13px;
}

.pager button {
  padding: 6px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.pager button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 768px) {
  h2 {
    font-size: 24px;
  }

  .tabs {
    flex-wrap: wrap;
  }

  .filters {
    flex-direction: column;
  }

  .filters input,
  .filters select,
  .btn-search,
  .btn-refresh {
    width: 100%;
  }

  .card {
    flex-direction: column;
  }
}
</style>
