import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { login } from './api/authApi'
import { getOrders } from './api/ordersApi'
import { getMetricNames } from './api/metricsApi'

vi.mock('./api/authApi', () => ({
  login: vi.fn(),
}))

vi.mock('./api/ordersApi', () => ({
  getOrders: vi.fn(),
  getOrderById: vi.fn(),
}))

vi.mock('./api/metricsApi', () => ({
  getMetricNames: vi.fn(),
  getMetricDetails: vi.fn(),
}))

const mockedGetOrders = vi.mocked(getOrders)

const mockedLogin = vi.mocked(login)

const mockedGetMetricNames = vi.mocked(getMetricNames)

function createTestToken(payload: object): string {
  return `test-header.${btoa(JSON.stringify(payload))}.test-signature`
}

describe('App login', () => {
  beforeEach(() => {
    mockedLogin.mockReset()
    mockedGetOrders.mockReset()
    mockedGetMetricNames.mockReset()
    mockedGetMetricNames.mockResolvedValue({
      names: ['jvm.memory.used', 'order.payments.completed'],
    })
    mockedGetOrders.mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
    })
  })

  it('opens the writer workspace for an order writer', async () => {
    const user = userEvent.setup()

    const writerToken = createTestToken({
      roles: ['ROLE_ORDER_WRITER'],
      exp: Math.floor(Date.now() / 1_000) + 3_600,
    })

    mockedLogin.mockResolvedValue({
      accessToken: writerToken,
      tokenType: 'Bearer',
    })

    render(<App />)

    await user.type(
      screen.getByLabelText('Username'),
      'order-writer',
    )
    await user.type(
      screen.getByLabelText('Password'),
      'writer-password',
    )
    await user.click(
      screen.getByRole('button', { name: 'Sign in' }),
    )

    expect(
      await screen.findByRole('heading', {
        name: 'Create and pay orders',
      }),
    ).toBeVisible()
  })

  it('opens the metrics workspace for an order administrator', async () => {
    const user = userEvent.setup()

    const adminToken = createTestToken({
      roles: ['ROLE_ORDER_ADMIN'],
      exp: Math.floor(Date.now() / 1_000) + 3_600,
    })

    mockedLogin.mockResolvedValue({
      accessToken: adminToken,
      tokenType: 'Bearer',
    })

    render(<App />)

    await user.type(
      screen.getByLabelText('Username'),
      'order-admin',
    )
    await user.type(
      screen.getByLabelText('Password'),
      'admin-password',
    )
    await user.click(
      screen.getByRole('button', { name: 'Sign in' }),
    )

    expect(
      await screen.findByRole('heading', {
        name: 'Application metrics',
      }),
    ).toBeVisible()

    expect(mockedGetMetricNames).toHaveBeenCalledWith(adminToken)
  })

  it('shows an error when the supplied credentials are rejected', async () => {
    const user = userEvent.setup()

    mockedLogin.mockRejectedValue(
      new Error('Sign in failed. Check your credentials and try again.'),
    )

    render(<App />)

    await user.type(
      screen.getByLabelText('Username'),
      'wrong-user',
    )
    await user.type(
      screen.getByLabelText('Password'),
      'wrong-password',
    )
    await user.click(
      screen.getByRole('button', { name: 'Sign in' }),
    )

    expect(mockedLogin).toHaveBeenCalledWith({
      username: 'wrong-user',
      password: 'wrong-password',
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Sign in failed. Check your credentials and try again.',
    )
  })

  it('stores a successful reader login and opens the order dashboard', async () => {
    const user = userEvent.setup()

    const readerToken = createTestToken({
      roles: ['ROLE_ORDER_READER'],
      exp: Math.floor(Date.now() / 1_000) + 3_600,
    })

    mockedLogin.mockResolvedValue({
      accessToken: readerToken,
      tokenType: 'Bearer',
    })

    render(<App />)

    await user.type(
      screen.getByLabelText('Username'),
      'order-reader',
    )
    await user.type(
      screen.getByLabelText('Password'),
      'reader-password',
    )
    await user.click(
      screen.getByRole('button', { name: 'Sign in' }),
    )

    expect(
      await screen.findByRole('heading', { name: 'Orders' }),
    ).toBeVisible()

    expect(sessionStorage.getItem('accessToken')).toBe(readerToken)
    expect(sessionStorage.getItem('tokenType')).toBe('Bearer')

    expect(mockedGetOrders).toHaveBeenCalledWith(
      readerToken,
      0,
      undefined,
    )
  })

  it('removes an expired saved token and returns to sign in', async () => {
    const expiredToken = createTestToken({
      roles: ['ROLE_ORDER_READER'],
      exp: Math.floor(Date.now() / 1_000) - 60,
    })

    sessionStorage.setItem('accessToken', expiredToken)
    sessionStorage.setItem('tokenType', 'Bearer')

    render(<App />)

    expect(
      screen.getByRole('heading', { name: 'Welcome back' }),
    ).toBeVisible()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Your session expired. Please sign in again.',
    )

    expect(sessionStorage.getItem('accessToken')).toBeNull()
    expect(sessionStorage.getItem('tokenType')).toBeNull()
    expect(mockedGetOrders).not.toHaveBeenCalled()
  })

  it('restores a valid saved session and removes it when signing out', async () => {
    const user = userEvent.setup()
    const readerToken = createTestToken({
      roles: ['ROLE_ORDER_READER'],
      exp: Math.floor(Date.now() / 1_000) + 3_600,
    })

    sessionStorage.setItem('accessToken', readerToken)
    sessionStorage.setItem('tokenType', 'Bearer')

    render(<App />)

    expect(
      await screen.findByRole('heading', { name: 'Orders' }),
    ).toBeVisible()
    expect(mockedLogin).not.toHaveBeenCalled()

    await user.click(
      screen.getByRole('button', { name: 'Sign out' }),
    )

    expect(
      screen.getByRole('heading', { name: 'Welcome back' }),
    ).toBeVisible()
    expect(sessionStorage.getItem('accessToken')).toBeNull()
    expect(sessionStorage.getItem('tokenType')).toBeNull()
  })
})
