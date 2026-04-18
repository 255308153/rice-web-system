<template>
  <div class="forum">
    <div class="hero">
      <h2>助农论坛</h2>
      <p>浏览交流经验、发布图文帖子、参与评论互动。</p>
    </div>

    <div class="toolbar">
      <div class="left">
        <button @click="showPostDialog = true" class="btn-post">发布帖子</button>
      </div>
      <div class="right">
        <input v-model.trim="keyword" placeholder="搜索标题或内容" @keyup.enter="reloadFirstPage" />
        <select v-model="categoryFilter" @change="reloadFirstPage">
          <option value="">全部话题</option>
          <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
        </select>
        <select v-model="sortBy" @change="reloadFirstPage">
          <option value="time">按时间排序</option>
          <option value="hot">按热度排序</option>
        </select>
        <button class="btn-search" @click="reloadFirstPage">搜索</button>
      </div>
    </div>

    <div class="posts">
      <div class="post-card" v-for="post in posts" :key="post.id">
        <h3 @click="$router.push(`/post/${post.id}`)" class="post-title">{{ post.title }}</h3>
        <div class="post-user">
          <button class="user-entry" @click="chatWithUser(post.userId)">
            <img
              v-if="post.userAvatar"
              :src="resolveImageUrl(post.userAvatar)"
              alt="用户头像"
              class="user-avatar"
            />
            <div v-else class="user-avatar user-avatar-fallback">{{ getAvatarText(post.username, post.userId) }}</div>
            <div class="user-meta">
              <span class="username-link">{{ post.username || `用户#${post.userId}` }}</span>
              <span class="user-id">ID: {{ post.userId || '-' }}</span>
            </div>
          </button>
          <span class="cat-tag">{{ post.category || '综合交流' }}</span>
          <span>{{ formatTime(post.createTime) }}</span>
        </div>
        <p class="content">{{ post.content }}</p>
        <div class="images" v-if="post.imagesList.length > 0">
          <img v-for="(img, idx) in post.imagesList" :key="`${img}-${idx}`" :src="resolveImageUrl(img)" alt="帖子图片" />
        </div>
        <div class="meta">
          <span>浏览 {{ post.views || 0 }}</span>
          <span :class="{ active: post.liked }" class="action" @click="likePost(post)">点赞 {{ post.likes || 0 }}</span>
          <span :class="{ active: post.favorited }" class="action" @click="favoritePost(post)">
            {{ post.favorited ? '已收藏' : '收藏' }}
          </span>
          <span class="action" @click="toggleComments(post)">
            {{ post.showComments ? '收起评论' : '展开评论' }}（{{ post.commentCount || 0 }}）
          </span>
        </div>

        <div v-if="post.showComments" class="comments">
          <div class="comment-input">
            <input v-model.trim="post.commentDraft" placeholder="写评论..." />
            <button @click="submitComment(post)">发送</button>
          </div>
          <div class="comment-list">
            <div class="comment-item" v-for="comment in getRootComments(post)" :key="comment.id">
              <div class="comment-head">
                <button class="comment-user-link" @click="chatWithUser(comment.userId)">
                  <img
                    v-if="comment.userAvatar"
                    :src="resolveImageUrl(comment.userAvatar)"
                    alt="用户头像"
                    class="comment-avatar"
                  />
                  <div v-else class="comment-avatar comment-avatar-fallback">{{ getAvatarText(comment.username, comment.userId) }}</div>
                  <div class="comment-user-group">
                    <div class="comment-user">{{ comment.username || `用户#${comment.userId}` }}</div>
                    <div class="comment-user-id">ID: {{ comment.userId || '-' }}</div>
                  </div>
                </button>
                <div class="comment-time">{{ formatTime(comment.createTime) }}</div>
              </div>
              <div class="comment-content">{{ comment.content }}</div>
              <div class="comment-actions">
                <button class="btn-reply-link" @click="toggleReplyInput(post, comment)">
                  {{ isReplying(post.id, comment.id) ? '取消回复' : '回复' }}
                </button>
              </div>
              <div v-if="isReplying(post.id, comment.id)" class="reply-input-box">
                <textarea
                  v-model.trim="replyDraftMap[getReplyKey(post.id, comment.id)]"
                  :placeholder="getReplyPlaceholder(post, comment)"
                />
                <div class="reply-actions">
                  <button class="btn-reply-submit" @click="submitReply(post, comment)">发送回复</button>
                </div>
              </div>
              <div v-if="getReplies(post, comment.id).length > 0" class="reply-list">
                <div v-for="reply in getReplies(post, comment.id)" :key="reply.id" class="reply-item">
                  <button class="comment-user-link" @click="chatWithUser(reply.userId)">
                    <img
                      v-if="reply.userAvatar"
                      :src="resolveImageUrl(reply.userAvatar)"
                      alt="回复用户头像"
                      class="comment-avatar"
                    />
                    <div v-else class="comment-avatar comment-avatar-fallback">{{ getAvatarText(reply.username, reply.userId) }}</div>
                    <div class="comment-user-group">
                      <div class="comment-user">{{ reply.username || `用户#${reply.userId}` }}</div>
                      <div class="comment-user-id">ID: {{ reply.userId || '-' }}</div>
                    </div>
                  </button>
                  <div class="reply-content">{{ getReplyPrefix(post, reply) }}{{ reply.content }}</div>
                  <div class="comment-actions">
                    <button class="btn-reply-link" @click="replyToReply(post, comment, reply)">回复</button>
                  </div>
                  <div class="comment-time">{{ formatTime(reply.createTime) }}</div>
                </div>
              </div>
            </div>
            <div v-if="getRootComments(post).length === 0" class="empty-comment">暂无评论</div>
          </div>
        </div>
      </div>
      <div v-if="posts.length === 0" class="empty">暂无帖子，点击“发布帖子”开始交流</div>
    </div>

    <div class="pager" v-if="totalPages > 1">
      <button :disabled="page <= 1" @click="changePage(page - 1)">上一页</button>
      <span>第 {{ page }} / {{ totalPages }} 页（{{ total }} 条）</span>
      <button :disabled="page >= totalPages" @click="changePage(page + 1)">下一页</button>
    </div>

    <div class="dialog" v-if="showPostDialog" @click="closeDialog">
      <div class="dialog-content" @click.stop>
        <h3>发布帖子</h3>
        <select v-model="newPost.category">
          <option v-for="item in categories" :key="item" :value="item">{{ item }}</option>
        </select>
        <input v-model.trim="newPost.title" placeholder="标题" />
        <textarea v-model.trim="newPost.content" placeholder="内容"></textarea>
        <div class="upload-area">
          <input ref="imageInput" type="file" accept="image/*" @change="uploadPostImage" />
          <button type="button" class="btn-upload" :disabled="uploadingImage">上传配图</button>
        </div>
        <div class="preview-list" v-if="newPost.images.length > 0">
          <div class="preview-item" v-for="(img, idx) in newPost.images" :key="`${img}-${idx}`">
            <img :src="resolveImageUrl(img)" alt="预览" />
            <button type="button" @click="removeImage(idx)">移除</button>
          </div>
        </div>
        <button @click="submitPost" class="btn-submit">发布</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()

