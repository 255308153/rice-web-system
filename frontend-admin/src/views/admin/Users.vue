<template>
  <div class="admin-users">
    <div class="hero">
      <h2>用户管理</h2>
      <p>支持按角色筛选、禁用/启用账号、重置密码与导出用户数据。</p>
    </div>

    <div class="filters">
      <select v-model="roleFilter" @change="reloadFirstPage">
        <option value="">全部角色</option>
        <option value="USER">普通用户</option>
        <option value="MERCHANT">商户</option>
        <option value="EXPERT">专家</option>
        <option value="ADMIN">管理员</option>
      </select>
      <select v-model.number="statusFilter" @change="reloadFirstPage">
        <option :value="-1">全部状态</option>
        <option :value="1">正常</option>
        <option :value="0">禁用</option>
      </select>
      <input
        v-model.trim="keyword"
        placeholder="搜索用户名/手机号"
        @keyup.enter="reloadFirstPage"
      />
      <button class="btn-search" @click="reloadFirstPage">查询</button>
      <button @click="exportUsers" class="btn-export">导出数据</button>
    </div>

    <div class="user-list">
      <div class="user-item" v-for="user in users" :key="user.id">
        <div class="user-info">
          <div class="line"><strong>{{ user.username }}</strong></div>
          <div class="line">用户ID：{{ user.id }}</div>
          <div class="line">手机：{{ user.phone || '-' }}</div>
          <div class="line">
            角色：<span class="role-tag">{{ formatRole(user.role) }}</span>
            <span class="status-tag" :class="'status-' + user.status">{{ user.status === 1 ? '正常' : '禁用' }}</span>
          </div>
          <div class="line">创建时间：{{ formatTime(user.createTime) }}</div>
        </div>
        <div class="user-actions">
          <button @click="toggleStatus(user)" :class="user.status === 1 ? 'btn-disable' : 'btn-enable'">
            {{ user.status === 1 ? '禁用' : '启用' }}
          </button>
          <button @click="resetPassword(user)" class="btn-reset">重置密码</button>
        </div>
      </div>
      <div v-if="users.length === 0" class="empty">暂无用户数据</div>
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

const users = ref([])
const roleFilter = ref('')
const statusFilter = ref(-1)
const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

const totalPages = computed(() => {
  const pages = Math.ceil(total.value / size.value)
  return pages > 0 ? pages : 1
})

const buildQuery = (base = {}) => {
  const query = new URLSearchParams()
  Object.entries(base).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, String(value))
    }
  })
  if (roleFilter.value) query.append('role', roleFilter.value)
  if (statusFilter.value >= 0) query.append('status', String(statusFilter.value))
  if (keyword.value) query.append('keyword', keyword.value)
  const raw = query.toString()
  return raw ? `?${raw}` : ''
}

const formatRole = (role) => {
  if (role === 'USER') return '普通用户'
  if (role === 'MERCHANT') return '商户'
  if (role === 'EXPERT') return '专家'
  if (role === 'ADMIN') return '管理员'
  return role || '-'
}

const formatTime = (time) => {
  if (!time) return '-'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
  }
  return String(time)
}

const loadUsers = async () => {
  const query = buildQuery({ page: page.value, size: size.value })
  const res = await request.get(`/admin/users${query}`)
  if (res.code === 200) {
    users.value = res.data.records || []
    total.value = res.data.total || users.value.length
  }
}

const reloadFirstPage = async () => {
  page.value = 1
  await loadUsers()
}

const changePage = async (nextPage) => {
  page.value = nextPage
  await loadUsers()
}

const toggleStatus = async (user) => {
  const nextStatus = user.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '禁用'
  if (!confirm(`确认${actionText}用户 ${user.username} 吗？`)) return

  const res = await request.put(`/admin/users/${user.id}/status`, { status: nextStatus })
  if (res.code === 200) {
    alert('操作成功')
    await loadUsers()
    return
  }
  alert(res.message || '操作失败')
}

const resetPassword = async (user) => {
  if (!confirm(`确认重置用户 ${user.username} 的密码为 123456 吗？`)) return
  const res = await request.put(`/admin/users/${user.id}/password`, { password: '123456' })
  if (res.code === 200) {
    alert('密码已重置为 123456')
    return
  }
  alert(res.message || '重置失败')
}

const exportUsers = async () => {
  const query = buildQuery()
  const res = await request.get(`/admin/users/export${query}`)
  if (res.code !== 200) {
    alert(res.message || '导出失败')
    return
  }

  const rows = res.data || []
  const csv = ['用户ID,用户名,手机号,角色,状态,创建时间']
    .concat(rows.map(u => [
      u.id,
      u.username || '',
      u.phone || '',
      formatRole(u.role),
      u.status === 1 ? '正常' : '禁用',
      formatTime(u.createTime)
    ].map(item => `"${String(item).replace(/"/g, '""')}"`).join(',')))
    .join('\n')

  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `用户数据_${new Date().toLocaleDateString('zh-CN')}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}

onMounted(loadUsers)
</script>

<style scoped>
.admin-users {
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

.filters {
  margin-bottom: 14px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.filters select,
.filters input {
  padding: 9px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  color: #475467;
}

.filters input {
  min-width: 220px;
}

.btn-search,
.btn-export {
  padding: 9px 16px;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
}

.btn-search {
  background: var(--brand);
}

.btn-export {
  background: #10b981;
}

.user-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.user-item {
  background: #fff;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.user-info {
  line-height: 1.8;
  color: #334155;
}

.line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.role-tag,
.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.role-tag {
  background: #eef6ff;
  border: 1px solid #dbeafe;
  color: #1d4ed8;
}

.status-tag.status-1 {
  background: #dcfce7;
  color: #166534;
}

.status-tag.status-0 {
  background: #fee2e2;
  color: #991b1b;
}

.user-actions {
  display: flex;
  gap: 8px;
}

.btn-disable,
.btn-enable,
.btn-reset {
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: #fff;
  font-weight: 700;
}

.btn-disable {
  background: #ef4444;
}

.btn-enable {
  background: #10b981;
}

.btn-reset {
  background: #f59e0b;
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
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.pager button {
  padding: 6px 10px;
  border: 1px solid var(--line-soft);
  background: #fff;
  border-radius: 8px;
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

  .filters {
    flex-direction: column;
  }

  .filters input,
  .filters select,
  .btn-search,
  .btn-export {
    width: 100%;
  }

  .user-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
