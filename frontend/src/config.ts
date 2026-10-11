const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()

// Local development works without an environment file. A deployed frontend can
// provide VITE_API_BASE_URL without requiring source-code changes.
export const API_BASE_URL = (
  configuredApiBaseUrl || 'http://localhost:8080'
).replace(/\/+$/, '')