<template>
  <div class="messages">
    <div class="hero">
      <h2>私聊消息</h2>
      <p>主动发起与商户/专家会话，支持发送文字和图片。</p>
    </div>
    <div class="message-container">
      <div class="side-panels">
        <section class="panel contacts">
          <div class="panel-title">发起私聊</div>
          <div class="panel-filters">
            <select v-model="contactRole" @change="loadContacts">
              <option value="ALL">商户+专家</option>
              <option value="MERCHANT">仅商户</option>
              <option value="EXPERT">仅专家</option>
            </select>
            <input v-model.trim="contactKeyword" placeholder="搜索联系人" @keyup.enter="loadContacts" />
          </div>
          <div class="contact-list">
            <div class="contact-item" v-for="contact in contacts" :key="contact.id" @click="startChat(contact)">
              <div class="mini-avatar">{{ (contact.username || 'U').slice(0, 1) }}</div>
              <div class="contact-main">
                <div class="contact-name">{{ contact.username }}</div>
                <div class="contact-meta">{{ formatRole(contact.role) }} · ID {{ contact.id }}</div>
              </div>
            </div>
            <div v-if="contacts.length === 0" class="empty">暂无可发起联系人</div>
          </div>
        </section>

        <section class="panel conversations">
          <div class="panel-title">会话列表</div>
          <div class="conversation-list">
            <div
              class="conv-item"
              v-for="conv in conversations"
              :key="conv.id"
              :class="{ active: conv.id === currentConvId }"
              @click="selectConv(conv)"
            >
              <div class="mini-avatar conv-avatar">{{ (conv.peerName || 'U').slice(0, 1) }}</div>
              <div class="conv-main">
                <div class="conv-top">
                  <div class="conv-user">{{ conv.peerName || `用户#${conv.peerId}` }}</div>
                  <span v-if="conv.unreadCount > 0" class="unread-dot" :title="`未读 ${conv.unreadCount}`"></span>
                </div>
                <div class="conv-last">{{ conv.lastMessage || '暂无消息' }}</div>
              </div>
            </div>
            <div v-if="conversations.length === 0" class="empty">暂无会话</div>
          </div>
        </section>
      </div>

      <div class="message-area">
        <div class="chat-title" v-if="currentConvId">
          与 {{ currentReceiverName || `用户#${currentReceiverId}` }} 对话中
        </div>
        <div
          ref="messageListRef"
          class="message-list"
          v-if="currentConvId"
          tabindex="0"
          @scroll="onMessageScroll"
        >
          <div class="msg-item" v-for="msg in messages" :key="msg.id" :class="isSelfMessage(msg) ? 'msg-self' : 'msg-other'">
            <div class="msg-bubble-wrap">
              <div class="msg-content" v-if="msg.type !== 'IMAGE'">{{ msg.content }}</div>
              <img v-else class="msg-image" :src="resolveImageUrl(msg.content)" alt="图片消息" />
              <div class="msg-time">{{ formatTime(msg.createTime) }}</div>
            </div>
          </div>
          <div v-if="messages.length === 0" class="empty-message">暂无消息，发送第一条消息吧</div>
        </div>
        <div class="send-area" v-if="currentConvId">
          <input v-model.trim="newMessage" @keyup.enter="sendTextMessage" placeholder="输入消息..." />
          <button @click="triggerImageUpload" class="btn-image">图片</button>
          <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="sendImageMessage" />
          <button @click="sendTextMessage" class="btn-send">发送</button>
        </div>
        <div v-else class="empty-message">请选择联系人或会话开始聊天</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import request from '../utils/request'
import { createChatSocket } from '../utils/chatSocket'

const route = useRoute()
const userId = ref(null)
const contacts = ref([])
const contactRole = ref('ALL')
const contactKeyword = ref('')

const conversations = ref([])
const messages = ref([])
const currentConvId = ref(null)
const currentReceiverId = ref(null)
const currentReceiverName = ref('')
const newMessage = ref('')
const imageInputRef = ref(null)
const messageListRef = ref(null)
const shouldAutoScroll = ref(true)
let refreshTimer = null
let wsClient = null

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

