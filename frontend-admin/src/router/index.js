import { createRouter, createWebHistory } from 'vue-router'

const resolveHomePathByRole = (role) => {
  if (role === 'ADMIN') return '/admin/dashboard'
  if (role === 'MERCHANT') return '/merchant/dashboard'
  return '/login'
}

const resolveHomePathByToken = () => {
  const token = localStorage.getItem('token')
  if (!token) return '/login'
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return resolveHomePathByRole(payload.role || 'USER')
  } catch (e) {
    localStorage.removeItem('token')
    return '/login'
  }
}

const routes = [
  {
    path: '/',
    redirect: () => resolveHomePathByToken()
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue')
  },
  {
    path: '/merchant/products',
    name: 'MerchantProducts',
    component: () => import('@/views/merchant/Products.vue')
  },
  {
    path: '/merchant/orders',
    name: 'MerchantOrders',
    component: () => import('@/views/merchant/Orders.vue')
  },
  {
    path: '/merchant/dashboard',
    name: 'MerchantDashboard',
    component: () => import('@/views/merchant/Dashboard.vue')
  },
  {
    path: '/merchant/shop',
    name: 'MerchantShop',
    component: () => import('@/views/merchant/ShopManage.vue')
  },
  {
    path: '/merchant/messages',
    name: 'MerchantMessages',
    component: () => import('@/views/merchant/Messages.vue')
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/admin/Users.vue')
  },
  {
    path: '/admin/dashboard',
    name: 'AdminDashboard',
    component: () => import('@/views/admin/Dashboard.vue')
  },
  {
    path: '/admin/audits',
    name: 'AdminAudits',
    component: () => import('@/views/admin/Audits.vue')
  },
  {
    path: '/admin/posts',
    name: 'AdminPosts',
    component: () => import('@/views/admin/Posts.vue')
  },
  {
    path: '/admin/config',
    name: 'AdminConfig',
    component: () => import('@/views/admin/Config.vue')
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: () => resolveHomePathByToken()
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')

  if (!token && to.path !== '/login') {
    return next('/login')
  }

  if (token && to.path === '/login') {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      const role = payload.role || 'USER'
      if (role === 'ADMIN') {
        return next('/admin/dashboard')
      } else if (role === 'MERCHANT') {
        return next('/merchant/dashboard')
      }
      return next('/login')
    } catch (e) {
      localStorage.removeItem('token')
      return next('/login')
    }
  }

  if (token) {
    try {
      const payload = JSON.parse(atob(token.split('.')[1]))
      const role = payload.role || 'USER'

      if (role !== 'ADMIN' && role !== 'MERCHANT') {
        localStorage.removeItem('token')
        return next('/login')
      }

      if (to.path.startsWith('/admin') && role !== 'ADMIN') {
        return next('/merchant/dashboard')
      }

      if (to.path.startsWith('/merchant') && role !== 'MERCHANT') {
        return next('/admin/dashboard')
      }
    } catch (e) {
      localStorage.removeItem('token')
      return next('/login')
    }
  }

  next()
})

export default router
