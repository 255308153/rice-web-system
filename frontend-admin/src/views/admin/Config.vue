<template>
  <div class="admin-config">
    <div class="hero">
      <h2>系统配置</h2>
      <p>统一管理 AI 参数、交易规则、论坛分类、系统公告和数据库备份。</p>
    </div>

    <div class="tabs">
      <button :class="{ active: tab === 'ai' }" @click="tab = 'ai'">AI参数</button>
      <button :class="{ active: tab === 'trade' }" @click="tab = 'trade'">交易规则</button>
      <button :class="{ active: tab === 'forum' }" @click="tab = 'forum'">论坛分类</button>
      <button :class="{ active: tab === 'carousel' }" @click="tab = 'carousel'">首页轮播</button>
      <button :class="{ active: tab === 'notice' }" @click="tab = 'notice'">系统公告</button>
      <button :class="{ active: tab === 'backup' }" @click="tab = 'backup'">数据库备份</button>
    </div>

    <div v-if="tab === 'ai'" class="panel">
      <div class="form-group">
        <label>YOLO 服务地址</label>
        <input v-model.trim="aiConfig.yoloUrl" placeholder="http://localhost:5000" />
      </div>
      <div class="form-group">
        <label>AI 对话提示词模板</label>
        <textarea v-model.trim="aiConfig.prompt" rows="4" placeholder="输入 AI 系统提示词"></textarea>
      </div>
      <button class="btn-save" @click="saveAiConfig">保存 AI 配置</button>
    </div>

    <div v-if="tab === 'trade'" class="panel">
      <div class="form-group">
        <label>退款时效（天）</label>
        <input v-model.number="tradeConfig.refundDays" type="number" min="1" />
      </div>
      <div class="form-group">
        <label>自动确认收货（天）</label>
        <input v-model.number="tradeConfig.autoConfirmDays" type="number" min="1" />
      </div>
      <button class="btn-save" @click="saveTradeConfig">保存交易配置</button>
    </div>

    <div v-if="tab === 'forum'" class="panel">
      <div class="form-group">
        <label>新增论坛话题分类</label>
        <div class="inline">
          <input v-model.trim="newCategory" placeholder="例如：稻田管理" @keyup.enter="addCategory" />
          <button class="btn-inline" @click="addCategory">添加</button>
        </div>
      </div>
      <div class="category-list">
        <div v-for="item in forumCategories" :key="item" class="category-item">
          <span>{{ item }}</span>
          <button @click="removeCategory(item)">删除</button>
        </div>
      </div>
      <button class="btn-save" @click="saveForumCategories">保存论坛分类</button>
    </div>

    <div v-if="tab === 'carousel'" class="notice-wrap">
      <div class="panel">
        <div class="form-group">
          <label>轮播标题</label>
          <input v-model.trim="carouselForm.title" placeholder="例如：春耕专场，优选大米上新" />
        </div>
        <div class="form-group">
          <label>轮播副标题</label>
          <input v-model.trim="carouselForm.subtitle" placeholder="例如：管理员可在后台动态维护首页轮播图" />
        </div>
        <div class="form-group">
          <label>跳转链接（可选）</label>
          <input v-model.trim="carouselForm.link" placeholder="例如：/shop 或 /ai" />
        </div>
        <div class="form-group">
          <label>按钮文案（可选）</label>
          <input v-model.trim="carouselForm.linkText" placeholder="例如：立即查看" />
        </div>
        <div class="form-group">
          <label>轮播图片地址</label>
          <div class="inline">
            <input v-model.trim="carouselForm.imageUrl" placeholder="输入图片URL，或点击右侧上传" />
            <label class="btn-inline upload-btn">
              {{ carouselUploading ? '上传中...' : '上传图片' }}
              <input type="file" accept="image/*" :disabled="carouselUploading" @change="uploadCarouselImage" hidden />
            </label>
          </div>
        </div>
        <button class="btn-save" @click="addCarouselItem">添加到轮播列表</button>
      </div>

      <div class="list">
        <div v-for="(item, idx) in carouselItems" :key="`${item.imageUrl}-${idx}`" class="list-item">
          <div class="item-main carousel-item-main">
            <img class="carousel-thumb" :src="resolveImageUrl(item.imageUrl)" alt="轮播图预览" />
            <div>
              <div class="item-title">{{ item.title || '未命名轮播图' }}</div>
              <div class="item-meta">
                <span>序号：{{ idx + 1 }}</span>
                <span>链接：{{ item.link || '-' }}</span>
              </div>
              <div class="item-content">{{ item.subtitle || '无副标题' }}</div>
            </div>
          </div>
          <button class="btn-delete" @click="removeCarouselItem(idx)">删除</button>
        </div>
        <div v-if="carouselItems.length === 0" class="empty">暂无轮播图，请先添加</div>
      </div>
      <button class="btn-save" @click="saveCarouselConfig">保存首页轮播配置</button>
    </div>

    <div v-if="tab === 'notice'" class="notice-wrap">
      <div class="panel">
        <div class="form-group">
          <label>公告标题</label>
          <input v-model.trim="noticeForm.title" placeholder="输入公告标题" />
        </div>
        <div class="form-group">
          <label>公告对象</label>
          <select v-model="noticeForm.role">
            <option value="ALL">全体用户</option>
            <option value="USER">普通用户</option>
            <option value="MERCHANT">商户</option>
            <option value="EXPERT">专家</option>
            <option value="ADMIN">管理员</option>
          </select>
        </div>
        <div class="form-group">
          <label>公告内容</label>
          <textarea v-model.trim="noticeForm.content" rows="4" placeholder="输入公告内容"></textarea>
        </div>
        <button class="btn-save" @click="createNotice">发布公告</button>
      </div>

      <div class="list">
        <div v-for="notice in notices" :key="notice.id" class="list-item">
          <div class="item-main">
            <div class="item-title">{{ notice.title }}</div>
            <div class="item-meta">
              <span>对象：{{ notice.role || 'ALL' }}</span>
              <span>时间：{{ formatTime(notice.createTime) }}</span>
            </div>
            <div class="item-content">{{ notice.content }}</div>
          </div>
          <button class="btn-delete" @click="deleteNotice(notice.id)">删除</button>
        </div>
        <div v-if="notices.length === 0" class="empty">暂无公告</div>
      </div>
    </div>

    <div v-if="tab === 'backup'" class="backup-wrap">
      <div class="panel">
        <p class="tip">点击按钮生成当前数据库 SQL 备份文件。</p>
        <button class="btn-save" @click="backupNow">立即备份</button>
      </div>
      <div class="list">
        <div v-for="item in backups" :key="item.filePath" class="list-item">
          <div class="item-main">
            <div class="item-title">{{ item.fileName }}</div>
            <div class="item-meta">
              <span>大小：{{ formatSize(item.size) }}</span>
              <span>修改时间：{{ item.modifiedTime || '-' }}</span>
            </div>
            <div class="item-content">{{ item.filePath }}</div>
          </div>
        </div>
        <div v-if="backups.length === 0" class="empty">暂无备份文件</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '../../utils/request'

