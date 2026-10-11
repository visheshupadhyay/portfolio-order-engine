import { describe, expect, it } from 'vitest'
import {
  getRolesFromAccessToken,
  isAccessTokenExpired,
} from './jwt'

function createTestToken(payload: object): string {
  const encodedPayload = btoa(JSON.stringify(payload))

  // These tests only inspect the payload. Spring Boot remains responsible for
  // checking the real token signature when an API request reaches the backend.
  return `test-header.${encodedPayload}.test-signature`
}

describe('JWT helpers', () => {
  it('reads roles from the token payload', () => {
    const token = createTestToken({
      roles: ['ROLE_ORDER_READER'],
      exp: Math.floor(Date.now() / 1_000) + 3_600,
    })

    expect(getRolesFromAccessToken(token)).toEqual([
      'ROLE_ORDER_READER',
    ])
  })

  it('recognizes an expired token', () => {
    const token = createTestToken({
      roles: ['ROLE_ORDER_READER'],
      exp: Math.floor(Date.now() / 1_000) - 60,
    })

    expect(isAccessTokenExpired(token)).toBe(true)
  })

  it('treats an unreadable token as expired and gives it no roles', () => {
    expect(getRolesFromAccessToken('not-a-jwt')).toEqual([])
    expect(isAccessTokenExpired('not-a-jwt')).toBe(true)
  })
})