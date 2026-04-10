<template>
  <div class="ai-page">
    <section class="hero-panel">
      <div class="hero-copy">
        <p class="hero-kicker">AI 识别联动</p>
        <h1>上传图片后，识别结果会自动进入 AI 助手对话</h1>
        <p class="hero-subtitle">页面头部已压缩，核心操作集中在下方双栏。左侧完成上传与识别，右侧持续追问处理建议。</p>
        <div class="hero-tags">
          <span>大米品类识别</span>
          <span>叶片病害识别</span>
          <span>智能问答</span>
        </div>
      </div>
      <div class="hero-badges">
        <div class="hero-chip">
          <strong>{{ recognitionHealth?.available ? '在线' : '离线' }}</strong>
          <span>识别服务</span>
        </div>
        <div class="hero-chip">
          <strong>{{ currentResult ? '已同步' : '待识别' }}</strong>
          <span>识别结果联动</span>
        </div>
        <div class="hero-chip">
          <strong>{{ messages.length }}</strong>
          <span>当前会话消息</span>
        </div>
      </div>
    </section>

    <section class="main-grid">
      <div class="left-panel">
        <article class="card upload-card">
          <div class="card-head">
            <h3>图片上传</h3>
            <p>选择识别类型后上传 JPG / PNG 图片，结果会直接同步到右侧助手。</p>
          </div>

          <div class="type-switch">
            <button
              class="type-btn"
              :class="{ active: recognitionType === RECOGNITION_RICE_TYPE }"
              @click="recognitionType = RECOGNITION_RICE_TYPE"
            >
              大米品类识别
            </button>
            <button
              class="type-btn"
              :class="{ active: recognitionType === RECOGNITION_DISEASE }"
              @click="recognitionType = RECOGNITION_DISEASE"
            >
              叶片病害识别
            </button>
          </div>

          <div class="upload-area" @click="triggerUpload">
            <input ref="fileInput" type="file" accept="image/*" @change="handleUpload" class="hidden-input" />
            <div v-if="!currentImageUrl" class="upload-empty">
              <div class="upload-mark">上传</div>
              <p>点击上传{{ currentTypeLabel }}图片</p>
            </div>
            <img v-else :src="currentImageUrl" alt="识别图片" />
          </div>
        </article>

        <article class="card result-card">
          <div class="card-head">
            <h3>AI 识别结果</h3>
            <p>仅保留识别结果、置信度和简单建议三项核心信息。</p>
          </div>

          <div v-if="currentLoadingRecognize" class="result-empty">识别中，请稍候...</div>
          <div v-else-if="currentResult" class="result-body">
            <div class="result-row">
              <span>{{ currentTypeLabel }}</span>
              <strong>{{ currentResultLabel }}</strong>
            </div>
            <div class="result-row">
              <span>识别置信度</span>
              <strong>{{ formatConfidence(currentResult.confidence) }}</strong>
            </div>
            <div class="result-text">
              <h4>简单建议</h4>
              <p>{{ currentResult.suggestions || '暂无建议内容' }}</p>
            </div>
          </div>
          <div v-else class="result-empty">上传图片后，这里会显示精简后的识别结果。</div>
        </article>
      </div>

      <article class="card chat-card">
        <div class="chat-head">
          <div>
            <h2>AI 助手对话</h2>
            <p>识别完成后，系统会自动把结果同步到这里并给出建议。</p>
          </div>
          <button class="btn-clear" @click="clearConversation">清空会话</button>
        </div>

        <div class="chat-messages">
          <div v-if="messages.length === 0" class="chat-empty">还没有会话内容，上传图片或输入问题后开始咨询。</div>
          <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
            <div class="message-role">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
            <div class="message-body">
              <p v-for="(part, idx) in formatMessageContent(msg.content)" :key="`${msg.id}-${idx}`">{{ part }}</p>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <textarea
            v-model.trim="question"
            placeholder="继续追问，例如：这种病害后续 7 天该怎么观察？"
            @keyup.enter.exact.prevent="sendMessage"
          />
          <div class="chat-actions">
            <button class="btn-send" :disabled="loadingChat" @click="sendMessage">
              {{ loadingChat ? '发送中...' : '发送' }}
            </button>
          </div>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import request from '@/utils/request'

const AI_CHAT_TIMEOUT_MS = 45000
const AI_RECOGNIZE_TIMEOUT_MS = 60000
const RECOGNITION_RICE_TYPE = 'RICE_TYPE'
const RECOGNITION_DISEASE = 'DISEASE'

const fileInput = ref(null)
const recognitionType = ref(RECOGNITION_RICE_TYPE)
const riceImageUrl = ref('')
const diseaseImageUrl = ref('')
const riceResult = ref(null)
const diseaseResult = ref(null)
const recognitionHealth = ref(null)
const messages = ref([])
const question = ref('')
const loadingRiceRecognize = ref(false)
const loadingDiseaseRecognize = ref(false)
const loadingChat = ref(false)

