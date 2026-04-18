<template>
  <div class="login-page">
    <div class="login-shell">
      <section class="login-brand">
        <p class="badge">Rice Platform</p>
        <h1>大米电商管理系统</h1>
        <p class="desc">覆盖用户购物、商户管理、专家服务与后台审核的一体化业务平台。</p>
      </section>
      <section class="login-box">
        <div class="switch-row">
          <button :class="['switch-btn', { active: mode === 'login' }]" @click="switchMode('login')">登录</button>
          <button :class="['switch-btn', { active: mode === 'register' }]" @click="switchMode('register')">注册</button>
        </div>
        <h2>{{ mode === 'login' ? '账号登录' : '用户注册' }}</h2>

        <input v-model.trim="form.username" placeholder="请输入用户名" />
        <input
          v-model.trim="form.password"
          type="password"
          :placeholder="mode === 'login' ? '请输入密码' : '请设置密码（至少6位）'"
          @keyup.enter="handleSubmit"
        />
        <template v-if="mode === 'register'">
          <input
            v-model.trim="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            @keyup.enter="handleSubmit"
          />
          <input v-model.trim="form.phone" placeholder="请输入手机号（可选）" @keyup.enter="handleSubmit" />
        </template>

        <button class="btn-primary" :disabled="loading" @click="handleSubmit">
          {{ loading ? (mode === 'login' ? '登录中...' : '注册中...') : (mode === 'login' ? '登录' : '注册') }}
        </button>
        <p class="tip" v-if="mode === 'login'">普通用户可直接切换到“注册”创建账号。</p>
        <p class="tip" v-else>注册成功后将自动登录并进入系统首页。</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { login, register } from '@/api/user'
import { useRouter } from 'vue-router'
import { getRoleFromToken } from '@/utils/jwt'

const router = useRouter()
const USER_FRONT_ROLES = ['USER', 'EXPERT']
const mode = ref('login')
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phone: ''
})
const loading = ref(false)

const resolveHomePathByRole = (role) => (role === 'EXPERT' ? '/expert' : '/')

const switchMode = (nextMode) => {
  mode.value = nextMode
  form.password = ''
  form.confirmPassword = ''
}

const handleSubmit = async () => {
  if (mode.value === 'login') {
    await handleLogin()
    return
  }
  await handleRegister()
}

const handleLogin = async () => {
  if (!form.username || !form.password) {
    alert('请输入用户名和密码')
    return
  }

  if (loading.value) return
  loading.value = true
  try {
    const res = await login(form)
    if (res.code !== 200 || !res.data?.token) {
      alert(res.message || '登录失败')
      return
    }

    const role = getRoleFromToken(res.data.token)
    if (!role || !USER_FRONT_ROLES.includes(role)) {
      alert('当前账号属于后台角色，请在后台前端登录')
      return
    }

    localStorage.setItem('token', res.data.token)
    router.push(resolveHomePathByRole(role))
    setTimeout(() => location.reload(), 100)
  } catch (err) {
    alert(err?.response?.data?.message || err?.message || '登录失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

const handleRegister = async () => {
  if (!form.username || !form.password) {
    alert('请输入用户名和密码')
    return
  }
  if (form.password.length < 6) {
    alert('密码长度不能少于6位')
    return
  }
  if (form.password !== form.confirmPassword) {
    alert('两次输入的密码不一致')
    return
  }

  if (loading.value) return
  loading.value = true
  try {
    const res = await register({
      username: form.username,
      password: form.password,
      phone: form.phone || null
    })
    if (res.code !== 200 || !res.data?.token) {
      alert(res.message || '注册失败')
      return
    }

    const role = getRoleFromToken(res.data.token)
    if (!role || !USER_FRONT_ROLES.includes(role)) {
      alert('注册成功，但当前账号不属于用户端')
      return
    }

    localStorage.setItem('token', res.data.token)
    router.push(resolveHomePathByRole(role))
    setTimeout(() => location.reload(), 100)
  } catch (err) {
    alert(err?.response?.data?.message || err?.message || '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(720px 320px at -10% -10%, rgba(15, 107, 207, 0.2), transparent 55%),
    radial-gradient(560px 300px at 110% 105%, rgba(15, 118, 110, 0.14), transparent 55%),
    #f4f8ff;
}

.login-shell {
  width: min(980px, 100%);
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(6px);
  border-radius: 20px;
  box-shadow: 0 24px 56px rgba(15, 40, 70, 0.16);
  overflow: hidden;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
}

.login-brand {
  padding: 44px 42px;
  color: #fff;
  background:
    radial-gradient(380px 180px at 10% 10%, rgba(255,255,255,0.25), transparent 65%),
    linear-gradient(135deg, #0f6bcf 0%, #0f766e 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 14px;
}

.badge {
  width: fit-content;
  padding: 5px 11px;
  border-radius: 999px;
  font-size: 12px;
  background: rgba(255,255,255,0.18);
  border: 1px solid rgba(255,255,255,0.35);
}

.login-brand h1 {
  font-size: 34px;
  line-height: 1.2;
  letter-spacing: 0.5px;
}

.desc {
  font-size: 14px;
  line-height: 1.8;
  color: rgba(255,255,255,0.9);
}

.login-box {
  padding: 44px 38px;
  background: #fff;
  display: flex;
  flex-direction: column;
}

.switch-row {
  display: flex;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  padding: 3px;
  margin-bottom: 16px;
  background: #f8fbff;
}

.switch-btn {
  flex: 1;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  padding: 8px 0;
  cursor: pointer;
  font-weight: 600;
}

.switch-btn.active {
  background: #fff;
  color: var(--brand);
  box-shadow: 0 2px 8px rgba(15, 40, 70, 0.08);
}

.login-box h2 {
  font-size: 30px;
  margin-bottom: 18px;
  color: #0f172a;
}

.login-box input {
  width: 100%;
  padding: 13px 14px;
  margin-bottom: 12px;
  border: 1px solid var(--line-soft);
  border-radius: 10px;
  font-size: 14px;
  background: #fbfdff;
}

.login-box .btn-primary {
  width: 100%;
  margin-top: 6px;
  padding: 13px;
  background: var(--brand);
  border: none;
  border-radius: 10px;
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  cursor: pointer;
}

.login-box .btn-primary:hover {
  background: var(--brand-strong);
}

.login-box .btn-primary:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}

.tip {
  margin-top: 12px;
  font-size: 12px;
  color: #667085;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .login-shell {
    grid-template-columns: 1fr;
  }

  .login-brand {
    padding: 28px 24px;
  }

  .login-brand h1 {
    font-size: 24px;
  }

  .login-box {
    padding: 28px 24px;
  }
}
</style>
