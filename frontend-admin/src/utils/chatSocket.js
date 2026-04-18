const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
const DEFAULT_WS_URL = `${protocol}//${window.location.hostname}:8080/ws/chat`

function getWsBaseUrl() {
  const envUrl = import.meta.env.VITE_WS_CHAT_URL
  if (envUrl && String(envUrl).trim()) {
    return String(envUrl).trim()
  }
  return DEFAULT_WS_URL
}

export function createChatSocket(callbacks = {}) {
  const {
    onOpen,
    onClose,
    onError,
    onMessage
  } = callbacks

  let socket = null
  let reconnectTimer = null
  let stopped = false
  let reconnectTimes = 0

  const clearReconnect = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const scheduleReconnect = () => {
    if (stopped) return
    clearReconnect()
    reconnectTimes += 1
    const delay = Math.min(2000 * reconnectTimes, 12000)
    reconnectTimer = setTimeout(() => {
      connect()
    }, delay)
  }

  const connect = () => {
    const token = localStorage.getItem('token')
    if (!token) return
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      return
    }

    const wsUrl = `${getWsBaseUrl()}?token=${encodeURIComponent(token)}`
    socket = new WebSocket(wsUrl)

    socket.onopen = () => {
      reconnectTimes = 0
      clearReconnect()
      if (typeof onOpen === 'function') {
        onOpen()
      }
    }

    socket.onclose = () => {
      if (typeof onClose === 'function') {
        onClose()
      }
      scheduleReconnect()
    }

    socket.onerror = (event) => {
      if (typeof onError === 'function') {
        onError(event)
      }
    }

    socket.onmessage = (event) => {
      if (typeof onMessage !== 'function') {
        return
      }
      try {
        const payload = JSON.parse(event.data)
        onMessage(payload)
      } catch (e) {
        // 忽略无效消息
      }
    }
  }

  const disconnect = () => {
    stopped = true
    clearReconnect()
    if (socket) {
      socket.close()
      socket = null
    }
  }

  return {
    connect,
    disconnect,
    isConnected: () => socket && socket.readyState === WebSocket.OPEN
  }
}
