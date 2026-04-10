<template>
  <div id="app" :class="{ 'login-mode': currentRoute === '/login' }">
    <template v-if="currentRoute !== '/login'">
      <div class="sidebar">
        <div class="logo">商铺系统</div>
        <nav class="menu">
        <!-- 商户菜单 -->
        <template v-if="userRole === 'MERCHANT'">
          <router-link to="/merchant/dashboard" class="menu-item">
            <span class="icon">■</span>
            <span>后台首页</span>
          </router-link>
          <router-link to="/merchant/shop" class="menu-item">
            <span class="icon">■</span>
            <span>AI销售建议</span>
          </router-link>
          <router-link to="/merchant/products" class="menu-item">
            <span class="icon">■</span>
            <span>商品管理</span>
          </router-link>
          <router-link to="/merchant/orders" class="menu-item">
            <span class="icon">■</span>
            <span>订单处理</span>
          </router-link>
          <router-link to="/merchant/messages" class="menu-item">
            <span class="icon">■</span>
            <span>私聊消息</span>
            <span v-if="hasUnreadMessages" class="menu-dot" aria-hidden="true"></span>
          </router-link>
        </template>

        <!-- 管理员菜单 -->
        <template v-if="userRole === 'ADMIN'">
          <router-link to="/admin/dashboard" class="menu-item">
            <span class="icon">■</span>
            <span>后台首页</span>
          </router-link>
          <router-link to="/admin/users" class="menu-item">
            <span class="icon">■</span>
            <span>用户管理</span>
          </router-link>
          <router-link to="/admin/audits" class="menu-item">
            <span class="icon">■</span>
            <span>商户审核</span>
          </router-link>
          <router-link to="/admin/posts" class="menu-item">
            <span class="icon">■</span>
            <span>内容审核</span>
          </router-link>
          <router-link to="/admin/config" class="menu-item">
            <span class="icon">■</span>
            <span>系统配置</span>
          </router-link>
        </template>

        <router-link to="/profile" class="menu-item">
          <span class="icon">■</span>
          <span>{{ profileMenuLabel }}</span>
        </router-link>
      </nav>
    </div>
    <div class="main-content">
      <div class="header">
        <div class="header-left">
          <div class="breadcrumb">首页 / {{ routeTitle }}</div>
          <div class="route-hint">{{ route.path }}</div>
        </div>
        <div class="user-info">
          <span class="fullscreen" aria-hidden="true"></span>
          <span class="avatar">{{ displayAvatarText }}</span>
          <span class="username">{{ displayName }}</span>
          <button @click="logout" class="btn-logout">退出</button>
        </div>
      </div>
      <div class="content">
        <router-view />
      </div>
    </div>
    </template>
    <template v-else>
      <router-view />
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import request from './utils/request'

const router = useRouter()
const route = useRoute()
const userRole = ref('MERCHANT')
const displayName = ref('商户')
const unreadMessages = ref(0)
const currentRoute = computed(() => route.path)
const hasUnreadMessages = computed(() => unreadMessages.value > 0)
const profileMenuLabel = computed(() => (userRole.value === 'MERCHANT' ? '店铺信息' : '个人信息'))
const displayAvatarText = computed(() => {
  const text = String(displayName.value || '').trim()
  return text ? text.slice(0, 1).toUpperCase() : '系'
})
let unreadTimer = null
const routeTitleMap = {
  '/merchant/dashboard': '商户后台',
  '/merchant/shop': 'AI销售建议',
  '/merchant/products': '商品管理',
  '/merchant/orders': '订单处理',
  '/merchant/messages': '消息管理',
  '/admin/dashboard': '管理后台',
  '/admin/users': '用户管理',
  '/admin/audits': '资质审核',
  '/admin/posts': '内容审核',
  '/admin/config': '系统配置'
}
const routeTitle = computed(() => {
  if (route.path === '/profile') {
    return profileMenuLabel.value
  }
  return routeTitleMap[route.path] || '页面'
})

const getToken = () => localStorage.getItem('token')

const initialToken = getToken()
if (initialToken) {
  try {
    const payload = JSON.parse(atob(initialToken.split('.')[1]))
    userRole.value = payload.role || 'MERCHANT'
    displayName.value = userRole.value === 'ADMIN' ? '系统管理员' : '商户'
  } catch (e) {
    userRole.value = 'MERCHANT'
    displayName.value = '商户'
  }
}

const resolveDisplayName = (info) => {
  const nickname = String(info?.nickname || '').trim()
  if (nickname) return nickname
  const username = String(info?.username || '').trim()
  if (username) return username
  return userRole.value === 'ADMIN' ? '系统管理员' : '商户'
}

const loadDisplayName = async () => {
  const token = getToken()
  if (!token) {
    displayName.value = userRole.value === 'ADMIN' ? '系统管理员' : '商户'
    return
  }

  const localUserInfo = localStorage.getItem('userInfo')
  if (localUserInfo) {
    try {
      displayName.value = resolveDisplayName(JSON.parse(localUserInfo))
    } catch (e) {
      displayName.value = userRole.value === 'ADMIN' ? '系统管理员' : '商户'
    }
  }

  try {
    const res = await request.get('/user/info')
    if (res.code === 200) {
      localStorage.setItem('userInfo', JSON.stringify(res.data || {}))
      displayName.value = resolveDisplayName(res.data)
    }
  } catch (e) {
    if (!displayName.value) {
      displayName.value = userRole.value === 'ADMIN' ? '系统管理员' : '商户'
    }
  }
}

