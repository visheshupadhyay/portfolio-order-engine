import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL } from '../config'
import { SessionExpiredError } from './apiErrors'
import { createOrder, getOrders } from './ordersApi'

const fetchMock = vi.fn()

function response(status: number, body?: object): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

describe('ordersApi', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads the requested page and optional status with bearer authentication', async () => {
    const orderPage = {
      content: [{ id: 'paid-order', status: 'PAID' as const }],
      page: 2,
      size: 10,
      totalElements: 21,
    }
    fetchMock.mockResolvedValue(response(200, orderPage))

    await expect(
      getOrders('reader-token', 2, 'PAID'),
    ).resolves.toEqual(orderPage)

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/orders?page=2&size=10&status=PAID`,
      {
        headers: {
          Authorization: 'Bearer reader-token',
        },
      },
    )
  })

  it('sends the order ID when creating an order', async () => {
    fetchMock.mockResolvedValue(
      response(201, { id: 'new-order', status: 'CREATED' }),
    )

    await expect(
      createOrder('writer-token', 'new-order'),
    ).resolves.toEqual({ id: 'new-order', status: 'CREATED' })

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/orders`,
      {
        method: 'POST',
        headers: {
          Authorization: 'Bearer writer-token',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ id: 'new-order' }),
      },
    )
  })

  it('maps an unauthorized response to a session-expired error', async () => {
    fetchMock.mockResolvedValue(response(401))

    await expect(
      getOrders('expired-token', 0),
    ).rejects.toBeInstanceOf(SessionExpiredError)
  })
})
