import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL } from '../config'
import { login } from './authApi'

const fetchMock = vi.fn()

function response(status: number, body?: object): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

describe('authApi', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('posts login credentials and returns the token response', async () => {
    fetchMock.mockResolvedValue(
      response(200, {
        accessToken: 'reader-token',
        tokenType: 'Bearer',
      }),
    )

    await expect(
      login({
        username: 'order-reader',
        password: 'reader-password',
      }),
    ).resolves.toEqual({
      accessToken: 'reader-token',
      tokenType: 'Bearer',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/auth/login`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          username: 'order-reader',
          password: 'reader-password',
        }),
      },
    )
  })

  it('returns a safe message when login is rejected', async () => {
    fetchMock.mockResolvedValue(response(401))

    await expect(
      login({ username: 'wrong-user', password: 'wrong-password' }),
    ).rejects.toThrow(
      'Sign in failed. Check your credentials and try again.',
    )
  })
})
