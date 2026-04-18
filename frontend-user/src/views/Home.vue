<template>
  <div class="home">
    <section class="hero-carousel">
      <div class="carousel-stage">
        <img
          v-if="activeCarousel.imageUrl"
          class="carousel-image"
          :src="activeCarousel.imageUrl"
          :alt="activeCarousel.title || '首页轮播图'"
        />
        <div v-else class="carousel-fallback"></div>
        <div class="carousel-overlay">
          <button class="role-badge">{{ roleText }}</button>
          <h2>{{ activeCarousel.title || (isExpert ? '专家工作台入口' : '欢迎回来，普通用户') }}</h2>
          <p class="carousel-subtitle">{{ activeCarousel.subtitle || currentDate }}</p>
          <div class="carousel-actions">
            <button class="btn-red" @click="goPrimary">{{ activeCarousel.linkText || primaryActionText }}</button>
            <button class="btn-green" @click="goSecondary">助农论坛</button>
          </div>
        </div>
      </div>
      <button v-if="carouselItems.length > 1" class="arrow prev" @click="prevCarousel">‹</button>
      <button v-if="carouselItems.length > 1" class="arrow next" @click="nextCarousel">›</button>
      <div v-if="carouselItems.length > 1" class="hero-dots">
        <button
          v-for="(item, idx) in carouselItems"
          :key="`${item.imageUrl}-${idx}`"
          :class="['hero-dot', { active: idx === currentSlideIndex }]"
          @click="goToCarousel(idx)"
        ></button>
      </div>
    </section>

    <div class="quick-links">
      <div class="link-card" @click="$router.push('/shop')">
        <div class="link-icon icon-shop"></div>
        <div class="link-title">商品购买</div>
        <div class="link-desc">浏览优质大米商品与猜你喜欢推荐</div>
      </div>
      <div class="link-card" @click="$router.push('/ai')">
        <div class="link-icon icon-ai"></div>
        <div class="link-title">AI服务</div>
        <div class="link-desc">智能识别后自动衔接助手建议</div>
      </div>
      <div class="link-card" @click="$router.push('/forum')">
        <div class="link-icon icon-forum"></div>
        <div class="link-title">助农论坛</div>
        <div class="link-desc">交流种植经验与查看审核后的帖子</div>
      </div>
      <div class="link-card" @click="$router.push('/orders')">
        <div class="link-icon icon-order"></div>
        <div class="link-title">我的订单</div>
        <div class="link-desc">查看购买记录和最近履约状态</div>
      </div>
    </div>

    <section class="notice-board">
      <div class="section-header">
        <h3>系统公告</h3>
        <span class="notice-tip">按当前账号角色展示</span>
      </div>
      <div class="notice-list">
        <div class="notice-item" v-for="notice in notices" :key="notice.id" @click="showNoticeDetail(notice)">
          <div class="notice-head">
            <div class="notice-title">{{ notice.title || '系统公告' }}</div>
            <div class="notice-time">{{ formatNoticeTime(notice.createTime) }}</div>
          </div>
          <div class="notice-content">{{ notice.content || '暂无内容' }}</div>
        </div>
        <div v-if="notices.length === 0" class="empty">暂无系统公告</div>
      </div>
    </section>

    <section class="recent-orders">
      <div class="section-header">
        <h3>最近订单</h3>
        <button class="btn-view-all" @click="$router.push('/orders')">查看全部</button>
      </div>
      <div class="orders-list">
        <div class="order-item" v-for="order in orders" :key="order.id">
          <div class="order-id">{{ order.orderNo }}</div>
          <div class="order-badge" :class="order.status">{{ order.statusText }}</div>
          <div class="order-detail">
            <div>金额：<span class="price">¥{{ order.totalAmount }}</span></div>
            <div>时间：{{ order.createTime }}</div>
          </div>
          <button class="btn-action" @click="$router.push('/orders')">查看详情</button>
        </div>
        <div v-if="orders.length === 0" class="empty">暂无订单数据</div>
      </div>
    </section>

    <div class="modal-overlay" v-if="showModal" @click="showModal = false">
      <div class="modal-content" @click.stop>
        <div class="modal-header">
          <h3>{{ selectedNotice?.title || '系统公告' }}</h3>
          <button class="modal-close" @click="showModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="modal-time">发布时间：{{ formatNoticeTime(selectedNotice?.createTime) }}</div>
          <div class="modal-text">{{ selectedNotice?.content || '暂无内容' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { getRoleFromToken } from '../utils/jwt'

const router = useRouter()
const currentDate = ref('')
const currentSlideIndex = ref(0)
const userRole = ref('USER')
const carouselItems = ref([])
const notices = ref([])
const orders = ref([])
const showModal = ref(false)
const selectedNotice = ref(null)
let carouselTimer = null

const isExpert = computed(() => userRole.value === 'EXPERT')
const roleText = computed(() => {
  const roleMap = {
    EXPERT: '农业专家',
    USER: '普通用户'
  }
  return roleMap[userRole.value] || '用户'
})

const activeCarousel = computed(() => {
  if (!carouselItems.value.length) {
    return {
      imageUrl: '',
      title: '',
      subtitle: '',
      link: '',
      linkText: ''
    }
  }
  return carouselItems.value[currentSlideIndex.value % carouselItems.value.length]
})

const primaryActionText = computed(() => (isExpert.value ? '进入工作台' : '开始购物'))

const decodeRole = () => {
  const token = localStorage.getItem('token')
  if (!token) {
    userRole.value = 'USER'
    return
  }
  userRole.value = getRoleFromToken(token) || 'USER'
}

const getStatusText = (status) => {
  const map = { 0: '待支付', 1: '待发货', 2: '待收货', 3: '已完成', 4: '售后' }
  return map[status] || '未知'
}

const getStatusClass = (status) => {
  const map = { 0: 'pending', 1: 'to-ship', 2: 'shipping', 3: 'done', 4: 'canceled' }
  return map[status] || 'pending'
}

const formatOrderTime = (order) => {
  const direct = order?.createTime || order?.create_time
  if (!direct) return '-'
  const parsed = new Date(direct)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
  }
  return String(direct)
}

const formatNoticeTime = (time) => {
  if (!time) return '-'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
  }
  return String(time)
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const normalizeCarouselItem = (item) => {
  if (!item) return null
  const imageUrl = resolveImageUrl(String(item.imageUrl || item.url || '').trim())
  if (!imageUrl) return null
  return {
    imageUrl,
    title: String(item.title || '').trim(),
    subtitle: String(item.subtitle || '').trim(),
    link: String(item.link || '').trim(),
    linkText: String(item.linkText || '').trim()
  }
}

const loadCarousel = async () => {
  try {
    const res = await request.get('/config/home-carousel')
    if (res.code === 200 && Array.isArray(res.data)) {
      carouselItems.value = res.data.map(normalizeCarouselItem).filter(Boolean)
      currentSlideIndex.value = 0
      return
    }
    carouselItems.value = []
  } catch (e) {
    carouselItems.value = []
  }
}

const loadOrders = async () => {
  try {
    const res = await request.get('/orders/page?page=1&size=5&status=-1')
    if (res.code === 200) {
      orders.value = (res.data?.records || []).map(item => ({
        id: item.id,
        orderNo: item.orderNo,
        totalAmount: item.totalPrice ?? 0,
        createTime: formatOrderTime(item),
        status: getStatusClass(item.status),
        statusText: getStatusText(item.status)
      }))
    }
  } catch (e) {
    orders.value = []
  }
}

const loadNotices = async () => {
  try {
    const res = await request.get('/notices?limit=5')
    if (res.code === 200) {
      notices.value = Array.isArray(res.data) ? res.data : []
    }
  } catch (e) {
    notices.value = []
  }
}

const showNoticeDetail = (notice) => {
  selectedNotice.value = notice
  showModal.value = true
}

const goPrimary = () => {
  if (activeCarousel.value.link) {
    router.push(activeCarousel.value.link)
    return
  }
  router.push(isExpert.value ? '/expert' : '/shop')
}

const goSecondary = () => {
  router.push('/forum')
}

const startCarouselTimer = () => {
  stopCarouselTimer()
  carouselTimer = setInterval(() => {
    const total = carouselItems.value.length || 1
    currentSlideIndex.value = (currentSlideIndex.value + 1) % total
  }, 5000)
}

const stopCarouselTimer = () => {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = null
  }
}

