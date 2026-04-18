<template>
  <div class="expert-home">
    <div class="hero">
      <div>
        <h2>专家工作台</h2>
        <p>聚合平台公告、待回复求助帖与消息提醒，优先处理高风险与高时效问题。</p>
      </div>
      <button class="btn-forum" @click="$router.push('/forum')">查看论坛</button>
    </div>

    <div class="top-grid">
      <section class="notice-panel">
        <div class="panel-head">
          <h3>系统公告</h3>
          <button @click="loadNotices">刷新</button>
        </div>
        <div v-if="notices.length === 0" class="panel-empty">当前暂无面向专家的系统公告。</div>
        <div v-for="item in notices" :key="item.id || item.title" class="notice-item">
          <div class="notice-title">{{ item.title }}</div>
          <div class="notice-content">{{ item.content }}</div>
          <div class="notice-time">{{ formatTime(item.createTime) }}</div>
        </div>
      </section>

      <section class="stats">
        <div class="stat-card">
          <div class="stat-value">{{ stats.unreadMessages }}</div>
          <div class="stat-label">未读私信</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ stats.helpPosts }}</div>
          <div class="stat-label">待回复求助</div>
        </div>
        <div class="stat-card">
          <div class="stat-value">{{ stats.myReplies }}</div>
          <div class="stat-label">我的回复</div>
        </div>
      </section>
    </div>

    <div class="help-posts">
      <div class="panel-head">
        <h3>待回复求助帖</h3>
        <button @click="loadData">刷新</button>
      </div>
      <div class="post-list">
        <div class="post-item" v-for="post in helpPosts" :key="post.id" @click="goToPost(post.id)">
          <div class="post-title">{{ post.title }}</div>
          <div class="post-content">{{ post.content }}</div>
          <div class="post-meta">
            <span>浏览 {{ post.views || 0 }}</span>
            <span>点赞 {{ post.likes || 0 }}</span>
            <span>评论 {{ post.commentCount || 0 }}</span>
          </div>
        </div>
        <div v-if="helpPosts.length === 0" class="panel-empty">暂无待回复求助帖。</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()
const stats = ref({
  unreadMessages: 0,
  helpPosts: 0,
  myReplies: 0
})
const helpPosts = ref([])
const notices = ref([])

const formatTime = (time) => {
  if (!time) return '刚刚'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
  }
  return String(time)
}

const loadNotices = async () => {
  try {
    const res = await request.get('/notices?limit=5')
    if (res.code === 200) {
      notices.value = res.data || []
    }
  } catch (e) {
    notices.value = []
  }
}

const loadData = async () => {
  try {
    const [postsRes, convRes, myCommentsRes] = await Promise.all([
      request.get('/posts?page=1&size=5&sortBy=hot'),
      request.get('/conversations?page=1&size=100'),
      request.get('/posts/my/comments?page=1&size=100')
    ])

    if (postsRes.code === 200) {
      const posts = postsRes.data?.records || []
      helpPosts.value = posts
      stats.value.helpPosts = Number(postsRes.data?.total || posts.length)
    }

    if (convRes.code === 200) {
      const conversations = convRes.data?.records || []
      stats.value.unreadMessages = conversations.reduce((sum, item) => sum + (Number(item.unreadCount) || 0), 0)
    }

    if (myCommentsRes.code === 200) {
      stats.value.myReplies = Number(myCommentsRes.data?.total || (myCommentsRes.data?.records || []).length)
    }
  } catch (e) {
    helpPosts.value = []
  }
}

const goToPost = (id) => {
  router.push(`/post/${id}`)
}

onMounted(async () => {
  await Promise.all([loadNotices(), loadData()])
})
</script>

<style scoped>
.expert-home {
  max-width: 1180px;
  margin: 0 auto;
  display: grid;
  gap: 16px;
}

.hero,
.notice-panel,
.stats,
.help-posts {
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
    radial-gradient(320px 160px at 12% 8%, rgba(255,255,255,0.2), transparent 64%),
    linear-gradient(135deg, #14b8a6 0%, #0d9488 100%);
  color: #fff;
}

.hero h2 {
  font-size: 32px;
  margin-bottom: 6px;
}

.hero p {
  color: rgba(255, 255, 255, 0.88);
}

.btn-forum,
.panel-head button {
  border: none;
  border-radius: 999px;
  cursor: pointer;
  font-weight: 700;
}

.btn-forum {
  background: #f0fdfa;
  color: #0f766e;
  border: 1px solid rgba(15, 118, 110, 0.2);
  padding: 10px 16px;
}

.top-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(300px, 0.9fr);
  gap: 16px;
}

.notice-panel,
.help-posts {
  padding: 18px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.panel-head h3 {
  font-size: 20px;
  color: #10233c;
}

.panel-head button {
  background: #ecfeff;
  color: #0f766e;
  padding: 8px 14px;
}

.notice-item + .notice-item {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #eef3f8;
}

.notice-title {
  font-weight: 800;
  color: #10233c;
  margin-bottom: 6px;
}

.notice-content {
  color: #5f7087;
  line-height: 1.7;
}

.notice-time {
  margin-top: 8px;
  color: #8a98ad;
  font-size: 12px;
}

.stats {
  padding: 16px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.stat-card {
  padding: 18px;
  border-radius: 14px;
  background: linear-gradient(155deg, #f0fdfa, #ecfeff);
  border: 1px solid #e6edf5;
}

.stat-value {
  font-size: 34px;
  font-weight: 800;
  color: #0f766e;
}

.stat-label {
  margin-top: 6px;
  color: #66778d;
}

.post-list {
  display: grid;
  gap: 12px;
}

.post-item {
  padding: 16px;
  border-radius: 14px;
  border: 1px solid #e6edf5;
  background: #fbfdff;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.post-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(15, 40, 70, 0.08);
}

.post-title {
  font-size: 18px;
  font-weight: 800;
  color: #10233c;
}

.post-content {
  margin-top: 8px;
  color: #5f7087;
  line-height: 1.7;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  display: flex;
  gap: 12px;
  margin-top: 10px;
  color: #8a98ad;
  font-size: 13px;
}

.panel-empty {
  padding: 24px 12px;
  text-align: center;
  color: #8a98ad;
  border: 1px dashed #d9e3ee;
  border-radius: 14px;
  background: #fbfdff;
}

@media (max-width: 960px) {
  .top-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
    padding: 18px;
  }

  .hero h2 {
    font-size: 26px;
  }
}
</style>
