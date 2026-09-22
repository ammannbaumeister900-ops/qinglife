const TokenKey = 'Admin-Token'

export function getToken() {
  return window.sessionStorage.getItem(TokenKey)
}

export function setToken(token) {
  window.sessionStorage.setItem(TokenKey, token)
}

export function removeToken() {
  window.sessionStorage.removeItem(TokenKey)
}
