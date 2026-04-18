<template>
  <div class="orders">
    <h2>订单管理</h2>
    <div class="status-filter">
      <button :class="{ active: statusFilter === null }" @click="statusFilter = null">全部</button>
      <button :class="{ active: statusFilter === 0 }" @click="statusFilter = 0">待支付</button>
      <button :class="{ active: statusFilter === 1 }" @click="statusFilter = 1">待发货</button>
      <button :class="{ active: statusFilter === 2 }" @click="statusFilter = 2">待收货</button>
      <button :class="{ active: statusFilter === 3 }" @click="statusFilter = 3">已完成</button>
    </div>
    <div class="order-list">
      <div class="order-card" v-for="order in filteredOrders" :key="order.id">
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

        <div class="order-products">
          <div
            v-for="item in getOrderItems(order.id)"
            :key="item.id"
            class="order-product"
            @click="viewProductDetail(item.productId)"
          >
            <img
              v-if="item.productImage"
              :src="resolveImageUrl(item.productImage)"
              alt="商品图片"
              class="product-image"
            />
            <div v-else class="product-image product-image-placeholder">无图</div>
            <div class="product-meta">
              <div class="product-name">{{ item.productName || `商品#${item.productId}` }}</div>
              <div class="product-sub">数量 x{{ item.quantity }} · 单价 ¥{{ formatMoney(item.price) }}</div>
            </div>
          </div>
          <div v-if="!hasLoadedOrderItems(order.id)" class="order-product-empty">商品加载中...</div>
          <div v-else-if="getOrderItems(order.id).length === 0" class="order-product-empty">暂无商品信息</div>
        </div>

        <div class="order-actions">
          <button @click="toggleDetail(order.id)" class="btn-detail">{{ order.showDetail ? '收起' : '查看详情' }}</button>
          <button v-if="order.status === 0" @click="payOrder(order.id)" class="btn-pay">支付</button>
          <button v-if="order.status === 2 && !isRefundPending(order)" @click="confirmOrder(order.id)" class="btn-confirm">确认收货</button>
          <button v-if="order.status === 3 && !order.reviewed && !isRefundPending(order)" @click="showReview(order)" class="btn-review">评价</button>
          <button v-if="order.status === 3 && order.reviewed && !isRefundPending(order)" class="btn-reviewed" disabled>已评价</button>
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
          <label>评价商品</label>
          <select v-model="reviewForm.productId" :disabled="reviewLoading || reviewOrderItems.length === 0">
            <option v-for="item in reviewOrderItems" :key="item.id" :value="item.productId">
              {{ item.productName || `商品#${item.productId}` }} x{{ item.quantity }}
            </option>
          </select>
          <div v-if="reviewLoading" class="hint-text">正在加载订单商品...</div>
          <div v-else-if="reviewOrderItems.length === 0" class="hint-text">该订单暂无可评价商品</div>
        </div>
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
          <button @click="submitReview" class="btn-submit" :disabled="reviewLoading">提交</button>
          <button @click="closeReviewModal" class="btn-cancel" :disabled="reviewLoading">取消</button>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()
const orders = ref([])
const statusFilter = ref(null)
const latestRefundMap = ref({})
const orderItemsMap = ref({})
const reviewModal = ref(false)
const reviewLoading = ref(false)
const reviewOrderItems = ref([])
const reviewForm = ref({ orderId: null, productId: null, rating: 5, content: '' })
const refundModal = ref(false)
const refundSubmitting = ref(false)
const refundForm = ref({ orderId: null, amount: '', reason: '' })
let refreshTimer = null

const filteredOrders = computed(() => {
  if (statusFilter.value === null) return orders.value
  return orders.value.filter(order => order.status === statusFilter.value)
})

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