const isRiceTypeSelected = computed(() => recognitionType.value === RECOGNITION_RICE_TYPE)
const currentTypeLabel = computed(() => (isRiceTypeSelected.value ? '品种' : '病害'))
const currentImageUrl = computed(() => (isRiceTypeSelected.value ? riceImageUrl.value : diseaseImageUrl.value))
const currentResult = computed(() => (isRiceTypeSelected.value ? riceResult.value : diseaseResult.value))
const currentLoadingRecognize = computed(() => (
  isRiceTypeSelected.value ? loadingRiceRecognize.value : loadingDiseaseRecognize.value
))

const currentResultLabel = computed(() => {
  if (!currentResult.value) return '--'
  return currentResult.value.riceType || currentResult.value.type || currentResult.value.diseaseName || '未识别'
})

const isTimeoutError = (err) => {
  const msg = String(err?.message || '').toLowerCase()
  return err?.code === 'ECONNABORTED' || msg.includes('timeout')
}

const formatConfidence = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return '--'
  const normalized = num > 1 ? Math.min(num, 100) / 100 : Math.max(num, 0)
  return `${(normalized * 100).toFixed(1)}%`
}

const formatMessageContent = (content) => {
  const raw = String(content || '').replace(/\r\n/g, '\n').trim()
  if (!raw) return ['']
  const lines = raw.split(/\n+/).map(item => item.trim()).filter(Boolean)
  if (lines.length > 1) return lines
  return raw.split(/(?<=[。！？；])/).map(item => item.trim()).filter(Boolean)
}

const triggerUpload = () => {
  fileInput.value?.click()
}

const refreshRecognitionHealth = async () => {
  try {
    const res = await request.get('/ai/recognition/health', { timeout: 8000 })
    if (res.code === 200) {
      recognitionHealth.value = res.data
      return
    }
  } catch (e) {
  }
  recognitionHealth.value = { available: false }
}

const pushRecognitionMessages = (result) => {
  const label = result.riceType || result.type || result.diseaseName || '未识别'
  const userContent = `已自动同步识别结果：${currentTypeLabel.value}${label}，置信度 ${formatConfidence(result.confidence)}。`
  messages.value.push({ id: Date.now(), role: 'user', content: userContent })
  messages.value.push({
    id: Date.now() + 1,
    role: 'assistant',
    content: result.assistantReply || result.suggestions || '识别已完成，请继续输入问题。'
  })
}

const handleUpload = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  const isRice = isRiceTypeSelected.value
  const endpoint = isRice ? '/ai/recognize/rice-type' : '/ai/recognize/disease'
  const loadingRef = isRice ? loadingRiceRecognize : loadingDiseaseRecognize
  const resultRef = isRice ? riceResult : diseaseResult

  if (isRice) {
    riceImageUrl.value = URL.createObjectURL(file)
  } else {
    diseaseImageUrl.value = URL.createObjectURL(file)
  }

  const formData = new FormData()
  formData.append('image', file)

  loadingRef.value = true
  try {
    const res = await request.post(endpoint, formData, { timeout: AI_RECOGNIZE_TIMEOUT_MS })
    if (res.code === 200) {
      resultRef.value = res.data
      pushRecognitionMessages(res.data)
      return
    }
    alert(res.message || '识别失败')
  } catch (e) {
    alert(isTimeoutError(e) ? '识别超时，请稍后重试' : '识别失败，请稍后重试')
  } finally {
    loadingRef.value = false
    event.target.value = ''
  }
}

const sendMessage = async () => {
  const text = question.value.trim()
  if (!text || loadingChat.value) return

  messages.value.push({ id: Date.now(), role: 'user', content: text })
  question.value = ''
  loadingChat.value = true

  try {
    const res = await request.post('/ai/chat', { message: text }, { timeout: AI_CHAT_TIMEOUT_MS })
    if (res.code === 200) {
      messages.value.push({
        id: Date.now() + 1,
        role: 'assistant',
        content: typeof res.data === 'string' ? res.data : (res.data?.answer || '已收到问题，暂无文本回复')
      })
      return
    }
    messages.value.push({ id: Date.now() + 1, role: 'assistant', content: res.message || '请求失败，请稍后重试。' })
  } catch (e) {
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: isTimeoutError(e) ? 'AI 响应超时，请稍后重试。' : '网络异常，请稍后重试。'
    })
  } finally {
    loadingChat.value = false
  }
}

const clearConversation = () => {
  messages.value = []
}

onMounted(() => {
  refreshRecognitionHealth()
})
</script>

<style scoped>
.ai-page {
  max-width: 1320px;
  margin: 0 auto;
  display: grid;
  gap: 16px;
}

.hero-panel {
  padding: 18px 22px;
  border-radius: var(--radius-lg);
  background:
    radial-gradient(340px 140px at 10% 10%, rgba(255,255,255,0.22), transparent 64%),
    linear-gradient(135deg, #14b8a6 0%, #0d9488 100%);
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  box-shadow: var(--shadow-soft);
}

.hero-kicker {
  font-size: 12px;
  letter-spacing: 1.8px;
  text-transform: uppercase;
  opacity: 0.84;
  margin-bottom: 8px;
}

.hero-panel h1 {
  font-size: 32px;
  line-height: 1.2;
  margin-bottom: 8px;
}

.hero-subtitle {
  color: rgba(255, 255, 255, 0.88);
  line-height: 1.7;
}

.hero-tags {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 14px;
}

.hero-tags span {
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.24);
  font-size: 12px;
}