const formatRole = (role) => {
  if (role === 'MERCHANT') return '商户'
  if (role === 'EXPERT') return '专家'
  if (role === 'ADMIN') return '管理员'
  return '用户'
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

const loadContacts = async () => {
  try {
    let url = `/messages/contacts?role=${contactRole.value}`
    if (contactKeyword.value) {
      url += `&keyword=${encodeURIComponent(contactKeyword.value)}`
    }
    const res = await request.get(url)
    if (res.code === 200) {
      contacts.value = res.data || []
    }
  } catch (e) {
    console.error('加载联系人失败', e)
  }
}

const loadConversations = async () => {
  try {
    const res = await request.get('/conversations?page=1&size=100')
    if (res.code === 200) {
      conversations.value = res.data.records || []
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

const onRealtimeMessage = async (event) => {
  if (!event || event.type !== 'CHAT_MESSAGE') {
    return
  }
  await loadConversations()
  if (currentConvId.value && String(event.conversationId) === String(currentConvId.value)) {
    shouldAutoScroll.value = true
    await loadMessages(currentConvId.value, { forceScroll: true })
  }
}

const initRealtimeChat = () => {
  if (wsClient) {
    wsClient.disconnect()
  }
  wsClient = createChatSocket({
    onMessage: onRealtimeMessage
  })
  wsClient.connect()
}

const selectConv = async (conv) => {
  currentConvId.value = conv.id
  currentReceiverId.value = conv.peerId || (String(conv.user1Id) === String(userId.value) ? conv.user2Id : conv.user1Id)
  currentReceiverName.value = conv.peerName || ''
  shouldAutoScroll.value = true
  await loadMessages(conv.id, { forceScroll: true })
  await loadConversations()
}

const startChat = async (contact) => {
  try {
    const res = await request.post('/conversations/start', { receiverId: contact.id })
    if (res.code === 200) {
      await loadConversations()
      const target = conversations.value.find(c => c.id === res.data.id || c.peerId === contact.id)
      if (target) {
        await selectConv(target)
      }
    } else {
      alert(res.message || '发起会话失败')
    }
  } catch (e) {
    alert('发起会话失败')
  }
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

const triggerImageUpload = () => {
  if (imageInputRef.value) {
    imageInputRef.value.click()
  }
}

const sendImageMessage = async (event) => {
  const file = event.target.files?.[0]
  if (!file || !currentReceiverId.value) return
  const formData = new FormData()
  formData.append('image', file)
  try {
    const uploadRes = await request.post('/messages/upload-image', formData)
    if (uploadRes.code !== 200 || !uploadRes.data?.url) {
      alert(uploadRes.message || '上传失败')
      return
    }

    const sendRes = await request.post('/messages', {
      receiverId: currentReceiverId.value,
      content: uploadRes.data.url,
      type: 'IMAGE'
    })
    if (sendRes.code === 200) {
      shouldAutoScroll.value = true
      await loadMessages(currentConvId.value, { forceScroll: true })
      await loadConversations()
      return
    }
    alert(sendRes.message || '图片发送失败')
  } catch (e) {
    alert('图片发送失败')
  } finally {
    event.target.value = ''
  }
}

const selectConversationFromRoute = async () => {
  const cid = Number(route.query?.cid)
  if (!Number.isFinite(cid) || cid <= 0) return
  const target = conversations.value.find(conv => Number(conv.id) === cid)
  if (target) {
    await selectConv(target)
  }
}

onMounted(async () => {
  decodeUserId()
  await loadContacts()
  await loadConversations()
  await selectConversationFromRoute()
  initRealtimeChat()
  refreshTimer = setInterval(async () => {
    await loadConversations()
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
  if (wsClient) {
    wsClient.disconnect()
    wsClient = null
  }
})
</script>

<style scoped>
.messages {
  max-width: 1260px;
  margin: 0 auto;
}

h2 {
  margin-bottom: 4px;
  font-size: 30px;
  color: #fff;
}

.hero {
  margin-bottom: 14px;
  padding: 18px 20px;
  border-radius: 16px;
  background:
    radial-gradient(360px 180px at 8% 16%, rgba(255, 255, 255, 0.2), transparent 65%),
    linear-gradient(135deg, #0f6bcf 0%, #0f766e 100%);
  box-shadow: 0 12px 30px rgba(15, 40, 70, 0.18);
}

.hero p {
  color: rgba(255, 255, 255, 0.9);
  font-size: 14px;
}

.message-container {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 14px;
  min-height: 680px;
}

.side-panels {
  display: grid;
  grid-template-rows: 1fr 1fr;
  gap: 14px;
}

.panel {
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  border-radius: 14px;
  padding: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 12px 26px rgba(15, 40, 70, 0.08);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-title {
  font-size: 14px;
  font-weight: 700;
  color: #0b3f7b;
  margin-bottom: 10px;
  letter-spacing: 0.2px;
}

.panel-filters {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.panel-filters select,
.panel-filters input {
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  padding: 9px 10px;
  background: #fbfdff;
  font-size: 13px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.panel-filters select:focus,
.panel-filters input:focus {
  outline: none;
  border-color: rgba(15, 107, 207, 0.45);
  box-shadow: 0 0 0 3px rgba(15, 107, 207, 0.12);
}

.panel-filters select {
  width: 120px;
}

.panel-filters input {
  flex: 1;
}

.contact-list,
.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 2px;
}

.contact-item,
.conv-item {
  padding: 10px;
  border-radius: 11px;
  border: 1px solid #e7eef9;
  margin-bottom: 8px;
  cursor: pointer;
  background: #fff;
  display: flex;
  align-items: center;
  gap: 10px;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.contact-item:hover,
.conv-item:hover {
  border-color: #d0dff4;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.08);
  transform: translateY(-1px);
}

.conv-item.active {
  background: #f1f7ff;
  border-color: rgba(15, 107, 207, 0.45);
  box-shadow: 0 8px 24px rgba(15, 107, 207, 0.14);
}

.mini-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(145deg, #0f6bcf 0%, #0f766e 100%);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.conv-avatar {
  background: linear-gradient(145deg, #334155 0%, #0f6bcf 100%);
}

.contact-main,
.conv-main {
  flex: 1;
  min-width: 0;
}

.contact-name,
.conv-user {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.contact-meta,
.conv-last {
  margin-top: 2px;
  font-size: 12px;
  color: #667085;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.message-area {
  background: linear-gradient(180deg, #ffffff 0%, #fafdff 100%);
  border-radius: 14px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 12px 28px rgba(15, 40, 70, 0.09);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-title {
  padding: 12px 16px;
  border-bottom: 1px solid var(--line-soft);
  color: #0f4b8a;
  font-weight: 700;
  font-size: 14px;
  background: linear-gradient(90deg, rgba(15, 107, 207, 0.09), rgba(15, 118, 110, 0.07));
}

.message-list {
  flex: 1;
  padding: 18px 16px;
  overflow-y: auto;
  scroll-behavior: smooth;
  overscroll-behavior: contain;
  background:
    radial-gradient(380px 180px at -2% -6%, rgba(15, 107, 207, 0.08), transparent 64%),
    linear-gradient(180deg, #f7fbff 0%, #f2f7ff 100%);
}

.message-list:focus {
  outline: none;
}

.msg-item {
  margin-bottom: 14px;
  display: flex;
}

.msg-self {
  justify-content: flex-end;
}

.msg-bubble-wrap {
  display: flex;
  flex-direction: column;
  max-width: 76%;
}

.msg-content {
  display: inline-block;
  padding: 10px 13px;
  border-radius: 12px;
  line-height: 1.6;
  text-align: left;
  word-break: break-word;
  box-shadow: 0 4px 10px rgba(15, 40, 70, 0.06);
}

.msg-self .msg-content {
  background: var(--brand);
  color: #fff;
  border-bottom-right-radius: 5px;
}

.msg-other .msg-content {
  background: #fff;
  border: 1px solid #dbe5f2;
  color: #334155;
  border-bottom-left-radius: 5px;
}

.msg-image {
  max-width: 220px;
  max-height: 220px;
  border-radius: 12px;
  border: 1px solid #dbe5f2;
  box-shadow: 0 4px 10px rgba(15, 40, 70, 0.08);
}

.msg-time {
  margin-top: 5px;
  font-size: 11px;
  color: #98a2b3;
}

.msg-self .msg-time {
  text-align: right;
}

.send-area {
  padding: 12px;
  border-top: 1px solid var(--line-soft);
  display: flex;
  gap: 8px;
  background: #fff;
}

.send-area input {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.send-area input:focus {
  outline: none;
  border-color: rgba(15, 107, 207, 0.46);
  box-shadow: 0 0 0 3px rgba(15, 107, 207, 0.12);
}

.btn-image,
.btn-send {
  padding: 10px 14px;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;
}

.btn-image {
  background: #fff;
  color: var(--brand);
  border: 1px solid rgba(15, 107, 207, 0.36);
}

.btn-image:hover,
.btn-send:hover {
  transform: translateY(-1px);
}

.btn-send {
  background: var(--brand);
  color: #fff;
  box-shadow: 0 6px 14px rgba(15, 107, 207, 0.22);
}

.empty,
.empty-message {
  text-align: center;
  padding: 28px 20px;
  color: #98a2b3;
}

@media (max-width: 980px) {
  h2 {
    font-size: 24px;
  }

  .message-container {
    grid-template-columns: 1fr;
    min-height: 0;
  }

  .side-panels {
    grid-template-rows: none;
    grid-template-columns: 1fr;
  }

  .message-area {
    min-height: 460px;
  }

  .msg-content {
    max-width: 88%;
  }
}
</style>
