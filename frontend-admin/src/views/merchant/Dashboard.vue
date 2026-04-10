<template>
  <div class="merchant-dashboard">
    <div class="hero">
      <h2>商户后台</h2>
      <p>快速查看核心经营指标和待处理订单。</p>
    </div>
    <div v-if="errorTip" class="warn-tip">{{ errorTip }}</div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-value">{{ stats.todayOrders }}</div>
        <div class="stat-label">今日订单</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.pendingOrders }}</div>
        <div class="stat-label">待处理订单</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">¥{{ formatMoney(stats.todaySales) }}</div>
        <div class="stat-label">今日销售额</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalProducts }}</div>
        <div class="stat-label">商品总数</div>
      </div>
    </div>

    <div class="chart-section">
      <h3>近7日销售趋势</h3>
      <div class="chart">
        <div v-for="(day, idx) in salesChart" :key="idx" class="chart-bar">
          <div class="bar-value">¥{{ day.value }}</div>
          <div class="bar" :style="{ height: day.height }"></div>
          <div class="bar-label">{{ day.label }}</div>
        </div>
      </div>
    </div>

    <div class="stats-row">
      <div class="chart-section half">
        <h3>订单状态分布</h3>
        <div class="pie-chart">
          <div v-for="status in orderStatus" :key="status.label" class="pie-item">
            <div class="pie-bar" :style="{ width: status.percent }"></div>
            <div class="pie-label">{{ status.label }}: {{ status.count }}</div>
          </div>
        </div>
      </div>

      <div class="chart-section half">
        <h3>热销商品TOP5</h3>
        <div class="rank-list">
          <div v-for="(item, idx) in topProducts" :key="idx" class="rank-item">
            <div class="rank-num">{{ idx + 1 }}</div>
            <div class="rank-name">{{ item.name }}</div>
            <div class="rank-sales">{{ item.sales }}件</div>
          </div>
          <div v-if="topProducts.length === 0" class="empty-small">暂无数据</div>
        </div>
      </div>
    </div>

    <div class="recent-orders">
      <h3>待处理订单</h3>
      <div class="order-list">
        <div class="order-item" v-for="order in pendingOrders" :key="order.id">
          <div class="order-row">
            <span>订单号</span>
            <strong>{{ order.orderNo }}</strong>
          </div>
          <div class="order-row">
            <span>金额</span>
            <strong class="price">¥{{ formatMoney(order.totalPrice) }}</strong>
          </div>
          <div class="order-row">
            <span>状态</span>
            <strong>{{ getStatus(order.status) }}</strong>
          </div>
        </div>
        <div v-if="pendingOrders.length === 0" class="empty">暂无待处理订单</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../../utils/request'

const stats = ref({
  todayOrders: 0,
  pendingOrders: 0,
  todaySales: 0,
  totalProducts: 0
})
const pendingOrders = ref([])
const salesChart = ref([])
const orderStatus = ref([])
const topProducts = ref([])
const errorTip = ref('')

const getStatus = (s) => ({ 0: '待支付', 1: '待发货', 2: '待收货', 3: '已完成', 4: '售后' }[s] || '未知')
const toNumber = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}
const formatMoney = (value) => toNumber(value).toFixed(2)

const getOrderTime = (order) => {
  if (!order) return ''
  const direct = order.createTime || order.create_time || order.orderTime || order.order_time
  if (direct) return direct

  const match = String(order.orderNo || '').match(/\d{13}/)
  return match ? Number(match[0]) : ''
}

const parseOrderTime = (order) => {
  const time = getOrderTime(order)
  if (!time) return null

  const parsed = new Date(String(time))
  if (!Number.isNaN(parsed.getTime())) {
    return parsed
  }

  if (typeof time === 'string') {
    const normalized = time
      .replace('T', ' ')
      .replace(/\.\d+$/, '')
      .replace(/-/g, '/')
    const fallback = new Date(normalized)
    if (!Number.isNaN(fallback.getTime())) {
      return fallback
    }
  }

  return null
}

const decodeCurrentUserId = () => {
  const token = localStorage.getItem('token')
  if (!token) return null
  try {
    const payloadPart = token.split('.')[1]
    if (!payloadPart) return null
    const normalized = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const payload = JSON.parse(atob(padded))
    const raw = payload?.sub ?? payload?.userId ?? payload?.id
    const id = Number(raw)
    return Number.isFinite(id) ? id : null
  } catch (e) {
    return null
  }
}

const buildSalesChart = (orders) => {
  const now = new Date()
  const buckets = []
  for (let i = 6; i >= 0; i--) {
    const day = new Date(now)
    day.setHours(0, 0, 0, 0)
    day.setDate(day.getDate() - i)
    buckets.push({
      date: day,
      label: `${day.getMonth() + 1}/${day.getDate()}`,
      value: 0
    })
  }

  const keyToIndex = {}
  buckets.forEach((item, index) => {
    keyToIndex[item.date.toDateString()] = index
  })

  ;(orders || []).forEach(order => {
    const status = Number(order?.status)
    if (![1, 2, 3, 4].includes(status)) return
    const dt = parseOrderTime(order)
    if (!dt) return
    const idx = keyToIndex[dt.toDateString()]
    if (idx === undefined) return
    buckets[idx].value += toNumber(order.totalPrice)
  })

  const maxSales = Math.max(...buckets.map(item => item.value), 0)
  salesChart.value = buckets.map(item => {
    if (maxSales <= 0) {
      return { ...item, height: '8%' }
    }
    const ratio = (item.value / maxSales) * 100
    return { ...item, height: `${Math.max(8, ratio)}%` }
  })
}