const handleUserInfoUpdated = () => {
  loadDisplayName()
}

const loadUnreadMessages = async () => {
  const token = getToken()
  if (!token || userRole.value === 'ADMIN') {
    unreadMessages.value = 0
    return
  }
  try {
    const res = await request.get('/conversations?page=1&size=100')
    if (res.code === 200) {
      unreadMessages.value = (res.data.records || []).reduce((sum, conv) => sum + (Number(conv.unreadCount) || 0), 0)
    }
  } catch (e) {
    unreadMessages.value = 0
  }
}

onMounted(async () => {
  window.addEventListener('user-info-updated', handleUserInfoUpdated)
  await loadDisplayName()
  await loadUnreadMessages()
  unreadTimer = setInterval(loadUnreadMessages, 10000)
})

onUnmounted(() => {
  window.removeEventListener('user-info-updated', handleUserInfoUpdated)
  if (unreadTimer) {
    clearInterval(unreadTimer)
    unreadTimer = null
  }
})

const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  router.push('/login')
  setTimeout(() => location.reload(), 100)
}
</script>

<style scoped>
#app {
  display: flex;
  width: 100%;
  height: 100vh;
  min-height: 100vh;
  background: transparent;
}

#app.login-mode {
  display: block;
  width: 100vw;
  min-height: 100vh;
}

#app.login-mode > * {
  width: 100%;
}

.sidebar {
  width: 236px;
  background: linear-gradient(180deg, #111827 0%, #0f172a 100%);
  color: #fff;
  display: flex;
  flex-direction: column;
  box-shadow: 8px 0 30px rgba(15, 23, 42, 0.22);
  z-index: 10;
}

.logo {
  padding: 22px 22px 18px;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 1px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}

.menu {
  flex: 1;
  padding: 12px 10px 16px;
  overflow-y: auto;
}

.menu-item {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
  padding: 11px 14px;
  color: #fff;
  text-decoration: none;
  cursor: pointer;
  border-radius: 10px;
  border-left: 3px solid transparent;
  transition: background-color 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.menu-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.2);
  margin-left: 8px;
  flex-shrink: 0;
}

.menu-item:hover {
  background: rgba(148, 163, 184, 0.16);
  transform: translateX(2px);
}

.menu-item.router-link-active {
  background: rgba(15, 107, 207, 0.24);
  border-left-color: #60a5fa;
}

.menu-item .icon {
  width: 22px;
  height: 22px;
  margin-right: 10px;
  font-size: 12px;
  color: rgba(255,255,255,0.9);
  border-radius: 6px;
  background: rgba(255,255,255,0.1);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.header {
  height: 64px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--line-soft);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
  box-shadow: 0 6px 20px rgba(15, 40, 70, 0.06);
}

.header-left {
  min-width: 0;
}

.breadcrumb {
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 600;
}

.route-hint {
  margin-top: 2px;
  color: #98a2b3;
  font-size: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.fullscreen {
  width: 18px;
  height: 18px;
  display: inline-block;
  position: relative;
  cursor: pointer;
}

.fullscreen::before {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(#64748b, #64748b) left top / 6px 2px no-repeat,
    linear-gradient(#64748b, #64748b) left top / 2px 6px no-repeat,
    linear-gradient(#64748b, #64748b) right top / 6px 2px no-repeat,
    linear-gradient(#64748b, #64748b) right top / 2px 6px no-repeat,
    linear-gradient(#64748b, #64748b) left bottom / 6px 2px no-repeat,
    linear-gradient(#64748b, #64748b) left bottom / 2px 6px no-repeat,
    linear-gradient(#64748b, #64748b) right bottom / 6px 2px no-repeat,
    linear-gradient(#64748b, #64748b) right bottom / 2px 6px no-repeat;
}

.avatar {
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, #0f6bcf 0%, #0f766e 100%);
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.username {
  font-size: 14px;
  cursor: pointer;
  color: #334155;
}

.btn-logout {
  padding: 6px 16px;
  background: var(--danger);
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

@media (max-width: 1160px) {
  .sidebar {
    width: 82px;
  }

  .logo {
    font-size: 14px;
    padding: 18px 12px 14px;
    text-align: center;
  }

  .menu {
    padding: 10px 8px;
  }

  .menu-item {
    justify-content: center;
    padding: 10px 8px;
  }

  .menu-item .icon {
    margin-right: 0;
  }

  .menu-item span:last-child {
    display: none;
  }
}

@media (max-width: 900px) {
  #app {
    flex-direction: column;
    height: auto;
    min-height: 100vh;
  }

  .sidebar {
    width: 100%;
    box-shadow: none;
  }

  .logo {
    text-align: left;
    padding: 14px 16px;
    font-size: 16px;
  }

  .menu {
    display: flex;
    overflow-x: auto;
    overflow-y: hidden;
    padding: 8px 10px 12px;
    gap: 8px;
  }

  .menu-item {
    min-width: max-content;
    margin-bottom: 0;
    padding: 8px 12px;
    border-left: none;
    border-radius: 999px;
  }

  .menu-item span:last-child {
    display: inline;
    font-size: 13px;
  }

  .menu-item .icon {
    margin-right: 8px;
  }

  .header {
    height: auto;
    min-height: 58px;
    padding: 10px 14px;
    gap: 8px;
  }

  .breadcrumb {
    font-size: 12px;
  }

  .route-hint {
    display: none;
  }

  .username {
    display: none;
  }

  .content {
    padding: 14px;
  }
}
</style>
