import axios from 'axios'

const request = axios.create({
  baseURL: 'http://localhost:8080/api',
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
    if ((status === 401 || status === 403) && !isLoginRequest) {
      clearAuthAndRedirect()
    }
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

export default request
