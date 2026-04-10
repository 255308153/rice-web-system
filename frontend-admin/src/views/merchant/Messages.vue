<template>
  <div class="merchant-messages">
    <div class="hero">
      <h2>消息管理</h2>
      <p>查看用户咨询和私聊消息。</p>
    </div>

    <div class="tabs">
      <button :class="{ active: tab === 'chat' }" @click="tab = 'chat'">私聊消息</button>
      <button :class="{ active: tab === 'notice' }" @click="tab = 'notice'">系统通知</button>
    </div>

    <div v-if="tab === 'chat'" class="message-container">
      <div class="side-panel conversations">
        <div class="panel-title">会话列表</div>
        <div class="conversation-list">
          <div
            class="conv-item"
            v-for="conv in conversations"
            :key="conv.id"
            :class="{ active: conv.id === currentConvId }"
            @click="selectConv(conv)"
          >
            <div class="conv-top">
              <div class="conv-user">{{ conv.peerName || `用户#${conv.peerId}` }}</div>
              <span v-if="conv.unreadCount > 0" class="unread-dot" :title="`未读 ${conv.unreadCount}`"></span>
            </div>
            <div class="conv-last">{{ conv.lastMessage || '暂无消息' }}</div>
          </div>
          <div v-if="conversations.length === 0" class="empty">暂无会话</div>
        </div>
      </div>

      <div class="message-area">
        <div class="chat-title" v-if="currentConvId">
          与 {{ currentReceiverName || `用户#${currentReceiverId}` }} 对话中
        </div>
        <div v-if="currentConvId" class="chat-scroll-panel">
          <div
            ref="messageListRef"
            class="message-list"
            tabindex="0"
            @scroll="onMessageScroll"
          >
            <div class="msg-item" v-for="msg in messages" :key="msg.id" :class="isSelfMessage(msg) ? 'msg-self' : 'msg-other'">
              <div class="msg-content" v-if="msg.type !== 'IMAGE'">{{ msg.content }}</div>
              <img v-else class="msg-image" :src="resolveImageUrl(msg.content)" alt="图片消息" />
              <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
            </div>
            <div v-if="messages.length === 0" class="empty-message">暂无消息</div>
          </div>
        </div>
        <div class="send-area" v-if="currentConvId">
          <input v-model.trim="newMessage" @keyup.enter="sendTextMessage" placeholder="输入消息..." />
          <button @click="sendTextMessage" class="btn-send">发送</button>
        </div>
        <div v-else class="empty-state">
          <div class="empty-message">请选择会话开始聊天</div>
        </div>
      </div>
    </div>

    <div v-if="tab === 'notice'" class="notice-list">
      <div v-for="notice in notices" :key="notice.id" class="message-item notice">
        <div class="msg-header">
          <span class="type">系统通知</span>
          <span class="time">{{ formatTime(notice.createTime) }}</span>
        </div>
        <div class="msg-content">{{ notice.content }}</div>
      </div>
      <div v-if="notices.length === 0" class="empty">暂无系统通知</div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import request from '../../utils/request'

const tab = ref('chat')
const userId = ref(null)
const conversations = ref([])
const messages = ref([])
const currentConvId = ref(null)
const currentReceiverId = ref(null)
const currentReceiverName = ref('')
const newMessage = ref('')
const messageListRef = ref(null)
const shouldAutoScroll = ref(true)
const notices = ref([])
let refreshTimer = null

