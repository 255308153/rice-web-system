import axios from 'axios'

const resolvedApiBase =
  import.meta.env.VITE_API_BASE_URL ||
  '/api'

const request = axios.create({
  baseURL: resolvedApiBase,
  timeout: 45000
})

function clearAuthAndRedirect() {
  localStorage.removeItem('token')
  localStorage.removeItem('userInfo')
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => response.data,
  error => {
    const status = error?.response?.status
    const isLoginRequest = (error?.config?.url || '').includes('/auth/login')
    // 401 代表登录态失效，才执行强制登出；403 仅表示权限不足，不应清空登录态
    if (status === 401 && !isLoginRequest) {
      clearAuthAndRedirect()
    }
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

export default request
