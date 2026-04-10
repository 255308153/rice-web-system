<template>
  <div class="ai-page">
    <div class="ai-shell">
      <section class="hero-panel">
        <div class="hero-copy">
          <h1 class="hero-title">AI智能识别中心</h1>
          <p class="hero-subtitle">上传图片识别大米品种与病害，并通过智能助手获取可执行建议。</p>
          <div class="hero-tags">
            <span>图片识别</span>
            <span>病害预警</span>
            <span>智能问答</span>
          </div>
        </div>
        <div class="hero-side">
          <div class="hero-chip">
            <strong>{{ result ? '已识别' : '待识别' }}</strong>
            <span>图片分析状态</span>
          </div>
          <div class="hero-chip">
            <strong>
              {{
                recognitionHealth
                  ? (recognitionHealth.available ? '在线' : '离线')
                  : '--'
              }}
            </strong>
            <span>
              识别服务
              {{
                recognitionHealth && recognitionHealth.provider
                  ? `(${recognitionHealth.provider})`
                  : ''
              }}
            </span>
          </div>
          <div class="hero-chip">
            <strong>{{ messages.length }}</strong>
            <span>助手会话消息</span>
          </div>
        </div>
      </section>

      <section class="ai-grid">
        <article class="card upload-card">
          <div class="card-head">
            <h3>图像上传</h3>
            <p>支持 JPG / PNG，建议清晰近景图</p>
          </div>
          <div class="upload-area" @click="triggerUpload">
            <input ref="fileInput" type="file" accept="image/*" @change="handleUpload" style="display:none" />
            <div v-if="!imageUrl" class="upload-empty">
              <div class="upload-icon">▣</div>
              <p>点击上传图片</p>
            </div>
            <img v-else :src="imageUrl" />
          </div>
          <p class="upload-tip">上传后将自动调用识别模型，结果会显示在右侧。</p>
        </article>

        <article class="card result-card">
          <div class="card-head">
            <h3>识别结果</h3>
            <p>展示品种、病害、置信度与处理建议</p>
          </div>
          <div v-if="loadingRecognize" class="empty-state">识别中，请稍候...</div>
          <div v-else-if="result" class="result-body">
            <div class="result-row">
              <span>品种</span>
              <strong>{{ result.riceType || result.type || '未知' }}</strong>
            </div>
            <div class="result-row">
              <span>病害</span>
              <strong>{{ result.diseaseName || '未识别病害' }}</strong>
            </div>
            <div class="result-row">
              <span>品种置信度</span>
              <strong>{{ formatConfidence(result.riceConfidence) }}</strong>
            </div>
            <div class="result-row">
              <span>病害置信度</span>
              <strong>{{ formatConfidence(result.diseaseConfidence) }}</strong>
            </div>
            <div class="result-row">
              <span>综合置信度</span>
              <strong>{{ formatConfidence(result.confidence) }}</strong>
            </div>
            <div class="result-row">
              <span>服务来源</span>
              <strong>{{ formatProvider(result.provider, result.modelVersion) }}</strong>
            </div>
            <div class="result-text">
              <h4>建议</h4>
              <p>{{ result.suggestions || '暂无建议内容' }}</p>
            </div>
            <p v-if="result.yoloServiceUrl" class="result-meta">服务地址：{{ result.yoloServiceUrl }}</p>
          </div>
          <div v-else class="empty-state">上传图片后，这里将显示识别结果。</div>
        </article>
      </section>

      <section class="card chat-card">
        <div class="chat-header">
          <h2>AI助手</h2>
          <div class="quick-ask">
            <button type="button" @click="fillQuestion('如何预防水稻病害？')">病害防治</button>
            <button type="button" @click="fillQuestion('适合当前季节的施肥建议？')">施肥建议</button>
            <button type="button" @click="fillQuestion('如何提高大米口感？')">品质提升</button>
          </div>
        </div>
        <div class="messages">
          <div v-if="messages.length === 0" class="empty-state">还没有对话，输入问题后开始咨询。</div>
          <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
            <div class="message-role">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
            <div>{{ msg.content }}</div>
          </div>
        </div>
        <div class="input-box">
          <input
            v-model="question"
            placeholder="输入问题，例如：稻叶发黄怎么处理？"
            @keyup.enter="sendMessage"
          />
          <button class="btn-primary" :disabled="loadingChat" @click="sendMessage">
            {{ loadingChat ? '发送中...' : '发送' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '@/utils/request'

const AI_CHAT_TIMEOUT_MS = 45000
const AI_RECOGNIZE_TIMEOUT_MS = 60000

const fileInput = ref(null)
const imageUrl = ref('')
const result = ref(null)
const recognitionHealth = ref(null)
const messages = ref([])
const question = ref('')
const loadingRecognize = ref(false)
const loadingChat = ref(false)

const isTimeoutError = (err) => {
  const msg = String(err?.message || '').toLowerCase()
  return err?.code === 'ECONNABORTED' || msg.includes('timeout')
}

const triggerUpload = () => {
  if (fileInput.value) {
    fileInput.value.click()
  }
}

const handleUpload = async (e) => {
  const file = e.target.files[0]
  if (!file) return

  imageUrl.value = URL.createObjectURL(file)

  const formData = new FormData()
  formData.append('image', file)

  loadingRecognize.value = true
  try {
    const res = await request.post('/ai/recognize', formData, { timeout: AI_RECOGNIZE_TIMEOUT_MS })
    if (res.code === 200) {
      result.value = res.data
      return
    }
    alert(res.message || '识别失败')
  } catch (e) {
    alert(isTimeoutError(e) ? '识别超时，请稍后重试' : '识别失败，请稍后重试')
  } finally {
    loadingRecognize.value = false
  }
}

const fillQuestion = (text) => {
  question.value = text
}

const refreshRecognitionHealth = async () => {
  try {
    const res = await request.get('/ai/recognition/health', { timeout: 8000 })
    if (res.code === 200) {
      recognitionHealth.value = res.data
      return
    }
  } catch {
  }
  recognitionHealth.value = {
    available: false,
    provider: 'unreachable'
  }
}

const normalizeConfidence = (value) => {
  const num = Number(value)
  if (!Number.isFinite(num)) return null
  if (num <= 0) return 0
  if (num > 1) return Math.min(num, 100) / 100
  return num
}

const formatConfidence = (value) => {
  const normalized = normalizeConfidence(value)
  if (normalized === null) return '--'
  return `${(normalized * 100).toFixed(1)}%`
}

const formatProvider = (provider, version) => {
  const source = String(provider || '').trim()
  const modelVersion = String(version || '').trim()
  const sourceLabel = source === 'mock-fallback' ? '本地兜底' : (source || '远程模型')
  return modelVersion ? `${sourceLabel} (${modelVersion})` : sourceLabel
}

const sendMessage = async () => {
  const text = question.value.trim()
  if (!text) return
  if (loadingChat.value) return

  messages.value.push({ id: Date.now(), role: 'user', content: text })
  question.value = ''

  loadingChat.value = true
  try {
    const res = await request.post('/ai/chat', { message: text }, { timeout: AI_CHAT_TIMEOUT_MS })
    if (res.code === 200) {
      const content = typeof res.data === 'string' ? res.data : (res.data?.answer || '已收到问题，暂无文本回复')
      messages.value.push({ id: Date.now() + 1, role: 'assistant', content })
      return
    }
    messages.value.push({ id: Date.now() + 1, role: 'assistant', content: res.message || '请求失败，请稍后重试。' })
  } catch (e) {
    messages.value.push({
      id: Date.now() + 1,
      role: 'assistant',
      content: isTimeoutError(e) ? 'AI响应超时，请稍后重试。' : '网络异常，请稍后重试。'
    })
  } finally {
    loadingChat.value = false
  }
}

onMounted(() => {
  refreshRecognitionHealth()
})
</script>

<style scoped>
.ai-page {
  padding: 6px 0 26px;
}

.ai-shell {
  max-width: 1320px;
  margin: 0 auto;
  font-family: 'Avenir Next', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', sans-serif;
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  align-items: stretch;
  gap: 14px;
  padding: 24px;
  border-radius: 16px;
  background:
    radial-gradient(420px 240px at 20% 15%, rgba(255,255,255,0.22), transparent 70%),
    linear-gradient(125deg, #0f6bcf 0%, #0f766e 100%);
  color: #fff;
  box-shadow: 0 12px 30px rgba(15, 40, 70, 0.2);
  margin-bottom: 14px;
  animation: riseIn 0.36s ease;
}

.hero-copy {
  flex: 1;
}

.hero-title {
  font-size: 32px;
  margin-bottom: 8px;
  letter-spacing: 0.4px;
}

.hero-subtitle {
  font-size: 15px;
  line-height: 1.7;
  opacity: 0.94;
  max-width: 680px;
}

.hero-tags {
  margin-top: 14px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.hero-tags span {
  padding: 6px 10px;
  font-size: 12px;
  border-radius: 999px;
  background: rgba(255,255,255,0.16);
  border: 1px solid rgba(255,255,255,0.3);
}

.hero-side {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 210px;
}

.hero-chip {
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(15, 23, 42, 0.2);
  border: 1px solid rgba(255,255,255,0.26);
  display: flex;
  flex-direction: column;
}

.hero-chip strong {
  font-size: 20px;
  margin-bottom: 2px;
}

.hero-chip span {
  font-size: 12px;
  opacity: 0.9;
}

.ai-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin-bottom: 14px;
}

.card {
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 14px;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  padding: 16px;
  animation: riseIn 0.34s ease;
}

.card-head {
  margin-bottom: 10px;
}

.card-head h3 {
  font-size: 20px;
  margin-bottom: 4px;
  color: #0f172a;
}

.card-head p {
  color: #667085;
  font-size: 13px;
}

.upload-area {
  height: 320px;
  border: 1px dashed rgba(15, 107, 207, 0.35);
  background:
    linear-gradient(145deg, #f7fbff 0%, #eff6ff 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s ease, transform 0.2s ease;
}

.upload-area:hover {
  border-color: rgba(15, 107, 207, 0.62);
  transform: translateY(-2px);
}

.upload-empty {
  text-align: center;
  color: #475467;
}

.upload-icon {
  font-size: 28px;
  margin-bottom: 8px;
}

.upload-area img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 12px;
}

.upload-tip {
  margin-top: 10px;
  font-size: 12px;
  color: #98a2b3;
}

.result-card {
  display: flex;
  flex-direction: column;
}

.result-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.result-row {
  border: 1px solid #e7eff9;
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-row span {
  color: #667085;
  font-size: 13px;
}

.result-row strong {
  color: #0f172a;
  font-size: 16px;
}

.result-text {
  border-radius: 10px;
  background: #f7fbff;
  border: 1px solid #e7eff9;
  padding: 12px;
}

.result-text h4 {
  margin-bottom: 6px;
  color: #0f172a;
}

.result-text p {
  color: #475467;
  line-height: 1.7;
  font-size: 14px;
}

.result-meta {
  margin: 0;
  font-size: 12px;
  color: #667085;
  word-break: break-all;
}

.chat-card {
  padding: 18px;
  animation-delay: 0.08s;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 12px;
}

.chat-header h2 {
  font-size: 24px;
  color: #0f172a;
}

.quick-ask {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.quick-ask button {
  border: 1px solid rgba(15, 107, 207, 0.32);
  border-radius: 999px;
  background: #fff;
  color: var(--brand);
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
}

.messages {
  background: linear-gradient(180deg, #f8fbff 0%, #f3f8ff 100%);
  border: 1px solid #e6edf8;
  border-radius: 12px;
  padding: 14px;
  height: 360px;
  overflow-y: auto;
  margin-bottom: 12px;
  text-align: left;
}

.message {
  padding: 10px 12px;
  border-radius: 12px;
  margin-bottom: 10px;
  max-width: 78%;
  line-height: 1.6;
  font-size: 14px;
}

.message-role {
  font-size: 11px;
  opacity: 0.78;
  margin-bottom: 3px;
}

.message.user {
  background: var(--brand);
  color: #fff;
  margin-left: auto;
}

.message.assistant {
  background: #ffffff;
  border: 1px solid #dbe5f2;
  color: #334155;
}

.empty-state {
  height: 100%;
  min-height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  color: #98a2b3;
  font-size: 14px;
  border: 1px dashed #d8e2f0;
  border-radius: 10px;
  background: #fbfdff;
}

.input-box {
  display: flex;
  gap: 12px;
}

.input-box input {
  flex: 1;
  padding: 12px 14px;
  border: 1px solid #d8e2f0;
  border-radius: 10px;
  font-size: 14px;
  background: #fff;
}

.btn-primary {
  border: none;
  border-radius: 10px;
  padding: 0 18px;
  background: var(--brand);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 768px) {
  .hero-panel {
    flex-direction: column;
    padding: 16px;
  }

  .hero-title {
    font-size: 24px;
  }

  .hero-side {
    width: 100%;
    min-width: 0;
  }

  .ai-grid {
    grid-template-columns: 1fr;
  }

  .upload-area {
    height: 240px;
  }

  .chat-header {
    flex-direction: column;
  }

  .chat-header h2 {
    font-size: 20px;
  }

  .messages {
    height: 300px;
    padding: 12px;
  }

  .message {
    max-width: 92%;
  }

  .input-box {
    flex-direction: column;
  }

  .btn-primary {
    height: 40px;
  }
}
</style>
