import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { API_BASE_URL } from '../config'
import { SessionExpiredError } from './apiErrors'
import { getMetricDetails, getMetricNames } from './metricsApi'

const fetchMock = vi.fn()

function response(status: number, body?: object): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response
}

describe('metricsApi', () => {
  beforeEach(() => {
    fetchMock.mockReset()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('loads metric names with the administrator token', async () => {
    fetchMock.mockResolvedValue(
      response(200, { names: ['jvm.memory.used'] }),
    )

    await expect(getMetricNames('admin-token')).resolves.toEqual({
      names: ['jvm.memory.used'],
    })

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/actuator/metrics`,
      {
        headers: {
          Authorization: 'Bearer admin-token',
        },
      },
    )
  })

  it('encodes a selected metric name before requesting its details', async () => {
    const details = {
      name: 'custom metric',
      measurements: [{ statistic: 'VALUE', value: 4 }],
    }
    fetchMock.mockResolvedValue(response(200, details))

    await expect(
      getMetricDetails('admin-token', 'custom metric'),
    ).resolves.toEqual(details)

    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/actuator/metrics/custom%20metric`,
      {
        headers: {
          Authorization: 'Bearer admin-token',
        },
      },
    )
  })

  it('maps an unauthorized response to a session-expired error', async () => {
    fetchMock.mockResolvedValue(response(401))

    await expect(
      getMetricNames('expired-token'),
    ).rejects.toBeInstanceOf(SessionExpiredError)
  })
})
