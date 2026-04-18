<template>
  <div class="product-detail">
    <div class="container">
      <div class="detail-content">
        <div class="product-gallery">
          <div class="product-img">
            <img v-if="activeImage" :src="resolveImageUrl(activeImage)" alt="商品图片" />
            <div v-else class="img-placeholder">暂无商品图片</div>
          </div>
          <div class="thumb-list" v-if="images.length > 1">
            <button
              v-for="(img, idx) in images"
              :key="`${img}-${idx}`"
              type="button"
              class="thumb-item"
              :class="{ active: img === activeImage }"
              @click="activeImage = img"
            >
              <img :src="resolveImageUrl(img)" alt="商品缩略图" />
            </button>
          </div>
        </div>
        <div class="info">
          <h1>{{ product.name }}</h1>
          <p class="price">¥{{ product.price }}</p>
          <p class="meta">分类：{{ product.category || '未分类' }} ｜ 库存：{{ product.stock ?? 0 }}</p>
          <p class="desc">{{ product.description || defaultDescription }}</p>
          <div class="specs-section">
            <h3>规格参数</h3>
            <div class="spec-grid">
              <div class="spec-item"><span>产地</span><strong>{{ specs.origin || '待补充' }}</strong></div>
              <div class="spec-item"><span>等级</span><strong>{{ specs.grade || '待补充' }}</strong></div>
              <div class="spec-item"><span>净含量</span><strong>{{ specs.weight || '待补充' }}</strong></div>
              <div class="spec-item"><span>口感</span><strong>{{ specs.taste || '待补充' }}</strong></div>
              <div class="spec-item"><span>保质期</span><strong>{{ specs.shelfLife || '待补充' }}</strong></div>
            </div>
          </div>
          <div class="actions">
            <button class="btn-secondary" @click="addCart">加入购物车</button>
            <button class="btn-primary" @click="buyNow">立即购买</button>
          </div>
        </div>
      </div>

      <div class="review-section">
        <h3>商品评价（{{ reviewTotal }}）</h3>
        <div v-if="reviewError" class="review-empty">{{ reviewError }}</div>
        <div v-else-if="reviews.length === 0" class="review-empty">暂无评价，欢迎购买后评价。</div>
        <div v-else class="review-list">
          <div class="review-item" v-for="item in reviews" :key="item.id">
            <div class="review-head">
              <div class="review-user">
                <img v-if="item.userAvatar" :src="resolveImageUrl(item.userAvatar)" alt="用户头像" class="review-avatar" />
                <div v-else class="review-avatar review-avatar-fallback">{{ getNameText(item.username, item.userId) }}</div>
                <span>{{ item.username || `用户${item.userId || ''}` }}</span>
              </div>
              <span class="review-time">{{ formatTime(item.createTime) }}</span>
            </div>
            <div class="review-rating">{{ renderStars(item.rating) }}</div>
            <p class="review-content">{{ item.content || '该用户未填写评价内容。' }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { addBrowseHistory } from '@/utils/history'

const route = useRoute()
const router = useRouter()
const product = ref({})
const images = ref([])
const activeImage = ref('')
const specs = ref({
  origin: '',
  grade: '',
  weight: '',
  taste: '',
  shelfLife: ''
})
const reviews = ref([])
const reviewTotal = ref(0)
const reviewError = ref('')
const defaultDescription = '该商品来自优质产区，米粒饱满，口感香软，适合日常家庭食用。'

const parseImages = (rawImages) => {
  if (!rawImages) return []
  if (Array.isArray(rawImages)) {
    return rawImages.map(item => String(item || '').trim()).filter(Boolean)
  }
  if (typeof rawImages === 'string') {
    const raw = rawImages.trim()
    if (!raw) return []
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        return parsed.map(item => String(item || '').trim()).filter(Boolean)
      }
      if (typeof parsed === 'string' && parsed.trim()) {
        return [parsed.trim()]
      }
    } catch (e) {
      if (raw.includes(',')) {
        return raw.split(',').map(item => item.trim()).filter(Boolean)
      }
      return [raw]
    }
  }
  return []
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const parseSpecs = (raw) => {
  if (!raw) {
    return {
      origin: '',
      grade: '',
      weight: '',
      taste: '',
      shelfLife: ''
    }
  }
  if (typeof raw === 'object') {
    return {
      origin: raw.origin || '',
      grade: raw.grade || '',
      weight: raw.weight || '',
      taste: raw.taste || '',
      shelfLife: raw.shelfLife || ''
    }
  }
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return {
        origin: parsed.origin || '',
        grade: parsed.grade || '',
        weight: parsed.weight || '',
        taste: parsed.taste || '',
        shelfLife: parsed.shelfLife || ''
      }
    } catch (e) {
      const loose = {
        origin: '',
        grade: '',
        weight: '',
        taste: '',
        shelfLife: ''
      }
      raw.replace(/([a-zA-Z]+):([^,{}]+)/g, (_, k, v) => {
        if (k === 'origin') loose.origin = String(v || '').trim()
        if (k === 'grade') loose.grade = String(v || '').trim()
        if (k === 'weight') loose.weight = String(v || '').trim()
        if (k === 'taste') loose.taste = String(v || '').trim()
        if (k === 'shelfLife') loose.shelfLife = String(v || '').trim()
        return ''
      })
      return loose
    }
  }
  return {
    origin: '',
    grade: '',
    weight: '',
    taste: '',
    shelfLife: ''
  }
}

const resolveReviewPayload = (res) => {
  if (res?.data?.records !== undefined || res?.data?.total !== undefined) return res.data
  if (res?.data?.data?.records !== undefined || res?.data?.data?.total !== undefined) return res.data.data
  if (res?.records !== undefined || res?.total !== undefined) return res
  return {}
}

