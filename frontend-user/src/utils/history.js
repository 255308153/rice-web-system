const HISTORY_KEY = 'browse_history'
const MAX_HISTORY = 100

const safeParse = (raw) => {
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch (e) {
    return []
  }
}

export const getBrowseHistory = (limit = 50) => {
  const list = safeParse(localStorage.getItem(HISTORY_KEY))
  const safeLimit = Math.max(1, Math.min(limit, MAX_HISTORY))
  return list.slice(0, safeLimit)
}

export const addBrowseHistory = (record) => {
  if (!record || !record.type || !record.id) return

  const list = safeParse(localStorage.getItem(HISTORY_KEY))
  const key = `${record.type}:${record.id}`

  const normalized = {
    type: record.type,
    id: record.id,
    title: record.title || '',
    subtitle: record.subtitle || '',
    path: record.path || '',
    image: record.image || '',
    time: new Date().toISOString()
  }

  const next = [normalized, ...list.filter(item => `${item.type}:${item.id}` !== key)].slice(0, MAX_HISTORY)
  localStorage.setItem(HISTORY_KEY, JSON.stringify(next))
}

export const clearBrowseHistory = () => {
  localStorage.removeItem(HISTORY_KEY)
}
