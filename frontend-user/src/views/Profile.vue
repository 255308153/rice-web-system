<template>
  <div class="profile-page">
    <div class="profile-layout">
      <aside class="profile-sidebar">
        <div class="side-title">个人用户中心</div>
        <button class="side-item" :class="{ active: activeSection === 'center' }" @click="switchSection('center')">
          <span class="side-item-title">个人中心</span>
          <span class="side-item-desc">订单、发帖、评论、收藏</span>
        </button>
        <button class="side-item" :class="{ active: activeSection === 'profile' }" @click="switchSection('profile')">
          <span class="side-item-title">修改个人信息</span>
          <span class="side-item-desc">手机号、头像、收货地址</span>
        </button>
        <button class="side-item" :class="{ active: activeSection === 'certification' }" @click="switchSection('certification')">
          <span class="side-item-title">申请身份认证</span>
          <span class="side-item-desc">商户/专家认证申请</span>
        </button>
      </aside>

      <main class="profile-main">
        <section class="profile-hero">
          <img v-if="user.avatar" class="hero-avatar" :src="resolveImageUrl(user.avatar)" alt="用户头像" />
          <div v-else class="hero-avatar hero-avatar-fallback">{{ getAvatarText(user.username, user.id) }}</div>
          <div class="hero-meta">
            <h2>{{ user.username || '未命名用户' }}</h2>
            <p class="hero-sub">{{ formatRole(user.role) }} · {{ user.phone || '未绑定手机号' }}</p>
            <div class="hero-tags">
              <span>ID {{ user.id || '-' }}</span>
              <span>当前模块：{{ getSectionName(activeSection) }}</span>
            </div>
          </div>
        </section>

        <section class="panel-section" v-if="activeSection === 'center'">
          <div class="section-head">
            <h3>个人中心</h3>
            <p>侧边栏切换不跳转路由，在当前页面查看所有中心数据。</p>
          </div>

          <div class="center-grid">
            <section class="center-card">
              <div class="card-head">
                <h4>我的订单（{{ orderPager.total }}）</h4>
                <div class="card-actions">
                  <select v-model.number="orderPager.status" @change="onOrderStatusChange">
                    <option :value="-1">全部状态</option>
                    <option :value="0">待支付</option>
                    <option :value="1">待发货</option>
                    <option :value="2">待收货</option>
                    <option :value="3">已完成</option>
                    <option :value="4">售后</option>
                  </select>
                  <button @click="router.push('/orders')">订单页</button>
                </div>
              </div>
              <div v-if="orderPager.loading" class="empty">加载中...</div>
              <div v-for="item in myOrders" :key="item.id" class="line-item">
                <div class="line-title">{{ item.orderNo }}</div>
                <div class="line-meta">￥{{ item.totalPrice }} · {{ formatOrderStatus(item.status) }}</div>
              </div>
              <div v-if="!orderPager.loading && myOrders.length === 0" class="empty">暂无订单</div>
              <div class="pager" v-if="orderPager.total > orderPager.size">
                <button :disabled="orderPager.page <= 1" @click="changeOrderPage(-1)">上一页</button>
                <span>{{ orderPager.page }} / {{ getTotalPages(orderPager) }}</span>
                <button :disabled="orderPager.page >= getTotalPages(orderPager)" @click="changeOrderPage(1)">下一页</button>
              </div>
            </section>

            <section class="center-card">
              <div class="card-head">
                <h4>我的发帖（{{ postPager.total }}）</h4>
                <button @click="router.push('/forum')">去论坛</button>
              </div>
              <div v-if="postPager.loading" class="empty">加载中...</div>
              <div v-for="item in myPosts" :key="item.id" class="line-item clickable" @click="router.push(`/post/${item.id}`)">
                <div class="line-title">{{ item.title }}</div>
                <div class="line-meta">点赞 {{ item.likes || 0 }} · 评论 {{ item.commentCount || 0 }} · {{ formatPostStatus(item.status) }}</div>
              </div>
              <div v-if="!postPager.loading && myPosts.length === 0" class="empty">暂无发帖</div>
              <div class="pager" v-if="postPager.total > postPager.size">
                <button :disabled="postPager.page <= 1" @click="changePostPage(-1)">上一页</button>
                <span>{{ postPager.page }} / {{ getTotalPages(postPager) }}</span>
                <button :disabled="postPager.page >= getTotalPages(postPager)" @click="changePostPage(1)">下一页</button>
              </div>
            </section>

            <section class="center-card">
              <div class="card-head">
                <h4>我的评论（{{ commentPager.total }}）</h4>
                <button @click="router.push('/forum')">去论坛</button>
              </div>
              <div v-if="commentPager.loading" class="empty">加载中...</div>
              <div
                v-for="item in myComments"
                :key="item.id"
                class="line-item clickable"
                @click="item.postId && router.push(`/post/${item.postId}`)"
              >
                <div class="line-title">{{ item.postTitle || '帖子' }}</div>
                <div class="line-meta">{{ item.content }}</div>
              </div>
              <div v-if="!commentPager.loading && myComments.length === 0" class="empty">暂无评论</div>
              <div class="pager" v-if="commentPager.total > commentPager.size">
                <button :disabled="commentPager.page <= 1" @click="changeCommentPage(-1)">上一页</button>
                <span>{{ commentPager.page }} / {{ getTotalPages(commentPager) }}</span>
                <button :disabled="commentPager.page >= getTotalPages(commentPager)" @click="changeCommentPage(1)">下一页</button>
              </div>
            </section>

            <section class="center-card">
              <div class="card-head">
                <h4>我的收藏（{{ favoritePager.total }}）</h4>
                <button @click="router.push('/forum')">去论坛</button>
              </div>
              <div v-if="favoritePager.loading" class="empty">加载中...</div>
              <div v-for="item in myFavorites" :key="item.id" class="line-item clickable" @click="router.push(`/post/${item.id}`)">
                <div class="line-title">{{ item.title }}</div>
                <div class="line-meta">浏览 {{ item.views || 0 }} · 点赞 {{ item.likes || 0 }}</div>
              </div>
              <div v-if="!favoritePager.loading && myFavorites.length === 0" class="empty">暂无收藏</div>
              <div class="pager" v-if="favoritePager.total > favoritePager.size">
                <button :disabled="favoritePager.page <= 1" @click="changeFavoritePage(-1)">上一页</button>
                <span>{{ favoritePager.page }} / {{ getTotalPages(favoritePager) }}</span>
                <button :disabled="favoritePager.page >= getTotalPages(favoritePager)" @click="changeFavoritePage(1)">下一页</button>
              </div>
            </section>

            <section class="center-card">
              <div class="card-head">
                <h4>我的私信（{{ conversationPager.total }}）</h4>
                <button @click="router.push('/messages')">打开私聊</button>
              </div>
              <div v-if="conversationPager.loading" class="empty">加载中...</div>
              <div v-for="item in myConversations" :key="item.id" class="line-item clickable" @click="openConversation(item)">
                <div class="line-title">{{ item.peerName || `用户#${item.peerId}` }}</div>
                <div class="line-meta">
                  {{ item.lastMessage || '暂无消息' }}
                  <span v-if="item.unreadCount > 0" class="badge-unread">{{ item.unreadCount }}</span>
                </div>
              </div>
              <div v-if="!conversationPager.loading && myConversations.length === 0" class="empty">暂无私信会话</div>
              <div class="pager" v-if="conversationPager.total > conversationPager.size">
                <button :disabled="conversationPager.page <= 1" @click="changeConversationPage(-1)">上一页</button>
                <span>{{ conversationPager.page }} / {{ getTotalPages(conversationPager) }}</span>
                <button :disabled="conversationPager.page >= getTotalPages(conversationPager)" @click="changeConversationPage(1)">下一页</button>
              </div>
            </section>

            <section class="center-card">
              <div class="card-head">
                <h4>浏览历史（{{ historyPager.total }}）</h4>
                <button @click="clearHistory">清空</button>
              </div>
              <div
                v-for="item in browseHistoryPageData"
                :key="`${item.type}-${item.id}`"
                class="line-item clickable"
                @click="jumpByHistory(item)"
              >
                <div class="line-title">[{{ item.type === 'POST' ? '帖子' : '商品' }}] {{ item.title }}</div>
                <div class="line-meta">{{ formatTime(item.time) }}</div>
              </div>
              <div v-if="browseHistory.length === 0" class="empty">暂无浏览记录</div>
              <div class="pager" v-if="historyPager.total > historyPager.size">
                <button :disabled="historyPager.page <= 1" @click="changeHistoryPage(-1)">上一页</button>
                <span>{{ historyPager.page }} / {{ getTotalPages(historyPager) }}</span>
                <button :disabled="historyPager.page >= getTotalPages(historyPager)" @click="changeHistoryPage(1)">下一页</button>
              </div>
            </section>
          </div>
        </section>

        <section class="panel-section" v-if="activeSection === 'profile'">
          <div class="section-head">
            <h3>修改个人信息</h3>
            <p>支持修改手机号，头像支持图片上传或手动填写地址。</p>
          </div>

          <div class="profile-card">
            <div class="form-group">
              <label>用户名</label>
              <input :value="user.username" disabled />
            </div>
            <div class="form-group">
              <label>手机号</label>
              <input v-model.trim="user.phone" placeholder="请输入手机号" />
            </div>
            <div class="form-group">
              <label>角色</label>
              <input :value="formatRole(user.role)" disabled />
            </div>
            <div class="form-group avatar-form-group">
              <label>头像</label>
              <div class="avatar-editor">
                <img v-if="user.avatar" class="avatar-preview" :src="resolveImageUrl(user.avatar)" alt="头像预览" />
                <div v-else class="avatar-preview avatar-preview-fallback">{{ getAvatarText(user.username, user.id) }}</div>
                <div class="avatar-editor-actions">
                  <button type="button" class="btn-upload-avatar" :disabled="avatarUploading" @click="triggerAvatarUpload">
                    {{ avatarUploading ? '上传中...' : '上传头像' }}
                  </button>
                  <input ref="avatarInputRef" class="hidden-input" type="file" accept="image/*" @change="onAvatarFileChange" />
                  <input v-model.trim="user.avatar" placeholder="http:// 或 /uploads/..." />
                </div>
              </div>
            </div>
            <button @click="saveProfile" class="btn-save">保存资料</button>
          </div>

          <div class="address-block">
            <h4>收货地址</h4>
            <div class="address-list">
              <div class="address-card" v-for="addr in addresses" :key="addr.id">
                <div class="addr-info">
                  <div><strong>{{ addr.name }}</strong> {{ addr.phone }}</div>
                  <div>{{ addr.province }} {{ addr.city }} {{ addr.district }} {{ addr.detail }}</div>
                </div>
                <span v-if="addr.isDefault" class="badge-default">默认</span>
              </div>
              <div v-if="addresses.length === 0" class="empty">暂无地址</div>
            </div>
          </div>
        </section>

        <section class="panel-section" v-if="activeSection === 'certification'">
          <div class="section-head">
            <h3>申请身份认证</h3>
            <p>在当前页面完成角色认证申请并查看审核记录。</p>
          </div>

          <div class="cert-section">
            <div class="cert-form" v-if="user.role === 'USER'">
              <div class="form-group">
                <label>申请角色</label>
                <select v-model="certForm.role">
                  <option value="MERCHANT">商户</option>
                  <option value="EXPERT">专家</option>
                </select>
              </div>
              <div class="form-group full">
                <label>资质说明</label>
                <textarea v-model.trim="certForm.credentials" rows="4" placeholder="请填写营业执照号、从业经历、专业资质等信息"></textarea>
              </div>
              <button class="btn-cert" @click="submitCertification">提交认证申请</button>
            </div>
            <div class="cert-tip" v-else>
              当前账号角色为 <strong>{{ formatRole(user.role) }}</strong>。
            </div>

            <div class="cert-history">
              <h4>我的申请记录</h4>
              <div v-for="item in certifications" :key="item.id" class="cert-item">
                <div class="cert-row">
                  <span>申请角色：{{ formatRole(item.role) }}</span>
                  <span class="cert-badge" :class="'status-' + item.status">{{ getCertStatusText(item.status) }}</span>
                </div>
                <div class="cert-meta">申请时间：{{ formatTime(item.createTime) }}</div>
                <div class="cert-meta" v-if="item.auditTime">审核时间：{{ formatTime(item.auditTime) }}</div>
                <div class="cert-meta" v-if="item.auditRemark">审核备注：{{ item.auditRemark }}</div>
                <p class="cert-credentials">{{ item.credentials || '未填写资质说明' }}</p>
              </div>
              <div v-if="certifications.length === 0" class="empty">暂无认证申请记录</div>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { clearBrowseHistory, getBrowseHistory } from '@/utils/history'

