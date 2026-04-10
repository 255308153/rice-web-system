<template>
  <div class="orders">
    <h2>订单管理</h2>
    <div class="order-list">
      <div class="order-card" v-for="order in orders" :key="order.id">
        <div class="order-header">
          <span class="order-no">订单号：{{ order.orderNo }}</span>
          <div class="header-right">
            <span class="order-status" :class="'status-' + order.status">{{ getStatusText(order.status) }}</span>
            <span
              v-if="order.latestRefund"
              class="refund-badge"
              :class="'refund-' + order.latestRefund.status"
            >{{ getRefundStatusText(order.latestRefund.status) }}</span>
          </div>
        </div>

        <div class="order-info">
          <div>金额：<span class="price">¥{{ formatMoney(order.totalPrice) }}</span></div>
          <div>时间：{{ formatTime(order.createTime) }}</div>
          <div v-if="order.latestRefund">退款状态：{{ getRefundStatusText(order.latestRefund.status) }}</div>
          <div v-if="order.latestRefund?.merchantRemark">商家说明：{{ order.latestRefund.merchantRemark }}</div>
        </div>

        <div class="order-actions">
          <button @click="toggleDetail(order.id)" class="btn-detail">{{ order.showDetail ? '收起' : '查看详情' }}</button>
          <button v-if="order.status === 0" @click="payOrder(order.id)" class="btn-pay">支付</button>
          <button v-if="order.status === 2 && !isRefundPending(order)" @click="confirmOrder(order.id)" class="btn-confirm">确认收货</button>
          <button v-if="order.status === 3 && !order.reviewed && !isRefundPending(order)" @click="showReview(order.id)" class="btn-review">评价</button>
          <button v-if="canApplyRefund(order)" @click="showRefund(order)" class="btn-refund">申请退款</button>
        </div>

        <div v-if="order.showDetail" class="order-detail">
          <div class="detail-section">
            <h4>订单信息</h4>
            <p>订单号：{{ order.orderNo }}</p>
            <p>创建时间：{{ formatTime(order.createTime) }}</p>
            <p>订单状态：{{ getStatusText(order.status) }}</p>
            <template v-if="order.latestRefund">
              <p>退款状态：{{ getRefundStatusText(order.latestRefund.status) }}</p>
              <p>退款原因：{{ order.latestRefund.reason || '-' }}</p>
              <p>退款金额：¥{{ formatMoney(order.latestRefund.amount) }}</p>
              <p v-if="order.latestRefund.merchantRemark">商家说明：{{ order.latestRefund.merchantRemark }}</p>
            </template>
          </div>
        </div>
      </div>
      <div v-if="orders.length === 0" class="empty">暂无订单</div>
    </div>

    <div v-if="reviewModal" class="modal">
      <div class="modal-content">
        <h3>订单评价</h3>
        <div class="form-group">
          <label>评分</label>
          <select v-model="reviewForm.rating">
            <option :value="5">5星-非常满意</option>
            <option :value="4">4星-满意</option>
            <option :value="3">3星-一般</option>
            <option :value="2">2星-不满意</option>
            <option :value="1">1星-非常不满意</option>
          </select>
        </div>
        <div class="form-group">
          <label>评价内容</label>
          <textarea v-model="reviewForm.content" rows="4" placeholder="请输入评价内容"></textarea>
        </div>
        <div class="form-actions">
          <button @click="submitReview" class="btn-submit">提交</button>
          <button @click="reviewModal = false" class="btn-cancel">取消</button>
        </div>
      </div>
    </div>

    <div v-if="refundModal" class="modal">
      <div class="modal-content">
        <h3>申请退款</h3>
        <div class="form-group">
          <label>退款金额</label>
          <input v-model.trim="refundForm.amount" type="number" min="0.01" step="0.01" />
        </div>
        <div class="form-group">
          <label>退款原因</label>
          <textarea v-model.trim="refundForm.reason" rows="4" placeholder="请填写退款原因"></textarea>
        </div>
        <div class="form-actions">
          <button @click="submitRefund" class="btn-submit" :disabled="refundSubmitting">提交申请</button>
          <button @click="refundModal = false" class="btn-cancel" :disabled="refundSubmitting">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import request from '../utils/request'

const orders = ref([])
const latestRefundMap = ref({})
const reviewModal = ref(false)
const reviewForm = ref({ orderId: null, productId: null, rating: 5, content: '' })
const refundModal = ref(false)
const refundSubmitting = ref(false)
const refundForm = ref({ orderId: null, amount: '', reason: '' })
let refreshTimer = null

const getStatusText = (status) => {
  const map = { 0: '待支付', 1: '待发货', 2: '待收货', 3: '已完成', 4: '售后' }
  return map[status] || '未知'
}

const getRefundStatusText = (status) => {
  const map = { 0: '退款处理中', 1: '退款已同意', 2: '退款已拒绝' }
  return map[status] || '未知'
}

const getOrderTime = (order) => {
  if (!order) return ''
  const direct = order.createTime || order.create_time || order.orderTime || order.order_time
  if (direct) return direct

  const match = String(order.orderNo || '').match(/\d{13}/)
  return match ? Number(match[0]) : ''
}