const posts = ref([])
const currentUserId = ref(null)
const showPostDialog = ref(false)
const sortBy = ref('time')
const keyword = ref('')
const categoryFilter = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const categories = ref(['综合交流', '种植经验', '病虫害防治', '市场行情', '政策资讯'])

const newPost = ref({
  category: '综合交流',
  title: '',
  content: '',
  images: []
})
const uploadingImage = ref(false)
const replyOpenMap = ref({})
const replyDraftMap = ref({})
const replyTargetMap = ref({})

const totalPages = computed(() => {
  const pages = Math.ceil(total.value / size.value)
  return pages > 0 ? pages : 1
})

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
      if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('/uploads/')) {
        return [raw]
      }
      return raw.split(',').map(item => item.trim()).filter(Boolean)
    }
  }
  return []
}

const resolveImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return `http://localhost:8080${url}`
}

const formatTime = (time) => {
  if (!time) return '-'
  const parsed = new Date(time)
  if (!Number.isNaN(parsed.getTime())) {
    return parsed.toLocaleString('zh-CN')
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

const loadPosts = async () => {
  const query = [
    `page=${page.value}`,
    `size=${size.value}`,
    `sortBy=${sortBy.value}`,
    keyword.value ? `keyword=${encodeURIComponent(keyword.value)}` : '',
    categoryFilter.value ? `category=${encodeURIComponent(categoryFilter.value)}` : ''
  ].filter(Boolean).join('&')

  const res = await request.get(`/posts?${query}`)
  if (res.code === 200) {
    const list = res.data.records || []
    posts.value = list.map(post => ({
      ...post,
      imagesList: parseImages(post.images),
      showComments: false,
      comments: [],
      commentDraft: ''
    }))
    total.value = res.data.total || list.length
  }
}

const reloadFirstPage = () => {
  page.value = 1
  loadPosts()
}

const changePage = (next) => {
  page.value = next
  loadPosts()
}

const closeDialog = () => {
  showPostDialog.value = false
  newPost.value = { category: categories.value[0] || '综合交流', title: '', content: '', images: [] }
}

const loadCategories = async () => {
  const res = await request.get('/posts/categories')
  if (res.code === 200 && Array.isArray(res.data) && res.data.length > 0) {
    categories.value = res.data
    if (!categories.value.includes(newPost.value.category)) {
      newPost.value.category = categories.value[0]
    }
  }
}

const uploadPostImage = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  const formData = new FormData()
  formData.append('image', file)
  uploadingImage.value = true
  try {
    const res = await request.post('/posts/upload-image', formData)
    if (res.code === 200 && res.data?.url) {
      newPost.value.images.push(res.data.url)
      return
    }
    alert(res.message || '上传失败')
  } catch (e) {
    alert('上传失败')
  } finally {
    uploadingImage.value = false
    event.target.value = ''
  }
}

const removeImage = (idx) => {
  newPost.value.images.splice(idx, 1)
}

const submitPost = async () => {
  if (!newPost.value.title || !newPost.value.content) {
    alert('请填写标题和内容')
    return
  }
  const payload = {
    category: newPost.value.category || categories.value[0] || '综合交流',
    title: newPost.value.title,
    content: newPost.value.content,
    images: newPost.value.images.length > 0 ? JSON.stringify(newPost.value.images) : ''
  }
  const res = await request.post('/posts', payload)
  if (res.code === 200) {
    closeDialog()
    reloadFirstPage()
    const status = Number(res.data?.status)
    if (status === 0) {
      alert(`帖子已提交，AI 预审判定为异常，等待管理员审核。${res.data?.auditRemark ? `\n原因：${res.data.auditRemark}` : ''}`)
      return
    }
    if (status === 1) {
      alert('帖子发布成功')
      return
    }
    alert('帖子已提交')
    return
  }
  alert(res.message || '发布失败')
}

const likePost = async (post) => {
  const res = await request.post(`/posts/${post.id}/like`)
  if (res.code === 200) {
    const liked = !!res.data
    post.liked = liked
    post.likes = Math.max(0, Number(post.likes || 0) + (liked ? 1 : -1))
  }
}

const favoritePost = async (post) => {
  const res = await request.post(`/posts/${post.id}/favorite`)
  if (res.code === 200) {
    post.favorited = !!res.data
  }
}

const loadPostComments = async (post) => {
  const res = await request.get(`/posts/${post.id}/comments?page=1&size=50`)
  if (res.code === 200) {
    post.comments = res.data.records || []
  }
}

const toggleComments = async (post) => {
  post.showComments = !post.showComments
  if (post.showComments) {
    await loadPostComments(post)
  }
}

const submitComment = async (post) => {
  if (!post.commentDraft) return
  const res = await request.post(`/posts/${post.id}/comment`, { content: post.commentDraft })
  if (res.code === 200) {
    post.commentDraft = ''
    post.commentCount = Number(post.commentCount || 0) + 1
    await loadPostComments(post)
    return
  }
  alert(res.message || '评论失败')
}

const getReplyKey = (postId, commentId) => `${postId}-${commentId}`

const isReplying = (postId, commentId) => {
  return !!replyOpenMap.value[getReplyKey(postId, commentId)]
}

const closeReplyInput = (postId, rootCommentId) => {
  const key = getReplyKey(postId, rootCommentId)
  replyOpenMap.value[key] = false
  replyDraftMap.value[key] = ''
  delete replyTargetMap.value[key]
}

const openReplyInput = (post, rootComment, targetComment) => {
  if (!post?.id || !rootComment?.id || !targetComment?.id) return
  const key = getReplyKey(post.id, rootComment.id)
  const targetId = targetComment.id
  if (replyOpenMap.value[key] && String(replyTargetMap.value[key] || '') === String(targetId)) {
    closeReplyInput(post.id, rootComment.id)
    return
  }
  replyOpenMap.value[key] = true
  replyTargetMap.value[key] = targetId
}

const toggleReplyInput = (post, rootComment) => {
  openReplyInput(post, rootComment, rootComment)
}

const replyToReply = (post, rootComment, replyComment) => {
  openReplyInput(post, rootComment, replyComment)
}

const getRootComments = (post) => {
  return (post?.comments || []).filter(comment => !comment.parentId)
}

const getPostCommentById = (post, commentId) => {
  return (post?.comments || []).find(comment => String(comment.id) === String(commentId))
}

const resolveRootCommentId = (post, commentId) => {
  let current = getPostCommentById(post, commentId)
  if (!current) return commentId
  const visited = new Set()
  while (current?.parentId != null) {
    if (visited.has(current.id)) break
    visited.add(current.id)
    const parent = getPostCommentById(post, current.parentId)
    if (!parent) return current.parentId
    current = parent
  }
  return current?.id ?? commentId
}

const getReplies = (post, rootCommentId) => {
  return (post?.comments || []).filter(comment => {
    if (!comment?.id) return false
    if (String(comment.id) === String(rootCommentId)) return false
    return String(resolveRootCommentId(post, comment.id)) === String(rootCommentId)
  })
}

const getReplyPrefix = (post, reply) => {
  const parent = getPostCommentById(post, reply?.parentId)
  if (!parent) return ''
  return `回复 ${parent.username || `用户#${parent.userId}`}：`
}

const getReplyPlaceholder = (post, rootComment) => {
  if (!post?.id || !rootComment?.id) return '回复评论'
  const key = getReplyKey(post.id, rootComment.id)
  const targetId = replyTargetMap.value[key]
  const target = getPostCommentById(post, targetId)
  const targetName = target?.username || (target?.userId ? `用户#${target.userId}` : '')
  return targetName ? `回复 ${targetName}` : `回复 ${rootComment.username || `用户#${rootComment.userId}`}`
}

const submitReply = async (post, rootComment) => {
  if (!post?.id || !rootComment?.id) return
  const key = getReplyKey(post.id, rootComment.id)
  const targetId = replyTargetMap.value[key] || rootComment.id
  const content = String(replyDraftMap.value[key] || '').trim()
  if (!content) return
  const res = await request.post(`/posts/${post.id}/comment`, {
    content,
    parentId: targetId
  })
  if (res.code === 200) {
    closeReplyInput(post.id, rootComment.id)
    post.commentCount = Number(post.commentCount || 0) + 1
    await loadPostComments(post)
    return
  }
  alert(res.message || '回复失败')
}

const chatWithUser = async (userId) => {
  if (!userId) return
  if (String(currentUserId.value || '') === String(userId)) return
  try {
    const res = await request.post('/conversations/start', { receiverId: userId })
    if (res.code === 200) {
      router.push(`/messages?cid=${res.data.id}`)
    } else {
      alert(`发起会话失败：${res.message || '未知错误'}`)
      console.error('发起会话失败', res)
    }
  } catch (e) {
    alert(`发起会话失败：${e.message || '网络错误'}`)
    console.error('发起会话异常', e)
  }
}

onMounted(async () => {
  decodeCurrentUserId()
  await loadCategories()
  await loadPosts()
})
</script>

<style scoped>
.forum {
  max-width: 1120px;
  margin: 0 auto;
}

.hero {
  border-radius: 14px;
  padding: 18px 20px;
  color: #fff;
  margin-bottom: 14px;
  background:
    radial-gradient(280px 130px at 16% 12%, rgba(255,255,255,0.2), transparent 64%),
    linear-gradient(125deg, #14b8a6 0%, #0d9488 100%);
  box-shadow: 0 14px 30px rgba(20, 184, 166, 0.2);
}

.hero h2 {
  font-size: 30px;
  margin-bottom: 4px;
  color: #fff;
}

.hero p {
  font-size: 14px;
  opacity: 0.92;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  background: #fff;
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
  gap: 10px;
}

.left,
.right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.right input {
  width: 220px;
  padding: 9px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.right select {
  padding: 9px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.btn-post,
.btn-search {
  padding: 10px 16px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.btn-post:hover,
.btn-search:hover {
  background: var(--brand-strong);
}

.posts {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.post-card {
  background: #fff;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid var(--line-soft);
  box-shadow: 0 8px 20px rgba(15, 40, 70, 0.06);
}

.post-title {
  cursor: pointer;
  color: #0f172a;
  transition: color 0.2s ease;
  font-size: 20px;
  margin-bottom: 6px;
}

.post-title:hover {
  color: var(--brand);
}

.post-user {
  margin-bottom: 8px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  color: #64748b;
  font-size: 12px;
}

.user-entry {
  border: none;
  background: transparent;
  padding: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid #dbe5f2;
  object-fit: cover;
  flex-shrink: 0;
}

.user-avatar-fallback {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.user-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
}

.username-link {
  color: var(--brand);
  font-weight: 600;
  transition: color 0.2s ease;
}

.user-entry:hover .username-link {
  color: var(--brand-strong);
  text-decoration: underline;
}

.user-id {
  color: #94a3b8;
  font-size: 11px;
}

.cat-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #dbeafe;
  color: #1d4ed8;
  background: #eff6ff;
}

.content {
  color: #475467;
  line-height: 1.75;
  margin-bottom: 10px;
  white-space: pre-wrap;
}

.images {
  margin-bottom: 10px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 8px;
}

.images img {
  width: 100%;
  height: 120px;
  border-radius: 8px;
  border: 1px solid #dbe5f2;
  object-fit: cover;
}

.meta {
  display: flex;
  gap: 14px;
  font-size: 13px;
  color: #667085;
  flex-wrap: wrap;
}

.action {
  cursor: pointer;
  color: var(--brand);
  font-weight: 600;
}

.action.active {
  color: #0c5bb0;
}

.comments {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #d8e2f0;
}

.comment-input {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.comment-input input {
  flex: 1;
  padding: 9px 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.comment-input button {
  padding: 8px 14px;
  background: var(--accent);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 600;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.comment-item {
  border: 1px solid #e6edf8;
  border-radius: 8px;
  padding: 8px 10px;
  background: #f8fbff;
}

.comment-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
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
  width: 28px;
  height: 28px;
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
  font-size: 12px;
  color: #334155;
  font-weight: 700;
}

.comment-user-id {
  font-size: 11px;
  color: #94a3b8;
}

.comment-user-link:hover .comment-user {
  color: var(--brand-strong);
  text-decoration: underline;
}

.comment-content {
  color: #475467;
  font-size: 13px;
  line-height: 1.6;
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
  background: #f1f7ff;
}

.reply-input-box textarea {
  width: 100%;
  min-height: 64px;
  resize: vertical;
  border: 1px solid #d9e2ef;
  border-radius: 8px;
  padding: 8px 10px;
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
  margin-left: 36px;
  border: 1px solid #dbe5f2;
  border-radius: 8px;
  background: #f3f8ff;
  padding: 8px 10px;
}

.reply-content {
  margin-top: 6px;
  color: #475467;
  font-size: 13px;
  line-height: 1.6;
}

.comment-time {
  color: #98a2b3;
  font-size: 11px;
  white-space: nowrap;
}

.empty,
.empty-comment {
  text-align: center;
  color: #98a2b3;
  font-size: 13px;
}

.empty {
  padding: 28px;
  border: 1px dashed #d8e2f0;
  border-radius: 12px;
  background: #fbfdff;
}

.pager {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  color: #667085;
  font-size: 13px;
}

.pager button {
  padding: 7px 12px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}

.pager button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.dialog {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-content {
  background: #fff;
  padding: 20px;
  border-radius: 14px;
  width: min(560px, calc(100vw - 28px));
  border: 1px solid var(--line-soft);
  box-shadow: 0 20px 48px rgba(15, 40, 70, 0.24);
}

.dialog-content h3 {
  margin-bottom: 10px;
  color: #0f172a;
}

.dialog-content input,
.dialog-content select,
.dialog-content textarea {
  width: 100%;
  padding: 10px;
  margin-bottom: 10px;
  border: 1px solid var(--line-soft);
  border-radius: 8px;
  background: #fbfdff;
}

.dialog-content textarea {
  height: 140px;
  resize: none;
}

.upload-area {
  margin-bottom: 8px;
}

.upload-area input {
  width: 100%;
}

.btn-upload {
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px solid rgba(15, 107, 207, 0.35);
  border-radius: 8px;
  background: #fff;
  color: var(--brand);
  cursor: pointer;
  font-weight: 600;
}

.preview-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.preview-item {
  border: 1px solid #e6edf8;
  border-radius: 8px;
  padding: 6px;
  background: #f8fbff;
}

.preview-item img {
  width: 100%;
  height: 80px;
  border-radius: 6px;
  object-fit: cover;
}

.preview-item button {
  width: 100%;
  margin-top: 6px;
  border: none;
  border-radius: 6px;
  padding: 5px;
  background: #ef4444;
  color: #fff;
  cursor: pointer;
  font-size: 12px;
}

.btn-submit {
  width: 100%;
  padding: 11px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 700;
}

.btn-submit:hover {
  background: var(--brand-strong);
}

@media (max-width: 768px) {
  h2 {
    font-size: 24px;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .left,
  .right {
    width: 100%;
    flex-wrap: wrap;
  }

  .right input {
    width: 100%;
  }

  .comment-input {
    flex-direction: column;
  }

  .comment-input button {
    width: 100%;
  }

  .reply-item {
    margin-left: 0;
  }

  .comment-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .pager {
    justify-content: space-between;
  }
}
</style>