const router = useRouter()
const activeSection = ref('center')
const user = ref({ id: null, username: '', phone: '', role: '', avatar: '' })
const avatarInputRef = ref(null)
const avatarUploading = ref(false)
const addresses = ref([])
const certifications = ref([])

const myOrders = ref([])
const myPosts = ref([])
const myComments = ref([])
const myFavorites = ref([])
const myConversations = ref([])
const browseHistory = ref([])

const orderPager = ref({ page: 1, size: 5, total: 0, loading: false, status: -1 })
const postPager = ref({ page: 1, size: 5, total: 0, loading: false })
const commentPager = ref({ page: 1, size: 5, total: 0, loading: false })
const favoritePager = ref({ page: 1, size: 5, total: 0, loading: false })
const conversationPager = ref({ page: 1, size: 5, total: 0, loading: false })
const historyPager = ref({ page: 1, size: 6, total: 0 })

const certForm = ref({
  role: 'MERCHANT',
  credentials: ''
})

const browseHistoryPageData = computed(() => {
  const start = (historyPager.value.page - 1) * historyPager.value.size
  return browseHistory.value.slice(start, start + historyPager.value.size)
})

const switchSection = (key) => {
  activeSection.value = key
}

const getSectionName = (key) => {
  if (key === 'profile') return '修改个人信息'
  if (key === 'certification') return '申请身份认证'
  return '个人中心'
}

