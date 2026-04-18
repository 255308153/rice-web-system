<template>
  <div class="profile-page">
  <div class="profile">
    <section class="profile-hero">
      <img v-if="user.avatar" class="hero-avatar" :src="resolveImageUrl(user.avatar)" alt="用户头像" />
      <div v-else class="hero-avatar hero-avatar-fallback">{{ getAvatarText(user.username, user.id) }}</div>
      <div class="hero-meta">
        <h2>{{ user.username || '未命名用户' }}</h2>
        <p class="hero-sub">{{ formatRole(user.role) }} · {{ user.phone || '未绑定手机号' }}</p>
        <div class="hero-tags">
          <span>ID {{ user.id || '-' }}</span>
          <span>{{ isMerchant ? '管理端个人中心' : '账号个人中心' }}</span>
        </div>
      </div>
    </section>

    <h2>个人信息</h2>
    <div class="profile-card" v-if="!isMerchant">
      <div class="form-group">
        <label>用户名</label>
        <input v-model="user.username" disabled />
      </div>
      <div class="form-group">
        <label>手机号</label>
        <input v-model="user.phone" />
      </div>
      <div class="form-group">
        <label>角色</label>
        <input :value="formatRole(user.role)" disabled />
      </div>
      <button @click="saveProfile" class="btn-save">保存</button>
    </div>
    <div class="profile-card merchant-profile" v-else>
      <div class="form-group">
        <label>用户名</label>
        <input v-model="user.username" disabled />
      </div>
      <div class="form-group">
        <label>手机号</label>
        <input v-model.trim="user.phone" placeholder="请输入手机号" />
      </div>
      <div class="form-group">
        <label>角色</label>
        <input :value="formatRole(user.role)" disabled />
      </div>
      <div class="form-group">
        <label>昵称</label>
        <input :value="user.username" disabled placeholder="请输入昵称，不填则默认显示用户名" />
      </div>
      <div class="form-group">
        <label>店铺评分</label>
        <input :value="shop.rating || 5.0" disabled />
      </div>
      <div class="form-group full avatar-form-group">
        <label>头像</label>
        <div class="avatar-editor">
          <img v-if="user.avatar" class="avatar-preview" :src="resolveImageUrl(user.avatar)" alt="头像预览" />
          <div v-else class="avatar-preview avatar-preview-fallback">{{ getAvatarText(user.username, user.id) }}</div>
          <div class="avatar-editor-actions">
            <button type="button" class="btn-upload-avatar" :disabled="avatarUploading" @click="triggerAvatarUpload">
              {{ avatarUploading ? '上传中...' : '上传头像' }}
            </button>
            <input ref="avatarInputRef" class="hidden-input" type="file" accept="image/*" @change="onAvatarFileChange" />
            <input v-model.trim="user.avatar" placeholder="/uploads/images/xxxx.png" />
          </div>
        </div>
      </div>
      <div class="form-group full section-tip">
        店铺信息
      </div>
      <div class="form-group">
        <label>店铺名称</label>
        <input v-model.trim="shop.name" placeholder="请输入店铺名称" />
      </div>
      <div class="form-group">
        <label>店铺联系方式</label>
        <input v-model.trim="shop.contact" placeholder="请输入店铺联系方式" />
      </div>
      <div class="form-group full">
        <label>店铺简介</label>
        <textarea v-model.trim="shop.description" rows="4" placeholder="请输入店铺简介"></textarea>
      </div>
      <div class="form-group">
        <label>营业执照</label>
        <input v-model.trim="shop.license" placeholder="请输入营业执照编号" />
      </div>
      <button @click="saveProfile" class="btn-save">保存</button>
    </div>

    <h3 v-if="!isMerchant">角色认证</h3>
    <div class="cert-section" v-if="!isMerchant">
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

    <h3 v-if="!isMerchant">我的中心</h3>
    <div class="center-grid" v-if="!isMerchant">
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

    <h3 v-if="!isMerchant">收货地址</h3>
    <div class="address-list" v-if="!isMerchant">
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
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { clearBrowseHistory, getBrowseHistory } from '@/utils/history'