const formatTime = (time) => {
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

const formatMoney = (value) => {
  const amount = Number(value || 0)
  return amount.toFixed(2)
}

const syncRefundToOrders = () => {
  orders.value = orders.value.map(order => ({
    ...order,
    latestRefund: latestRefundMap.value[order.id] || null
  }))
}

const loadRefunds = async () => {
  try {
    const res = await request.get('/orders/refunds')
    if (res.code === 200) {
      const map = {}
      ;(res.data || []).forEach(refund => {
        const existing = map[refund.orderId]
        if (!existing || Number(refund.id || 0) > Number(existing.id || 0)) {
          map[refund.orderId] = refund
        }
      })
      latestRefundMap.value = map
      syncRefundToOrders()
    }
  } catch (e) {
    console.error('加载退款记录失败', e)
  }
}

const loadOrders = async () => {
  try {
    const detailStateMap = {}
    orders.value.forEach(item => {
      detailStateMap[item.id] = !!item.showDetail
    })

    const res = await request.get('/orders')
    if (res.code === 200) {
      orders.value = (res.data || []).map(o => ({
        ...o,
        createTime: getOrderTime(o),
        showDetail: detailStateMap[o.id] || false,
        latestRefund: latestRefundMap.value[o.id] || null
      }))
    }
  } catch (e) {
    console.error('加载订单失败', e)
  }
}

const reloadOrderData = async () => {
  await loadRefunds()
  await loadOrders()
}

const toggleDetail = (id) => {
  const order = orders.value.find(o => o.id === id)
  if (order) order.showDetail = !order.showDetail
}

const isRefundPending = (order) => {
  return order?.latestRefund?.status === 0
}

const canApplyRefund = (order) => {
  if (!order || ![1, 2, 3, 4].includes(order.status)) return false
  if (!order.latestRefund) return true
  return order.latestRefund.status === 2
}

const payOrder = async (id) => {
  try {
    const res = await request.put(`/orders/${id}/pay`)
    if (res.code === 200) {
      alert('支付成功')
      await reloadOrderData()
    }
  } catch (e) {
    alert('支付失败')
  }
}

const confirmOrder = async (id) => {
  try {
    const res = await request.put(`/orders/${id}/confirm`)
    if (res.code === 200) {
      alert('确认收货成功')
      await reloadOrderData()
    }
  } catch (e) {
    alert('操作失败')
  }
}

const showReview = (orderId) => {
  reviewForm.value = { orderId, productId: 1, rating: 5, content: '' }
  reviewModal.value = true
}

const submitReview = async () => {
  try {
    await request.post(`/orders/${reviewForm.value.orderId}/review`, {
      productId: reviewForm.value.productId,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    alert('评价成功')
    reviewModal.value = false
    await loadOrders()
  } catch (e) {
    alert('评价失败')
  }
}

const showRefund = (order) => {
  refundForm.value = {
    orderId: order.id,
    amount: formatMoney(order.totalPrice),
    reason: ''
  }
  refundModal.value = true
}

const submitRefund = async () => {
  if (!refundForm.value.reason) {
    alert('请填写退款原因')
    return
  }
  const amount = Number(refundForm.value.amount)
  if (!Number.isFinite(amount) || amount <= 0) {
    alert('退款金额必须大于0')
    return
  }

  refundSubmitting.value = true
  try {
    const res = await request.post(`/orders/${refundForm.value.orderId}/refund`, {
      reason: refundForm.value.reason,
      amount
    })
    if (res.code === 200) {
      alert('退款申请已提交')
      refundModal.value = false
      await reloadOrderData()
      return
    }
    alert(res.message || '提交失败')
  } catch (e) {
    alert('提交失败')
  } finally {
    refundSubmitting.value = false
  }
}

onMounted(async () => {
  await reloadOrderData()
  refreshTimer = setInterval(() => {
    reloadOrderData()
  }, 10000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.orders {
  max-width: 1200px;
  margin: 0 auto;
}

h2 {
  margin-bottom: 16px;
  font-size: 28px;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  padding: 18px;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.order-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line-soft);
  gap: 8px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.order-no {
  font-weight: 600;
  color: #0f172a;
  word-break: break-all;
}

.order-status {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
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
  background: #fce7f3;
  color: #9f1239;
}

.status-3 {
  background: #d1fae5;
  color: #065f46;
}

.status-4 {
  background: #fee2e2;
  color: #b91c1c;
}

.refund-badge {
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
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

.order-info {
  margin-bottom: 12px;
  font-size: 13px;
  color: #667085;
  line-height: 1.7;
}

.price {
  color: #dc2626;
  font-weight: 700;
  font-size: 17px;
}

.order-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.btn-detail {
  padding: 8px 16px;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}

.btn-pay,
.btn-confirm,
.btn-review,
.btn-refund {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
}

.btn-pay {
  background: #ef4444;
  color: #fff;
}

.btn-confirm {
  background: #10b981;
  color: #fff;
}

.btn-review {
  background: var(--brand);
  color: #fff;
}

.btn-refund {
  background: #f59e0b;
  color: #fff;
}

.order-detail {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--line-soft);
}

.detail-section h4 {
  margin-bottom: 12px;
  font-size: 15px;
}

.detail-section p {
  margin: 8px 0;
  font-size: 14px;
  color: #666;
}

.modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  padding: 24px;
  border-radius: 14px;
  width: 500px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 20px 48px rgba(15, 40, 70, 0.22);
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-weight: 500;
}

.form-group select,
.form-group textarea,
.form-group input {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
}

.form-actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
}

.btn-submit,
.btn-cancel {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}

.btn-submit {
  background: var(--brand);
  color: #fff;
}

.btn-submit:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.btn-cancel {
  background: #e5e7eb;
}

.empty {
  text-align: center;
  padding: 40px;
  color: #98a2b3;
}

@media (max-width: 768px) {
  h2 {
    font-size: 22px;
  }

  .order-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-right {
    justify-content: flex-start;
  }

  .order-actions button {
    flex: 1;
    min-width: 46%;
  }

  .modal-content {
    width: calc(100vw - 28px);
    padding: 18px;
  }
}
</style>
