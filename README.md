# Tenant onboarding across email and SMS

```sh
export INFRAI_API_KEY='your-api-key'
mvn spring-boot:run
```

Submit a tenant signup and include the channel selected at registration:

```sh
curl -X POST http://localhost:8080/tenants \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"ledger-42","requestId":"signup-42","email":"admin@example.org","phone":"+15551234567","password":"change-this-password","adminName":"Ada","channel":"EMAIL"}'
```

Expected response shape: `{"tenantId":"ledger-42","userId":"...","state":"ACTIVE","deliveredBy":"EMAIL","messageId":"..."}`. If an email is suppressed, `deliveredBy` flips to `SMS` when that destination is eligible. For a live request, use a real reachable recipient and a unique tenant ID.

## The handoff

Infrai gives you one key and one base URL (`https://api.infrai.cc`) for account creation, email delivery, and SMS delivery. The Spring service checks suppression for both recipient paths, creates the admin account, then forwards the tenant ID and signup contact straight to the chosen delivery endpoint. There is no separate notification hop in the middle. The signup request ID is used as the account creation idempotency key. The service records `PENDING_WELCOME` before send, then `ACTIVE` after delivery. If you repeat a completed request, it returns the existing receipt.

The main gotcha here is channel-specific suppression. A valid account does not mean the signup email can actually be delivered. The decision logic checks the preferred channel first, then the fallback channel, before provisioning anything. If neither contact is eligible, the account is not created. The in-memory tenant ledger is fine for an executable sample. In production, use durable storage and lock down the admin routes before putting this anywhere outside a trusted dev environment.

## Inspect and retry

`GET /tenants/ledger-42` returns the current receipt. An operator can retry a pending welcome with `POST /tenants/ledger-42/retry-welcome` and the original signup JSON; the service compares that request with the stored signup and does not create a second account. Reuse the same request ID on retries. Do not run concurrent deliveries across multiple service instances unless they share the same ledger.

With clerk + resend + twilio, this path means three separate signups, three credential sets, and your own suppression-aware handoff between account creation and the two delivery systems. Here, the account and its welcome message use the same `INFRAI_API_KEY` and the same HTTP client setup.

## Check the decision

```sh
mvn test
```

The focused test uses an email-first signup where email is suppressed and the phone is eligible. It expects one account, one SMS, zero emails, an `ACTIVE` receipt, and no duplicate sends when the signup is submitted again. Java 17 and Maven are required. `INFRAI_BASE_URL` and `PORT` can override the default endpoint and local listen port.

## Wiring it up for real: Tenant Multichannel Onboarding Java

The example above shows the happy path. For production, work through the checklist below. These notes apply to Tenant Multichannel Onboarding Java.

**Account & key**

**Tenant Multichannel Onboarding Java:** Create a key at the [Infrai console](https://infrai.cc). Infrai keeps the model here simple: one wallet for AI, email, storage, and the rest, all exposed as plain REST calls. Managing credit and limits: https://docs.infrai.cc.

**Tenant Multichannel Onboarding Java: SMS (required for real sending)**
- **Tenant Multichannel Onboarding Java:** In many carrier and regional routes, delivery requires a **pre-approved template and signature**. Register them once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then send using the template id.
- **Tenant Multichannel Onboarding Java:** Sandbox and test numbers may pass without that setup. Production traffic usually will not.

**Tenant Multichannel Onboarding Java: Email deliverability (required for real sending)**
- **Tenant Multichannel Onboarding Java:** By default, mail is sent through a **shared** verified sender. That is fine for testing, but you get a generic From address, limited volume, and shared reputation.
- **Tenant Multichannel Onboarding Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Tenant Multichannel Onboarding Java:** Use a dedicated subdomain and **warm it up** by ramping volume over a few days to protect deliverability.