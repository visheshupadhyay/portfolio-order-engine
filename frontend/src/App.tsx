import { type FormEvent, useEffect, useState } from 'react'
import { login } from './api/authApi'
import { getRolesFromAccessToken, isAccessTokenExpired } from './auth/jwt'
import AdminWorkspace from './components/AdminWorkspace'
import Dashboard from './components/Dashboard'
import WriterWorkspace from './components/WriterWorkspace'
import './App.css'

interface RestoredSession {
  accessToken: string | null
  hasExpiredToken: boolean
}

function restoreSession(): RestoredSession {
  const savedAccessToken = sessionStorage.getItem('accessToken')

  if (!savedAccessToken) {
    return { accessToken: null, hasExpiredToken: false }
  }

  if (isAccessTokenExpired(savedAccessToken)) {
    return { accessToken: null, hasExpiredToken: true }
  }

  return { accessToken: savedAccessToken, hasExpiredToken: false }
}

function App() {
  const [restoredSession] = useState(restoreSession)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState(
    restoredSession.hasExpiredToken
      ? 'Your session expired. Please sign in again.'
      : '',
  )
  const [isSubmitting, setIsSubmitting] = useState(false)

  // Restore only a token whose readable expiry time is still in the future.
  const [accessToken, setAccessToken] = useState(restoredSession.accessToken)

  useEffect(() => {
    if (restoredSession.hasExpiredToken) {
      sessionStorage.removeItem('accessToken')
      sessionStorage.removeItem('tokenType')
    }
  }, [restoredSession.hasExpiredToken])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    setErrorMessage('')
    setIsSubmitting(true)

    try {
      const loginResponse = await login({ username, password })

      sessionStorage.setItem('accessToken', loginResponse.accessToken)
      sessionStorage.setItem('tokenType', loginResponse.tokenType)

      // Updating React state makes App render Dashboard instead of Login.
      setAccessToken(loginResponse.accessToken)
    } catch (error) {
      if (error instanceof Error) {
        setErrorMessage(error.message)
      } else {
        setErrorMessage('Sign in failed. Please try again.')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  function handleSignOut() {
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('tokenType')

    setAccessToken(null)
    setPassword('')
    setErrorMessage('')
  }

  function handleSessionExpired() {
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('tokenType')

    setAccessToken(null)
    setPassword('')
    setErrorMessage('Your session expired. Please sign in again.')
  }
  // Token exists: show the after-login screen instead of the login form.
  if (accessToken) {
    const roles = getRolesFromAccessToken(accessToken)

    if (roles.includes('ROLE_ORDER_READER')) {
      return (
        <Dashboard
          accessToken={accessToken}
          onSignOut={handleSignOut}
          onSessionExpired={handleSessionExpired}
        />
      )
    }

    if (roles.includes('ROLE_ORDER_WRITER')) {
      return (
        <WriterWorkspace
          accessToken={accessToken}
          onSignOut={handleSignOut}
          onSessionExpired={handleSessionExpired}
        />
      )
    }

    if (roles.includes('ROLE_ORDER_ADMIN')) {
      return (
        <AdminWorkspace
          accessToken={accessToken}
          onSignOut={handleSignOut}
          onSessionExpired={handleSessionExpired}
        />
      )
    }

    return (
      <main className="app-shell">
        <section className="login-card">
          <h1>Access unavailable</h1>
          <p className="intro">
            This token does not contain a supported Order Engine role.
          </p>
          <button type="button" onClick={handleSignOut}>
            Sign out
          </button>
        </section>
      </main>
    )
  }

  return (
    <main className="app-shell">
      <section className="login-card" aria-labelledby="login-heading">
        <div className="login-brand" aria-hidden="true">
          <span className="brand-mark">OE</span>
          <span>Portfolio operations</span>
        </div>

        <div className="login-heading-group">
          <p className="eyebrow">SECURE ORDER ENGINE</p>
          <h1 id="login-heading">Welcome back</h1>
          <p className="intro">
            Sign in to enter your role-specific operations workspace.
          </p>
        </div>

        <form className="login-form" onSubmit={handleSubmit}>
          <label htmlFor="username">Username</label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
            disabled={isSubmitting}
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
            disabled={isSubmitting}
          />

          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <div className="login-assurance">
          <span className="assurance-dot" aria-hidden="true" />
          Protected by short-lived JWT access and server-side role checks
        </div>

        {errorMessage && (
          <p className="form-error" role="alert">
            {errorMessage}
          </p>
        )}
      </section>
    </main>
  )
}

export default App
