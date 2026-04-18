import { createRouter, createWebHistory } from 'vue-router'
import { getRoleFromToken } from '@/utils/jwt'

const USER_FRONT_ROLES = ['USER', 'EXPERT']

const getUserFrontHome = (role) => {
  if (role === 'EXPERT') return '/expert'
  return '/'
}

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue')
  },
  {
    path: '/shop',
    name: 'Shop',
    component: () => import('@/views/Shop.vue')
  },
  {
    path: '/shop/store/:id',
    name: 'ShopStore',
    component: () => import('@/views/ShopStore.vue')
  },
  {
    path: '/product/:id',
    name: 'Product',
    component: () => import('@/views/ProductDetail.vue')
  },
  {
    path: '/ai',
    name: 'AI',
    component: () => import('@/views/AI.vue')
  },
  {
    path: '/forum',
    name: 'Forum',
    component: () => import('@/views/Forum.vue')
  },
  {
    path: '/post/:id',
    name: 'PostDetail',
    component: () => import('@/views/PostDetail.vue')
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('@/views/Orders.vue')
  },
  {
    path: '/messages',
    name: 'Messages',
    component: () => import('@/views/Messages.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue')
  },
  {
    path: '/cart',
    name: 'Cart',
    component: () => import('@/views/Cart.vue')
  },
  {
    path: '/checkout',
    name: 'Checkout',
    component: () => import('@/views/Checkout.vue')
  },
  {
    path: '/expert',
    name: 'ExpertHome',
    component: () => import('@/views/ExpertHome.vue')
  },
  {
    path: '/expert/profile',
    name: 'ExpertProfile',
    component: () => import('@/views/ExpertProfile.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  const role = getRoleFromToken(token)

  if (!token && to.path !== '/login') {
    return next('/login')
  }

  if (token && to.path === '/login') {
    if (!role || !USER_FRONT_ROLES.includes(role)) {
      localStorage.removeItem('token')
      return next('/login')
    }
    return next(getUserFrontHome(role))
  }

  if (token) {
    if (!role || !USER_FRONT_ROLES.includes(role)) {
      localStorage.removeItem('token')
      return next('/login')
    }

    if (role === 'USER' && to.path.startsWith('/expert')) {
      return next('/')
    }
  }

  next()
})

export default router