const getTotalPages = (pager) => {
  const total = Number(pager?.total || 0)
  const size = Math.max(1, Number(pager?.size || 1))
  return Math.max(1, Math.ceil(total / size))
}

const getCertStatusText = (status) => {
  if (status === 0) return '待审核'
  if (status === 1) return '已通过'
  if (status === 2) return '已拒绝'
  return '未知'
}

const formatRole = (role) => {
  if (role === 'MERCHANT') return '商户'
  if (role === 'EXPERT') return '专家'
  if (role === 'ADMIN') return '管理员'
  return '普通用户'
}

const formatOrderStatus = (status) => {
  const map = { 0: '待支付', 1: '待发货', 2: '待收货', 3: '已完成', 4: '售后' }
  return map[status] || '未知'
}

const formatPostStatus = (status) => {
  if (Number(status) === 0) return '异常待审'
  if (Number(status) === 2) return '已下架'
  return '正常'
}

const formatTime = (time) => {
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

const getAvatarText = (name, id) => {
  const text = String(name || '').trim()
  if (text) return text.slice(0, 1).toUpperCase()
  if (id !== undefined && id !== null) return String(id).slice(-1)
  return 'U'
}

const loadProfile = async () => {
  try {
    const res = await request.get('/user/info')
    if (res.code === 200) {
      user.value = res.data
    }
  } catch (e) {
    console.error('加载失败', e)
  }
}

const loadAddresses = async () => {
  try {
    const res = await request.get('/addresses')
    if (res.code === 200) {
      addresses.value = res.data || []
    }
  } catch (e) {
    console.error('加载地址失败', e)
  }
}

const saveProfile = async () => {
  try {
    const payload = {
      phone: user.value.phone,
      avatar: user.value.avatar
    }
    const res = await request.put('/user/info', payload)
    if (res.code === 200) {
      alert('保存成功')
      await loadProfile()
    }
  } catch (e) {
    alert('保存失败')
  }
}

const triggerAvatarUpload = () => {
  if (avatarInputRef.value) {
    avatarInputRef.value.click()
  }
}

const onAvatarFileChange = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('image', file)
  avatarUploading.value = true
  try {
    const res = await request.post('/user/upload-avatar', formData)
    if (res.code === 200 && res.data?.url) {
      user.value.avatar = res.data.url
      return
    }
    alert(res.message || '头像上传失败')
  } catch (e) {
    alert('头像上传失败')
  } finally {
    avatarUploading.value = false
    event.target.value = ''
  }
}

