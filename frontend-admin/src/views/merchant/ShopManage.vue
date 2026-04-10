<template>
  <div class="merchant-assistant">
    <div class="hero">
      <div>
        <h2>AI 助手销售建议</h2>
        <p>基于店铺交易数据自动总结销售情况，并在对话中持续保留经营上下文。</p>
      </div>
      <button class="btn-refresh" @click="loadSummary">刷新数据</button>
    </div>

    <div v-if="summary" class="summary-grid">
      <div class="stat-card">
        <div class="stat-value">¥{{ formatMoney(summary.metrics?.daySalesAmount) }}</div>
        <div class="stat-label">今日销售额</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">¥{{ formatMoney(summary.metrics?.monthSalesAmount) }}</div>
        <div class="stat-label">本月销售额</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ summary.metrics?.pendingOrders || 0 }}</div>
        <div class="stat-label">待处理订单</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ summary.metrics?.uniqueBuyerCount || 0 }}</div>
        <div class="stat-label">成交买家数</div>
      </div>
    </div>

    <div class="content-grid">
      <section class="panel overview-panel">
        <div class="panel-head">
          <h3>{{ summary?.shop?.name || '店铺经营概览' }}</h3>
          <p>{{ summary?.shop?.description || '当前店铺暂未填写简介。' }}</p>
        </div>

        <div class="advice-box" v-if="summary?.assistantReply">
          <h4>AI 总结建议</h4>
          <p v-for="(line, idx) in splitMessage(summary.assistantReply)" :key="`advice-${idx}`">{{ line }}</p>
        </div>

        <div class="top-products">
          <h4>热销商品</h4>
          <div v-if="(summary?.topProducts || []).length === 0" class="empty">暂无热销商品数据</div>
          <div v-for="item in summary?.topProducts || []" :key="item.productId" class="product-item">
            <div>
              <strong>{{ item.productName || `商品#${item.productId}` }}</strong>
              <p>销量 {{ item.salesCount || 0 }} 件</p>
            </div>
            <span>¥{{ formatMoney(item.salesAmount) }}</span>
          </div>
        </div>
      </section>

      <section class="panel chat-panel">
        <div class="panel-head">
          <h3>销售助手对话</h3>
          <p>系统会一直带着当前店铺经营摘要回答，无需重复描述销售数据。</p>
        </div>

        <div class="messages">
          <div v-if="messages.length === 0" class="empty">暂无会话内容</div>
          <div v-for="msg in messages" :key="msg.id" :class="['message', msg.role]">
            <div class="message-role">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
            <div class="message-content">
              <p v-for="(line, idx) in splitMessage(msg.content)" :key="`${msg.id}-${idx}`">{{ line }}</p>
            </div>
          </div>
        </div>

        <div class="input-box">
          <textarea
            v-model.trim="question"
            placeholder="例如：最近该主推哪类商品？需要怎么做组合促销？"
            @keyup.enter.exact.prevent="sendMessage"
          />
          <button class="btn-send" :disabled="loadingChat" @click="sendMessage">
            {{ loadingChat ? '发送中...' : '发送' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import request from '../../utils/request'

const summary = ref(null)
const messages = ref([])
const question = ref('')
const loadingChat = ref(false)

const formatMoney = (value) => {
  const num = Number(value)
  return Number.isFinite(num) ? num.toFixed(2) : '0.00'
}

const splitMessage = (text) => {
  const raw = String(text || '').replace(/\r\n/g, '\n').trim()
  if (!raw) return ['']
  const lines = raw.split(/\n+/).map(item => item.trim()).filter(Boolean)
  if (lines.length > 1) return lines
  return raw.split(/(?<=[。！？；])/).map(item => item.trim()).filter(Boolean)
}

const loadSummary = async () => {
  try {
    const res = await request.get('/merchant/assistant/summary')
    if (res.code === 200) {
      summary.value = res.data
      messages.value = res.data?.assistantReply
        ? [{ id: Date.now(), role: 'assistant', content: res.data.assistantReply }]
        : []
      return
    }
    alert(res.message || '加载经营摘要失败')
  } catch (e) {
    alert('加载经营摘要失败')
  }
}

const sendMessage = async () => {
  const text = question.value.trim()
  if (!text || loadingChat.value) return

  messages.value.push({ id: Date.now(), role: 'user', content: text })
  question.value = ''
  loadingChat.value = true
  try {
    const res = await request.post('/merchant/assistant/chat', { message: text })
    if (res.code === 200) {
      messages.value.push({ id: Date.now() + 1, role: 'assistant', content: res.data || '暂无回复内容' })
      return
    }
    messages.value.push({ id: Date.now() + 1, role: 'assistant', content: res.message || '请求失败，请稍后重试。' })
  } catch (e) {
    messages.value.push({ id: Date.now() + 1, role: 'assistant', content: '网络异常，请稍后重试。' })
  } finally {
    loadingChat.value = false
  }
}

onMounted(loadSummary)
</script>

<style scoped>
.merchant-assistant {
  max-width: 1220px;
  margin: 0 auto;
  display: grid;
  gap: 16px;
}

.hero,
.panel,
.stat-card {
  background: #fff;
  border: 1px solid #e6edf5;
  border-radius: 18px;
  box-shadow: 0 14px 30px rgba(15, 40, 70, 0.06);
}

.hero {
  padding: 22px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background:
    radial-gradient(320px 150px at 12% 8%, rgba(255,255,255,0.18), transparent 64%),
    linear-gradient(135deg, #0f6bcf 0%, #0f766e 100%);
  color: #fff;
}

.hero h2 {
  font-size: 30px;
  margin-bottom: 6px;
}

.hero p {
  color: rgba(255, 255, 255, 0.88);
}

.btn-refresh,
.btn-send {
  border: none;
  border-radius: 12px;
  cursor: pointer;
  font-weight: 700;
}

.btn-refresh {
  background: #fff7ed;
  color: #9a3412;
  padding: 10px 16px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  padding: 18px;
}

.stat-value {
  font-size: 30px;
  font-weight: 800;
  color: #0f6bcf;
}

.stat-label {
  margin-top: 6px;
  color: #66778d;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
}

.panel {
  padding: 18px;
}

.panel-head h3 {
  font-size: 22px;
  color: #10233c;
  margin-bottom: 6px;
}

.panel-head p {
  color: #66778d;
  line-height: 1.7;
}

.advice-box {
  margin-top: 14px;
  padding: 16px;
  border-radius: 16px;
  background: linear-gradient(160deg, #fff7ed, #fff);
  border: 1px solid #fde6d3;
}

.advice-box h4,
.top-products h4 {
  font-size: 16px;
  color: #9a3412;
  margin-bottom: 8px;
}

.advice-box p {
  color: #4b5563;
  line-height: 1.75;
}

.top-products {
  margin-top: 16px;
}

.product-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-top: 1px solid #eef3f8;
}

.product-item:first-of-type {
  border-top: none;
}

.product-item strong {
  color: #10233c;
}

.product-item p {
  color: #7b8794;
  margin-top: 4px;
}

.messages {
  margin-top: 14px;
  min-height: 420px;
  max-height: 560px;
  overflow: auto;
  display: grid;
  align-content: start;
  gap: 12px;
}

.message {
  max-width: 88%;
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
  color: #7c8da3;
  margin-bottom: 6px;
}

.message-content {
  padding: 14px 16px;
  border-radius: 16px;
  line-height: 1.75;
}

.message.user .message-content {
  background: #0f6bcf;
  color: #fff;
}

.message.assistant .message-content {
  background: #f8fbff;
  color: #24364b;
  border: 1px solid #e6edf5;
}

.input-box {
  margin-top: 14px;
  border-top: 1px solid #edf2f7;
  padding-top: 14px;
}

.input-box textarea {
  width: 100%;
  min-height: 110px;
  border: 1px solid #dbe6f2;
  border-radius: 16px;
  padding: 14px 16px;
  resize: vertical;
  background: #fbfdff;
}

.btn-send {
  margin-top: 10px;
  background: #0f6bcf;
  color: #fff;
  padding: 11px 18px;
}

.btn-send:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.empty {
  padding: 20px 12px;
  text-align: center;
  color: #8a98ad;
  border: 1px dashed #d9e3ee;
  border-radius: 14px;
  background: #fbfdff;
}

@media (max-width: 1080px) {
  .summary-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .hero h2 {
    font-size: 26px;
  }

  .message {
    max-width: 100%;
  }
}
</style>