const tab = ref('ai')
const aiConfig = ref({
  yoloUrl: '',
  prompt: ''
})
const tradeConfig = ref({
  refundDays: 7,
  autoConfirmDays: 7
})
const forumCategories = ref(['综合交流', '种植经验', '病虫害防治', '市场行情', '政策资讯'])
const newCategory = ref('')
const carouselForm = ref({
  title: '',
  subtitle: '',
  link: '',
  linkText: '',
  imageUrl: ''
})
const carouselItems = ref([])
const carouselUploading = ref(false)
const noticeForm = ref({
  title: '',
  role: 'ALL',
  content: ''
})
const notices = ref([])
const backups = ref([])

const safeJsonParse = (value, fallback) => {
  if (!value || typeof value !== 'string') return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

const formatTime = (time) => {
  if (!time) return '-'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) return parsed.toLocaleString('zh-CN')
  return String(time)
}

const formatSize = (size) => {
  const bytes = Number(size || 0)
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const normalizeCarouselItem = (item) => {
  if (!item) return null
  const imageUrl = String(item.imageUrl || item.url || '').trim()
  if (!imageUrl) return null
  return {
    imageUrl,
    title: String(item.title || '').trim(),
    subtitle: String(item.subtitle || '').trim(),
    link: String(item.link || '').trim(),
    linkText: String(item.linkText || '').trim()
  }
}

const loadConfig = async () => {
  const [aiRes, tradeRes, forumRes, carouselRes] = await Promise.all([
    request.get('/admin/config/ai'),
    request.get('/admin/config/trade'),
    request.get('/admin/forum/categories'),
    request.get('/admin/config/home_carousel')
  ])

  if (aiRes.code === 200 && aiRes.data) {
    const parsed = safeJsonParse(aiRes.data, {})
    aiConfig.value = {
      yoloUrl: parsed.yoloUrl || '',
      prompt: parsed.prompt || ''
    }
  }

  if (tradeRes.code === 200 && tradeRes.data) {
    const parsed = safeJsonParse(tradeRes.data, {})
    tradeConfig.value = {
      refundDays: Number(parsed.refundDays || 7),
      autoConfirmDays: Number(parsed.autoConfirmDays || 7)
    }
  }

  if (forumRes.code === 200 && Array.isArray(forumRes.data) && forumRes.data.length > 0) {
    forumCategories.value = forumRes.data
  }

  if (carouselRes.code === 200 && carouselRes.data) {
    const parsed = safeJsonParse(carouselRes.data, [])
    if (Array.isArray(parsed)) {
      carouselItems.value = parsed.map(normalizeCarouselItem).filter(Boolean)
    }
  }
}

const loadNotices = async () => {
  const res = await request.get('/admin/notices?limit=100')
  if (res.code === 200) {
    notices.value = res.data || []
  }
}

const loadBackups = async () => {
  const res = await request.get('/admin/backups')
  if (res.code === 200) {
    backups.value = res.data || []
  }
}

const saveAiConfig = async () => {
  const payload = {
    yoloUrl: aiConfig.value.yoloUrl || '',
    prompt: aiConfig.value.prompt || ''
  }
  const res = await request.put('/admin/config', {
    key: 'ai',
    value: JSON.stringify(payload),
    description: 'AI配置(识别地址/提示词)'
  })
  if (res.code === 200) {
    alert('AI 配置已保存')
    return
  }
  alert(res.message || '保存失败')
}

const saveTradeConfig = async () => {
  const payload = {
    refundDays: Math.max(1, Number(tradeConfig.value.refundDays || 7)),
    autoConfirmDays: Math.max(1, Number(tradeConfig.value.autoConfirmDays || 7))
  }
  const res = await request.put('/admin/config', {
    key: 'trade',
    value: JSON.stringify(payload),
    description: '交易规则配置'
  })
  if (res.code === 200) {
    alert('交易配置已保存')
    return
  }
  alert(res.message || '保存失败')
}

const addCategory = () => {
  const value = newCategory.value.trim()
  if (!value) return
  if (forumCategories.value.includes(value)) {
    alert('该分类已存在')
    return
  }
  forumCategories.value.push(value)
  newCategory.value = ''
}

const removeCategory = (target) => {
  forumCategories.value = forumCategories.value.filter(item => item !== target)
}

const saveForumCategories = async () => {
  if (forumCategories.value.length === 0) {
    alert('至少保留一个分类')
    return
  }
  const res = await request.put('/admin/forum/categories', {
    categories: forumCategories.value
  })
  if (res.code === 200) {
    alert('论坛分类已保存')
    return
  }
  alert(res.message || '保存失败')
}

const addCarouselItem = () => {
  const normalized = normalizeCarouselItem(carouselForm.value)
  if (!normalized) {
    alert('请先上传或填写轮播图片地址')
    return
  }
  carouselItems.value.push(normalized)
  carouselForm.value = {
    title: '',
    subtitle: '',
    link: '',
    linkText: '',
    imageUrl: ''
  }
}

const removeCarouselItem = (index) => {
  carouselItems.value.splice(index, 1)
}

const uploadCarouselImage = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('image', file)
  carouselUploading.value = true
  try {
    const res = await request.post('/posts/upload-image', formData)
    if (res.code === 200 && res.data?.url) {
      carouselForm.value.imageUrl = res.data.url
      alert('图片上传成功')
    } else {
      alert(res.message || '图片上传失败')
    }
  } catch (e) {
    alert('图片上传失败，请稍后重试')
  } finally {
    carouselUploading.value = false
    event.target.value = ''
  }
}

