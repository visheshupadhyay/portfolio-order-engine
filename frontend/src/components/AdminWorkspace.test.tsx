import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SessionExpiredError } from '../api/apiErrors'
import {
  getMetricDetails,
  getMetricNames,
} from '../api/metricsApi'
import AdminWorkspace from './AdminWorkspace'

vi.mock('../api/metricsApi', () => ({
  getMetricNames: vi.fn(),
  getMetricDetails: vi.fn(),
}))

const mockedGetMetricNames = vi.mocked(getMetricNames)
const mockedGetMetricDetails = vi.mocked(getMetricDetails)

describe('AdminWorkspace', () => {
  beforeEach(() => {
    mockedGetMetricNames.mockReset()
    mockedGetMetricDetails.mockReset()

    mockedGetMetricNames.mockResolvedValue({
      names: [
        'jvm.memory.used',
        'order.payments.completed',
      ],
    })

    mockedGetMetricDetails.mockResolvedValue({
      name: 'order.payments.completed',
      description: 'Number of completed order payments',
      baseUnit: 'payments',
      measurements: [
        {
          statistic: 'COUNT',
          value: 7,
        },
      ],
    })
  })

  it('searches for a metric and displays its current value', async () => {
    const user = userEvent.setup()

    render(
      <AdminWorkspace
        accessToken="admin-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    const searchInput = await screen.findByLabelText('Find a metric')

    await user.type(searchInput, 'payments')

    expect(
      screen.getByRole('button', {
        name: 'order.payments.completed',
      }),
    ).toBeVisible()

    expect(
      screen.queryByRole('button', {
        name: 'jvm.memory.used',
      }),
    ).not.toBeInTheDocument()

    await user.click(
      screen.getByRole('button', {
        name: 'order.payments.completed',
      }),
    )

    expect(mockedGetMetricDetails).toHaveBeenCalledWith(
      'admin-token',
      'order.payments.completed',
    )

    expect(
      await screen.findByText('Number of completed order payments'),
    ).toBeVisible()

    expect(screen.getByText('7 payments')).toBeVisible()
  })

  it('shows an error when metric names cannot be loaded', async () => {
    mockedGetMetricNames.mockRejectedValue(
      new Error('Application metrics could not be loaded.'),
    )

    render(
      <AdminWorkspace
        accessToken="admin-token"
        onSignOut={vi.fn()}
        onSessionExpired={vi.fn()}
      />,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Application metrics could not be loaded.',
    )
  })

  it('ends the session when the metrics request returns unauthorized', async () => {
    const onSessionExpired = vi.fn()
    mockedGetMetricNames.mockRejectedValue(new SessionExpiredError())

    render(
      <AdminWorkspace
        accessToken="expired-admin-token"
        onSignOut={vi.fn()}
        onSessionExpired={onSessionExpired}
      />,
    )

    await waitFor(() => {
      expect(onSessionExpired).toHaveBeenCalledOnce()
    })
  })
})
