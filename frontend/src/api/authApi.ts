import { API_BASE_URL } from '../config'
import type { LoginRequest, LoginResponse } from './contracts'

export async function login(
  loginRequest: LoginRequest,
): Promise<LoginResponse> {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(loginRequest),
  })

  if (!response.ok) {
    throw new Error('Sign in failed. Check your credentials and try again.')
  }

  const loginResponse: LoginResponse = await response.json()
  return loginResponse
}
