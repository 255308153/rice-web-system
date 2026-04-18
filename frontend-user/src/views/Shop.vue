<template>
  <div class="shop-page">
    <section class="search-card">
      <select v-model="searchMode">
        <option value="PRODUCT">商品搜索</option>
        <option value="SHOP">店铺搜索</option>
      </select>
      <input
        v-model.trim="searchKeyword"
        :placeholder="searchMode === 'PRODUCT' ? '输入商品名称搜索' : '输入店铺名称搜索'"
        @keyup.enter="handleSearch"
      />
      <button class="btn-search" @click="handleSearch">搜索</button>
    </section>

    <section class="shop-results">
      <div class="section-head">
        <h3>店铺筛选</h3>
      </div>
      <div class="shop-chip-group">
        <button class="shop-chip" :class="{ active: browsingMode === 'ALL' }" @click="chooseAllProducts">全部商品</button>
        <button class="shop-chip" :class="{ active: browsingMode === 'RECOMMEND' }" @click="chooseRecommendations">猜你喜欢</button>
        <button
          v-for="shop in shopResults"
          :key="shop.id"
          class="shop-chip"
          :class="{ active: browsingMode === 'SHOP' && Number(selectedShopId) === Number(shop.id) }"
          @click="chooseShop(shop.id)"
        >
          {{ shop.name || `店铺#${shop.id}` }}
        </button>
      </div>
      <div v-if="browsingMode === 'SHOP' && selectedShopId" class="shop-actions">
        <button class="btn-enter-shop" @click="goShop(selectedShopId)">进入当前店铺</button>
      </div>
    </section>

    <section class="filter-card">
      <button class="filter-pill" :class="{ active: !isRecommendationMode && sortBy === 'time' }" @click="chooseComprehensive">
        综合
      </button>
      <button class="filter-pill" :class="{ active: !isRecommendationMode && sortBy === 'sales' }" @click="chooseSales">
        销量
      </button>
      <button class="filter-pill" :class="{ active: isRecommendationMode }" @click="chooseRecommendations">
        猜你喜欢
      </button>
    </section>

    <section class="product-card-wrapper">
      <div class="section-head">
        <div>
          <h3>{{ currentSectionTitle }}</h3>
        </div>
        <div class="pager-inline">
          <button :disabled="currentPage <= 1" @click="changeCurrentPage(-1)">上一页</button>
          <span>{{ currentPage }} / {{ currentTotalPages }}</span>
          <button :disabled="currentPage >= currentTotalPages" @click="changeCurrentPage(1)">下一页</button>
        </div>
      </div>

      <div v-if="currentLoading" class="section-empty">{{ browsingMode === 'RECOMMEND' ? '推荐商品加载中...' : '商品加载中...' }}</div>
      <div v-else-if="!isRecommendationMode && loadError" class="section-empty">{{ loadError }}</div>
      <div v-else-if="currentDisplayItems.length === 0" class="section-empty">{{ currentEmptyText }}</div>

      <div v-else class="product-grid">
        <div class="product-item" v-for="item in currentDisplayItems" :key="`${browsingMode}-${item.id}`">
          <div class="product-cover">
            <img v-if="getCoverImage(item.images)" :src="resolveImageUrl(getCoverImage(item.images))" alt="商品图片" />
            <div v-else class="cover-placeholder">无图</div>
          </div>
          <div class="product-body">
            <div class="product-title">{{ item.name }}</div>
            <button class="shop-link" @click="goShop(item.shopId)">{{ getShopName(item.shopId) }}</button>
            <div v-if="isRecommendationMode" class="recommend-badge">猜你喜欢</div>
            <div class="product-tags">
              <span>{{ item.category || '大米商品' }}</span>
              <span>{{ getProductOrigin(item) || '待补充产地' }}</span>
            </div>
            <p class="product-desc">{{ getDescription(item) }}</p>
            <div class="product-footer">
              <strong>¥{{ item.price }}</strong>
              <span>库存 {{ item.stock || 0 }}</span>
            </div>
            <div class="product-actions">
              <button class="btn-detail" @click="viewDetail(item.id)">查看详情</button>
              <button class="btn-cart" @click="addToCart(item)">加入购物车</button>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()
