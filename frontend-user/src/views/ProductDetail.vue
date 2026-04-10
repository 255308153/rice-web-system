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
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
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

onMounted(async () => {
  const res = await request.get(`/products/${route.params.id}`)
  product.value = res.data
  images.value = parseImages(res.data?.images)
  activeImage.value = images.value[0] || ''
  specs.value = parseSpecs(res.data?.specs)
  if (product.value?.id) {
    addBrowseHistory({
      type: 'PRODUCT',
      id: product.value.id,
      title: product.value.name,
      subtitle: `￥${product.value.price || 0}`,
      path: `/product/${product.value.id}`
    })
  }
})

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
