<template>
  <div class="merchant-orders">
    <div class="hero">
      <h2>订单管理</h2>
      <p>查看全部订单、按状态筛选、发货录单和处理退款申请。</p>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-value">{{ stats.daySalesVolume }}</div>
        <div class="stat-label">日销量</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">¥{{ formatMoney(stats.daySalesAmount) }}</div>
        <div class="stat-label">日销售额</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.monthSalesVolume }}</div>
        <div class="stat-label">月销量</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">¥{{ formatMoney(stats.monthSalesAmount) }}</div>
        <div class="stat-label">月销售额</div>
      </div>
    </div>

    <div class="panel orders-panel">
      <div class="panel-head">
        <h3>订单列表</h3>
        <button class="btn-ghost" @click="reloadAll" :disabled="loadingOrders">刷新</button>
      </div>

      <div class="status-tabs">
        <button
          v-for="tab in statusTabs"
          :key="tab.value"
          :class="['tab-btn', { active: statusFilter === tab.value }]"
          @click="changeStatus(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="order-list" v-if="orders.length > 0">
        <div class="order-card" v-for="order in orders" :key="order.id">
          <div class="order-header">
            <span class="order-no">订单号：{{ order.orderNo }}</span>
            <span class="status" :class="'status-' + order.status">{{ getOrderStatusText(order.status) }}</span>
          </div>

          <div class="order-info">
            <div>金额：<strong class="price">¥{{ formatMoney(order.totalPrice) }}</strong></div>
            <div>时间：{{ formatTime(order) }}</div>
            <div>用户ID：{{ order.userId }}</div>
          </div>

          <div v-if="order.status === 1" class="ship-form">
            <input
              v-model.trim="shipData[order.id].company"
              placeholder="物流公司（可选）"
            />
            <input
              v-model.trim="shipData[order.id].trackingNumber"
              placeholder="物流单号（必填）"
            />
            <button @click="shipOrder(order)">发货</button>
          </div>
        </div>
      </div>
      <div v-else class="empty">暂无订单数据</div>
    </div>

    <div class="panel refunds-panel">
      <div class="panel-head">
        <h3>退款申请</h3>
        <div class="refund-filter">
          <label>状态</label>
          <select v-model.number="refundStatusFilter" @change="loadRefunds">
            <option :value="-1">全部</option>
            <option :value="0">待处理</option>
            <option :value="1">已同意</option>
            <option :value="2">已拒绝</option>
          </select>
        </div>
      </div>

      <div class="refund-list" v-if="refunds.length > 0">
        <div class="refund-card" v-for="refund in refunds" :key="refund.id">
          <div class="refund-head">
            <div class="refund-title">订单 {{ refund.orderNo || ('#' + refund.orderId) }}</div>
            <span class="refund-status" :class="'refund-' + refund.status">{{ getRefundStatusText(refund.status) }}</span>
          </div>

          <div class="refund-info">
            <div>申请人：{{ refund.username || ('用户#' + refund.userId) }}</div>
            <div>退款金额：¥{{ formatMoney(refund.amount) }}</div>
            <div>申请时间：{{ formatTime(refund.createTime) }}</div>
            <div>原因：{{ refund.reason || '-' }}</div>
            <div v-if="refund.merchantRemark">商户说明：{{ refund.merchantRemark }}</div>
          </div>

          <div v-if="refund.status === 0" class="refund-actions">
            <textarea
              v-model.trim="refundRemarks[refund.id]"
              rows="2"
              placeholder="处理说明（拒绝时必填）"
            ></textarea>
            <div class="action-row">
              <button
                class="btn-approve"
                @click="processRefund(refund, 1)"
                :disabled="processingRefundId === refund.id"
              >同意退款</button>
              <button
                class="btn-reject"
                @click="processRefund(refund, 2)"
                :disabled="processingRefundId === refund.id"
              >拒绝退款</button>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="empty">暂无退款申请</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import request from '../../utils/request'

const statusTabs = [
  { label: '全部', value: -1 },
  { label: '待付款', value: 0 },
  { label: '待发货', value: 1 },
  { label: '待收货', value: 2 },
  { label: '已完成', value: 3 },
  { label: '售后', value: 4 }
]

