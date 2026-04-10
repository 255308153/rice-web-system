<template>
  <div class="admin-audits">
    <div class="hero">
      <h2>资质审核</h2>
      <p>支持按角色和状态筛选，完善审核备注与审核时间记录。</p>
    </div>

    <div class="filters">
      <select v-model="roleFilter" @change="reloadFirstPage">
        <option value="MERCHANT">商户申请</option>
        <option value="EXPERT">专家申请</option>
        <option value="">全部角色</option>
      </select>
      <select v-model.number="statusFilter" @change="reloadFirstPage">
        <option :value="0">待审核</option>
        <option :value="1">已通过</option>
        <option :value="2">已拒绝</option>
        <option :value="-1">全部状态</option>
      </select>
      <button class="btn-refresh" @click="loadAudits">刷新</button>
    </div>

    <div class="audit-list">
      <div class="audit-item" v-for="audit in audits" :key="audit.id">
        <div class="audit-main">
          <div class="audit-info">
            <div>申请ID：{{ audit.id }}</div>
            <div>用户：{{ audit.username || `用户#${audit.userId}` }}（ID: {{ audit.userId }}）</div>
            <div>手机号：{{ audit.phone || '-' }}</div>
            <div>当前角色：{{ audit.currentRole || '-' }}</div>
            <div>申请角色：{{ formatRole(audit.role) }}</div>
            <div>状态：<span class="status" :class="'status-' + audit.status">{{ getStatusText(audit.status) }}</span></div>
            <div>申请时间：{{ formatTime(audit.createTime) }}</div>
            <div v-if="audit.auditTime">审核时间：{{ formatTime(audit.auditTime) }}</div>
            <div v-if="audit.auditRemark">审核备注：{{ audit.auditRemark }}</div>
          </div>
          <div class="credentials">
            <h4>资质材料</h4>
            <p>{{ audit.credentials || '未填写' }}</p>
          </div>
        </div>
        <div v-if="audit.status === 0" class="audit-actions">
          <button @click="openAuditDialog(audit, 1)" class="btn-approve">通过</button>
          <button @click="openAuditDialog(audit, 2)" class="btn-reject">拒绝</button>
        </div>
      </div>
      <div v-if="audits.length === 0" class="empty">暂无审核申请</div>
    </div>

    <div class="pager">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>第 {{ page }} 页 / 共 {{ totalPages }} 页（{{ total }} 条）</span>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </div>

    <div v-if="auditDialog.visible" class="dialog">
      <div class="dialog-content">
        <h3>{{ auditDialog.action === 1 ? '通过申请' : '拒绝申请' }}</h3>
        <p class="dialog-line">申请ID：{{ auditDialog.target?.id }}</p>
        <p class="dialog-line">用户：{{ auditDialog.target?.username || `用户#${auditDialog.target?.userId}` }}</p>
        <p class="dialog-line">角色：{{ formatRole(auditDialog.target?.role) }}</p>
        <textarea
          v-model.trim="auditDialog.remark"
          :placeholder="auditDialog.action === 1 ? '可选：填写审核备注（例如：资料完整，审核通过）' : '必填：请输入拒绝原因'"
          rows="4"
        />
        <div class="dialog-actions">
          <button class="btn-save" @click="submitAudit">确认提交</button>
          <button class="btn-cancel" @click="closeAuditDialog">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import request from '../../utils/request'

