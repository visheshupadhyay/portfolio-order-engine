interface JwtPayload {
  roles?: string[]
  exp?: number
}

function getJwtPayload(accessToken: string): JwtPayload | null {
  try {
    const tokenParts = accessToken.split('.')
    const payloadPart = tokenParts[1]

    if (!payloadPart) {
      return null
    }

    const base64Payload = payloadPart
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(Math.ceil(payloadPart.length / 4) * 4, '=')

    return JSON.parse(atob(base64Payload)) as JwtPayload
  } catch {
    // A broken token should never crash the page or unlock a screen.
    return null
  }
}

export function getRolesFromAccessToken(accessToken: string): string[] {
  return getJwtPayload(accessToken)?.roles ?? []
}

export function isAccessTokenExpired(accessToken: string): boolean {
  const expirationTimeSeconds = getJwtPayload(accessToken)?.exp

  // A token without a readable expiry is not safe for React to restore.
  if (!expirationTimeSeconds) {
    return true
  }

  return Date.now() >= expirationTimeSeconds * 1_000
}