const loadCertifications = async () => {
  try {
    const res = await request.get('/certifications/my?limit=20')
    if (res.code === 200) {
      certifications.value = res.data || []
    }
  } catch (e) {
    console.error('加载认证记录失败', e)
  }
}

const submitCertification = async () => {
  if (!certForm.value.credentials) {
    alert('请填写资质说明')
    return
  }
  try {
    const res = await request.post('/certifications/apply', certForm.value)
    if (res.code === 200) {
      alert('提交成功，请等待管理员审核')
      certForm.value.credentials = ''
      await loadCertifications()
      return
    }
    alert(res.message || '提交失败')
  } catch (e) {
    alert('提交失败')
  }
}

const loadMyOrders = async () => {
  orderPager.value.loading = true
  try {
    const res = await request.get(`/orders/page?page=${orderPager.value.page}&size=${orderPager.value.size}&status=${orderPager.value.status}`)
    if (res.code === 200) {
      myOrders.value = res.data?.records || []
      orderPager.value.total = Number(res.data?.total || 0)
    }
  } catch (e) {
    console.error('加载我的订单失败', e)
  } finally {
    orderPager.value.loading = false
  }
}

const loadMyPosts = async () => {
  postPager.value.loading = true
  try {
    const res = await request.get(`/posts/my?page=${postPager.value.page}&size=${postPager.value.size}`)
    if (res.code === 200) {
      myPosts.value = res.data?.records || []
      postPager.value.total = Number(res.data?.total || 0)
    }
  } catch (e) {
    console.error('加载我的发帖失败', e)
  } finally {
    postPager.value.loading = false
  }
}