const stats = ref({
  daySalesVolume: 0,
  daySalesAmount: 0,
  monthSalesVolume: 0,
  monthSalesAmount: 0
})
const statusFilter = ref(-1)
const refundStatusFilter = ref(-1)
const loadingOrders = ref(false)
const processingRefundId = ref(null)
const orders = ref([])
const refunds = ref([])
const shipData = ref({})
const refundRemarks = ref({})
let refreshTimer = null

const getOrderStatusText = (status) => {
  const map = {
    0: '待付款',
    1: '待发货',
    2: '待收货',
    3: '已完成',
    4: '售后'
  }
  return map[status] || '未知'
}

const getRefundStatusText = (status) => {
  const map = {
    0: '待处理',
    1: '已同意',
    2: '已拒绝'
  }
  return map[status] || '未知'
}

const getOrderTime = (order) => {
  if (!order) return ''
  const direct = order.createTime || order.create_time || order.orderTime || order.order_time
  if (direct) return direct

  const match = String(order.orderNo || '').match(/\d{13}/)
  return match ? Number(match[0]) : ''
}

const formatMoney = (amount) => {
  const value = Number(amount || 0)
  return value.toFixed(2)
}

const formatTime = (value) => {
  const time = typeof value === 'object' && value !== null ? getOrderTime(value) : value
  if (!time) return '-'

  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
  }

  if (typeof time === 'string') {
    const fallback = new Date(time.replace(/-/g, '/'))
    if (!Number.isNaN(fallback.getTime())) {
      return fallback.toLocaleString('zh-CN')
    }
  }

  return String(time)
}

const buildQuery = (params) => {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, String(value))
    }
  })
  const raw = query.toString()
  return raw ? `?${raw}` : ''
}

const initShipData = () => {
  const next = {}
  orders.value.forEach(order => {
    const old = shipData.value[order.id] || {}
    next[order.id] = {
      company: old.company || '',
      trackingNumber: old.trackingNumber || ''
    }
  })
  shipData.value = next
}

const loadOrders = async () => {
  loadingOrders.value = true
  try {
    const query = buildQuery({ status: statusFilter.value >= 0 ? statusFilter.value : '' })
    const res = await request.get(`/merchant/orders${query}`)
    if (res.code === 200) {
      orders.value = (res.data || []).map(item => ({
        ...item,
        createTime: getOrderTime(item)
      }))
      initShipData()
    }
  } catch (e) {
    console.error('加载订单失败', e)
  } finally {
    loadingOrders.value = false
  }
}

const loadStats = async () => {
  try {
    const res = await request.get('/merchant/orders/stats')
    if (res.code === 200 && res.data) {
      stats.value = {
        daySalesVolume: Number(res.data.daySalesVolume || 0),
        daySalesAmount: Number(res.data.daySalesAmount || 0),
        monthSalesVolume: Number(res.data.monthSalesVolume || 0),
        monthSalesAmount: Number(res.data.monthSalesAmount || 0)
      }
    }
  } catch (e) {
    console.error('加载订单统计失败', e)
  }
}

const loadRefunds = async () => {
  try {
    const query = buildQuery({ status: refundStatusFilter.value >= 0 ? refundStatusFilter.value : '' })
    const res = await request.get(`/merchant/refunds${query}`)
    if (res.code === 200) {
      refunds.value = res.data || []
    }
  } catch (e) {
    console.error('加载退款申请失败', e)
  }
}

const shipOrder = async (order) => {
  const form = shipData.value[order.id] || { company: '', trackingNumber: '' }
  if (!form.trackingNumber) {
    alert('请填写物流单号')
    return
  }

  try {
    const res = await request.post(`/merchant/orders/${order.id}/ship`, {
      company: form.company,
      trackingNumber: form.trackingNumber
    })
    if (res.code === 200) {
      alert('发货成功')
      await Promise.all([loadOrders(), loadStats()])
      return
    }
    alert(res.message || '发货失败')
  } catch (e) {
    alert('发货失败')
  }
}

const processRefund = async (refund, status) => {
  const remark = refundRemarks.value[refund.id] || ''
  if (status === 2 && !remark) {
    alert('拒绝退款时请填写处理说明')
    return
  }

  processingRefundId.value = refund.id
  try {
    const res = await request.post(`/merchant/refunds/${refund.id}/process`, {
      status,
      merchantRemark: remark
    })
    if (res.code === 200) {
      alert('处理成功')
      await Promise.all([loadRefunds(), loadOrders(), loadStats()])
      return
    }
    alert(res.message || '处理失败')
  } catch (e) {
    alert('处理失败')
  } finally {
    processingRefundId.value = null
  }
}

