export const decodeJwtPayload = (token) => {
  if (!token) return null
  try {
    const payloadPart = String(token).split('.')[1]
    if (!payloadPart) return null
    const base64 = payloadPart.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const utf8Json = decodeURIComponent(
      atob(padded)
        .split('')
        .map((ch) => `%${ch.charCodeAt(0).toString(16).padStart(2, '0')}`)
        .join('')
    )
    return JSON.parse(utf8Json)
  } catch (e) {
    return null
  }
}

export const getRoleFromToken = (token) => {
  const payload = decodeJwtPayload(token)
  return payload?.role || null
}