const loadMyComments = async () => {
  commentPager.value.loading = true
  try {
    const res = await request.get(`/posts/my/comments?page=${commentPager.value.page}&size=${commentPager.value.size}`)
    if (res.code === 200) {
      myComments.value = res.data?.records || []
      commentPager.value.total = Number(res.data?.total || 0)
    }
  } catch (e) {
    console.error('加载我的评论失败', e)
  } finally {
    commentPager.value.loading = false
  }
}

const loadMyFavorites = async () => {
  favoritePager.value.loading = true
  try {
    const res = await request.get(`/posts/favorites?page=${favoritePager.value.page}&size=${favoritePager.value.size}`)
    if (res.code === 200) {
      myFavorites.value = res.data?.records || []
      favoritePager.value.total = Number(res.data?.total || 0)
    }
  } catch (e) {
    console.error('加载我的收藏失败', e)
  } finally {
    favoritePager.value.loading = false
  }
}

const loadMyConversations = async () => {
  conversationPager.value.loading = true
  try {
    const res = await request.get(`/conversations?page=${conversationPager.value.page}&size=${conversationPager.value.size}`)
    if (res.code === 200) {
      myConversations.value = res.data?.records || []
      conversationPager.value.total = Number(res.data?.total || 0)
    }
  } catch (e) {
    console.error('加载我的私信失败', e)
  } finally {
    conversationPager.value.loading = false
  }
}

const changePage = async (pagerRef, delta, loader) => {
  const current = pagerRef.value.page
  const next = current + delta
  const maxPage = getTotalPages(pagerRef.value)
  if (next < 1 || next > maxPage) return
  pagerRef.value.page = next
  await loader()
}

const onOrderStatusChange = async () => {
  orderPager.value.page = 1
  await loadMyOrders()
}

