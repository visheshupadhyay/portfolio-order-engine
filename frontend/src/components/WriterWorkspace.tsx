import { type FormEvent, useState } from 'react'
import { createOrder, payOrder } from '../api/ordersApi'
import { SessionExpiredError } from '../api/apiErrors'

interface WriterWorkspaceProps {
  accessToken: string
  onSignOut: () => void
  onSessionExpired: () => void
}

function WriterWorkspace({
  accessToken,
  onSignOut,
  onSessionExpired,
}: WriterWorkspaceProps) {
  const [newOrderId, setNewOrderId] = useState('')
  const [orderIdToPay, setOrderIdToPay] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isCreating, setIsCreating] = useState(false)
  const [isPaying, setIsPaying] = useState(false)

  async function handleCreateOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmedOrderId = newOrderId.trim()

    if (!trimmedOrderId) {
      setErrorMessage('Enter an order ID before creating the order.')
      return
    }

    setIsCreating(true)
    setSuccessMessage('')
    setErrorMessage('')

    try {
      const order = await createOrder(accessToken, trimmedOrderId)

      setSuccessMessage(
        `Order ${order.id} was created with status ${order.status}.`,
      )
      setNewOrderId('')
    } catch (error) {
      if (error instanceof SessionExpiredError) {
        onSessionExpired()
        return
      }

      setErrorMessage(
        error instanceof Error
          ? error.message
          : 'The order could not be created.',
      )
    } finally {
      setIsCreating(false)
    }
  }

  async function handlePayOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmedOrderId = orderIdToPay.trim()

    if (!trimmedOrderId) {
      setErrorMessage('Enter an order ID before marking it as paid.')
      return
    }

    setIsPaying(true)
    setSuccessMessage('')
    setErrorMessage('')

    try {
      const order = await payOrder(accessToken, trimmedOrderId)

      setSuccessMessage(
        `Order ${order.id} is now ${order.status}. Its notification will be handled in the background.`,
      )
      setOrderIdToPay('')
    } catch (error) {
      if (error instanceof SessionExpiredError) {
        onSessionExpired()
        return
      }

      setErrorMessage(
        error instanceof Error
          ? error.message
          : 'The payment could not be completed.',
      )
    } finally {
      setIsPaying(false)
    }
  }

  return (
    <main className="dashboard-shell">
      <header className="dashboard-header">
        <div className="workspace-title">
          <span className="brand-mark" aria-hidden="true">OE</span>
          <div>
            <span className="role-pill">Writer workspace</span>
            <h1>Create and pay orders</h1>
            <p>Commit business state first; deliver notifications safely in the background.</p>
          </div>
        </div>

        <button type="button" className="secondary-button" onClick={onSignOut}>
          Sign out
        </button>
      </header>

      <section className="delivery-flow" aria-label="Payment notification flow">
        <div><span>1</span><strong>Payment</strong><small>Commit PAID</small></div>
        <i aria-hidden="true">→</i>
        <div><span>2</span><strong>Outbox</strong><small>Save event</small></div>
        <i aria-hidden="true">→</i>
        <div><span>3</span><strong>Kafka</strong><small>Publish safely</small></div>
        <i aria-hidden="true">→</i>
        <div><span>4</span><strong>Notification</strong><small>Send SMS</small></div>
      </section>

      {successMessage && <p className="form-message workspace-message">{successMessage}</p>}
      {errorMessage && <p className="form-error workspace-message">{errorMessage}</p>}

      <section className="writer-grid" aria-label="Order actions">
        <article className="writer-card">
          <div className="writer-card-icon" aria-hidden="true">+</div>
          <p className="eyebrow">STEP 1 · REGISTER</p>
          <h2>Create an order</h2>
          <p>The new order begins with the status <strong>CREATED</strong>.</p>

          <form className="writer-form" onSubmit={handleCreateOrder}>
            <label htmlFor="new-order-id">Order ID</label>
            <input
              id="new-order-id"
              value={newOrderId}
              onChange={(event) => setNewOrderId(event.target.value)}
              placeholder="for example, ui-order-101"
              disabled={isCreating}
            />

            <button type="submit" disabled={isCreating}>
              {isCreating ? 'Creating order…' : 'Create order'}
            </button>
          </form>
        </article>

        <article className="writer-card">
          <div className="writer-card-icon writer-card-icon-paid" aria-hidden="true">✓</div>
          <p className="eyebrow">STEP 2 · SETTLE</p>
          <h2>Mark an order as paid</h2>
          <p>This changes the order to <strong>PAID</strong> and starts the notification workflow.</p>

          <form className="writer-form" onSubmit={handlePayOrder}>
            <label htmlFor="pay-order-id">Order ID</label>
            <input
              id="pay-order-id"
              value={orderIdToPay}
              onChange={(event) => setOrderIdToPay(event.target.value)}
              placeholder="for example, ui-order-101"
              disabled={isPaying}
            />

            <button type="submit" disabled={isPaying}>
              {isPaying ? 'Marking as paid…' : 'Mark as paid'}
            </button>
          </form>
        </article>
      </section>
    </main>
  )
}

export default WriterWorkspace
