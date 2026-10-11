# ADR-063: Reactive database CA trust

## Situation

On dev, every endpoint backed by Hibernate Reactive (feed, posts, topics) returned 500 with
`io.vertx.core.VertxException: SSL handshake failed`. The app showed "We couldn't load the feed".

Both datasources connect to Aiven Postgres with `sslmode=require`, but the two drivers read it
differently:

- **JDBC (pgjdbc):** `require` encrypts and does not verify the server certificate.
- **Reactive (Vert.x pg client):** `require` verifies the certificate against the JVM trust store.
  Aiven signs it with a per-project CA the JVM does not trust, so the handshake fails.

## Options considered

1. `quarkus.datasource.reactive.trust-all=true` - encrypted but unauthenticated; open to MITM.
2. Aiven CA delivered as a new GitHub secret and rendered to a file, like the Firebase credential.
3. Aiven CA committed to the deploy bundle and mounted read-only into `post-service`.

## Decision

Option 3. `service/deploy/certs/aiven-dev-ca.pem` ships in the deploy bundle, is mounted at
`/etc/your-say-news/certs/aiven-dev-ca.pem`, and `%dev` sets
`quarkus.datasource.reactive.trust-certificate-pem.certs` to it with
`hostname-verification-algorithm=HTTPS`.

## Reason

A CA certificate is public, so secret plumbing (option 2) adds moving parts with no security gain.
Option 1 hides the failure without authenticating the server.

## Consequences

- Rotating the Aiven CA, or moving database provider, means replacing the committed file.
- A future prod environment needs its own CA file and `%prod` lines.
- Follow-up: JDBC (user domains, Liquibase migration) is still encrypted but unverified. Switching
  `DEV_DB_JDBC_URL` to `sslmode=verify-full&sslrootcert=<path>` would need the same file mounted
  into `post-service` and the `migrate` container.