const buildOrderStatusChart = (orders) => {
  const statusMap = { 0: '待支付', 1: '待发货', 2: '待收货', 3: '已完成', 4: '售后' }
  const total = Math.max((orders || []).length, 1)
  orderStatus.value = [0, 1, 2, 3, 4].map(status => {
    const count = (orders || []).filter(item => Number(item.status) === status).length
    return {
      label: statusMap[status],
      count,
      percent: `${Math.max(4, Math.round((count / total) * 100))}%`
    }
  })
}

const loadProductCount = async () => {
  const userId = decodeCurrentUserId()
  if (!userId) return
  const shopRes = await request.get(`/shops/user/${userId}`)
  if (shopRes.code !== 200 || !shopRes.data?.id) return
  const shopId = shopRes.data.id
  const productsRes = await request.get(`/products?page=1&size=1&shopId=${shopId}`)
  if (productsRes.code === 200) {
    const total = toNumber(productsRes.data?.total)
    stats.value.totalProducts = total
  }
}

const loadData = async () => {
  errorTip.value = ''
  stats.value = { todayOrders: 0, pendingOrders: 0, todaySales: 0, totalProducts: 0 }
  pendingOrders.value = []
  topProducts.value = []
  buildSalesChart([])
  buildOrderStatusChart([])

  const [ordersRes, statsRes, topProductsRes, productsCountRes] = await Promise.allSettled([
    request.get('/merchant/orders'),
    request.get('/merchant/orders/stats'),
    request.get('/merchant/orders/top-products?limit=5'),
    loadProductCount()
  ])

  let orders = []
  if (ordersRes.status === 'fulfilled' && ordersRes.value.code === 200) {
    orders = ordersRes.value.data || []
  } else if (ordersRes.status === 'fulfilled') {
    errorTip.value = ordersRes.value.message || '订单数据加载失败'
  } else {
    errorTip.value = '订单数据加载失败'
  }

  const today = new Date().toDateString()
  stats.value.todayOrders = orders.filter(order => {
    const orderDate = parseOrderTime(order)
    return orderDate && orderDate.toDateString() === today
  }).length
  stats.value.pendingOrders = orders.filter(order => Number(order.status) === 1).length
  pendingOrders.value = orders.filter(order => Number(order.status) === 1).slice(0, 5)

  if (statsRes.status === 'fulfilled' && statsRes.value.code === 200 && statsRes.value.data) {
    stats.value.todaySales = toNumber(statsRes.value.data.daySalesAmount)
  } else {
    stats.value.todaySales = orders
      .filter(order => {
        const orderDate = parseOrderTime(order)
        return orderDate && orderDate.toDateString() === today && [1, 2, 3, 4].includes(Number(order.status))
      })
      .reduce((sum, order) => sum + toNumber(order.totalPrice), 0)
  }

  if (topProductsRes.status === 'fulfilled' && topProductsRes.value.code === 200) {
    topProducts.value = (topProductsRes.value.data || []).map(item => ({
      name: item.productName || `商品#${item.productId || '-'}`,
      sales: toNumber(item.salesCount)
    }))
  }

  if (productsCountRes.status === 'rejected' && !errorTip.value) {
    errorTip.value = '商品数据加载失败'
  }

  buildSalesChart(orders)
  buildOrderStatusChart(orders)
}

onMounted(loadData)
</script>

<style scoped>
.merchant-dashboard {
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

.warn-tip {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
  font-size: 13px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.stat-card {
  background:
    linear-gradient(150deg, rgba(15,107,207,0.08), rgba(15,118,110,0.05)),
    #fff;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: var(--brand);
  margin-bottom: 6px;
}

.stat-label {
  color: #64748b;
  font-size: 13px;
}

.chart-section {
  background: #fff;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  margin-bottom: 12px;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.half {
  margin-bottom: 0;
}

.pie-chart {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.pie-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pie-bar {
  height: 24px;
  background: linear-gradient(90deg, var(--brand), var(--brand-strong));
  border-radius: 4px;
  min-width: 20px;
  transition: width 0.3s ease;
}

.pie-label {
  font-size: 13px;
  color: #475467;
  white-space: nowrap;
}

.rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: #f8fbff;
  border-radius: 8px;
}

.rank-num {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.rank-name {
  flex: 1;
  color: #334155;
  font-size: 14px;
}

.rank-sales {
  color: var(--brand);
  font-weight: 700;
  font-size: 13px;
}

.empty-small {
  text-align: center;
  padding: 20px;
  color: #98a2b3;
  font-size: 13px;
}

.chart {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  height: 200px;
  gap: 8px;
  padding: 20px 0;
}

.chart-bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  height: 100%;
  justify-content: flex-end;
}

.bar {
  width: 100%;
  background: linear-gradient(180deg, var(--brand), var(--brand-strong));
  border-radius: 6px 6px 0 0;
  min-height: 4px;
  transition: height 0.3s ease;
}

.bar-label {
  font-size: 12px;
  color: #64748b;
}

.bar-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--brand);
}

.recent-orders {
  background: #fff;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

h3 {
  margin-bottom: 10px;
  color: #0f172a;
}

.order-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.order-item {
  padding: 12px;
  background: #f8fbff;
  border-radius: 10px;
  border: 1px solid #e6edf8;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.order-row {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  color: #64748b;
}

.order-row strong {
  color: #0f172a;
}

.price {
  color: #dc2626;
}

.empty {
  grid-column: 1 / -1;
  padding: 20px;
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  background: #fbfdff;
  color: #98a2b3;
  text-align: center;
}

@media (max-width: 1000px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .order-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  h2 {
    font-size: 24px;
  }
}
</style>
