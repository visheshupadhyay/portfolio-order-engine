import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionExpiredError } from '../api/apiErrors'
import { createOrder, payOrder } from '../api/ordersApi'
import WriterWorkspace from './WriterWorkspace'

vi.mock('../api/ordersApi', () => ({
  createOrder: vi.fn(),
  payOrder: vi.fn(),
}))

const mockedCreateOrder = vi.mocked(createOrder)
const mockedPayOrder = vi.mocked(payOrder)

describe('WriterWorkspace', () => {
  beforeEach(() => {
    mockedCreateOrder.mockReset()
    mockedPayOrder.mockReset()
  })

  it('creates an order and displays its resulting status', async () => {
    const user = userEvent.setup()

    mockedCreateOrder.mockResolvedValue({
      id: 'writer-order-101',
      status: 'CREATED',
    })

    render(
      <WriterWorkspace
        accessToken="writer-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    await user.type(
      screen.getByLabelText('Order ID', {
        selector: '#new-order-id',
      }),
      '  writer-order-101  ',
    )

    await user.click(
      screen.getByRole('button', { name: 'Create order' }),
    )

    expect(mockedCreateOrder).toHaveBeenCalledWith(
      'writer-token',
      'writer-order-101',
    )

    expect(
      await screen.findByText(
        'Order writer-order-101 was created with status CREATED.',
      ),
    ).toBeVisible()
  })

  it('does not call the API when the create-order ID is blank', async () => {
    const user = userEvent.setup()

    render(
      <WriterWorkspace
        accessToken="writer-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    await user.type(
      screen.getByLabelText('Order ID', {
        selector: '#new-order-id',
      }),
      '   ',
    )
    await user.click(
      screen.getByRole('button', { name: 'Create order' }),
    )

    expect(mockedCreateOrder).not.toHaveBeenCalled()
    expect(
      screen.getByText('Enter an order ID before creating the order.'),
    ).toBeVisible()
  })

  it('marks an order as paid and explains that notification is asynchronous', async () => {
    const user = userEvent.setup()

    mockedPayOrder.mockResolvedValue({
      id: 'writer-order-101',
      status: 'PAID',
    })

    render(
      <WriterWorkspace
        accessToken="writer-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    await user.type(
      screen.getByLabelText('Order ID', {
        selector: '#pay-order-id',
      }),
      'writer-order-101',
    )

    await user.click(
      screen.getByRole('button', { name: 'Mark as paid' }),
    )

    expect(mockedPayOrder).toHaveBeenCalledWith(
      'writer-token',
      'writer-order-101',
    )

    expect(
      await screen.findByText(
        'Order writer-order-101 is now PAID. Its notification will be handled in the background.',
      ),
    ).toBeVisible()
  })

  it('ends the session when a payment request returns unauthorized', async () => {
    const user = userEvent.setup()
    const onSessionExpired = vi.fn()

    mockedPayOrder.mockRejectedValue(new SessionExpiredError())

    render(
      <WriterWorkspace
        accessToken="expired-writer-token"
        onSignOut={vi.fn()}
        onSessionExpired={onSessionExpired}
      />,
    )

    await user.type(
      screen.getByLabelText('Order ID', {
        selector: '#pay-order-id',
      }),
      'writer-order-101',
    )
    await user.click(
      screen.getByRole('button', { name: 'Mark as paid' }),
    )

    await waitFor(() => {
      expect(onSessionExpired).toHaveBeenCalledOnce()
    })
  })
})