const prevCarousel = () => {
  const total = carouselItems.value.length || 1
  currentSlideIndex.value = (currentSlideIndex.value - 1 + total) % total
}

const nextCarousel = () => {
  const total = carouselItems.value.length || 1
  currentSlideIndex.value = (currentSlideIndex.value + 1) % total
}

const goToCarousel = (index) => {
  currentSlideIndex.value = index
}

onMounted(async () => {
  decodeRole()
  currentDate.value = new Date().toLocaleString('zh-CN')
  await Promise.all([loadCarousel(), loadOrders(), loadNotices()])
  startCarouselTimer()
})

onUnmounted(() => {
  stopCarouselTimer()
})
</script>

<style scoped>
.home {
  max-width: 1440px;
  margin: 0 auto;
}

.hero-carousel {
  position: relative;
  margin-bottom: 24px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-soft);
}

.carousel-stage {
  height: 260px;
  position: relative;
  background: transparent;
}

.carousel-image,
.carousel-fallback {
  width: 100%;
  height: 100%;
  object-fit: contain;
  object-position: center;
}

.carousel-fallback {
  background:
    radial-gradient(420px 180px at 10% 10%, rgba(255, 255, 255, 0.24), transparent 60%),
    linear-gradient(120deg, #14b8a6 0%, #0d9488 100%);
}

.carousel-overlay {
  position: absolute;
  inset: 0;
  padding: 28px 30px;
  background: transparent;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
}

.carousel-overlay h2 {
  font-size: 34px;
  font-weight: 700;
  margin: 10px 0 8px;
  letter-spacing: 0.4px;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.55);
}

