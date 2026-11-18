import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { App } from '../App'
import { setToken } from '../api/client'

/** Renderiza o app inteiro numa rota; logged=true simula sessão salva. */
export function renderApp(path = '/', { logged = true } = {}) {
  setToken(logged ? 'token-teste' : null)
  return render(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
}
