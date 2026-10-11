import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionExpiredError } from '../api/apiErrors'
import { getOrderById, getOrders } from '../api/ordersApi'
import Dashboard from './Dashboard'

vi.mock('../api/ordersApi', () => ({
  getOrders: vi.fn(),
  getOrderById: vi.fn(),
}))

const mockedGetOrders = vi.mocked(getOrders)
const mockedGetOrderById = vi.mocked(getOrderById)

describe('Dashboard', () => {
  beforeEach(() => {
    mockedGetOrders.mockReset()
    mockedGetOrderById.mockReset()
    mockedGetOrders.mockResolvedValue({
      content: [],
      page: 0,
      size: 10,
      totalElements: 0,
    })
  })

  it('finds an order by its exact ID', async () => {
    const user = userEvent.setup()

    mockedGetOrderById.mockResolvedValue({
      id: 'reader-order-101',
      status: 'PAID',
    })

    render(
      <Dashboard
        accessToken="reader-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    await user.type(
      screen.getByLabelText('Find order by ID'),
      '  reader-order-101  ',
    )

    await user.click(
      screen.getByRole('button', { name: 'Find order' }),
    )

    expect(mockedGetOrderById).toHaveBeenCalledWith(
      'reader-token',
      'reader-order-101',
    )

    expect(
      await screen.findByRole('region', { name: 'Found order' }),
    ).toHaveTextContent('reader-order-101')

    expect(
      screen.getByRole('region', { name: 'Found order' }),
    ).toHaveTextContent('PAID')
  })

  it('shows the lookup error returned by the API client', async () => {
    const user = userEvent.setup()

    mockedGetOrderById.mockRejectedValue(
      new Error('No order exists with this ID.'),
    )

    render(
      <Dashboard
        accessToken="reader-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    await user.type(
      screen.getByLabelText('Find order by ID'),
      'missing-order',
    )
    await user.click(
      screen.getByRole('button', { name: 'Find order' }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No order exists with this ID.',
    )
  })

  it('ends the session when loading orders returns unauthorized', async () => {
    const onSessionExpired = vi.fn()

    mockedGetOrders.mockRejectedValue(
      new SessionExpiredError(),
    )

    render(
      <Dashboard
        accessToken="expired-reader-token"
        onSignOut={vi.fn()}
        onSessionExpired={onSessionExpired}
      />,
    )

    await waitFor(() => {
      expect(onSessionExpired).toHaveBeenCalledOnce()
    })

    expect(mockedGetOrders).toHaveBeenCalledWith(
      'expired-reader-token',
      0,
      undefined,
    )
  })

  it('loads paid orders when the reader selects the Paid filter', async () => {
    const user = userEvent.setup()

    render(
      <Dashboard
        accessToken="reader-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    await waitFor(() => {
      expect(mockedGetOrders).toHaveBeenCalledWith(
        'reader-token',
        0,
        undefined,
      )
    })

    await user.selectOptions(
      screen.getByLabelText('Status'),
      'PAID',
    )

    await waitFor(() => {
      expect(mockedGetOrders).toHaveBeenLastCalledWith(
        'reader-token',
        0,
        'PAID',
      )
    })
  })
})