const allProducts = ref([])
const shopResults = ref([])
const shopMap = ref({})
const selectedShopId = ref(null)
const browsingMode = ref('ALL')
const searchMode = ref('PRODUCT')
const searchKeyword = ref('')
const activeProductKeyword = ref('')
const sortBy = ref('time')
const productPage = ref(1)
const recommendationPage = ref(1)
const recommendations = ref([])
const recommendationTotal = ref(0)
const loadingProducts = ref(false)
const loadingRecommendations = ref(false)
const loadError = ref('')
const RECOMMEND_MIN_COUNT = 10
const isRecommendationMode = computed(() => browsingMode.value === 'RECOMMEND')

const filteredProducts = computed(() => {
  let list = [...allProducts.value]

  if (sortBy.value === 'sales') {
    list.sort((a, b) => Number(b.sales || 0) - Number(a.sales || 0))
  } else {
    list.sort((a, b) => Number(b.id || 0) - Number(a.id || 0))
  }

  return list
})

const productTotalPages = computed(() => Math.max(1, Math.ceil(filteredProducts.value.length / 10)))
const recommendationTotalPages = computed(() => Math.max(1, Math.ceil(recommendationTotal.value / 10)))
const currentPage = computed(() => (isRecommendationMode.value ? recommendationPage.value : productPage.value))
const currentTotalPages = computed(() => (isRecommendationMode.value ? recommendationTotalPages.value : productTotalPages.value))
const currentLoading = computed(() => (isRecommendationMode.value ? loadingRecommendations.value : loadingProducts.value))
const currentDisplayItems = computed(() => (isRecommendationMode.value ? recommendations.value : productPageData.value))
const currentSectionTitle = computed(() => {
  if (browsingMode.value === 'RECOMMEND') return '猜你喜欢'
  if (browsingMode.value === 'SHOP' && selectedShopId.value) return `${getShopName(selectedShopId.value)} 商品`
  return '全部商品'
})
const currentEmptyText = computed(() => (
  isRecommendationMode.value ? '当前暂无推荐商品。' : '暂无符合条件的商品'
))

const productPageData = computed(() => {
  const start = (productPage.value - 1) * 10
  return filteredProducts.value.slice(start, start + 10)
})

const fillRecommendationsToMinimum = (items) => {
  const deduped = []
  const seenIds = new Set()

  for (const item of items || []) {
    const id = Number(item?.id)
    if (!Number.isFinite(id) || seenIds.has(id)) continue
    seenIds.add(id)
    deduped.push(item)
  }

  if (deduped.length >= RECOMMEND_MIN_COUNT) {
    return deduped.slice(0, RECOMMEND_MIN_COUNT)
  }

  const fallbackPool = [...allProducts.value].sort((a, b) => Number(b?.sales || 0) - Number(a?.sales || 0))
  for (const item of fallbackPool) {
    const id = Number(item?.id)
    if (!Number.isFinite(id) || seenIds.has(id)) continue
    seenIds.add(id)
    deduped.push(item)
    if (deduped.length >= RECOMMEND_MIN_COUNT) break
  }

  return deduped
}

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

const parseSpecs = (raw) => {
  if (!raw) return {}
  if (typeof raw === 'object') return raw
  try {
    const parsed = JSON.parse(raw)
    return typeof parsed === 'object' && parsed ? parsed : {}
  } catch (e) {
    return {}
  }
}

const getProductOrigin = (item) => {
  if (item?.origin) return String(item.origin)
  const specs = parseSpecs(item?.specs)
  return String(specs.origin || '')
}