.hero-badges {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.hero-chip {
  min-width: 118px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.1);
  text-align: center;
}

.hero-chip strong {
  display: block;
  font-size: 20px;
  margin-bottom: 4px;
}

.hero-chip span {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.84);
}

.main-grid {
  display: grid;
  grid-template-columns: minmax(420px, 0.95fr) minmax(0, 1.05fr);
  gap: 16px;
}

.left-panel {
  display: grid;
  gap: 16px;
}

.card {
  background: #fff;
  border: 1px solid rgba(94, 234, 212, 0.18);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: 18px;
}

.card-head h3,
.chat-head h2 {
  color: var(--text-main);
  font-size: 22px;
  margin-bottom: 6px;
}

.card-head p,
.chat-head p {
  color: var(--text-muted);
  line-height: 1.7;
}

.type-switch {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin: 14px 0;
}

.type-btn {
  border: 1px solid rgba(94, 234, 212, 0.28);
  background: rgba(45, 212, 191, 0.06);
  border-radius: 10px;
  padding: 12px 14px;
  font-weight: 700;
  color: var(--brand-strong);
  cursor: pointer;
}

.type-btn.active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

.upload-area {
  margin-top: 6px;
  min-height: 280px;
  border: 1px dashed rgba(94, 234, 212, 0.36);
  border-radius: 14px;
  background: linear-gradient(145deg, rgba(20, 184, 166, 0.08), rgba(45, 212, 191, 0.14));
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
}

.upload-area img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.upload-empty {
  text-align: center;
  color: var(--text-muted);
}

.upload-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 82px;
  height: 82px;
  border-radius: 50%;
  background: rgba(20, 184, 166, 0.16);
  color: var(--brand-strong);
  font-weight: 800;
  margin-bottom: 14px;
}

.hidden-input {
  display: none;
}

.result-body {
  display: grid;
  gap: 14px;
  margin-top: 12px;
}

.result-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-radius: 12px;
  background: rgba(45, 212, 191, 0.06);
  border: 1px solid rgba(94, 234, 212, 0.16);
}

.result-row span {
  color: var(--text-muted);
}

.result-row strong {
  color: var(--text-main);
  font-size: 18px;
}

.result-text {
  padding: 16px;
  border-radius: 16px;
  background: rgba(20, 184, 166, 0.08);
  border: 1px solid rgba(94, 234, 212, 0.22);
}

.result-text h4 {
  font-size: 16px;
  color: var(--brand-strong);
  margin-bottom: 8px;
}

.result-text p,
.result-empty,
.chat-empty {
  color: var(--text-muted);
  line-height: 1.7;
}

.chat-card {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 640px;
}

.chat-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.btn-clear,
.btn-send {
  border: none;
  cursor: pointer;
  font-weight: 700;
  border-radius: 12px;
}

.btn-clear {
  background: rgba(20, 184, 166, 0.1);
  color: var(--brand-strong);
  padding: 10px 14px;
}

.chat-messages {
  margin-top: 14px;
  padding: 6px 2px 0;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 12px;
}

.message {
  max-width: 88%;
  display: grid;
  gap: 6px;
}

.message.user {
  justify-self: end;
}

.message.assistant {
  justify-self: start;
}

.message-role {
  font-size: 12px;
  font-weight: 700;
  color: var(--text-muted);
}

.message-body {
  padding: 14px 16px;
  border-radius: 16px;
  line-height: 1.75;
  box-shadow: 0 8px 18px rgba(15, 40, 70, 0.04);
}

.message.user .message-body {
  background: var(--brand);
  color: #fff;
}

.message.assistant .message-body {
  background: rgba(45, 212, 191, 0.06);
  color: #24364b;
  border: 1px solid rgba(94, 234, 212, 0.16);
}

.chat-input {
  margin-top: 12px;
  border-top: 1px solid rgba(94, 234, 212, 0.18);
  padding-top: 14px;
}

.chat-input textarea {
  width: 100%;
  min-height: 110px;
  border: 1px solid rgba(94, 234, 212, 0.3);
  border-radius: 16px;
  padding: 14px 16px;
  resize: vertical;
  background: #ffffff;
}

.chat-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.btn-send {
  background: var(--brand);
  color: #fff;
  padding: 11px 18px;
}

.btn-send:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 1080px) {
  .main-grid {
    grid-template-columns: 1fr;
  }

  .chat-card {
    min-height: auto;
  }
}

@media (max-width: 768px) {
  .hero-panel,
  .chat-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .hero-panel h1 {
    font-size: 26px;
  }

  .type-switch {
    grid-template-columns: 1fr;
  }

  .message {
    max-width: 100%;
  }
}
</style>