const loadReviews = async () => {
  reviewError.value = ''
  try {
    const res = await request.get(`/products/${route.params.id}/reviews?page=1&size=20`)
    const payload = resolveReviewPayload(res)
    reviews.value = Array.isArray(payload?.records) ? payload.records : []
    reviewTotal.value = Number(payload?.total ?? reviews.value.length)
  } catch (e) {
    reviews.value = []
    reviewTotal.value = 0
    reviewError.value = '评价加载失败，请重试'
    console.error('加载商品评价失败:', e)
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) return parsed.toLocaleString('zh-CN')
  return String(time)
}

const renderStars = (rating) => {
  const count = Math.max(1, Math.min(5, Number(rating || 5)))
  return `${'★'.repeat(count)}${'☆'.repeat(5 - count)}`
}

const getNameText = (name, id) => {
  const value = String(name || '').trim()
  if (value) return value.slice(0, 1).toUpperCase()
  return String(id || 'U').slice(0, 1).toUpperCase()
}

const loadProductDetail = async () => {
  const res = await request.get(`/products/${route.params.id}`)
  product.value = res.data
  images.value = parseImages(res.data?.images)
  activeImage.value = images.value[0] || ''
  specs.value = parseSpecs(res.data?.specs)
  if (product.value?.id) {
    try {
      addBrowseHistory({
        type: 'PRODUCT',
        id: product.value.id,
        title: product.value.name,
        subtitle: `￥${product.value.price || 0}`,
        path: `/product/${product.value.id}`
      })
    } catch (e) {
      console.warn('记录浏览历史失败:', e)
    }
  }
}

onMounted(async () => {
  await loadProductDetail()
  await loadReviews()
})

watch(
  () => route.params.id,
  async (id, oldId) => {
    if (!id || id === oldId) return
    await loadProductDetail()
    await loadReviews()
  }
)

const addCart = async () => {
  await request.post('/cart/add', {
    productId: product.value.id,
    quantity: 1
  })
  alert('已加入购物车')
}

const buyNow = async () => {
  await request.post('/cart/add', {
    productId: product.value.id,
    quantity: 1
  })
  router.push('/checkout')
}
</script>

<style scoped>
.product-detail {
  padding-top: 26px;
  padding-bottom: 40px;
}

.detail-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26px;
  align-items: center;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  padding: 24px;
  box-shadow: 0 10px 30px rgba(15, 40, 70, 0.08);
}

.product-gallery {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.product-img {
  width: 100%;
  height: 340px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  border-radius: 14px;
  overflow: hidden;
}

.product-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.img-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 14px;
}

.thumb-list {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

.thumb-item {
  width: 64px;
  height: 64px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #dbe5f2;
  padding: 0;
  cursor: pointer;
  background: #fff;
  flex-shrink: 0;
}

.thumb-item.active {
  border-color: rgba(15, 107, 207, 0.55);
  box-shadow: 0 0 0 2px rgba(15, 107, 207, 0.16);
}

.thumb-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.info h1 {
  font-size: 34px;
  font-weight: 700;
  margin-bottom: 12px;
}

.price {
  font-size: 30px;
  color: #dc2626;
  font-weight: 700;
  margin-bottom: 12px;
}

.desc {
  font-size: 15px;
  color: #475467;
  line-height: 1.6;
  margin-bottom: 16px;
}

.meta {
  color: #667085;
  margin-bottom: 12px;
  font-size: 14px;
}

.specs-section {
  margin-bottom: 24px;
}

.specs-section h3 {
  font-size: 17px;
  margin-bottom: 10px;
  color: #0f172a;
}

.spec-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.spec-item {
  background: #f7fbff;
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
  border: 1px solid #e7eff9;
}

.spec-item span {
  color: #6e6e73;
}

.spec-item strong {
  color: #111827;
}

.actions {
  display: flex;
  gap: 12px;
}

.review-section {
  margin-top: 20px;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  padding: 18px;
}

.review-section h3 {
  margin-bottom: 12px;
  font-size: 20px;
  color: #0f172a;
}

.review-empty {
  color: #98a2b3;
  font-size: 14px;
  padding: 10px 0;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-item {
  border: 1px solid #e7eff9;
  border-radius: 10px;
  padding: 12px;
  background: #f8fbff;
}

.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.review-user {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #0f172a;
  font-weight: 600;
}

.review-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
}

.review-avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
}

.review-time {
  color: #94a3b8;
  font-size: 12px;
}

.review-rating {
  margin-top: 8px;
  color: #f59e0b;
  letter-spacing: 1px;
}

.review-content {
  margin-top: 8px;
  color: #475467;
  line-height: 1.6;
  font-size: 14px;
}

.btn-secondary {
  padding: 11px 20px;
  border-radius: 10px;
  background: #fff;
  color: var(--brand);
  border: 1px solid rgba(15, 107, 207, 0.34);
  cursor: pointer;
  font-weight: 600;
}

.btn-primary {
  padding: 11px 20px;
  border-radius: 10px;
  background: var(--brand);
  border: none;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
}

@media (max-width: 768px) {
  .product-detail {
    padding-top: 8px;
  }

  .detail-content {
    grid-template-columns: 1fr;
    padding: 16px;
    gap: 14px;
  }

  .product-img {
    height: 220px;
  }

  .info h1 {
    font-size: 24px;
  }

  .price {
    font-size: 24px;
  }

  .spec-grid {
    grid-template-columns: 1fr;
  }

  .actions {
    flex-direction: column;
  }
}
</style>
