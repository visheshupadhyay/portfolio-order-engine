import { API_BASE_URL } from '../config'
import type { Order, OrderPage, OrderStatus } from './contracts'
import { SessionExpiredError } from './apiErrors'

export async function getOrders(
    accessToken: string,
    page: number,
    status?: OrderStatus,
): Promise<OrderPage> {
    const query = new URLSearchParams({
        page: String(page),
        size: '10',
    })

    if (status) {
        query.set('status', status)
    }

    const response = await fetch(
        `${API_BASE_URL}/orders?${query}`,
        {
            headers: {
                Authorization: `Bearer ${accessToken}`,
            },
        },
    )

    if (response.status === 401) {
        throw new SessionExpiredError()
    }

    if (response.status === 403) {
        throw new Error('Your account is not allowed to view orders.')
    }

    if (!response.ok) {
        throw new Error('Orders could not be loaded. Please try again.')
    }

    const orderPage: OrderPage = await response.json()
    return orderPage
}

export async function getOrderById(
    accessToken: string,
    orderId: string,
): Promise<Order> {
    const response = await fetch(
        `${API_BASE_URL}/orders/${encodeURIComponent(orderId)}`,
        {
            headers: {
                Authorization: `Bearer ${accessToken}`,
            },
        },
    )

    if (response.status === 401) {
        throw new SessionExpiredError()
    }

    if (response.status === 403) {
        throw new Error('Your account is not allowed to view orders.')
    }

    if (response.status === 404) {
        throw new Error('No order exists with this ID.')
    }

    if (!response.ok) {
        throw new Error('The order could not be loaded. Please try again.')
    }

    return (await response.json()) as Order
}

export async function createOrder(
    accessToken: string,
    orderId: string,
): Promise<Order> {
    const response = await fetch(`${API_BASE_URL}/orders`, {
        method: 'POST',
        headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ id: orderId }),
    })

    if (response.status === 401) {
        throw new SessionExpiredError()
    }

    if (response.status === 403) {
        throw new Error('Your account is not allowed to create orders.')
    }

    if (response.status === 409) {
        throw new Error('An order with this ID already exists.')
    }

    if (!response.ok) {
        throw new Error('The order could not be created. Please try again.')
    }

    return (await response.json()) as Order
}

export async function payOrder(
    accessToken: string,
    orderId: string,
): Promise<Order> {
    const response = await fetch(
        `${API_BASE_URL}/orders/${encodeURIComponent(orderId)}/pay`,
        {
            method: 'POST',
            headers: {
                Authorization: `Bearer ${accessToken}`,
            },
        },
    )

    if (response.status === 401) {
        throw new SessionExpiredError()
    }

    if (response.status === 403) {
        throw new Error('Your account is not allowed to pay for orders.')
    }

    if (response.status === 404) {
        throw new Error('No order exists with this ID.')
    }

    if (response.status === 409) {
        throw new Error('This order cannot be paid in its current state.')
    }

    if (!response.ok) {
        throw new Error('The payment could not be completed. Please try again.')
    }

    return (await response.json()) as Order
}
