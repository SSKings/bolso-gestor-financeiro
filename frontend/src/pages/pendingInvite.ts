// Convite aberto sem login: o código espera na sessão do navegador (some ao fechar a aba) até a pessoa entrar.
const KEY = 'bolso.pendingInvite'

export const pendingInvite = {
  get: () => sessionStorage.getItem(KEY),
  set: (code: string) => sessionStorage.setItem(KEY, code),
  clear: () => sessionStorage.removeItem(KEY),
}
