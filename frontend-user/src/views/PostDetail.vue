<template>
  <div class="post-detail">
    <button @click="$router.back()" class="btn-back">← 返回</button>
    <div class="post" v-if="post">
      <h2>{{ post.title }}</h2>
      <div class="post-meta">
        <button class="author-entry" @click="chatWithUser(post.userId)">
          <img
            v-if="post.userAvatar"
            :src="resolveImageUrl(post.userAvatar)"
            alt="作者头像"
            class="author-avatar"
          />
          <div v-else class="author-avatar author-avatar-fallback">{{ getAvatarText(post.username, post.userId) }}</div>
          <div class="author-meta">
            <span class="author-name">{{ post.username || `用户#${post.userId}` }}</span>
            <span class="author-id">ID: {{ post.userId || '-' }}</span>
          </div>
        </button>
        <span>发布时间：{{ formatTime(post.createTime) }}</span>
      </div>
      <p class="content">{{ post.content }}</p>
      <div v-if="images.length > 0" class="images">
        <img v-for="(img, idx) in images" :key="`${img}-${idx}`" :src="resolveImageUrl(img)" alt="帖子图片" />
      </div>
      <div class="meta">
        <span>浏览 {{ post.views || 0 }}</span>
        <span>点赞 {{ post.likes || 0 }}</span>
        <span>评论 {{ post.commentCount || comments.length }}</span>
      </div>
      <div class="actions">
        <button :class="['btn-like', { active: post.liked }]" @click="toggleLike">
          {{ post.liked ? '已点赞' : '点赞' }}
        </button>
        <button :class="['btn-fav', { active: post.favorited }]" @click="toggleFavorite">
          {{ post.favorited ? '已收藏' : '收藏' }}
        </button>
      </div>
    </div>
    <div class="comments-section">
      <h3>评论（{{ comments.length }}）</h3>
      <div class="comment-input">
        <textarea v-model.trim="commentText" placeholder="写评论..." />
        <button @click="submitComment">发送评论</button>
      </div>
      <div class="comment-list">
        <div v-for="comment in rootComments" :key="comment.id" class="comment">
          <div class="comment-head">
            <button class="comment-user-link" @click="chatWithUser(comment.userId)">
              <img
                v-if="comment.userAvatar"
                :src="resolveImageUrl(comment.userAvatar)"
                alt="评论用户头像"
                class="comment-avatar"
              />
              <div v-else class="comment-avatar comment-avatar-fallback">{{ getAvatarText(comment.username, comment.userId) }}</div>
              <div class="comment-user-group">
                <div class="comment-user">
                  {{ comment.username || `用户#${comment.userId}` }}
                  <span v-if="comment.userRole === 'EXPERT'" class="expert-badge">专家</span>
                </div>
                <div class="comment-user-id">ID: {{ comment.userId || '-' }}</div>
              </div>
            </button>
            <div class="comment-time">{{ formatTime(comment.createTime) }}</div>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <div class="comment-actions">
            <button class="btn-reply-link" @click="toggleReply(comment)">
              {{ isReplying(comment.id) ? '取消回复' : '回复' }}
            </button>
          </div>
          <div v-if="isReplying(comment.id)" class="reply-input-box">
            <textarea
              v-model.trim="replyDraftMap[comment.id]"
              :placeholder="getReplyPlaceholder(comment)"
            />
            <div class="reply-actions">
              <button class="btn-reply-submit" @click="submitReply(comment)">发送回复</button>
            </div>
          </div>
          <div v-if="getReplies(comment.id).length > 0" class="reply-list">
            <div v-for="reply in getReplies(comment.id)" :key="reply.id" class="reply-item">
              <button class="comment-user-link" @click="chatWithUser(reply.userId)">
                <img
                  v-if="reply.userAvatar"
                  :src="resolveImageUrl(reply.userAvatar)"
                  alt="回复用户头像"
                  class="comment-avatar"
                />
                <div v-else class="comment-avatar comment-avatar-fallback">{{ getAvatarText(reply.username, reply.userId) }}</div>
                <div class="comment-user-group">
                  <div class="comment-user">
                    {{ reply.username || `用户#${reply.userId}` }}
                    <span v-if="reply.userRole === 'EXPERT'" class="expert-badge">专家</span>
                  </div>
                  <div class="comment-user-id">ID: {{ reply.userId || '-' }}</div>
                </div>
              </button>
              <div class="reply-content">{{ getReplyPrefix(reply) }}{{ reply.content }}</div>
              <div class="comment-actions">
                <button class="btn-reply-link" @click="replyToReply(comment, reply)">回复</button>
              </div>
              <div class="comment-time">{{ formatTime(reply.createTime) }}</div>
            </div>
          </div>
        </div>
        <div v-if="rootComments.length === 0" class="empty">暂无评论，快来抢沙发</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../utils/request'