const saveCarouselConfig = async () => {
  const payload = carouselItems.value.map(item => ({
    imageUrl: item.imageUrl,
    title: item.title || '',
    subtitle: item.subtitle || '',
    link: item.link || '',
    linkText: item.linkText || ''
  }))
  const res = await request.put('/admin/config', {
    key: 'home_carousel',
    value: JSON.stringify(payload),
    description: '用户首页轮播图配置'
  })
  if (res.code === 200) {
    alert('首页轮播配置已保存')
    return
  }
  alert(res.message || '保存失败')
}

const createNotice = async () => {
  if (!noticeForm.value.title || !noticeForm.value.content) {
    alert('请填写完整公告信息')
    return
  }
  const res = await request.post('/admin/notices', noticeForm.value)
  if (res.code === 200) {
    alert('公告发布成功')
    noticeForm.value = { title: '', role: 'ALL', content: '' }
    await loadNotices()
    return
  }
  alert(res.message || '发布失败')
}

const deleteNotice = async (id) => {
  if (!confirm('确认删除这条公告吗？')) return
  const res = await request.delete(`/admin/notices/${id}`)
  if (res.code === 200) {
    await loadNotices()
    return
  }
  alert(res.message || '删除失败')
}

const backupNow = async () => {
  if (!confirm('确认立即执行数据库备份吗？')) return
  const res = await request.post('/admin/backup')
  if (res.code === 200) {
    alert(`备份成功：${res.data.fileName}`)
    await loadBackups()
    return
  }
  alert(res.message || '备份失败')
}