const changeOrderPage = async (delta) => {
  await changePage(orderPager, delta, loadMyOrders)
}

const changePostPage = async (delta) => {
  await changePage(postPager, delta, loadMyPosts)
}

const changeCommentPage = async (delta) => {
  await changePage(commentPager, delta, loadMyComments)
}

const changeFavoritePage = async (delta) => {
  await changePage(favoritePager, delta, loadMyFavorites)
}

const changeConversationPage = async (delta) => {
  await changePage(conversationPager, delta, loadMyConversations)
}

const loadBrowseHistory = () => {
  browseHistory.value = getBrowseHistory(100)
  historyPager.value.total = browseHistory.value.length
  const maxPage = getTotalPages(historyPager.value)
  if (historyPager.value.page > maxPage) {
    historyPager.value.page = maxPage
  }
}

const changeHistoryPage = (delta) => {
  const next = historyPager.value.page + delta
  const maxPage = getTotalPages(historyPager.value)
  if (next < 1 || next > maxPage) return
  historyPager.value.page = next
}

const clearHistory = () => {
  clearBrowseHistory()
  historyPager.value.page = 1
  loadBrowseHistory()
}

const jumpByHistory = (item) => {
  if (item?.path) {
    router.push(item.path)
  }
}

const openConversation = (item) => {
  router.push({ path: '/messages', query: { cid: item?.id } })
}

const loadCenterData = async () => {
  await Promise.all([loadMyOrders(), loadMyPosts(), loadMyComments(), loadMyFavorites(), loadMyConversations()])
}

onMounted(async () => {
  await Promise.all([loadProfile(), loadAddresses(), loadCertifications(), loadCenterData()])
  loadBrowseHistory()
})
</script>

<style scoped>
.profile-page {
  max-width: 1280px;
  margin: 0 auto;
}

.profile-layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
  align-items: start;
}

.profile-sidebar {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 10px 26px rgba(15, 40, 70, 0.08);
  position: sticky;
  top: 80px;
}

.side-title {
  font-size: 18px;
  font-weight: 800;
  color: #0f172a;
  margin-bottom: 10px;
}

.side-item {
  width: 100%;
  text-align: left;
  border: 1px solid #e5edf7;
  border-radius: 10px;
  background: #fff;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  cursor: pointer;
  margin-bottom: 8px;
  transition: all 0.2s ease;
}

.side-item:hover {
  border-color: rgba(15, 107, 207, 0.35);
  background: #f7fbff;
}