const decodeJwtPayload = (token) => {
  try {
    const payloadPart = token.split('.')[1]
    if (!payloadPart) return null
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const utf8Json = decodeURIComponent(
      atob(padded)
        .split('')
        .map(ch => `%${ch.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    )
    return JSON.parse(utf8Json)
  } catch (e) {
    return null
  }
}

const decodeUserId = () => {
  const token = localStorage.getItem('token')
  if (!token) return
  const payload = decodeJwtPayload(token)
  const rawId = payload?.userId ?? payload?.id ?? payload?.sub
  if (rawId === undefined || rawId === null || rawId === '') return
  const parsedId = Number(rawId)
  if (Number.isFinite(parsedId)) {
    userId.value = parsedId
  }
}

const isSelfMessage = (msg) => {
  return String(msg?.senderId ?? '') === String(userId.value ?? '')
}

const formatTime = (time) => {
  if (!time) return ''
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

const loadConversations = async (options = {}) => {
  const { autoSelect = false } = options
  try {
    const res = await request.get('/conversations?page=1&size=100')
    if (res.code === 200) {
      const list = res.data.records || []
      conversations.value = list

      if (autoSelect && !currentConvId.value && list.length > 0) {
        const target = list.find(item => Number(item.unreadCount) > 0) || list[0]
        await selectConv(target)
        return
      }

      if (currentConvId.value && !list.some(item => item.id === currentConvId.value)) {
        currentConvId.value = null
        messages.value = []
        const target = list[0]
        if (target) {
          await selectConv(target)
        }
      }
    }
  } catch (e) {
    console.error('加载会话失败', e)
  }
}

const isNearBottom = () => {
  const el = messageListRef.value
  if (!el) return true
  const threshold = 80
  return el.scrollHeight - el.scrollTop - el.clientHeight <= threshold
}

const scrollToBottom = (force = false) => {
  const el = messageListRef.value
  if (!el) return
  if (!force && !shouldAutoScroll.value) return
  el.scrollTop = el.scrollHeight
}

const onMessageScroll = () => {
  shouldAutoScroll.value = isNearBottom()
}

const loadMessages = async (convId, options = {}) => {
  const { forceScroll = false } = options
  const prevScrollTop = messageListRef.value?.scrollTop ?? 0
  try {
    const res = await request.get(`/conversations/${convId}/messages?page=1&size=200`)
    if (res.code === 200) {
      messages.value = res.data.records || []
      await nextTick()
      if (forceScroll || shouldAutoScroll.value) {
        scrollToBottom(true)
      } else if (messageListRef.value) {
        const maxScrollTop = Math.max(0, messageListRef.value.scrollHeight - messageListRef.value.clientHeight)
        messageListRef.value.scrollTop = Math.min(prevScrollTop, maxScrollTop)
      }
    }
  } catch (e) {
    console.error('加载消息失败', e)
  }
}

const selectConv = async (conv) => {
  currentConvId.value = conv.id
  currentReceiverId.value = conv.peerId || (String(conv.user1Id) === String(userId.value) ? conv.user2Id : conv.user1Id)
  currentReceiverName.value = conv.peerName || ''
  shouldAutoScroll.value = true
  await loadMessages(conv.id, { forceScroll: true })
  await loadConversations()
}

const sendTextMessage = async () => {
  if (!newMessage.value || !currentReceiverId.value) return
  try {
    const res = await request.post('/messages', {
      receiverId: currentReceiverId.value,
      content: newMessage.value,
      type: 'TEXT'
    })
    if (res.code === 200) {
      newMessage.value = ''
      shouldAutoScroll.value = true
      await loadMessages(currentConvId.value, { forceScroll: true })
      await loadConversations()
      return
    }
    alert(res.message || '发送失败')
  } catch (e) {
    alert('发送失败')
  }
}

const loadNotices = async () => {
  try {
    const res = await request.get('/admin/notices')
    if (res.code === 200) {
      notices.value = res.data || []
    }
  } catch (e) {
    console.log('加载通知失败', e)
  }
}

onMounted(async () => {
  decodeUserId()
  await loadConversations({ autoSelect: true })
  await loadNotices()
  refreshTimer = setInterval(async () => {
    await loadConversations({ autoSelect: !currentConvId.value })
    if (currentConvId.value) {
      await loadMessages(currentConvId.value)
    }
  }, 8000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.merchant-messages {
  max-width: 1260px;
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

.message-container {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 12px;
  height: clamp(600px, calc(100vh - 220px), 760px);
  min-height: 600px;
}

.side-panel {
  background: #fff;
  border-radius: 12px;
  padding: 10px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 10px 22px rgba(15, 40, 70, 0.07);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 8px;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
}

.conv-item {
  padding: 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  margin-bottom: 6px;
  cursor: pointer;
}

.conv-item:hover {
  background: #f8fbff;
  border-color: #e6edf8;
}

.conv-item.active {
  background: #eef6ff;
  border-color: rgba(15, 107, 207, 0.3);
}

.conv-user {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.conv-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.unread-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.25);
  flex-shrink: 0;
}

.conv-last {
  margin-top: 2px;
  font-size: 12px;
  color: #667085;
}

.message-area {
  background: #fff;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 10px 22px rgba(15, 40, 70, 0.07);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}

.chat-scroll-panel {
  flex: 1;
  min-height: 0;
  margin: 10px;
  padding: 8px;
  border-radius: 12px;
  border: 1px solid #dbe5f2;
  background: linear-gradient(180deg, #eef5ff 0%, #eaf2ff 100%);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.7);
  overflow: hidden;
  display: flex;
}

.chat-title {
  padding: 10px 14px;
  border-bottom: 1px solid var(--line-soft);
  color: #334155;
  font-size: 14px;
}

.message-list {
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow-y: auto;
  scroll-behavior: smooth;
  overscroll-behavior: contain;
  border-radius: 10px;
  background: linear-gradient(180deg, #f8fbff 0%, #f4f8ff 100%);
}

.message-list:focus {
  outline: none;
}

.msg-item {
  margin-bottom: 12px;
}

.msg-self {
  text-align: right;
}

.msg-content {
  display: inline-block;
  padding: 10px 12px;
  border-radius: 10px;
  max-width: 72%;
  line-height: 1.6;
  text-align: left;
  word-break: break-word;
}

.msg-self .msg-content {
  background: var(--brand);
  color: #fff;
  border-bottom-right-radius: 4px;
}

.msg-other .msg-content {
  background: #fff;
  border: 1px solid #dbe5f2;
  color: #334155;
  border-bottom-left-radius: 4px;
}

.msg-image {
  max-width: 220px;
  max-height: 220px;
  border-radius: 10px;
  border: 1px solid #dbe5f2;
  object-fit: cover;
}

.msg-time {
  margin-top: 3px;
  font-size: 11px;
  color: #98a2b3;
}

.send-area {
  padding: 12px;
  border-top: 1px solid var(--line-soft);
  display: flex;
  gap: 8px;
}

.send-area input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.btn-send {
  padding: 10px 14px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
  background: var(--brand);
  color: #fff;
}

.notice-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-item {
  background: #fff;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.message-item.notice {
  border-left: 3px solid #f59e0b;
}

.msg-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.type {
  font-weight: 700;
  color: #0f172a;
}

.time {
  font-size: 13px;
  color: #98a2b3;
}

.empty,
.empty-message {
  text-align: center;
  padding: 20px;
  color: #98a2b3;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px;
}

@media (max-width: 980px) {
  .message-container {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .side-panel {
    max-height: 300px;
  }

  .message-area {
    min-height: 460px;
  }

  .chat-scroll-panel {
    margin: 8px;
  }
}
</style>