const getDescription = (item) => {
  if (item?.description) return item.description
  return `精选${item?.category || '大米'}，适合家庭日常食用。`
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const getCoverImage = (images) => {
  const list = parseImages(images)
  return list[0] || ''
}

const mergeShopMap = (shops) => {
  const next = { ...shopMap.value }
  ;(shops || []).forEach(shop => {
    if (shop?.id != null) {
      next[shop.id] = shop
    }
  })
  shopMap.value = next
}

const loadShops = async (keyword = '') => {
  try {
    const res = await request.get('/shops', {
      params: {
        page: 1,
        size: 50,
        keyword: keyword || undefined
      }
    })
    if (res.code === 200) {
      shopResults.value = res.data?.records || []
      mergeShopMap(shopResults.value)
      return
    }
  } catch (e) {
  }
  shopResults.value = []
}

const loadProducts = async () => {
  loadingProducts.value = true
  loadError.value = ''
  try {
    const res = await request.get('/products', {
      params: {
        page: 1,
        size: 200,
        keyword: activeProductKeyword.value || undefined,
        shopId: selectedShopId.value || undefined
      }
    })
    if (res.code === 200) {
      const list = res.data?.records || res.data || []
      allProducts.value = list

      if (recommendationPage.value === 1 && recommendations.value.length > 0 && recommendations.value.length < RECOMMEND_MIN_COUNT) {
        const nextRecords = fillRecommendationsToMinimum(recommendations.value)
        recommendations.value = nextRecords
        recommendationTotal.value = Math.max(recommendationTotal.value, nextRecords.length)
      }

      const shopIds = [...new Set(list.map(item => item.shopId).filter(Boolean))]
      const missing = shopIds.filter(id => !shopMap.value[id])
      if (missing.length > 0) {
        await loadShops('')
      }
      return
    }
    loadError.value = res.message || '商品加载失败，请稍后重试'
    allProducts.value = []
  } catch (e) {
    loadError.value = '商品加载失败，请确认后端服务正常'
    allProducts.value = []
  } finally {
    loadingProducts.value = false
  }
}

const loadRecommendations = async () => {
  loadingRecommendations.value = true
  try {
    const res = await request.get('/products/recommendations', {
      params: {
        page: recommendationPage.value,
        size: 10
      }
    })
    if (res.code === 200) {
      const rawRecords = res.data?.records || []
      const nextRecords = recommendationPage.value === 1
        ? fillRecommendationsToMinimum(rawRecords)
        : rawRecords
      recommendations.value = nextRecords
      recommendationTotal.value = Math.max(
        Number(res.data?.total || rawRecords.length),
        nextRecords.length
      )
      return
    }
  } catch (e) {
  } finally {
    loadingRecommendations.value = false
  }
  recommendations.value = []
  recommendationTotal.value = 0
}

const handleSearch = async () => {
  if (searchMode.value === 'PRODUCT') {
    browsingMode.value = 'ALL'
    selectedShopId.value = null
    activeProductKeyword.value = searchKeyword.value
    productPage.value = 1
    await loadProducts()
    return
  }

  browsingMode.value = 'ALL'
  selectedShopId.value = null
  activeProductKeyword.value = ''
  await loadShops(searchKeyword.value)
  await loadProducts()
}

const chooseAllProducts = async () => {
  browsingMode.value = 'ALL'
  selectedShopId.value = null
  productPage.value = 1
  await loadProducts()
}

const chooseRecommendations = async () => {
  browsingMode.value = 'RECOMMEND'
  selectedShopId.value = null
  recommendationPage.value = 1
  if (recommendations.value.length === 0) {
    await loadRecommendations()
  }
}

const chooseComprehensive = async () => {
  sortBy.value = 'time'
  browsingMode.value = 'ALL'
  selectedShopId.value = null
  productPage.value = 1
  await loadProducts()
}

const chooseSales = async () => {
  sortBy.value = 'sales'
  browsingMode.value = 'ALL'
  selectedShopId.value = null
  productPage.value = 1
  await loadProducts()
}

const chooseShop = async (shopId) => {
  browsingMode.value = 'SHOP'
  selectedShopId.value = shopId == null ? null : Number(shopId)
  productPage.value = 1
  await loadProducts()
}

const changeProductPage = (delta) => {
  const next = productPage.value + delta
  if (next < 1 || next > productTotalPages.value) return
  productPage.value = next
}

const changeRecommendationPage = async (delta) => {
  const next = recommendationPage.value + delta
  if (next < 1 || next > recommendationTotalPages.value) return
  recommendationPage.value = next
  await loadRecommendations()
}

const changeCurrentPage = async (delta) => {
  if (isRecommendationMode.value) {
    await changeRecommendationPage(delta)
    return
  }
  changeProductPage(delta)
}

const getShopName = (shopId) => {
  if (!shopId) return '未知店铺'
  return shopMap.value[shopId]?.name || `店铺#${shopId}`
}

const addToCart = async (item) => {
  await request.post('/cart/add', { productId: item.id, quantity: 1 })
  alert('已加入购物车')
}

const viewDetail = (id) => {
  router.push(`/product/${id}`)
}

const goShop = (shopId) => {
  if (!shopId) return
  router.push(`/shop/store/${shopId}`)
}

watch(sortBy, () => {
  productPage.value = 1
})

onMounted(async () => {
  await Promise.all([loadShops(''), loadProducts(), loadRecommendations()])
})
</script>

<style scoped>
.shop-page {
  max-width: 1360px;
  margin: 0 auto;
  display: grid;
  gap: 18px;
}

.search-card,
.shop-results,
.filter-card,
.product-card-wrapper {
  background: #fff;
  border: 1px solid rgba(94, 234, 212, 0.18);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.btn-search,
.btn-enter-shop,
.btn-detail,
.btn-cart,
.pager-inline button {
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 700;
}

.search-card,
.filter-card {
  padding: 16px;
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) 120px;
  gap: 12px;
}

.filter-card {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.search-card select,
.search-card input,
.filter-pill {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(94, 234, 212, 0.34);
  border-radius: 10px;
  background: #ffffff;
}

.filter-pill {
  cursor: pointer;
  font-weight: 700;
  color: var(--brand-strong);
}

.filter-pill.active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

.btn-search {
  background: var(--brand);
  color: #fff;
}

.shop-results,
.product-card-wrapper {
  padding: 18px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head h3 {
  font-size: 22px;
  color: var(--text-main);
  margin-bottom: 4px;
}

.section-head p {
  color: var(--text-muted);
  line-height: 1.7;
  font-size: 13px;
}

.shop-chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.shop-chip {
  border: 1px solid rgba(94, 234, 212, 0.28);
  background: rgba(45, 212, 191, 0.08);
  color: var(--brand-strong);
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
  font-weight: 700;
}

.shop-chip.active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

.shop-actions {
  margin-top: 12px;
}

.btn-enter-shop {
  background: rgba(20, 184, 166, 0.12);
  color: var(--brand-strong);
  padding: 10px 14px;
}

.pager-inline {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-muted);
}

.pager-inline button {
  background: rgba(20, 184, 166, 0.1);
  color: var(--brand-strong);
  padding: 8px 12px;
}

.pager-inline button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}

.product-item {
  border: 1px solid rgba(94, 234, 212, 0.16);
  border-radius: 14px;
  overflow: hidden;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 22px rgba(20, 184, 166, 0.08);
}

.product-cover {
  height: 190px;
  background: linear-gradient(160deg, rgba(20, 184, 166, 0.08), rgba(45, 212, 191, 0.18));
  overflow: hidden;
}

.product-cover img {
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
  color: var(--text-muted);
  font-weight: 700;
}

.product-body {
  padding: 14px;
  display: grid;
  gap: 8px;
}

.product-title {
  font-size: 18px;
  font-weight: 800;
  color: var(--text-main);
}

.product-desc,
.shop-link {
  color: var(--text-muted);
}

.shop-link {
  background: transparent;
  border: none;
  padding: 0;
  text-align: left;
  cursor: pointer;
  font-weight: 700;
}

.product-footer strong {
  font-size: 22px;
  color: var(--brand-strong);
}

.recommend-badge {
  width: fit-content;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.12);
  color: var(--brand-strong);
  font-size: 12px;
  font-weight: 700;
}

.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.product-tags span {
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(45, 212, 191, 0.12);
  color: var(--brand-strong);
  font-size: 12px;
  font-weight: 700;
}

.product-desc {
  line-height: 1.7;
  min-height: 48px;
}

.product-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: var(--text-muted);
}

.product-actions {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.btn-detail {
  background: rgba(20, 184, 166, 0.1);
  color: var(--brand-strong);
  padding: 10px 0;
}

.btn-cart {
  background: var(--brand);
  color: #fff;
  padding: 10px 0;
}

.section-empty {
  padding: 28px 12px;
  text-align: center;
  color: var(--text-muted);
  border: 1px dashed rgba(94, 234, 212, 0.34);
  border-radius: 16px;
  background: rgba(45, 212, 191, 0.04);
}

@media (max-width: 1200px) {
  .product-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .search-card,
  .filter-card,
  .section-head {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: flex-start;
  }

  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .product-grid {
    grid-template-columns: 1fr;
  }
}
</style>