import { addBrowseHistory } from '@/utils/history'

const route = useRoute()
const router = useRouter()
const post = ref(null)
const comments = ref([])
const commentText = ref('')
const images = ref([])
const currentUserId = ref(null)
const replyOpenMap = ref({})
const replyDraftMap = ref({})
const replyTargetMap = ref({})

const getCommentTime = (comment) => {
  if (!comment) return ''
  return comment.createTime || comment.create_time || comment.commentTime || comment.comment_time || ''
}

const formatTime = (time) => {
  if (!time) return '-'

  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
  }

  if (typeof time === 'string') {
    const fallback = new Date(time.replace(/-/g, '/'))
    if (!Number.isNaN(fallback.getTime())) {
      return fallback.toLocaleString('zh-CN')
    }
  }

  return String(time)
}

const decodeJwtPayload = (token) => {
  if (!token) return null
  try {
    const payloadPart = token.split('.')[1]
    if (!payloadPart) return null
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    )
    return JSON.parse(json)
  } catch (e) {
    return null
  }
}

const decodeCurrentUserId = () => {
  const token = localStorage.getItem('token')
  if (!token) return
  const payload = decodeJwtPayload(token)
  const rawId = payload?.userId ?? payload?.id ?? payload?.sub
  const parsedId = Number(rawId)
  if (Number.isFinite(parsedId)) {
    currentUserId.value = parsedId
  }
}

const getAvatarText = (username, userId) => {
  const name = String(username || '').trim()
  if (name) return name.slice(0, 1).toUpperCase()
  if (userId != null) return String(userId).slice(-1)
  return 'U'
}

const parseImages = (raw) => {
  if (!raw) return []
  if (Array.isArray(raw)) return raw.filter(Boolean)
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) {
        return parsed.filter(Boolean)
      }
    } catch (e) {
      if (raw.startsWith('http') || raw.startsWith('/uploads/')) {
        return [raw]
      }
      return raw.split(',').map(item => item.trim()).filter(Boolean)
    }
  }
  return []
}

const commentsById = computed(() => {
  const map = {}
  ;(comments.value || []).forEach(comment => {
    if (comment?.id != null) {
      map[comment.id] = comment
    }
  })
  return map
})

const rootComments = computed(() => {
  return (comments.value || []).filter(comment => !comment.parentId)
})

const resolveRootCommentId = (commentId) => {
  let current = commentsById.value[commentId]
  if (!current) return commentId
  const visited = new Set()
  while (current?.parentId != null) {
    if (visited.has(current.id)) break
    visited.add(current.id)
    const parent = commentsById.value[current.parentId]
    if (!parent) return current.parentId
    current = parent
  }
  return current?.id ?? commentId
}