const router = useRouter()
const user = ref({ id: null, username: '', phone: '', role: '', avatar: '' })
const avatarInputRef = ref(null)
const avatarUploading = ref(false)
const shop = ref({ id: null, name: '', description: '', license: '', contact: '', rating: 5.0 })
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
const isMerchant = computed(() => user.value.role === 'MERCHANT')

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

const loadProfile = async () => {
  try {
    const res = await request.get('/user/info')
    if (res.code === 200) {
      user.value = res.data
      if (res.data?.role === 'MERCHANT' && res.data?.id) {
        await loadShop(res.data.id)
      }
    }
  } catch (e) {
    console.error('加载失败', e)
  }
}

const loadShop = async (userId) => {
  try {
    const res = await request.get(`/shops/user/${userId}`)
    if (res.code === 200 && res.data) {
      shop.value = {
        id: res.data.id,
        name: res.data.name || '',
        description: res.data.description || '',
        license: res.data.license || '',
        contact: res.data.contact || '',
        rating: res.data.rating || 5.0
      }
    }
  } catch (e) {
    console.error('加载店铺信息失败', e)
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
    if (isMerchant.value) {
      const userPayload = {
        username: user.value.username,
        phone: user.value.phone,
        avatar: user.value.avatar
      }
      const userRes = await request.put('/user/info', userPayload)
      if (userRes.code !== 200) {
        alert(userRes.message || '个人信息保存失败')
        return
      }

      if (!shop.value.id) {
        alert('个人信息已保存，未找到店铺信息')
        await loadProfile()
        return
      }
      const shopRes = await request.put(`/shops/${shop.value.id}`, shop.value)
      if (shopRes.code === 200) {
        alert('保存成功')
        await loadProfile()
      } else {
        alert(shopRes.message || '店铺信息保存失败')
      }
      return
    }

    const payload = {
      username: user.value.username,
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
  await loadProfile()
  if (!isMerchant.value) {
    await Promise.all([loadAddresses(), loadCertifications(), loadCenterData()])
    loadBrowseHistory()
  }
})
</script>

<style scoped>
.profile-page {
  max-width: 1280px;
  margin: 0 auto;
}

.profile {
  max-width: 1120px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.profile-hero {
  border: 1px solid #1c8f84;
  border-radius: 14px;
  padding: 16px;
  background: linear-gradient(120deg, #238ea3 0%, #1f7e74 100%);
  box-shadow: 0 12px 26px rgba(15, 40, 70, 0.08);
  display: flex;
  align-items: center;
  gap: 12px;
  color: #fff;
}

.hero-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.55);
  object-fit: cover;
  flex-shrink: 0;
}

.hero-avatar-fallback {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
}

.hero-meta h2 {
  margin: 0 0 2px;
  color: #fff;
  font-size: 24px;
}

.hero-sub {
  color: rgba(255, 255, 255, 0.92);
  font-size: 13px;
  margin: 0 0 6px;
}

.hero-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.hero-tags span {
  font-size: 12px;
  color: #e8fcf7;
  border: 1px solid rgba(220, 252, 231, 0.35);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  padding: 3px 10px;
}

h2, h3 {
  margin-bottom: 12px;
  color: #0f172a;
}

h3 {
  margin-top: 20px;
  font-size: 22px;
}

.profile-card, .address-card, .cert-section, .center-card {
  background: #fff;
  border-radius: 12px;
  padding: 18px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 10px 22px rgba(15, 40, 70, 0.07);
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
  width: 60px;
  height: 60px;
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
  font-size: 22px;
  font-weight: 700;
}

.avatar-editor-actions {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
}

.btn-upload-avatar {
  padding: 8px 12px;
  border: none;
  border-radius: 8px;
  background: #0f766e;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  white-space: nowrap;
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

.merchant-profile .section-tip {
  margin-top: 4px;
  margin-bottom: -2px;
  font-weight: 700;
  color: #334155;
  border-top: 1px dashed #d6e2f2;
  padding-top: 8px;
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

@media (max-width: 900px) {
  .profile-card {
    grid-template-columns: 1fr;
  }

  .cert-form {
    grid-template-columns: 1fr;
  }

  .center-grid {
    grid-template-columns: 1fr;
  }
}
</style>
