<template>
  <div class="admin-dashboard">
    <div class="hero">
      <h2>数据监控</h2>
      <p>实时查看平台交易、AI 调用、论坛规模与用户活跃度。</p>
    </div>

    <div class="pending-tasks">
      <div class="task-card">
        <div class="task-title">待审核商户</div>
        <div class="task-count">{{ stats.pendingMerchants }}</div>
        <button @click="$router.push({ path: '/admin/audits', query: { role: 'MERCHANT', status: '0' } })" class="btn-view">查看</button>
      </div>
      <div class="task-card">
        <div class="task-title">待审核专家</div>
        <div class="task-count">{{ stats.pendingExperts }}</div>
        <button @click="$router.push({ path: '/admin/audits', query: { role: 'EXPERT', status: '0' } })" class="btn-view">查看</button>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-label">总订单数</div>
        <div class="stat-value">{{ stats.totalOrders }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总交易额</div>
        <div class="stat-value">¥{{ formatMoney(stats.totalTradeAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日订单</div>
        <div class="stat-value">{{ stats.todayOrders }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日交易额</div>
        <div class="stat-value">¥{{ formatMoney(stats.todayTradeAmount) }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">AI 调用总次数</div>
        <div class="stat-value">{{ stats.aiTotalCalls }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">AI 对话次数</div>
        <div class="stat-value">{{ stats.aiChatCalls }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">AI 识别次数</div>
        <div class="stat-value">{{ stats.aiRecognitionCalls }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">今日 AI 调用</div>
        <div class="stat-value">{{ stats.todayAICalls }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">论坛帖子总数</div>
        <div class="stat-value">{{ stats.totalPosts }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">论坛评论总数</div>
        <div class="stat-value">{{ stats.totalComments }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">违规帖子</div>
        <div class="stat-value abnormal">{{ stats.violationPosts }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">违规评论</div>
        <div class="stat-value abnormal">{{ stats.violationComments }}</div>
      </div>
    </div>

    <div class="activity-card">
      <div class="section-head">
        <h3>用户活跃度</h3>
      </div>
      <div class="activity-grid">
        <div class="activity-item">
          <div class="activity-label">近 1 天</div>
          <div class="activity-value">{{ stats.activeUsers1d }}</div>
        </div>
        <div class="activity-item">
          <div class="activity-label">近 7 天</div>
          <div class="activity-value">{{ stats.activeUsers7d }}</div>
        </div>
        <div class="activity-item">
          <div class="activity-label">近 30 天</div>
          <div class="activity-value">{{ stats.activeUsers30d }}</div>
        </div>
      </div>
    </div>

    <div class="hot-products-card">
      <div class="section-head">
        <h3>热销商品 TOP5</h3>
        <span>按已支付/履约订单销量统计</span>
      </div>
      <div class="hot-products-list" v-if="topProducts.length > 0">
        <div class="hot-item" v-for="(item, idx) in topProducts" :key="item.productId || idx">
          <div class="hot-rank">{{ idx + 1 }}</div>
          <div class="hot-main">
            <div class="hot-name">{{ item.productName || '未命名商品' }}</div>
            <div class="hot-meta">销量 {{ item.salesCount }} 件 · 销售额 ¥{{ formatMoney(item.salesAmount) }}</div>
            <div class="hot-track">
              <div class="hot-bar" :style="{ width: `${item.ratio}%` }"></div>
            </div>
          </div>
          <div class="hot-count">{{ item.salesCount }}</div>
        </div>
      </div>
      <div v-else class="empty">暂无热销数据</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '../../utils/request'

const stats = ref({
  pendingMerchants: 0,
  pendingExperts: 0,
  totalOrders: 0,
  todayOrders: 0,
  totalTradeAmount: 0,
  todayTradeAmount: 0,

  aiChatCalls: 0,
  aiRecognitionCalls: 0,
  aiTotalCalls: 0,
  todayAICalls: 0,

  totalPosts: 0,
  totalComments: 0,
  violationPosts: 0,
  violationComments: 0,

  activeUsers1d: 0,
  activeUsers7d: 0,
  activeUsers30d: 0
})

const topProducts = ref([])

const toNumber = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num : 0
}

const formatMoney = (value) => toNumber(value).toFixed(2)

const loadData = async () => {
  try {
    const [monitorRes, hotProductsRes] = await Promise.all([
      request.get('/admin/monitor/overview?windowDays=30&threshold=3'),
      request.get('/admin/hot-products?limit=5')
    ])

    if (monitorRes.code === 200 && monitorRes.data) {
      stats.value = {
        ...stats.value,
        ...monitorRes.data
      }
    }

    if (hotProductsRes.code === 200) {
      const rawTop = Array.isArray(hotProductsRes.data) ? hotProductsRes.data : []
      const maxSales = Math.max(...rawTop.map(item => toNumber(item.salesCount)), 1)
      topProducts.value = rawTop.map(item => {
        const salesCount = toNumber(item.salesCount)
        return {
          ...item,
          salesCount,
          salesAmount: toNumber(item.salesAmount),
          ratio: Math.max((salesCount / maxSales) * 100, 8)
        }
      })
      return
    }
    topProducts.value = []
  } catch (error) {
    console.error('加载监控数据失败', error)
    topProducts.value = []
  }
}

onMounted(loadData)
</script>

<style scoped>
.admin-dashboard {
  max-width: 1220px;
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

.pending-tasks {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}

.task-card {
  background:
    linear-gradient(145deg, rgba(15,107,207,0.08), rgba(15,118,110,0.05)),
    #fff;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  text-align: center;
}

.task-title {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.task-count {
  font-size: 36px;
  font-weight: 700;
  color: #ef4444;
  margin-bottom: 10px;
}

.btn-view {
  padding: 8px 16px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.stat-card {
  background: #fff;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  text-align: center;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--brand);
}

.stat-value.abnormal {
  color: #ef4444;
}

.activity-card,
.hot-products-card {
  margin-top: 12px;
  background: #fff;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  border-radius: 12px;
  padding: 16px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.section-head h3 {
  margin: 0;
  font-size: 18px;
  color: #0f172a;
}

.section-head span {
  font-size: 12px;
  color: #94a3b8;
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.activity-item {
  border: 1px solid #e6edf8;
  border-radius: 10px;
  padding: 12px;
  background: #f8fbff;
}

.activity-label {
  color: #64748b;
  font-size: 12px;
  margin-bottom: 4px;
}

.activity-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--brand);
}

.hot-products-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hot-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid #e6edf8;
  border-radius: 10px;
  background: #f8fbff;
}

.hot-rank {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--brand);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.hot-main {
  flex: 1;
  min-width: 0;
}

.hot-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-meta {
  margin-top: 2px;
  margin-bottom: 6px;
  font-size: 12px;
  color: #64748b;
}

.hot-track {
  width: 100%;
  height: 8px;
  background: #e7eef8;
  border-radius: 999px;
  overflow: hidden;
}

.hot-bar {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #0f6bcf 0%, #0f766e 100%);
  transition: width 0.3s ease;
}

.hot-count {
  min-width: 52px;
  text-align: right;
  font-size: 18px;
  font-weight: 700;
  color: var(--brand);
}

.empty {
  text-align: center;
  color: #98a2b3;
  padding: 24px;
}

@media (max-width: 1000px) {
  .pending-tasks {
    grid-template-columns: 1fr;
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  h2 {
    font-size: 24px;
  }

  .section-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .stats-grid,
  .activity-grid {
    grid-template-columns: 1fr;
  }
}
</style>
