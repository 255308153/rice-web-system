<template>
  <div class="shop-store">
    <div class="store-hero" v-if="shop">
      <div class="store-main">
        <div class="store-avatar" v-if="shop.avatar">
          <img :src="resolveImageUrl(shop.avatar)" alt="店铺头像" />
        </div>
        <div class="store-avatar" v-else>{{ (shop.name || '店').slice(0, 1) }}</div>
        <div class="store-info">
          <h2>{{ shop.name || '店铺' }}</h2>
          <p>{{ shop.description || '该店铺暂未填写简介' }}</p>
          <div class="store-meta">
            <span>评分：{{ formatRating(shop.rating) }}</span>
            <span>联系方式：{{ shop.contact || '未公开' }}</span>
          </div>
        </div>
      </div>
      <button class="btn-chat" @click="goChatWithShop">联系店铺客服</button>
    </div>

    <div class="store-toolbar">
      <input v-model.trim="keyword" placeholder="搜索该店铺商品" @keyup.enter="loadProducts" />
      <select v-model="sortBy" @change="loadProducts">
        <option value="time">最新上架</option>
        <option value="hot">热销优先</option>
      </select>
      <button class="btn-filter" @click="loadProducts">筛选</button>
    </div>

    <div class="products">
      <div class="product-card" v-for="item in products" :key="item.id">
        <div class="product-cover">
          <video
            v-if="getCoverImage(item.images) && isVideoUrl(getCoverImage(item.images))"
            :src="resolveImageUrl(getCoverImage(item.images))"
            muted
            preload="metadata"
          />
          <img v-else-if="getCoverImage(item.images)" :src="resolveImageUrl(getCoverImage(item.images))" alt="商品图片" />
          <div v-else class="cover-placeholder">无图</div>
        </div>
        <div class="product-info">
          <h3>{{ item.name }}</h3>
          <p class="category">{{ item.category || '未分类' }}</p>
          <p class="price">¥{{ item.price }}</p>
          <p class="stock">库存：{{ item.stock ?? 0 }}</p>
          <p class="desc">{{ item.description || '优选稻米，品质稳定。' }}</p>
        </div>
        <div class="product-actions">
          <button class="btn-detail" @click="viewDetail(item.id)">查看详情</button>
          <button class="btn-cart" @click="addToCart(item.id)">加入购物车</button>
        </div>
      </div>
      <div v-if="products.length === 0" class="empty">该店铺暂无符合条件的商品</div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'

const route = useRoute()
const router = useRouter()

const shop = ref(null)
const products = ref([])
const keyword = ref('')
const sortBy = ref('time')

const isVideoUrl = (url) => /\.(mp4|webm|ogg)(\?|$)/i.test(String(url || ''))

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

const getCoverImage = (images) => {
  const list = parseImages(images)
  return list[0] || ''
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const formatRating = (rating) => {
  const num = Number(rating)
  if (!Number.isFinite(num)) return '5.0'
  return num.toFixed(1)
}

const loadShop = async () => {
  const res = await request.get(`/shops/${route.params.id}`)
  if (res.code === 200) {
    shop.value = res.data
  } else {
    shop.value = null
  }
}

const loadProducts = async () => {
  const res = await request.get('/products', {
    params: {
      page: 1,
      size: 200,
      shopId: route.params.id,
      keyword: keyword.value || undefined,
      sortBy: sortBy.value
    }
  })
  if (res.code === 200) {
    products.value = res.data?.records || []
  } else {
    products.value = []
  }
}

const viewDetail = (id) => {
  router.push(`/product/${id}`)
}

const addToCart = async (productId) => {
  await request.post('/cart/add', { productId, quantity: 1 })
  alert('已加入购物车')
}

const goChatWithShop = async () => {
  if (!shop.value?.userId) {
    alert('该店铺暂未绑定客服账号')
    return
  }
  const res = await request.post('/conversations/start', { receiverId: shop.value.userId })
  if (res.code === 200 && res.data?.id) {
    router.push(`/messages?cid=${res.data.id}`)
    return
  }
  alert(res.message || '发起会话失败')
}

onMounted(async () => {
  await Promise.all([loadShop(), loadProducts()])
})
</script>

<style scoped>
.shop-store {
  max-width: 1260px;
  margin: 0 auto;
}

.store-hero {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  border-radius: 14px;
  padding: 18px;
  margin-bottom: 14px;
  color: #fff;
  background:
    radial-gradient(320px 180px at 8% 12%, rgba(255,255,255,0.2), transparent 68%),
    linear-gradient(125deg, #0f6bcf 0%, #0f766e 100%);
}

.store-main {
  display: flex;
  align-items: center;
  gap: 14px;
}

.store-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: rgba(255,255,255,0.22);
  border: 1px solid rgba(255,255,255,0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
  overflow: hidden;
}

.store-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.store-info h2 {
  margin-bottom: 4px;
}

.store-info p {
  opacity: 0.95;
  margin-bottom: 4px;
}

.store-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  opacity: 0.9;
}

.btn-chat {
  border: none;
  border-radius: 10px;
  background: #fff;
  color: #0f6bcf;
  font-weight: 700;
  padding: 10px 14px;
  cursor: pointer;
}

.store-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  padding: 12px;
}

.store-toolbar input,
.store-toolbar select {
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 10px;
  background: #fbfdff;
}

.store-toolbar input {
  flex: 1;
}

.btn-filter {
  border: none;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  padding: 10px 14px;
  cursor: pointer;
}

.products {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.product-card {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  padding: 14px;
  display: flex;
  flex-direction: column;
}

.product-cover {
  width: 100%;
  height: 170px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  margin-bottom: 10px;
}

.product-cover img,
.product-cover video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.product-info h3 {
  margin-bottom: 6px;
}

.category {
  color: #667085;
  font-size: 13px;
  margin-bottom: 6px;
}

.price {
  color: #dc2626;
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 6px;
}

.stock {
  color: #98a2b3;
  font-size: 13px;
  margin-bottom: 8px;
}

.desc {
  font-size: 13px;
  color: #475467;
  line-height: 1.5;
  min-height: 58px;
  margin-bottom: 10px;
}

.product-actions {
  display: flex;
  gap: 8px;
}

.btn-detail,
.btn-cart {
  flex: 1;
  border-radius: 8px;
  border: none;
  padding: 9px 12px;
  cursor: pointer;
}

.btn-detail {
  border: 1px solid rgba(15, 107, 207, 0.35);
  color: var(--brand);
  background: #fff;
}

.btn-cart {
  background: var(--brand);
  color: #fff;
}

.empty {
  grid-column: 1 / -1;
  text-align: center;
  color: #98a2b3;
  padding: 26px;
  border: 1px dashed #d7e1ef;
  border-radius: 12px;
  background: #fff;
}

@media (max-width: 900px) {
  .store-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .store-toolbar {
    flex-direction: column;
  }
}
</style>