const route = useRoute()
const audits = ref([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const statusFilter = ref(0)
const roleFilter = ref('MERCHANT')
const auditDialog = ref({
  visible: false,
  action: 1,
  target: null,
  remark: ''
})

const totalPages = computed(() => {
  const pages = Math.ceil(total.value / size.value)
  return pages > 0 ? pages : 1
})

const getStatusText = (status) => {
  if (status === 0) return '待审核'
  if (status === 1) return '已通过'
  return '已拒绝'
}

const formatRole = (role) => {
  if (role === 'MERCHANT') return '商户'
  if (role === 'EXPERT') return '专家'
  if (role === 'ADMIN') return '管理员'
  if (role === 'USER') return '普通用户'
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

const normalizeRoleFilter = (value) => {
  const role = String(value || '').trim().toUpperCase()
  if (role === 'EXPERT') return 'EXPERT'
  if (role === 'MERCHANT') return 'MERCHANT'
  return 'MERCHANT'
}

const normalizeStatusFilter = (value) => {
  const status = Number(value)
  if ([0, 1, 2, -1].includes(status)) return status
  return 0
}

const loadAudits = async () => {
  let url = `/admin/audits?page=${page.value}&size=${size.value}&status=${statusFilter.value}`
  if (roleFilter.value) {
    url += `&role=${encodeURIComponent(roleFilter.value)}`
  }
  const res = await request.get(url)
  if (res.code === 200) {
    audits.value = res.data.records || []
    total.value = res.data.total || audits.value.length
  }
}

const reloadFirstPage = () => {
  page.value = 1
  loadAudits()
}

const changePage = (nextPage) => {
  page.value = nextPage
  loadAudits()
}

const openAuditDialog = (target, action) => {
  auditDialog.value = {
    visible: true,
    action,
    target,
    remark: action === 1 ? '审核通过' : ''
  }
}

const closeAuditDialog = () => {
  auditDialog.value.visible = false
  auditDialog.value.target = null
  auditDialog.value.remark = ''
}

const submitAudit = async () => {
  const target = auditDialog.value.target
  if (!target) return
  if (auditDialog.value.action === 2 && !auditDialog.value.remark) {
    alert('拒绝申请时请填写审核备注')
    return
  }

  const res = await request.put(`/admin/audits/${target.id}`, {
    status: auditDialog.value.action,
    remark: auditDialog.value.remark || ''
  })
  if (res.code === 200) {
    alert('审核操作成功')
    closeAuditDialog()
    await loadAudits()
    return
  }
  alert(res.message || '审核操作失败')
}

watch(
  () => [route.query.role, route.query.status],
  async () => {
    roleFilter.value = normalizeRoleFilter(route.query.role)
    statusFilter.value = normalizeStatusFilter(route.query.status)
    page.value = 1
    await loadAudits()
  },
  { immediate: true }
)
</script>

<style scoped>
.admin-audits {
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
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  padding: 10px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid var(--line-soft);
}

.filters select {
  min-width: 120px;
  padding: 8px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.btn-refresh {
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  cursor: pointer;
  font-weight: 700;
}

.audit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.audit-item {
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

.audit-main {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.audit-info {
  line-height: 1.8;
  color: #334155;
  font-size: 13px;
}

.credentials {
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  padding: 10px;
  background: #fbfdff;
}

.credentials h4 {
  margin-bottom: 6px;
  font-size: 14px;
  color: #0f172a;
}

.credentials p {
  color: #475467;
  line-height: 1.7;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}

.status {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.status-0 {
  background: #fef3c7;
  color: #92400e;
}

.status-1 {
  background: #dcfce7;
  color: #166534;
}

.status-2 {
  background: #fee2e2;
  color: #991b1b;
}

.audit-actions {
  display: flex;
  gap: 8px;
}

.btn-approve, .btn-reject {
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: #fff;
  font-weight: 700;
}

.btn-approve {
  background: #10b981;
}

.btn-reject {
  background: #ef4444;
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

.dialog {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.dialog-content {
  width: min(560px, calc(100vw - 28px));
  background: #fff;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  padding: 18px;
}

.dialog-content h3 {
  margin-bottom: 8px;
  color: #0f172a;
}

.dialog-line {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 4px;
}

.dialog-content textarea {
  width: 100%;
  margin-top: 8px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 10px;
  background: #fbfdff;
}

.dialog-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.btn-save,
.btn-cancel {
  flex: 1;
  border: none;
  border-radius: 8px;
  padding: 10px;
  cursor: pointer;
  font-weight: 700;
}

.btn-save {
  background: var(--brand);
  color: #fff;
}

.btn-cancel {
  background: #e5e7eb;
}

@media (max-width: 768px) {
  h2 {
    font-size: 24px;
  }

  .filters {
    flex-wrap: wrap;
  }

  .audit-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .audit-main {
    grid-template-columns: 1fr;
  }

  .audit-actions {
    width: 100%;
  }

  .audit-actions button {
    flex: 1;
  }

  .pager {
    justify-content: space-between;
  }
}
</style>
