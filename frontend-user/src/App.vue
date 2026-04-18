<template>
  <div id="app">
    <template v-if="currentRoute !== '/login'">
      <div class="navbar">
        <div class="logo">助农大米交流平台</div>
        <nav class="menu">
        <router-link v-if="userRole !== 'EXPERT'" to="/" class="menu-item">首页</router-link>

        <!-- 普通用户菜单 -->
        <template v-if="userRole === 'USER'">
          <router-link to="/shop" class="menu-item">商品购买</router-link>
          <router-link to="/cart" class="menu-item">购物车</router-link>
          <router-link to="/orders" class="menu-item">我的订单</router-link>
          <router-link to="/ai" class="menu-item">AI服务</router-link>
          <router-link to="/forum" class="menu-item">助农论坛</router-link>
          <router-link to="/messages" class="menu-item">
            私聊消息
            <span v-if="hasUnreadMessages" class="menu-dot"></span>
          </router-link>
        </template>

        <!-- 专家菜单 -->
        <template v-if="userRole === 'EXPERT'">
          <router-link to="/expert" class="menu-item">工作台</router-link>
          <router-link to="/forum" class="menu-item">助农论坛</router-link>
          <router-link to="/messages" class="menu-item">
            私聊消息
            <span v-if="hasUnreadMessages" class="menu-dot"></span>
          </router-link>
          <router-link to="/ai" class="menu-item">AI服务</router-link>
        </template>
      </nav>
        <div class="user-info">
          <router-link to="/profile" class="menu-item">个人信息</router-link>
          <button @click="logout" class="btn-logout">退出</button>
        </div>
      </div>
      <div class="content">
        <router-view />
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
import { getRoleFromToken } from './utils/jwt'

const router = useRouter()
const route = useRoute()
const userRole = ref('USER')
const unreadMessages = ref(0)
const currentRoute = computed(() => route.path)
const hasUnreadMessages = computed(() => unreadMessages.value > 0)
let unreadTimer = null
const routeTitleMap = {
  '/': '工作台',
  '/shop': '商品购买',
  '/cart': '购物车',
  '/checkout': '确认订单',
  '/orders': '我的订单',
  '/ai': 'AI 服务',
  '/forum': '助农论坛',
  '/messages': '私聊消息',
  '/expert': '专家工作台',
  '/profile': '个人信息',
  '/merchant/dashboard': '商户后台',
  '/merchant/shop': '店铺管理',
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
  if (route.path.startsWith('/product/')) return '商品详情'
  if (route.path.startsWith('/shop/store/')) return '店铺详情'
  if (route.path.startsWith('/post/')) return '帖子详情'
  return routeTitleMap[route.path] || '页面'
})

const getToken = () => localStorage.getItem('token')

const initialToken = getToken()
if (initialToken) {
  userRole.value = getRoleFromToken(initialToken) || 'USER'
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
  await loadUnreadMessages()
  unreadTimer = setInterval(loadUnreadMessages, 10000)
})

onUnmounted(() => {
  if (unreadTimer) {
    clearInterval(unreadTimer)
    unreadTimer = null
  }
})

const logout = () => {
  localStorage.removeItem('token')
  router.push('/login')
  setTimeout(() => location.reload(), 100)
}
</script>

<style scoped>
#app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

.navbar {
  height: 48px;
  background: rgba(20, 184, 166, 0.95);
  backdrop-filter: blur(20px);
  display: flex;
  align-items: center;
  padding: 0 24px;
  box-shadow: 0 1px 3px rgba(20, 184, 166, 0.2);
  position: sticky;
  top: 0;
  z-index: 100;
}

.logo {
  font-size: 16px;
  font-weight: 600;
  color: #faf6f0;
  margin-right: 40px;
  font-family: 'STXingkai', 'STKaiti', 'KaiTi', 'LiSu', serif;
  letter-spacing: 2px;
}

.menu {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.menu-item {
  padding: 6px 14px;
  color: rgba(250, 246, 240, 0.85);
  text-decoration: none;
  font-size: 13px;
  border-radius: 4px;
  transition: all 0.2s ease;
  position: relative;
  white-space: nowrap;
}

.menu-item:hover {
  background: rgba(250, 246, 240, 0.1);
  color: #faf6f0;
}

.menu-item.router-link-active {
  color: #faf6f0;
  background: rgba(250, 246, 240, 0.15);
}

.menu-dot {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef5350;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.btn-logout {
  padding: 5px 14px;
  background: rgba(250, 246, 240, 0.15);
  color: #faf6f0;
  border: 1px solid rgba(250, 246, 240, 0.2);
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s ease;
}

.btn-logout:hover {
  background: rgba(250, 246, 240, 0.25);
}

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
