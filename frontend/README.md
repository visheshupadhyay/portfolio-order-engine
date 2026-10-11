# Order Engine frontend

React and TypeScript browser interface for the Portfolio Order Engine. The UI
uses the Spring Boot API for authentication, order operations, and protected
application metrics.

## Available workspaces

The backend places one application role in each JWT. The frontend reads that
role to choose a helpful screen, while Spring Security remains responsible for
enforcing the real authorization boundary.

| Role | Workspace | Capabilities |
| --- | --- | --- |
| `ROLE_ORDER_READER` | Order dashboard | List, filter, paginate, and look up orders |
| `ROLE_ORDER_WRITER` | Order actions | Create orders and mark them as paid |
| `ROLE_ORDER_ADMIN` | Administration | Search and inspect protected Actuator metrics |

## Prerequisites

- Node.js 24
- npm 11 or later
- the Order Engine backend running and reachable from the browser

The local backend normally listens on `http://localhost:8080`.

## Local development

From the `frontend` directory:

```powershell
npm ci
npm run dev
```

Open `http://localhost:5173`.

Vite reloads the page automatically when frontend source files change. The
Spring Boot backend must run separately.

## API configuration

The UI defaults to:

```text
http://localhost:8080
```

To use another backend, copy `.env.example` to `.env.local` and change:

```dotenv
VITE_API_BASE_URL=https://your-api.example.com
```

`.env.local` is ignored by Git. Vite variables are visible in the browser, so
never place passwords, JWT signing keys, database credentials, or other secrets
in them.

## Verification commands

```powershell
npm run lint
npm test
npm run build
```

- `lint` checks TypeScript and React source quality.
- `test` runs the Vitest and Testing Library suite once.
- `build` type-checks the project and creates the production files in `dist`.

During development, tests can remain active with:

```powershell
npm run test:watch
```

## Test coverage

The frontend tests cover:

- JWT role parsing, invalid tokens, and expiry;
- rejected and successful login flows;
- reader, writer, and administrator workspace selection;
- session restoration, sign-out, and unauthorized responses;
- order filtering and exact-ID lookup;
- order creation and payment behavior;
- metric search, selection, values, and failures;
- HTTP URLs, query parameters, request bodies, bearer headers, and safe errors.

The tests mock the network boundary so they remain fast and deterministic. The
Spring Boot suite separately verifies real authentication, authorization,
PostgreSQL, Redis, Kafka, and end-to-end reliability behavior.

## Security boundary

The JWT is kept in `sessionStorage`. It survives a refresh in the same browser
tab and is removed when the tab/window session ends or the user signs out.

Reading a role in React is only a user-interface decision. It is not a security
check. Every protected API request still sends the bearer token to Spring Boot,
which validates its signature, expiry, issuer, and required role.