.carousel-subtitle {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.94);
  margin-bottom: 16px;
  max-width: min(760px, 90%);
  line-height: 1.65;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.45);
}

.role-badge {
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.32);
  backdrop-filter: blur(6px);
  color: #fff;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
}

.carousel-actions {
  display: flex;
  gap: 10px;
}

.hero-dots {
  position: absolute;
  left: 30px;
  bottom: 14px;
  display: flex;
  gap: 9px;
  z-index: 3;
}

.hero-dot {
  width: 24px;
  height: 7px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
  cursor: pointer;
}

.hero-dot.active {
  background: #fff;
}

.arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: none;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.86);
  color: #0f766e;
  font-size: 26px;
  line-height: 1;
  z-index: 3;
}

.arrow.prev {
  left: 10px;
}

.arrow.next {
  right: 10px;
}

.btn-red,
.btn-green,
.btn-view-all,
.btn-action {
  border: none;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 700;
}

.btn-red {
  background: #fff;
  color: #14b8a6;
  padding: 10px 18px;
  font-size: 14px;
}

.btn-green {
  background: rgba(255, 255, 255, 0.18);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.24);
  padding: 10px 18px;
  font-size: 14px;
}

.quick-links {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
  margin-bottom: 24px;
}

.link-card,
.notice-board,
.recent-orders,
.modal-content {
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.link-card {
  padding: 28px 22px;
  cursor: pointer;
  border: 1px solid rgba(94, 234, 212, 0.18);
  transition: transform 0.18s ease, box-shadow 0.2s ease;
}

.link-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 22px rgba(20, 184, 166, 0.18);
}

.link-icon {
  width: 56px;
  height: 56px;
  border-radius: 18px;
  background: linear-gradient(135deg, rgba(20, 184, 166, 0.18), rgba(45, 212, 191, 0.26));
  margin-bottom: 16px;
  position: relative;
}

.link-icon::after {
  content: '';
  position: absolute;
  inset: 16px;
  border-radius: 10px;
  background: rgba(13, 148, 136, 0.7);
}

.link-title {
  font-size: 22px;
  color: #0f172a;
  margin-bottom: 8px;
}

.link-desc {
  color: #64748b;
  font-size: 14px;
  line-height: 1.8;
}

.notice-board,
.recent-orders {
  padding: 24px;
  margin-bottom: 24px;
  border: 1px solid rgba(94, 234, 212, 0.16);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.section-header h3 {
  font-size: 24px;
  color: #0f172a;
}

.notice-tip {
  font-size: 13px;
  color: #0f766e;
  background: rgba(20, 184, 166, 0.12);
  padding: 6px 12px;
  border-radius: 999px;
}

.notice-list,
.orders-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notice-item,
.order-item {
  border: 1px solid rgba(94, 234, 212, 0.2);
  border-radius: 14px;
  background: #f8fbff;
  padding: 16px;
}

.notice-item {
  cursor: pointer;
}

.notice-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 8px;
}

.notice-title,
.order-id {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.notice-time,
.order-detail {
  color: #64748b;
  font-size: 13px;
}

.notice-content {
  color: #475569;
  line-height: 1.8;
}

.btn-view-all {
  background: var(--brand);
  color: #fff;
  padding: 8px 16px;
}

.order-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.order-badge {
  min-width: 88px;
  text-align: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.order-badge.pending {
  background: #fef3c7;
  color: #92400e;
}

.order-badge.to-ship {
  background: #d1fae5;
  color: #065f46;
}

.order-badge.shipping {
  background: #dbeafe;
  color: #1d4ed8;
}

.order-badge.done {
  background: #dcfce7;
  color: #166534;
}

.order-badge.canceled {
  background: #fee2e2;
  color: #991b1b;
}

.price {
  color: var(--brand-strong);
  font-weight: 700;
}

.btn-action {
  background: rgba(20, 184, 166, 0.12);
  color: var(--brand-strong);
  padding: 8px 14px;
}

.empty {
  text-align: center;
  padding: 18px;
  color: #94a3b8;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  z-index: 200;
}

.modal-content {
  width: min(760px, 100%);
  padding: 24px;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.modal-header h3 {
  color: #0f172a;
  font-size: 22px;
}

.modal-close {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 50%;
  background: rgba(20, 184, 166, 0.12);
  color: var(--brand-strong);
  cursor: pointer;
  font-size: 20px;
}

.modal-time {
  margin-bottom: 12px;
  color: #64748b;
  font-size: 13px;
}

.modal-text {
  color: #334155;
  line-height: 1.9;
}

@media (max-width: 980px) {
  .carousel-stage,
  .order-item {
    height: 220px;
  }

  .quick-links {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .home {
    padding-bottom: 18px;
  }

  .hero-carousel,
  .notice-board,
  .recent-orders {
    margin-bottom: 18px;
  }

  .carousel-stage {
    height: 210px;
  }

  .carousel-overlay {
    padding: 18px;
  }

  .carousel-overlay h2 {
    font-size: 24px;
  }

  .quick-links {
    grid-template-columns: 1fr;
  }

  .arrow {
    display: none;
  }

  .section-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
}
</style>
