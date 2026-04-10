import request from '@/utils/request'

export const register = (data) => request.post('/auth/register', data)
export const login = (data) => request.post('/auth/login', data)
export const getUserInfo = () => request.get('/user/info')
export const updateUserInfo = (data) => request.put('/user/info', data)
