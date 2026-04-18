<template>
  <div class="expert-profile">
    <div class="hero">
      <h2>专家个人中心</h2>
      <p>查看认证状态、回复记录和收藏内容。</p>
    </div>

    <div class="tabs">
      <button :class="{ active: tab === 'cert' }" @click="tab = 'cert'">认证状态</button>
      <button :class="{ active: tab === 'replies' }" @click="tab = 'replies'">我的回复</button>
      <button :class="{ active: tab === 'favorites' }" @click="tab = 'favorites'">我的收藏</button>
      <button :class="{ active: tab === 'messages' }" @click="tab = 'messages'">我的私信</button>
    </div>

    <div v-if="tab === 'cert'" class="cert-section">
      <div class="cert-card">
        <div class="cert-status" :class="certStatus">
          {{ certStatusText }}
        </div>
        <div class="cert-info">
          <p><strong>专业领域：</strong>{{ expertField || '未设置' }}</p>
          <p><strong>认证时间：</strong>{{ certTime || '-' }}</p>
        </div>
      </div>
    </div>

    <div v-if="tab === 'replies'" class="list-section">
      <div v-for="reply in replies" :key="reply.id" class="list-item" @click="goToPost(reply.postId)">
        <div class="item-title">回复：{{ reply.postTitle }}</div>
        <div class="item-content">{{ reply.content }}</div>
        <div class="item-time">{{ formatTime(reply.createTime) }}</div>
      </div>
      <div v-if="replies.length === 0" class="empty">暂无回复记录</div>
    </div>

    <div v-if="tab === 'favorites'" class="list-section">
      <div v-for="fav in favorites" :key="fav.id" class="list-item" @click="goToPost(fav.postId)">
        <div class="item-title">{{ fav.postTitle }}</div>
        <div class="item-time">收藏于 {{ formatTime(fav.createTime) }}</div>
      </div>
      <div v-if="favorites.length === 0" class="empty">暂无收藏</div>
    </div>

    <div v-if="tab === 'messages'" class="list-section">
      <div v-for="msg in messages" :key="msg.id" class="list-item">
        <div class="msg-direction">{{ msg.senderId === currentUserId ? '发给' : '来自' }}</div>
        <div class="item-title">{{ msg.senderId === currentUserId ? (msg.receiverName || `用户#${msg.receiverId}`) : (msg.senderName || `用户#${msg.senderId}`) }}</div>
        <div class="item-content">{{ msg.content }}</div>
        <div class="item-time">{{ formatTime(msg.createTime) }}</div>
      </div>
      <div v-if="messages.length === 0" class="empty">暂无私信</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import { decodeJwtPayload } from '../utils/jwt'

const router = useRouter()
const tab = ref('cert')
const certStatus = ref('approved')
const certStatusText = ref('已认证')
const expertField = ref('水稻病害防治')
const certTime = ref('2026-01-15')
const replies = ref([])
const favorites = ref([])
const messages = ref([])
const currentUserId = ref(null)

const decodeUserId = () => {
  const token = localStorage.getItem('token')
  if (!token) return
  const payload = decodeJwtPayload(token)
  if (!payload) return
  currentUserId.value = payload.userId || payload.id
}

const formatTime = (time) => {
  if (!time) return '-'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
  }
  return String(time)
}

const goToPost = (postId) => {
  router.push(`/post/${postId}`)
}

const loadData = async () => {
  decodeUserId()

  // 加载回复记录
  try {
    const res = await request.get('/expert/replies')
    if (res.code === 200) {
      replies.value = res.data || []
    }
  } catch (e) {
    console.log('加载回复失败', e)
  }

  // 加载收藏
  try {
    const favRes = await request.get('/favorites')
    if (favRes.code === 200) {
      favorites.value = favRes.data || []
    }
  } catch (e) {
    console.log('加载收藏失败', e)
  }

  // 加载私信
  try {
    const msgRes = await request.get('/messages')
    if (msgRes.code === 200) {
      messages.value = msgRes.data || []
    }
  } catch (e) {
    console.log('加载私信失败', e)
  }
}

onMounted(loadData)
</script>

<style scoped>
.expert-profile {
  max-width: 1000px;
  margin: 0 auto;
}

h2 {
  font-size: 30px;
  color: #0f172a;
  margin-bottom: 4px;
}

.hero {
  margin-bottom: 14px;
}

.hero p {
  font-size: 14px;
  color: #667085;
}

.tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
}

.tabs button {
  padding: 9px 16px;
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

.cert-section {
  background: #fff;
  padding: 20px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.cert-card {
  text-align: center;
}

.cert-status {
  display: inline-block;
  padding: 10px 24px;
  border-radius: 999px;
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 20px;
}

.cert-status.approved {
  background: #dcfce7;
  color: #166534;
}

.cert-info {
  text-align: left;
  max-width: 500px;
  margin: 0 auto;
}

.cert-info p {
  margin: 12px 0;
  color: #475467;
}

.list-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-item {
  background: #fff;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  cursor: pointer;
  transition: transform 0.2s ease;
}

.list-item:hover {
  transform: translateY(-2px);
}

.msg-direction {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  margin-bottom: 6px;
  background: #f1f5f9;
  color: #64748b;
}

.item-title {
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 8px;
}

.item-content {
  color: #475467;
  margin-bottom: 8px;
  line-height: 1.6;
}

.item-time {
  font-size: 13px;
  color: #98a2b3;
}

.empty {
  text-align: center;
  padding: 40px;
  color: #98a2b3;
  background: #fff;
  border-radius: 12px;
  border: 1px dashed var(--line-soft);
}
</style>
