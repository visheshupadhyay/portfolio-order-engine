import { API_BASE_URL } from '../config'
import type { MetricDetails, MetricNamesResponse } from './contracts'
import { SessionExpiredError } from './apiErrors'

// This calls Spring Boot's existing protected Actuator endpoint. It returns
// metric names; Grafana remains responsible for historical charts and alerts.
export async function getMetricNames(
  accessToken: string,
): Promise<MetricNamesResponse> {
  const response = await fetch(`${API_BASE_URL}/actuator/metrics`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  })

  if (response.status === 401) {
    throw new SessionExpiredError()
  }

  if (response.status === 403) {
    throw new Error('Your account is not allowed to view application metrics.')
  }

  if (!response.ok) {
    throw new Error('Application metrics could not be loaded. Please try again.')
  }

  return (await response.json()) as MetricNamesResponse
}

export async function getMetricDetails(
  accessToken: string,
  metricName: string,
): Promise<MetricDetails> {
  const response = await fetch(
    `${API_BASE_URL}/actuator/metrics/${encodeURIComponent(metricName)}`,
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
    throw new Error('Your account is not allowed to view application metrics.')
  }

  if (response.status === 404) {
    throw new Error('This metric is no longer available.')
  }

  if (!response.ok) {
    throw new Error('Metric details could not be loaded. Please try again.')
  }

  return (await response.json()) as MetricDetails
}