onMounted(async () => {
  await Promise.all([loadConfig(), loadNotices(), loadBackups()])
})
</script>

<style scoped>
.admin-config {
  max-width: 960px;
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

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.tabs button {
  padding: 9px 14px;
  border: 1px solid var(--line-soft);
  background: #fff;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
  color: #475467;
}

.tabs button.active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

.panel {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  padding: 16px;
  margin-bottom: 12px;
}

.form-group {
  margin-bottom: 12px;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: #475467;
  font-weight: 600;
}

.form-group input,
.form-group textarea,
.form-group select {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
  font-family: inherit;
}

.inline {
  display: flex;
  gap: 8px;
}

.btn-inline {
  white-space: nowrap;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  color: #fff;
  font-weight: 700;
  background: #0ea5e9;
  cursor: pointer;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.category-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.category-item {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  padding: 7px 10px;
  border-radius: 999px;
  background: #eff6ff;
  border: 1px solid #dbeafe;
  color: #1d4ed8;
  font-size: 13px;
}

.category-item button {
  border: none;
  background: transparent;
  color: #ef4444;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.btn-save {
  width: 100%;
  padding: 10px 0;
  border: none;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.notice-wrap,
.backup-wrap {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.list-item {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 12px;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  padding: 14px;
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.item-main {
  flex: 1;
}

.carousel-item-main {
  display: flex;
  gap: 12px;
  align-items: center;
}

.carousel-thumb {
  width: 180px;
  height: 88px;
  border-radius: 8px;
  object-fit: contain;
  object-position: center;
  border: 1px solid #dbe7f3;
  background: transparent;
  flex-shrink: 0;
}

.item-title {
  font-size: 15px;
  color: #0f172a;
  font-weight: 700;
  margin-bottom: 6px;
}

.item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.item-content {
  color: #475467;
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
}

.btn-delete {
  border: none;
  border-radius: 8px;
  background: #ef4444;
  color: #fff;
  font-weight: 700;
  padding: 8px 14px;
  cursor: pointer;
}

.tip {
  margin-bottom: 10px;
  color: #475467;
  font-size: 14px;
}

.empty {
  text-align: center;
  padding: 24px;
  color: #98a2b3;
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  background: #fbfdff;
}

@media (max-width: 768px) {
  h2 {
    font-size: 24px;
  }

  .inline {
    flex-direction: column;
  }

  .btn-inline {
    width: 100%;
    padding: 9px 0;
  }

  .list-item {
    flex-direction: column;
  }

  .carousel-item-main {
    flex-direction: column;
    align-items: flex-start;
  }

  .carousel-thumb {
    width: 100%;
    height: 140px;
  }
}
</style>