const parseOrderItems = (data) => {
  if (!Array.isArray(data)) return []
  return data
    .map(item => {
      const productId = item?.productId ?? item?.product_id ?? null
      const orderId = item?.orderId ?? item?.order_id ?? null
      const quantity = item?.quantity ?? item?.qty ?? 0
      const price = item?.price ?? item?.unitPrice ?? 0
      return {
        ...item,
        productId,
        orderId,
        quantity: Number(quantity || 0),
        price: Number(price || 0)
      }
    })
    .filter(item => item && item.productId != null)
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const getOrderItems = (orderId) => {
  return orderItemsMap.value[orderId] || []
}

const hasLoadedOrderItems = (orderId) => {
  return Object.prototype.hasOwnProperty.call(orderItemsMap.value, orderId)
}

const viewProductDetail = (productId) => {
  if (!productId) return
  router.push(`/product/${productId}`)
}

const loadOrderItemsByOrderIds = async (orderIds) => {
  const ids = (orderIds || []).filter(id => id != null)
  if (ids.length === 0) {
    orderItemsMap.value = {}
    return
  }

  const nextMap = { ...orderItemsMap.value }
  await Promise.all(ids.map(async (orderId) => {
    try {
      const res = await request.get(`/orders/${orderId}/items`)
      nextMap[orderId] = parseOrderItems(res?.data)
    } catch (e) {
      nextMap[orderId] = []
    }
  }))
  orderItemsMap.value = nextMap
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
        reviewed: !!o.reviewed,
        createTime: getOrderTime(o),
        showDetail: detailStateMap[o.id] || false,
        latestRefund: latestRefundMap.value[o.id] || null
      }))
      await loadOrderItemsByOrderIds(orders.value.map(item => item.id))
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

const closeReviewModal = () => {
  reviewModal.value = false
  reviewLoading.value = false
  reviewOrderItems.value = []
  reviewForm.value = { orderId: null, productId: null, rating: 5, content: '' }
}

const showReview = async (order) => {
  if (!order || order.status !== 3) {
    alert('仅已完成订单可评价')
    return
  }

  reviewLoading.value = true
  reviewOrderItems.value = []
  reviewForm.value = { orderId: order.id, productId: null, rating: 5, content: '' }
  try {
    const res = await request.get(`/orders/${order.id}/items`)
    const items = parseOrderItems(res?.data)
    reviewOrderItems.value = items
    if (items.length === 0) {
      alert('该订单暂无可评价商品')
      return
    }
    reviewForm.value.productId = items[0].productId
    reviewModal.value = true
  } catch (e) {
    const msg = e?.response?.data?.message || '加载订单商品失败'
    alert(msg)
  } finally {
    reviewLoading.value = false
  }
}

const submitReview = async () => {
  const currentOrder = orders.value.find(item => item.id === reviewForm.value.orderId)
  if (!currentOrder || currentOrder.status !== 3) {
    alert('仅已完成订单可评价')
    closeReviewModal()
    await reloadOrderData()
    return
  }
  if (reviewForm.value.productId == null) {
    alert('请选择评价商品')
    return
  }

  try {
    await request.post(`/orders/${reviewForm.value.orderId}/review`, {
      productId: reviewForm.value.productId,
      rating: reviewForm.value.rating,
      content: reviewForm.value.content
    })
    orders.value = orders.value.map(order => order.id === reviewForm.value.orderId
      ? { ...order, reviewed: true }
      : order)
    alert('评价成功')
    closeReviewModal()
    await reloadOrderData()
  } catch (e) {
    const msg = e?.response?.data?.message || '评价失败'
    alert(msg)
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

.status-filter {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.status-filter button {
  padding: 8px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #475467;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
}

.status-filter button:hover {
  border-color: var(--brand);
  color: var(--brand);
}

.status-filter button.active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
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

.order-products {
  border: 1px solid #e5edf6;
  border-radius: 10px;
  background: #f8fbff;
  padding: 10px;
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order-product {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  border: 1px solid transparent;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.order-product:hover {
  border-color: rgba(15, 107, 207, 0.28);
  transform: translateY(-1px);
  box-shadow: 0 6px 14px rgba(15, 40, 70, 0.08);
}

.product-image {
  width: 62px;
  height: 62px;
  object-fit: cover;
  border-radius: 8px;
  border: 1px solid #d9e2ef;
  flex-shrink: 0;
}

.product-image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #98a2b3;
  background: #f8fafc;
}

.product-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-name {
  font-size: 14px;
  color: #0f172a;
  font-weight: 600;
  line-height: 1.35;
  word-break: break-all;
}

.product-sub {
  font-size: 12px;
  color: #667085;
}

.order-product-empty {
  font-size: 12px;
  color: #98a2b3;
  padding: 6px 4px;
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
.btn-reviewed,
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

.btn-reviewed {
  background: #e2e8f0;
  color: #64748b;
  cursor: not-allowed;
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

.hint-text {
  margin-top: 8px;
  font-size: 12px;
  color: #667085;
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

  .product-image {
    width: 54px;
    height: 54px;
  }

  .modal-content {
    width: calc(100vw - 28px);
    padding: 18px;
  }
}
</style>
