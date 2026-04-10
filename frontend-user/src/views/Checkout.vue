<template>
  <div class="checkout">
    <h2>确认订单</h2>
    <div class="address-section">
      <h3>收货地址</h3>
      <button type="button" class="btn-add-address" @click="toggleAddressForm">
        {{ showAddressForm ? '取消新增' : '新增地址' }}
      </button>
      <div v-if="addresses.length === 0" class="empty-address">
        暂无收货地址，请先到个人信息页添加地址
      </div>
      <div v-else class="address-list">
        <label
          v-for="addr in addresses"
          :key="addr.id"
          class="address-card"
          :class="{ active: selectedAddressId === addr.id }"
        >
          <input type="radio" v-model="selectedAddressId" :value="addr.id" />
          <div>
            <div class="address-head">
              <strong>{{ addr.name }}</strong>
              <span>{{ addr.phone }}</span>
              <span v-if="addr.isDefault" class="default-tag">默认</span>
            </div>
            <div>{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}</div>
          </div>
        </label>
      </div>
      <div v-if="showAddressForm" class="address-form">
        <div class="form-row">
          <input v-model.trim="addressForm.name" placeholder="收货人姓名" />
          <input v-model.trim="addressForm.phone" placeholder="手机号" />
        </div>
        <div class="form-row triple">
          <input v-model.trim="addressForm.province" placeholder="省" />
          <input v-model.trim="addressForm.city" placeholder="市" />
          <input v-model.trim="addressForm.district" placeholder="区" />
        </div>
        <input v-model.trim="addressForm.detail" placeholder="详细地址" />
        <div class="form-actions">
          <label class="default-check">
            <input type="checkbox" v-model="addressForm.isDefault" />
            设为默认地址
          </label>
          <button type="button" class="btn-save-address" @click="saveAddress">保存地址</button>
        </div>
      </div>
    </div>

    <div class="order-items">
      <div v-for="item in cartItems" :key="item.id" class="item">
        <span>{{ item.productName }}</span>
        <span>x{{ item.quantity }}</span>
        <span class="price">¥{{ (item.price * item.quantity).toFixed(2) }}</span>
      </div>
    </div>
    <div class="total">总计：¥{{ total }}</div>
    <button @click="submitOrder" class="btn-submit">提交订单</button>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()
const cartItems = ref([])
const addresses = ref([])
const selectedAddressId = ref(null)
const showAddressForm = ref(false)
const addressForm = ref({
  name: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: true
})

const total = computed(() => {
  return cartItems.value.reduce((sum, item) => {
    const price = parseFloat(item.price) || 0
    const quantity = parseInt(item.quantity) || 0
    return sum + price * quantity
  }, 0).toFixed(2)
})

const loadCart = async () => {
  const res = await request.get('/cart/list')
  if (res.code === 200) {
    cartItems.value = res.data || []
  }
}

const loadAddresses = async () => {
  const res = await request.get('/addresses')
  if (res.code === 200) {
    addresses.value = res.data || []
    const defaultAddr = addresses.value.find(a => a.isDefault === 1) || addresses.value[0]
    selectedAddressId.value = defaultAddr ? defaultAddr.id : null
    if (addresses.value.length === 0) {
      showAddressForm.value = true
      addressForm.value.isDefault = true
    }
  }
}

const toggleAddressForm = () => {
  showAddressForm.value = !showAddressForm.value
}

const saveAddress = async () => {
  const payload = {
    name: addressForm.value.name,
    phone: addressForm.value.phone,
    province: addressForm.value.province,
    city: addressForm.value.city,
    district: addressForm.value.district,
    detail: addressForm.value.detail,
    isDefault: addressForm.value.isDefault ? 1 : 0
  }

  if (!payload.name || !payload.phone || !payload.province || !payload.city || !payload.district || !payload.detail) {
    alert('请完整填写收货地址信息')
    return
  }

  const res = await request.post('/addresses', payload)
  if (res.code === 200) {
    alert('地址保存成功')
    addressForm.value = {
      name: '',
      phone: '',
      province: '',
      city: '',
      district: '',
      detail: '',
      isDefault: false
    }
    showAddressForm.value = false
    await loadAddresses()
    return
  }

  alert(res.message || '地址保存失败')
}

const submitOrder = async () => {
  if (cartItems.value.length === 0) {
    alert('购物车为空')
    return
  }
  if (!selectedAddressId.value) {
    alert('请先选择收货地址')
    return
  }

  const items = cartItems.value.map(item => ({
    productId: item.productId,
    quantity: item.quantity,
    price: item.price
  }))

  try {
    let shopId = null
    if (items.length > 0 && items[0].productId) {
      const productRes = await request.get(`/products/${items[0].productId}`)
      if (productRes.code === 200 && productRes.data) {
        shopId = productRes.data.shopId || null
      }
    }

    const res = await request.post('/orders', {
      shopId,
      addressId: selectedAddressId.value,
      items
    })
    if (res.code === 200) {
      alert('订单创建成功')
      await request.delete('/cart/clear')
      router.push('/orders')
      return
    }

    alert(res.message || '订单创建失败')
  } catch (e) {
    alert('订单创建失败')
  }
}

onMounted(() => {
  loadCart()
  loadAddresses()
})
</script>

<style scoped>
.checkout {
  max-width: 920px;
  margin: 0 auto;
}

h2 {
  margin-bottom: 16px;
  font-size: 28px;
}

h3 {
  margin-bottom: 12px;
  font-size: 18px;
}

.address-section {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  margin-bottom: 16px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.btn-add-address {
  border: 1px solid rgba(15, 107, 207, 0.4);
  color: var(--brand);
  background: #fff;
  border-radius: 8px;
  padding: 7px 11px;
  cursor: pointer;
  margin-bottom: 10px;
  font-weight: 600;
}

.address-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.address-card {
  display: flex;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.2s ease, background-color 0.2s ease;
}

.address-card.active {
  border-color: rgba(15, 107, 207, 0.45);
  background: #f4f9ff;
}

.address-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.default-tag {
  font-size: 12px;
  color: #fff;
  background: #10b981;
  padding: 2px 8px;
  border-radius: 999px;
}

.empty-address {
  color: #999;
  font-size: 14px;
}

.address-form {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--line-soft);
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.form-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.form-row.triple {
  grid-template-columns: repeat(3, 1fr);
}

.address-form input {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.default-check {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 14px;
  color: #666;
}

.btn-save-address {
  border: none;
  background: var(--accent);
  color: #fff;
  border-radius: 8px;
  padding: 8px 14px;
  cursor: pointer;
  font-weight: 600;
}

.order-items {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  margin-bottom: 14px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.item {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--line-soft);
  font-size: 14px;
}

.item:last-child {
  border-bottom: none;
}

.price {
  color: #dc2626;
  font-weight: 700;
}

.total {
  text-align: right;
  font-size: 22px;
  font-weight: 700;
  color: #dc2626;
  margin-bottom: 14px;
}

.btn-submit {
  width: 100%;
  padding: 13px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 10px;
  font-size: 16px;
  cursor: pointer;
  font-weight: 700;
}

.btn-submit:hover {
  background: var(--brand-strong);
}

@media (max-width: 768px) {
  h2 {
    font-size: 22px;
  }

  .form-row,
  .form-row.triple {
    grid-template-columns: 1fr;
  }

  .form-actions {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }

  .btn-save-address,
  .btn-submit {
    width: 100%;
  }
}
</style>
