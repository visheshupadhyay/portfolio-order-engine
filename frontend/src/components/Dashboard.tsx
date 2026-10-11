import { type FormEvent, useEffect, useState } from 'react'
import { getOrderById, getOrders } from '../api/ordersApi'
import type { Order, OrderStatus } from '../api/contracts'
import { SessionExpiredError } from '../api/apiErrors'

interface DashboardProps {
    accessToken: string
    onSignOut: () => void
    onSessionExpired: () => void
}

function Dashboard({
    accessToken,
    onSignOut,
    onSessionExpired,
}: DashboardProps) {
    const [orders, setOrders] = useState<Order[]>([])
    const [page, setPage] = useState(0)
    const [status, setStatus] = useState<OrderStatus | undefined>(undefined)
    const [pageSize, setPageSize] = useState(10)
    const [totalElements, setTotalElements] = useState(0)
    const [isLoading, setIsLoading] = useState(true)
    const [errorMessage, setErrorMessage] = useState('')
    const [orderIdToFind, setOrderIdToFind] = useState('')
    const [foundOrder, setFoundOrder] = useState<Order | null>(null)
    const [lookupErrorMessage, setLookupErrorMessage] = useState('')
    const [isLookingUp, setIsLookingUp] = useState(false)

    useEffect(() => {
        async function loadOrders() {
            setIsLoading(true)
            setErrorMessage('')

            try {
                const orderPage = await getOrders(accessToken, page, status)

                setOrders(orderPage.content)
                setPageSize(orderPage.size)
                setTotalElements(orderPage.totalElements)
            } catch (error) {
                if (error instanceof SessionExpiredError) {
                    onSessionExpired()
                    return
                }
                if (error instanceof Error) {
                    setErrorMessage(error.message)
                } else {
                    setErrorMessage('Orders could not be loaded.')
                }
            } finally {
                setIsLoading(false)
            }
        }

        void loadOrders()
    }, [accessToken, page, status, onSessionExpired])

    const hasPreviousPage = page > 0
    const hasNextPage = (page + 1) * pageSize < totalElements
    const totalPages = Math.max(1, Math.ceil(totalElements / pageSize))

    async function handleOrderLookup(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()

        const trimmedOrderId = orderIdToFind.trim()

        if (!trimmedOrderId) {
            setFoundOrder(null)
            setLookupErrorMessage('Enter an order ID to look it up.')
            return
        }

        setIsLookingUp(true)
        setFoundOrder(null)
        setLookupErrorMessage('')

        try {
            const order = await getOrderById(accessToken, trimmedOrderId)
            setFoundOrder(order)
        } catch (error) {
            if (error instanceof SessionExpiredError) {
                onSessionExpired()
                return
            }

            setLookupErrorMessage(
                error instanceof Error
                    ? error.message
                    : 'The order could not be loaded.',
            )
        } finally {
            setIsLookingUp(false)
        }
    }

    return (
        <main className="dashboard-shell">
            <header className="dashboard-header">
                <div className="workspace-title">
                    <span className="brand-mark" aria-hidden="true">OE</span>
                    <div>
                        <span className="role-pill">Reader workspace</span>
                        <h1>Orders</h1>
                        <p>Find and monitor the latest persisted order state.</p>
                    </div>
                </div>

                <button type="button" onClick={onSignOut}>
                    Sign out
                </button>
            </header>

            <section className="summary-grid" aria-label="Order overview">
                <article className="summary-card summary-card-primary">
                    <span>Matching orders</span>
                    <strong>{totalElements}</strong>
                    <small>For the selected status</small>
                </article>
                <article className="summary-card">
                    <span>Current page</span>
                    <strong>{page + 1}</strong>
                    <small>of {totalPages}</small>
                </article>
                <article className="summary-card">
                    <span>Active view</span>
                    <strong className="summary-card-label">{status ?? 'ALL'}</strong>
                    <small>Order status filter</small>
                </article>
            </section>

            <section className="orders-panel" aria-labelledby="orders-heading">
                <div className="section-heading">
                    <div>
                        <p className="eyebrow">ORDER DIRECTORY</p>
                        <h2 id="orders-heading">Your orders</h2>
                    </div>
                    <span className="live-indicator"><i aria-hidden="true" />Live API data</span>
                </div>
                <div className="filter-row">
                    <label htmlFor="order-status">Status</label>

                    <select
                        id="order-status"
                        value={status ?? ''}
                        onChange={(event) => {
                            const selectedStatus = event.target.value

                            setStatus(
                                selectedStatus === ''
                                    ? undefined
                                    : (selectedStatus as OrderStatus),
                            )

                            setPage(0)
                        }}
                        disabled={isLoading}
                    >
                        <option value="">All statuses</option>
                        <option value="CREATED">Created</option>
                        <option value="PAID">Paid</option>
                    </select>
                </div>

                <form className="order-lookup" onSubmit={handleOrderLookup}>
                    <label htmlFor="order-id-lookup">Find order by ID</label>
                    <input
                        id="order-id-lookup"
                        value={orderIdToFind}
                        onChange={(event) => setOrderIdToFind(event.target.value)}
                        placeholder="for example, ui-order-101"
                        disabled={isLookingUp}
                    />
                    <button type="submit" disabled={isLookingUp}>
                        {isLookingUp ? 'Finding order…' : 'Find order'}
                    </button>
                </form>

                {lookupErrorMessage && (
                    <p className="form-error" role="alert">
                        {lookupErrorMessage}
                    </p>
                )}

                {foundOrder && (
                    <section className="order-result" aria-label="Found order">
                        <p>Found order</p>
                        <strong>{foundOrder.id}</strong>
                        <span className={`status-badge status-${foundOrder.status.toLowerCase()}`}>
                            {foundOrder.status}
                        </span>
                    </section>
                )}

                {isLoading && <p role="status">Loading orders…</p>}

                {errorMessage && (
                    <p className="form-error" role="alert">
                        {errorMessage}
                    </p>
                )}

                {!isLoading && !errorMessage && orders.length === 0 && (
                    <p className="empty-message">No orders exist yet.</p>
                )}

                {!isLoading && !errorMessage && orders.length > 0 && (
                    <>
                        <p className="order-summary">
                            Showing {orders.length} of {totalElements} order
                            {totalElements === 1 ? '' : 's'}
                            {status ? ` with status ${status}` : ''}.
                        </p>
                        <div
                            className="orders-table-wrapper"
                            tabIndex={0}
                            aria-label="Scrollable order table"
                        >
                            <table>
                                <thead>
                                    <tr>
                                        <th scope="col">Order ID</th>
                                        <th scope="col">Status</th>
                                    </tr>
                                </thead>

                                <tbody>
                                    {orders.map((order) => (
                                        <tr key={order.id}>
                                            <td>{order.id}</td>
                                            <td>
                                                <span className={`status-badge status-${order.status.toLowerCase()}`}>
                                                    {order.status}
                                                </span>
                                            </td>
                                        </tr>
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        <nav className="pagination" aria-label="Order pages">
                            <button
                                type="button"
                                onClick={() => setPage(page - 1)}
                                disabled={!hasPreviousPage}
                            >
                                Previous
                            </button>

                            <p>
                                Page {page + 1} of {totalPages}
                            </p>

                            <button
                                type="button"
                                onClick={() => setPage(page + 1)}
                                disabled={!hasNextPage}
                            >
                                Next
                            </button>
                        </nav>
                    </>
                )}
            </section>
        </main>
    )
}

export default Dashboard
