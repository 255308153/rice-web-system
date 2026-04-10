<template>
  <div class="cart-page">
    <section class="cart-main">
      <div class="cart-toolbar">
        <h2>购物车</h2>
        <div class="search-box">
          <input v-model.trim="keyword" placeholder="搜索购物车内商品" />
          <button v-if="keyword" class="clear-btn" @click="keyword = ''">清空</button>
        </div>
      </div>

      <div class="cart-head">
        <span class="col-product">商品</span>
        <span class="col-price">单价</span>
        <span class="col-qty">数量</span>
        <span class="col-subtotal">小计</span>
        <span class="col-action">操作</span>
      </div>

      <div class="cart-list">
        <div class="cart-item" v-for="item in filteredItems" :key="item.id">
          <div class="product-col">
            <div class="item-cover" @click="goProduct(item.productId)">
              <img v-if="item.productImage" :src="resolveImageUrl(item.productImage)" alt="商品图片" />
              <div v-else class="cover-placeholder">无图</div>
            </div>
            <button class="item-name" @click="goProduct(item.productId)">
              {{ item.productName || `商品#${item.productId}` }}
            </button>
          </div>

          <div class="price-col">¥{{ formatMoney(item.price) }}</div>

          <div class="qty-col">
            <button class="qty-btn" @click="updateQuantity(item, -1)" :disabled="isUpdating(item.id)">-</button>
            <span>{{ item.quantity }}</span>
            <button class="qty-btn" @click="updateQuantity(item, 1)" :disabled="isUpdating(item.id)">+</button>
          </div>

          <div class="subtotal-col">¥{{ itemSubtotal(item) }}</div>

          <div class="action-col">
            <button class="btn-remove" @click="removeItem(item.id)" :disabled="isUpdating(item.id)">删除</button>
          </div>
        </div>

        <div v-if="filteredItems.length === 0" class="empty">
          {{ keyword ? '没有匹配的商品' : '购物车为空' }}
        </div>
      </div>
    </section>

    <aside class="cart-summary">
      <h3>结算明细</h3>
      <div class="summary-line">
        <span>商品种类</span>
        <strong>{{ cartItems.length }}</strong>
      </div>
      <div class="summary-line">
        <span>商品件数</span>
        <strong>{{ totalCount }}</strong>
      </div>
      <div class="summary-line" v-if="keyword">
        <span>筛选金额</span>
        <strong>¥{{ filteredTotal }}</strong>
      </div>
      <div class="summary-total">
        <span>合计</span>
        <strong>¥{{ total }}</strong>
      </div>
      <button class="btn-checkout" @click="checkout" :disabled="cartItems.length === 0">结算</button>
    </aside>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()
const cartItems = ref([])
const keyword = ref('')
const updatingIds = ref(new Set())

const filteredItems = computed(() => {
  if (!keyword.value) return cartItems.value
  return cartItems.value.filter(item => String(item.productName || '').includes(keyword.value))
})

const total = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + calcSubtotal(item), 0).toFixed(2)
})

const filteredTotal = computed(() => {
  return filteredItems.value.reduce((sum, item) => sum + calcSubtotal(item), 0).toFixed(2)
})

const totalCount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0)
})

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const formatMoney = (value) => {
  return (Number(value) || 0).toFixed(2)
}

const calcSubtotal = (item) => {
  const price = Number(item?.price) || 0
  const quantity = Number(item?.quantity) || 0
  return price * quantity
}

const itemSubtotal = (item) => {
  return calcSubtotal(item).toFixed(2)
}

const isUpdating = (id) => updatingIds.value.has(id)

const setUpdating = (id, active) => {
  const next = new Set(updatingIds.value)
  if (active) next.add(id)
  else next.delete(id)
  updatingIds.value = next
}

const loadCart = async () => {
  try {
    const res = await request.get('/cart/list')
    if (res.code === 200) {
      cartItems.value = res.data || []
    }
  } catch (e) {
    alert('加载购物车失败')
  }
}

const updateQuantity = async (item, delta) => {
  const newQty = Number(item.quantity || 0) + delta
  if (newQty < 1 || isUpdating(item.id)) return

  setUpdating(item.id, true)
  try {
    await request.put(`/cart/${item.id}`, { quantity: newQty })
    await loadCart()
  } catch (e) {
    const msg = e?.response?.data?.message || '更新数量失败'
    alert(msg)
  } finally {
    setUpdating(item.id, false)
  }
}

