<template>
  <div class="merchant-products">
    <div class="header">
      <div>
        <h2>商品管理</h2>
        <p>维护商品价格、库存、规格与介绍信息。</p>
      </div>
      <button @click="showAddForm = true" class="btn-add">添加商品</button>
    </div>

    <div class="product-list">
      <div class="product-item" v-for="product in products" :key="product.id">
        <div class="product-main">
          <div class="product-cover">
            <img v-if="getCoverImage(product.images)" :src="resolveImageUrl(getCoverImage(product.images))" alt="商品图片" />
            <div v-else class="cover-placeholder">无图</div>
          </div>
          <div class="product-info">
            <div class="product-name">{{ product.name }}</div>
            <div class="product-price">¥{{ product.price }}</div>
            <div class="product-stock">库存：{{ product.stock }}</div>
            <div class="product-desc">{{ product.description || '暂无商品介绍' }}</div>
            <div class="specs-line">{{ formatSpecsLine(product.specs) }}</div>
          </div>
        </div>
        <div class="product-actions">
          <button @click="editProduct(product)" class="btn-edit">编辑</button>
          <button @click="deleteProduct(product.id)" class="btn-delete">删除</button>
        </div>
      </div>
      <div v-if="products.length === 0" class="empty">暂无商品，请点击右上角添加商品</div>
    </div>

    <div v-if="showAddForm" class="modal">
      <div class="modal-content">
        <h3>{{ editingProduct ? '编辑商品' : '添加商品' }}</h3>
        <div class="form-group">
          <label>商品名称</label>
          <input v-model="form.name" />
        </div>
        <div class="form-group">
          <label>价格</label>
          <input v-model="form.price" type="number" />
        </div>
        <div class="form-group">
          <label>库存</label>
          <input v-model="form.stock" type="number" />
        </div>
        <div class="form-group">
          <label>分类</label>
          <input v-model="form.category" />
        </div>
        <div class="form-group">
          <label>详细介绍</label>
          <textarea v-model="form.description" rows="4" placeholder="请输入商品详细介绍"></textarea>
        </div>
        <div class="form-group">
          <label>商品图片</label>
          <div class="image-upload-row">
            <button class="btn-upload" type="button" :disabled="uploadingImage" @click="triggerImageUpload">
              {{ uploadingImage ? '上传中...' : '上传图片' }}
            </button>
            <span class="upload-tip">支持 jpg/png/webp，最多 6 张</span>
          </div>
          <input
            ref="imageInputRef"
            type="file"
            accept="image/*"
            multiple
            style="display:none"
            @change="uploadProductImages"
          />
          <div class="image-preview-list" v-if="form.images.length > 0">
            <div class="preview-item" v-for="(img, idx) in form.images" :key="`${img}-${idx}`">
              <img :src="resolveImageUrl(img)" alt="商品预览" />
              <button type="button" class="btn-remove-image" @click="removeImage(idx)">删除</button>
            </div>
          </div>
        </div>
        <div class="form-group">
          <label>规格参数</label>
          <div class="spec-grid">
            <input v-model="form.specs.origin" placeholder="产地（例：黑龙江五常）" />
            <input v-model="form.specs.grade" placeholder="等级（例：一级）" />
            <input v-model="form.specs.weight" placeholder="净含量（例：5kg/袋）" />
            <input v-model="form.specs.taste" placeholder="口感（例：软糯香甜）" />
            <input v-model="form.specs.shelfLife" placeholder="保质期（例：12个月）" />
          </div>
        </div>
        <div class="form-actions">
          <button @click="saveProduct" class="btn-save">保存</button>
          <button @click="closeForm" class="btn-cancel">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '../../utils/request'

const createEmptySpecs = () => ({
  origin: '',
  grade: '',
  weight: '',
  taste: '',
  shelfLife: ''
})
const createEmptyForm = () => ({
  name: '',
  price: 0,
  stock: 0,
  category: '',
  description: '',
  images: [],
  specs: createEmptySpecs()
})

const products = ref([])
const showAddForm = ref(false)
const editingProduct = ref(null)
const form = ref(createEmptyForm())
const currentShopId = ref(null)
const imageInputRef = ref(null)
const uploadingImage = ref(false)

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

const ensureShopId = async () => {
  if (currentShopId.value) return currentShopId.value
  const userId = decodeCurrentUserId()
  if (!userId) {
    throw new Error('无法识别当前用户')
  }
  const shopRes = await request.get(`/shops/user/${userId}`)
  if (shopRes.code === 200 && shopRes.data?.id) {
    currentShopId.value = shopRes.data.id
    return currentShopId.value
  }
  throw new Error(shopRes.message || '未找到商户店铺')
}