const changeStatus = async (status) => {
  statusFilter.value = status
  await loadOrders()
}

const reloadAll = async () => {
  await Promise.all([loadOrders(), loadStats(), loadRefunds()])
}

onMounted(async () => {
  await reloadAll()
  refreshTimer = setInterval(() => {
    reloadAll()
  }, 15000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.merchant-orders {
  max-width: 1240px;
  margin: 0 auto;
}

.hero {
  margin-bottom: 14px;
}

h2 {
  margin-bottom: 4px;
  font-size: 30px;
  color: #0f172a;
}

.hero p {
  color: #667085;
  font-size: 14px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}

.stat-card {
  background:
    linear-gradient(160deg, rgba(15, 107, 207, 0.08), rgba(15, 118, 110, 0.05)),
    #fff;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.stat-value {
  font-size: 30px;
  color: var(--brand);
  font-weight: 700;
}

.stat-label {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.panel {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  padding: 14px;
}

.orders-panel {
  margin-bottom: 14px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 10px;
}

.panel-head h3 {
  font-size: 18px;
  color: #0f172a;
}

.btn-ghost {
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid var(--line-soft);
  background: #fff;
  color: #334155;
  cursor: pointer;
  font-weight: 600;
}

.btn-ghost:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.status-tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.tab-btn {
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid var(--line-soft);
  background: #fff;
  color: #334155;
  cursor: pointer;
  font-size: 13px;
}

.tab-btn.active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order-card {
  border: 1px solid #e6edf8;
  border-radius: 10px;
  background: #fbfdff;
  padding: 14px;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.order-no {
  font-weight: 700;
  color: #0f172a;
  word-break: break-all;
}

.status {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.status-0 {
  background: #fef3c7;
  color: #92400e;
}

.status-1 {
  background: #dbeafe;
  color: #1e40af;
}

.status-2 {
  background: #dcfce7;
  color: #166534;
}

.status-3 {
  background: #ede9fe;
  color: #5b21b6;
}

.status-4 {
  background: #fee2e2;
  color: #b91c1c;
}

.order-info {
  color: #667085;
  font-size: 13px;
  line-height: 1.8;
}

.price {
  color: #dc2626;
}

.ship-form {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.ship-form input {
  flex: 1;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  padding: 9px 10px;
}

.ship-form button {
  padding: 9px 14px;
  border: none;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  cursor: pointer;
  font-weight: 700;
  white-space: nowrap;
}

.refund-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #64748b;
  font-size: 13px;
}

.refund-filter select {
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 7px 10px;
  background: #fff;
}

.refund-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.refund-card {
  border: 1px solid #e6edf8;
  border-radius: 10px;
  background: #fbfdff;
  padding: 12px;
}

.refund-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  gap: 8px;
}

.refund-title {
  font-weight: 700;
  color: #0f172a;
}

.refund-status {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.refund-0 {
  background: #dbeafe;
  color: #1e40af;
}

.refund-1 {
  background: #dcfce7;
  color: #166534;
}

.refund-2 {
  background: #fee2e2;
  color: #b91c1c;
}

.refund-info {
  color: #667085;
  font-size: 13px;
  line-height: 1.8;
}

.refund-actions {
  margin-top: 8px;
}

.refund-actions textarea {
  width: 100%;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 8px 10px;
  resize: vertical;
  background: #fff;
  margin-bottom: 8px;
}

.action-row {
  display: flex;
  gap: 8px;
}

.btn-approve,
.btn-reject {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: #fff;
  font-weight: 700;
}

.btn-approve {
  background: #16a34a;
}

.btn-reject {
  background: #dc2626;
}

.btn-approve:disabled,
.btn-reject:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.empty {
  text-align: center;
  padding: 24px;
  color: #98a2b3;
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  background: #fbfdff;
}

@media (max-width: 1080px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .ship-form {
    flex-direction: column;
  }

  .ship-form button {
    width: 100%;
  }
}

@media (max-width: 700px) {
  h2 {
    font-size: 24px;
  }

  .stats-grid {
    grid-template-columns: 1fr;
  }

  .panel-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .order-header,
  .refund-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .action-row {
    flex-direction: column;
  }
}
</style>