const removeItem = async (id) => {
  if (isUpdating(id)) return
  setUpdating(id, true)
  try {
    await request.delete(`/cart/${id}`)
    await loadCart()
  } catch (e) {
    const msg = e?.response?.data?.message || '删除失败'
    alert(msg)
  } finally {
    setUpdating(id, false)
  }
}

const goProduct = (id) => {
  if (!id) return
  router.push(`/product/${id}`)
}

const checkout = () => {
  if (cartItems.value.length === 0) {
    alert('购物车为空')
    return
  }
  router.push('/checkout')
}

onMounted(loadCart)
</script>

<style scoped>
.cart-page {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
}

.cart-main {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(15, 40, 70, 0.06);
  padding: 16px;
}

.cart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

h2 {
  font-size: 28px;
  color: #111827;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
}

.search-box input {
  width: 280px;
  max-width: 56vw;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  font-size: 14px;
  outline: none;
}

.search-box input:focus {
  border-color: #fb7d2e;
  box-shadow: 0 0 0 3px rgba(251, 125, 46, 0.14);
}

.clear-btn {
  border: 1px solid #f3d6c3;
  background: #fff7f2;
  color: #c65a14;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12px;
  cursor: pointer;
}

.cart-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px 132px 132px 90px;
  color: #667085;
  font-size: 13px;
  border-bottom: 1px solid var(--line-soft);
  padding: 10px 4px;
}

.cart-list {
  display: flex;
  flex-direction: column;
}

.cart-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px 132px 132px 90px;
  align-items: center;
  gap: 0;
  border-bottom: 1px solid #edf2f7;
  padding: 14px 4px;
}

.product-col {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.item-cover {
  width: 84px;
  height: 84px;
  border-radius: 10px;
  border: 1px solid #dbe5f2;
  overflow: hidden;
  background: #f8fafc;
  cursor: pointer;
  flex: 0 0 auto;
}

.item-cover img {
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
  font-size: 12px;
}

.item-name {
  border: none;
  background: transparent;
  text-align: left;
  padding: 0;
  color: #111827;
  font-size: 14px;
  line-height: 1.5;
  cursor: pointer;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.item-name:hover {
  color: #fb7d2e;
}

.price-col,
.subtotal-col {
  color: #f97316;
  font-weight: 700;
}

.qty-col {
  display: flex;
  align-items: center;
  gap: 8px;
}

.qty-btn {
  width: 30px;
  height: 30px;
  border: 1px solid #d6dbe1;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-weight: 700;
  color: #344054;
}

.qty-btn:disabled,
.btn-remove:disabled,
.btn-checkout:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.btn-remove {
  border: none;
  background: transparent;
  color: #6b7280;
  cursor: pointer;
}

.btn-remove:hover {
  color: #ef4444;
}

.empty {
  padding: 40px 0;
  text-align: center;
  color: #98a2b3;
}

.cart-summary {
  position: sticky;
  top: 92px;
  align-self: start;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  box-shadow: 0 8px 24px rgba(15, 40, 70, 0.08);
  padding: 16px;
}

.cart-summary h3 {
  font-size: 18px;
  margin-bottom: 12px;
  color: #0f172a;
}

.summary-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: #64748b;
  margin-bottom: 8px;
}

.summary-total {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.summary-total span {
  color: #475569;
}

.summary-total strong {
  font-size: 28px;
  color: #f97316;
}

.btn-checkout {
  width: 100%;
  margin-top: 14px;
  border: none;
  background: #fb7d2e;
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  border-radius: 10px;
  padding: 11px 0;
  cursor: pointer;
}

.btn-checkout:hover:not(:disabled) {
  background: #ea670f;
}

@media (max-width: 1024px) {
  .cart-page {
    grid-template-columns: 1fr;
  }

  .cart-summary {
    position: static;
  }
}

@media (max-width: 768px) {
  .cart-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .search-box {
    width: 100%;
  }

  .search-box input {
    width: 100%;
    max-width: none;
  }

  .cart-head {
    display: none;
  }

  .cart-item {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .product-col {
    align-items: flex-start;
  }

  .item-cover {
    width: 72px;
    height: 72px;
  }

  .price-col::before {
    content: '单价: ';
    color: #94a3b8;
    font-weight: 400;
  }

  .subtotal-col::before {
    content: '小计: ';
    color: #94a3b8;
    font-weight: 400;
  }

  .action-col {
    text-align: left;
  }
}
</style>