const parseSpecs = (specs) => {
  if (!specs) return createEmptySpecs()
  if (typeof specs === 'object') {
    return { ...createEmptySpecs(), ...specs }
  }
  if (typeof specs === 'string') {
    try {
      const parsed = JSON.parse(specs)
      if (parsed && typeof parsed === 'object') {
        return { ...createEmptySpecs(), ...parsed }
      }
    } catch (e) {
      const loose = createEmptySpecs()
      const keyMap = {
        origin: 'origin',
        grade: 'grade',
        weight: 'weight',
        taste: 'taste',
        shelfLife: 'shelfLife'
      }
      specs.replace(/([a-zA-Z]+):([^,{}]+)/g, (_, k, v) => {
        const target = keyMap[k]
        if (target) {
          loose[target] = String(v || '').trim()
        }
        return ''
      })
      return loose
    }
  }
  return createEmptySpecs()
}

const stringifySpecs = (specs) => {
  const normalized = { ...createEmptySpecs(), ...(specs || {}) }
  return JSON.stringify(normalized)
}

const parseImages = (images) => {
  if (!images) return []
  if (Array.isArray(images)) {
    return images.map(item => String(item || '').trim()).filter(Boolean)
  }
  if (typeof images === 'string') {
    const raw = images.trim()
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

const stringifyImages = (images) => {
  const list = parseImages(images)
  return list.length > 0 ? JSON.stringify(list) : ''
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

const formatSpecsLine = (specs) => {
  const parsed = parseSpecs(specs)
  const parts = [
    parsed.origin ? `产地:${parsed.origin}` : '',
    parsed.grade ? `等级:${parsed.grade}` : '',
    parsed.weight ? `净含量:${parsed.weight}` : '',
    parsed.taste ? `口感:${parsed.taste}` : '',
    parsed.shelfLife ? `保质期:${parsed.shelfLife}` : ''
  ].filter(Boolean)
  return parts.join(' ｜ ') || '规格参数待完善'
}

const loadProducts = async () => {
  try {
    const shopId = await ensureShopId()
    const res = await request.get(`/products?page=1&size=200&shopId=${shopId}`)
    if (res.code === 200) {
      products.value = res.data.records || res.data || []
      return
    }
    alert(res.message || '加载商品失败')
  } catch (e) {
    alert('加载商品失败')
  }
}

const editProduct = (product) => {
  editingProduct.value = product
  form.value = {
    ...createEmptyForm(),
    ...product,
    images: parseImages(product.images),
    specs: parseSpecs(product.specs)
  }
  showAddForm.value = true
}

const saveProduct = async () => {
  try {
    const shopId = await ensureShopId()
    const payload = {
      ...form.value,
      shopId: editingProduct.value?.shopId || shopId,
      images: stringifyImages(form.value.images),
      specs: stringifySpecs(form.value.specs),
      status: form.value.status ?? 1
    }

    let res
    if (editingProduct.value) {
      res = await request.put(`/merchant/products/${editingProduct.value.id}`, payload)
    } else {
      res = await request.post('/merchant/products', payload)
    }

    if (res.code === 200) {
      alert('保存成功')
      closeForm()
      await loadProducts()
      return
    }

    alert(res.message || '保存失败')
  } catch (e) {
    alert('保存失败')
  }
}

const deleteProduct = async (id) => {
  if (!confirm('确认删除？')) return
  const res = await request.delete(`/merchant/products/${id}`)
  if (res.code === 200) {
    await loadProducts()
    return
  }
  alert(res.message || '删除失败')
}

const closeForm = () => {
  showAddForm.value = false
  editingProduct.value = null
  form.value = createEmptyForm()
}

const triggerImageUpload = () => {
  if (imageInputRef.value) {
    imageInputRef.value.click()
  }
}

const uploadProductImages = async (event) => {
  const files = Array.from(event.target.files || [])
  if (files.length === 0) return

  const maxImages = 6
  if (!Array.isArray(form.value.images)) {
    form.value.images = parseImages(form.value.images)
  }
  const remaining = Math.max(0, maxImages - form.value.images.length)
  if (remaining <= 0) {
    alert(`最多上传 ${maxImages} 张图片`)
    event.target.value = ''
    return
  }
  const uploadQueue = files.slice(0, remaining)
  if (uploadQueue.length < files.length) {
    alert(`最多上传 ${maxImages} 张图片，已自动截取前 ${uploadQueue.length} 张`)
  }

  uploadingImage.value = true
  let success = 0
  try {
    for (const file of uploadQueue) {
      const formData = new FormData()
      formData.append('image', file)
      const res = await request.post('/merchant/products/upload-image', formData)
      if (res.code === 200 && res.data?.url) {
        form.value.images.push(res.data.url)
        success += 1
      }
    }
    if (success === 0) {
      alert('图片上传失败，请重试')
    }
  } catch (e) {
    alert('图片上传失败，请重试')
  } finally {
    uploadingImage.value = false
    event.target.value = ''
  }
}

const removeImage = (idx) => {
  if (!Array.isArray(form.value.images)) return
  form.value.images.splice(idx, 1)
}

onMounted(loadProducts)
</script>

<style scoped>
.merchant-products {
  max-width: 1220px;
  margin: 0 auto;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 14px;
  gap: 10px;
}

.header h2 {
  margin-bottom: 4px;
  font-size: 30px;
  color: #0f172a;
}

.header p {
  font-size: 14px;
  color: #667085;
}

.btn-add {
  padding: 10px 16px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.btn-add:hover {
  background: var(--brand-strong);
}

.product-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.product-item {
  background: #fff;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
}

.product-main {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  min-width: 0;
  flex: 1;
}

.product-cover {
  width: 88px;
  height: 88px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  flex-shrink: 0;
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
  color: #94a3b8;
  font-size: 12px;
}

.product-name {
  font-weight: 700;
  margin-bottom: 6px;
  color: #0f172a;
}

.product-price {
  color: #dc2626;
  font-size: 20px;
  font-weight: 700;
}

.product-stock {
  color: #667085;
  font-size: 13px;
}

.product-desc {
  margin-top: 8px;
  color: #475467;
  font-size: 13px;
  line-height: 1.7;
  max-width: 620px;
}

.specs-line {
  margin-top: 6px;
  color: #64748b;
  font-size: 12px;
}

.product-actions {
  display: flex;
  gap: 10px;
}

.btn-edit, .btn-delete {
  padding: 8px 14px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: #fff;
  font-weight: 700;
}

.btn-edit {
  background: #0ea5a5;
}

.btn-delete {
  background: #ef4444;
}

.modal {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 16px;
  overflow-y: auto;
  z-index: 1000;
}

.modal-content {
  background: #fff;
  padding: 20px;
  border-radius: 14px;
  width: min(720px, calc(100vw - 28px));
  border: 1px solid var(--line-soft);
  box-shadow: 0 20px 48px rgba(15, 40, 70, 0.24);
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  margin: auto 0;
}

.modal-content h3 {
  margin-bottom: 10px;
  color: #0f172a;
}

.form-group {
  margin-bottom: 10px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 600;
  font-size: 13px;
  color: #475467;
}

.form-group input,
.form-group textarea {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.spec-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}

.image-upload-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.btn-upload {
  padding: 8px 14px;
  border: 1px solid rgba(15, 107, 207, 0.35);
  border-radius: 8px;
  background: #fff;
  color: var(--brand);
  cursor: pointer;
  font-weight: 700;
}

.btn-upload:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.upload-tip {
  font-size: 12px;
  color: #64748b;
}

.image-preview-list {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.preview-item {
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.preview-item img {
  width: 100%;
  height: 86px;
  object-fit: cover;
  display: block;
}

.btn-remove-image {
  width: 100%;
  border: none;
  border-top: 1px solid #e2e8f0;
  background: #fff;
  color: #ef4444;
  font-size: 12px;
  padding: 6px 0;
  cursor: pointer;
}

.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  position: sticky;
  bottom: 0;
  background: #fff;
  padding-top: 10px;
  border-top: 1px solid #e2e8f0;
}

.btn-save, .btn-cancel {
  flex: 1;
  padding: 10px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.btn-save {
  background: var(--brand);
  color: #fff;
}

.btn-cancel {
  background: #e5e7eb;
}

.empty {
  text-align: center;
  padding: 24px;
  color: #98a2b3;
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  background: #fbfdff;
}

@media (max-width: 900px) {
  .modal {
    padding: 10px;
  }

  .modal-content {
    width: calc(100vw - 20px);
    max-height: calc(100vh - 20px);
    padding: 14px;
  }

  .header {
    flex-direction: column;
    align-items: stretch;
  }

  .header h2 {
    font-size: 24px;
  }

  .btn-add {
    width: 100%;
  }

  .product-item {
    flex-direction: column;
    align-items: flex-start;
  }

  .product-main {
    width: 100%;
  }

  .product-actions {
    width: 100%;
  }

  .product-actions button {
    flex: 1;
  }

  .spec-grid {
    grid-template-columns: 1fr;
  }

  .image-preview-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