const getReplies = (rootCommentId) => {
  return (comments.value || []).filter(comment => {
    if (!comment?.id) return false
    if (String(comment.id) === String(rootCommentId)) return false
    return String(resolveRootCommentId(comment.id)) === String(rootCommentId)
  })
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const chatWithUser = async (userId) => {
  if (!userId) return
  if (String(currentUserId.value || '') === String(userId)) return
  try {
    const res = await request.post('/conversations/start', { receiverId: userId })
    if (res.code === 200) {
      router.push(`/messages?cid=${res.data.id}`)
      return
    }
    alert(res.message || '发起会话失败')
  } catch (e) {
    alert(e?.response?.data?.message || '发起会话失败')
  }
}

const loadPost = async () => {
  const res = await request.get(`/posts/${route.params.id}`)
  if (res.code === 200) {
    post.value = res.data
    images.value = parseImages(res.data?.images)
    addBrowseHistory({
      type: 'POST',
      id: res.data.id,
      title: res.data.title,
      subtitle: res.data.content?.slice(0, 28) || '',
      path: `/post/${res.data.id}`,
      image: images.value[0] || ''
    })
  }
}

const loadComments = async () => {
  const res = await request.get(`/posts/${route.params.id}/comments?page=1&size=100`)
  if (res.code === 200) {
    const list = res.data?.records || res.data || []
    comments.value = list.map(c => ({
      ...c,
      createTime: getCommentTime(c)
    }))
  }
}

const toggleLike = async () => {
  const res = await request.post(`/posts/${route.params.id}/like`)
  if (res.code === 200) {
    await loadPost()
  }
}

const toggleFavorite = async () => {
  const res = await request.post(`/posts/${route.params.id}/favorite`)
  if (res.code === 200) {
    await loadPost()
  }
}

const submitComment = async () => {
  if (!commentText.value) return
  const res = await request.post(`/posts/${route.params.id}/comment`, { content: commentText.value })
  if (res.code === 200) {
    commentText.value = ''
    await loadComments()
    await loadPost()
  } else {
    alert(res.message || '评论失败')
  }
}

const isReplying = (commentId) => {
  return !!replyOpenMap.value[commentId]
}

const closeReply = (rootCommentId) => {
  replyOpenMap.value[rootCommentId] = false
  replyDraftMap.value[rootCommentId] = ''
  delete replyTargetMap.value[rootCommentId]
}

const openReply = (rootComment, targetComment) => {
  if (!rootComment?.id || !targetComment?.id) return
  const rootId = rootComment.id
  const targetId = targetComment.id
  if (replyOpenMap.value[rootId] && String(replyTargetMap.value[rootId] || '') === String(targetId)) {
    closeReply(rootId)
    return
  }
  replyOpenMap.value[rootId] = true
  replyTargetMap.value[rootId] = targetId
}

const toggleReply = (rootComment) => {
  openReply(rootComment, rootComment)
}

const replyToReply = (rootComment, replyComment) => {
  openReply(rootComment, replyComment)
}

const getReplyPlaceholder = (rootComment) => {
  if (!rootComment?.id) return '回复评论'
  const targetId = replyTargetMap.value[rootComment.id]
  const target = commentsById.value[targetId]
  const targetName = target?.username || (target?.userId ? `用户#${target.userId}` : '')
  return targetName ? `回复 ${targetName}` : `回复 ${rootComment.username || `用户#${rootComment.userId}`}`
}

const getReplyPrefix = (reply) => {
  const parent = commentsById.value[reply?.parentId]
  if (!parent) return ''
  return `回复 ${parent.username || `用户#${parent.userId}`}：`
}

const submitReply = async (parentComment) => {
  if (!parentComment?.id) return
  const rootId = parentComment.id
  const targetId = replyTargetMap.value[rootId] || rootId
  const content = String(replyDraftMap.value[rootId] || '').trim()
  if (!content) return
  const res = await request.post(`/posts/${route.params.id}/comment`, {
    content,
    parentId: targetId
  })
  if (res.code === 200) {
    closeReply(rootId)
    await loadComments()
    await loadPost()
    return
  }
  alert(res.message || '回复失败')
}

onMounted(() => {
  decodeCurrentUserId()
  loadPost()
  loadComments()
})
</script>

<style scoped>
.post-detail {
  max-width: 960px;
  margin: 0 auto;
}

.btn-back {
  padding: 8px 14px;
  background: #fff;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 12px;
  color: #475467;
  font-weight: 600;
}

.post {
  background: #fff;
  padding: 22px;
  border-radius: 14px;
  margin-bottom: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 10px 22px rgba(15, 40, 70, 0.07);
}

.post h2 {
  margin-bottom: 8px;
  font-size: 30px;
  color: #0f172a;
}

.post-meta {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 10px;
}

.author-entry {
  border: none;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.author-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid #dbe5f2;
  object-fit: cover;
  flex-shrink: 0;
}

.author-avatar-fallback {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #1d4ed8;
  font-size: 14px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.author-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
}

.author-name {
  color: var(--brand);
  font-weight: 700;
}

.author-id {
  font-size: 12px;
  color: #94a3b8;
}

.author-entry:hover .author-name {
  color: var(--brand-strong);
  text-decoration: underline;
}

.content {
  line-height: 1.9;
  color: #334155;
  margin-bottom: 14px;
  white-space: pre-wrap;
}

.images {
  margin-bottom: 10px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}

.images img {
  width: 100%;
  height: 140px;
  border-radius: 10px;
  border: 1px solid #dbe5f2;
  object-fit: cover;
}

.meta {
  display: flex;
  gap: 12px;
  color: #64748b;
  font-size: 13px;
}

.actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.btn-like,
.btn-fav {
  border: 1px solid var(--line-soft);
  background: #fff;
  color: #475467;
  border-radius: 8px;
  padding: 8px 12px;
  cursor: pointer;
  font-weight: 600;
}

.btn-like.active,
.btn-fav.active {
  border-color: rgba(15, 107, 207, 0.35);
  background: #eef6ff;
  color: var(--brand);
}

.comments-section {
  background: #fff;
  padding: 20px;
  border-radius: 14px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 10px 22px rgba(15, 40, 70, 0.07);
}

.comments-section h3 {
  margin-bottom: 12px;
  font-size: 20px;
  color: #0f172a;
}

.comment-input {
  margin-bottom: 8px;
}

.comment-input textarea {
  width: 100%;
  padding: 12px 13px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  resize: vertical;
  min-height: 92px;
  margin-bottom: 10px;
  background: #fbfdff;
}

.comment-input button {
  padding: 9px 18px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.comment-input button:hover {
  background: var(--brand-strong);
}

.comment {
  padding: 12px 0;
  border-bottom: 1px solid var(--line-soft);
}

.comment:last-child {
  border-bottom: none;
}

.comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}

.comment-user-link {
  border: none;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.comment-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid #dbe5f2;
  object-fit: cover;
  flex-shrink: 0;
}

.comment-avatar-fallback {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.comment-user-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.comment-user {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 6px;
}

.comment-user-id {
  font-size: 12px;
  color: #94a3b8;
}

.comment-user-link:hover .comment-user {
  color: var(--brand-strong);
  text-decoration: underline;
}

.expert-badge {
  display: inline-block;
  padding: 2px 8px;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  color: #fff;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
}

.comment-content {
  line-height: 1.6;
  color: #334155;
}

.comment-actions {
  margin-top: 6px;
}

.btn-reply-link {
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 12px;
  font-weight: 700;
  padding: 0;
  cursor: pointer;
}

.btn-reply-link:hover {
  color: var(--brand-strong);
  text-decoration: underline;
}

.reply-input-box {
  margin-top: 8px;
  margin-bottom: 8px;
  padding: 10px;
  border: 1px solid #e6edf8;
  border-radius: 10px;
  background: #f8fbff;
}

.reply-input-box textarea {
  width: 100%;
  min-height: 68px;
  resize: vertical;
  padding: 8px 10px;
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  background: #fff;
}

.reply-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.btn-reply-submit {
  border: none;
  background: var(--brand);
  color: #fff;
  border-radius: 8px;
  font-size: 12px;
  font-weight: 700;
  padding: 6px 12px;
  cursor: pointer;
}

.btn-reply-submit:hover {
  background: var(--brand-strong);
}

.reply-list {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reply-item {
  margin-left: 42px;
  border: 1px solid #e6edf8;
  border-radius: 10px;
  padding: 10px;
  background: #f8fbff;
}

.reply-content {
  margin-top: 6px;
  line-height: 1.6;
  color: #334155;
  font-size: 13px;
}

.comment-time {
  font-size: 12px;
  color: #98a2b3;
  white-space: nowrap;
}

.empty {
  text-align: center;
  padding: 20px;
  color: #98a2b3;
}

@media (max-width: 768px) {
  .post h2 {
    font-size: 24px;
  }

  .post,
  .comments-section {
    padding: 16px;
  }

  .images {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .comment-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .reply-item {
    margin-left: 0;
  }
}
</style>