.side-item.active {
  border-color: rgba(15, 107, 207, 0.5);
  background: linear-gradient(120deg, #eff6ff 0%, #e9fcf6 100%);
}

.side-item-title {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.side-item-desc {
  font-size: 12px;
  color: #64748b;
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-hero {
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  padding: 18px;
  background:
    radial-gradient(300px 140px at 0% 10%, rgba(15, 107, 207, 0.12), transparent 64%),
    linear-gradient(120deg, #ffffff 0%, #f6fbff 100%);
  box-shadow: 0 12px 28px rgba(15, 40, 70, 0.08);
  display: flex;
  align-items: center;
  gap: 14px;
}

.hero-avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  border: 2px solid #dbe5f2;
  object-fit: cover;
  flex-shrink: 0;
}

.hero-avatar-fallback {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
}

.hero-meta h2 {
  margin-bottom: 2px;
  color: #0f172a;
}

.hero-sub {
  color: #475467;
  font-size: 13px;
  margin-bottom: 6px;
}

.hero-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-tags span {
  font-size: 12px;
  color: #1d4ed8;
  border: 1px solid #dbeafe;
  border-radius: 999px;
  background: #eff6ff;
  padding: 3px 10px;
}

.panel-section {
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  padding: 16px;
  background: #fff;
  box-shadow: 0 10px 24px rgba(15, 40, 70, 0.08);
}

.section-head {
  margin-bottom: 12px;
}

.section-head h3 {
  color: #0f172a;
  margin-bottom: 4px;
}

.section-head p {
  color: #64748b;
  font-size: 13px;
}

.profile-card,
.address-card,
.cert-section,
.center-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e6edf8;
}

.profile-card {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
}

.avatar-form-group {
  grid-column: 1 / -1;
}

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-preview {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  border: 1px solid #dbe5f2;
  object-fit: cover;
  flex-shrink: 0;
}

.avatar-preview-fallback {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #1d4ed8;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  font-weight: 700;
}

.avatar-editor-actions {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.btn-upload-avatar {
  width: fit-content;
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.btn-upload-avatar:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.hidden-input {
  display: none;
}

.form-group label {
  display: block;
  margin-bottom: 6px;
  font-weight: 600;
  font-size: 13px;
  color: #475467;
}

.form-group input,
.form-group select,
.form-group textarea {
  width: 100%;
  padding: 10px 11px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.btn-save {
  grid-column: 1 / -1;
  padding: 11px 16px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.address-block {
  margin-top: 14px;
}

.address-block h4 {
  margin-bottom: 8px;
  color: #0f172a;
}

.cert-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 14px;
  margin-bottom: 12px;
}

.form-group.full {
  grid-column: 1 / -1;
}

.btn-cert {
  grid-column: 1 / -1;
  width: 100%;
  border: none;
  border-radius: 8px;
  padding: 11px 14px;
  background: var(--accent);
  color: #fff;
  font-weight: 700;
  cursor: pointer;
}

.cert-tip {
  margin-bottom: 10px;
  color: #475467;
  font-size: 14px;
  line-height: 1.7;
}

.cert-history h4 {
  margin-bottom: 8px;
  color: #0f172a;
}

.cert-item {
  border: 1px solid #e6edf8;
  border-radius: 10px;
  background: #f8fbff;
  padding: 10px;
  margin-bottom: 8px;
}

.cert-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  color: #334155;
  font-size: 13px;
  margin-bottom: 4px;
}

.cert-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.cert-badge.status-0 {
  background: #fef3c7;
  color: #92400e;
}

.cert-badge.status-1 {
  background: #dcfce7;
  color: #166534;
}

.cert-badge.status-2 {
  background: #fee2e2;
  color: #991b1b;
}

.cert-meta {
  color: #64748b;
  font-size: 12px;
  line-height: 1.7;
}

.cert-credentials {
  margin-top: 4px;
  color: #475467;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.center-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.card-head h4 {
  font-size: 16px;
  color: #0f172a;
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-head button {
  padding: 6px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  color: #475467;
  font-size: 12px;
}

.card-head select {
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  padding: 6px 8px;
  color: #475467;
  font-size: 12px;
}

.line-item {
  padding: 8px;
  border: 1px solid #e6edf8;
  border-radius: 8px;
  background: #f8fbff;
  margin-bottom: 8px;
}

.line-item.clickable {
  cursor: pointer;
}

.line-title {
  font-size: 13px;
  color: #0f172a;
  font-weight: 700;
  margin-bottom: 3px;
}

.line-meta {
  font-size: 12px;
  color: #64748b;
}

.badge-unread {
  margin-left: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 10px;
}

.pager button {
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  padding: 4px 10px;
  font-size: 12px;
  color: #475467;
  cursor: pointer;
}

.pager button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pager span {
  font-size: 12px;
  color: #667085;
}

.address-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.address-card {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
}

.addr-info {
  line-height: 1.8;
  color: #334155;
}

.badge-default {
  background: #10b981;
  color: #fff;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}

.empty {
  text-align: center;
  padding: 14px;
  color: #98a2b3;
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  background: #fbfdff;
}

@media (max-width: 980px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .profile-sidebar {
    position: static;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
  }

  .side-title {
    grid-column: 1 / -1;
    margin-bottom: 0;
  }

  .side-item {
    margin-bottom: 0;
  }

  .profile-card,
  .cert-form {
    grid-template-columns: 1fr;
  }

  .avatar-editor {
    align-items: flex-start;
    flex-direction: column;
  }

  .center-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .profile-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .profile-sidebar {
    grid-template-columns: 1fr;
  }
}
</style>
