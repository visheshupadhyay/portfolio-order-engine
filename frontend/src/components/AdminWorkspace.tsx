import { useEffect, useState } from 'react'
import { getMetricDetails, getMetricNames } from '../api/metricsApi'
import type { MetricDetails } from '../api/contracts'
import { SessionExpiredError } from '../api/apiErrors'

interface AdminWorkspaceProps {
  accessToken: string
  onSignOut: () => void
  onSessionExpired: () => void
}

function AdminWorkspace({
  accessToken,
  onSignOut,
  onSessionExpired,
}: AdminWorkspaceProps) {
  const [metricNames, setMetricNames] = useState<string[]>([])
  const [errorMessage, setErrorMessage] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [loadAttempt, setLoadAttempt] = useState(0)
  const [metricSearch, setMetricSearch] = useState('')
  const [selectedMetricName, setSelectedMetricName] = useState<string | null>(null)
  const [metricDetails, setMetricDetails] = useState<MetricDetails | null>(null)
  const [metricDetailsErrorMessage, setMetricDetailsErrorMessage] = useState('')
  const [isLoadingMetricDetails, setIsLoadingMetricDetails] = useState(false)

  useEffect(() => {
    async function loadMetrics() {
      setIsLoading(true)
      setErrorMessage('')

      try {
        const metrics = await getMetricNames(accessToken)
        setMetricNames([...metrics.names].sort())
      } catch (error) {
        if (error instanceof SessionExpiredError) {
          onSessionExpired()
          return
        }

        setErrorMessage(
          error instanceof Error
            ? error.message
            : 'Application metrics could not be loaded.',
        )
      } finally {
        setIsLoading(false)
      }
    }

    void loadMetrics()
  }, [accessToken, loadAttempt, onSessionExpired])

  useEffect(() => {
    const metricName: string = selectedMetricName ?? ''

    if (!metricName) {
      return
    }

    async function loadMetricDetails() {
      setIsLoadingMetricDetails(true)
      setMetricDetails(null)
      setMetricDetailsErrorMessage('')

      try {
        const details = await getMetricDetails(accessToken, metricName)
        setMetricDetails(details)
      } catch (error) {
        if (error instanceof SessionExpiredError) {
          onSessionExpired()
          return
        }

        setMetricDetailsErrorMessage(
          error instanceof Error
            ? error.message
            : 'Metric details could not be loaded.',
        )
      } finally {
        setIsLoadingMetricDetails(false)
      }
    }

    void loadMetricDetails()
  }, [accessToken, onSessionExpired, selectedMetricName])

  const normalizedMetricSearch = metricSearch.trim().toLowerCase()
  const visibleMetricNames = metricNames.filter((metricName) =>
    metricName.toLowerCase().includes(normalizedMetricSearch),
  )

  function handleMetricSelection(metricName: string) {
    setSelectedMetricName(metricName)
  }

  return (
    <main className="dashboard-shell">
      <header className="dashboard-header">
        <div>
          <p className="eyebrow">Order Engine administration</p>
          <h1>Application metrics</h1>
          <p>Use Grafana for charts and alerts. This screen shows the metrics the application currently exposes.</p>
        </div>

        <button type="button" onClick={onSignOut}>
          Sign out
        </button>
      </header>

      <section className="metrics-panel" aria-labelledby="metrics-heading">
        <div className="metrics-panel-header">
          <div>
            <h2 id="metrics-heading">Available metrics</h2>
            <p>
              Showing {visibleMetricNames.length} of {metricNames.length} metric names exposed by Spring Boot.
            </p>
          </div>

          <button
            type="button"
            className="secondary-button"
            onClick={() => setLoadAttempt((attempt) => attempt + 1)}
            disabled={isLoading}
          >
            {isLoading ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>

        {isLoading && <p role="status">Loading metrics…</p>}

        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}

        {!isLoading && !errorMessage && metricNames.length > 0 && (
          <div className="metric-search">
            <label htmlFor="metric-search">Find a metric</label>
            <input
              id="metric-search"
              value={metricSearch}
              onChange={(event) => setMetricSearch(event.target.value)}
              placeholder="for example, kafka, jvm.memory, or order.payments"
            />
          </div>
        )}

        {!isLoading && !errorMessage && metricNames.length === 0 && (
          <p className="empty-message">No metrics are currently exposed.</p>
        )}

        {!isLoading && !errorMessage && metricNames.length > 0 && visibleMetricNames.length === 0 && (
          <p className="empty-message">No metric names match this search.</p>
        )}

        {!isLoading && !errorMessage && visibleMetricNames.length > 0 && (
          <div className="metrics-browser">
            <ul className="metric-list" aria-label="Available metric names">
              {visibleMetricNames.map((metricName) => (
                <li key={metricName}>
                  <button
                    type="button"
                    className={`metric-name-button${selectedMetricName === metricName ? ' is-selected' : ''}`}
                    onClick={() => handleMetricSelection(metricName)}
                    aria-pressed={selectedMetricName === metricName}
                  >
                    {metricName}
                  </button>
                </li>
              ))}
            </ul>

            <aside className="metric-details" aria-live="polite">
              {!selectedMetricName && (
                <>
                  <h3>Select a metric</h3>
                  <p className="metric-details-note">
                    Choose a metric name from the list to see its current value.
                  </p>
                </>
              )}

              {selectedMetricName && (
                <>
                  <h3>Current metric value</h3>
                  <p className="selected-metric-name">{selectedMetricName}</p>
                  <p className="metric-details-note">
                    This is the current value from Spring Boot. Use Grafana for its history over time.
                  </p>

                  {isLoadingMetricDetails && <p role="status">Loading metric details…</p>}

                  {metricDetailsErrorMessage && (
                    <p className="form-error" role="alert">
                      {metricDetailsErrorMessage}
                    </p>
                  )}

                  {metricDetails && (
                    <>
                      {metricDetails.description && <p>{metricDetails.description}</p>}
                      <dl className="measurement-list">
                        {metricDetails.measurements.map((measurement) => (
                          <div key={measurement.statistic}>
                            <dt>{measurement.statistic}</dt>
                            <dd>
                              {measurement.value.toLocaleString(undefined, {
                                maximumFractionDigits: 2,
                              })}
                              {metricDetails.baseUnit ? ` ${metricDetails.baseUnit}` : ''}
                            </dd>
                          </div>
                        ))}
                      </dl>
                    </>
                  )}
                </>
              )}
            </aside>
          </div>
        )}
      </section>
    </main>
  )
}

export default AdminWorkspace
