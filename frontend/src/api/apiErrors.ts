export class SessionExpiredError extends Error {
  constructor() {
    super('Your session is no longer valid. Please sign in again.')
    this.name = 'SessionExpiredError'
  }
}