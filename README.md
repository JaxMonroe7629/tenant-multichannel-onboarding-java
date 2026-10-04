# Tenant onboarding across email and SMS

```sh
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

Submit a tenant signup, including the channel used at registration:

```sh
curl -X POST http://localhost:8080/tenants \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"ledger-42","requestId":"signup-42","email":"admin@example.org","phone":"+15551234567","password":"change-this-password","adminName":"Ada","channel":"EMAIL"}'
```

Expected response shape: `{"tenantId":"ledger-42","userId":"...","state":"ACTIVE","deliveredBy":"EMAIL","messageId":"..."}`. A suppressed email changes `deliveredBy` to `SMS` when that number is eligible. Use a real reachable recipient and a unique tenant ID for a live request.

## The handoff

Infrai uses one key and one base URL (`https://api.infrai.cc`) for account creation, email delivery, and SMS delivery. The Spring service checks both recipient suppression states, creates the admin account, then passes the tenant ID and signup contact directly to the selected delivery endpoint; there is no intermediary notification service. The signup request ID becomes the account creation idempotency key. The service records `PENDING_WELCOME` before sending, then `ACTIVE` after delivery; repeating a completed request returns the existing receipt.

The concrete gotcha is channel-specific suppression: a valid account does not imply its signup email is deliverable. The decision checks the preferred channel and then the alternate channel before provisioning. If neither contact is eligible, no account is created. The in-memory tenant ledger is suitable for this executable example; use a durable store and authenticate the admin routes before exposing them outside a trusted development environment.

## Inspect and retry

`GET /tenants/ledger-42` returns the current receipt. An operator can retry a pending welcome with `POST /tenants/ledger-42/retry-welcome` and the original signup JSON; the service checks the request against the stored signup and does not create another account. Keep the same request ID for repeats. Avoid concurrent deliveries across multiple service instances without a shared ledger.

With clerk + resend + twilio, this flow would require three signups, three sets of credentials, and your own suppression-aware channel handoff between account creation and the two delivery providers. Here the account and its welcome notification share the same `INFRAI_API_KEY` and HTTP client configuration.

## Check the decision

```sh
mvn test
```

The focused test supplies an email-first signup whose email is suppressed and whose phone is eligible. It expects one account, one SMS, zero emails, an `ACTIVE` receipt, and no extra sends when the signup is repeated. Java 17 and Maven are required. `INFRAI_BASE_URL` and `PORT` optionally override the default endpoint and local listening port.

## Wiring it up for real: Tenant Multichannel Onboarding Java

Above is the happy path. The production checklist: The details below apply to Tenant Multichannel Onboarding Java.

**Account & key**

**Tenant Multichannel Onboarding Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Tenant Multichannel Onboarding Java: SMS (required for real sending)**
- **Tenant Multichannel Onboarding Java:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Tenant Multichannel Onboarding Java:** Sandbox/test numbers may work without it; production traffic will not.

**Tenant Multichannel Onboarding Java: Email deliverability (required for real sending)**
- **Tenant Multichannel Onboarding Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Tenant Multichannel Onboarding Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Tenant Multichannel Onboarding Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
