# Postman collection

Every endpoint of the API, plus seven end-to-end flows with tests that you can run in one click.

| File | What it is |
|---|---|
| `ecommerce-platform.postman_collection.json` | 141 requests: 11 reference folders and 7 flows |
| `local.postman_environment.json` | `baseUrl`, Mailpit URL and the seed accounts for local development |
| `sample-product.png` | The image used by **Admin: Products → Upload an image** |

## Setup

1. Start the backend: `docker compose up -d`, then run the app with the `dev` profile (it loads the seed accounts).
2. In Postman: **Import** both JSON files and select the **E-commerce · local** environment (top right).
3. Send any request.

You don't need to sign in first. Before each request, the collection signs in the seed customer and admin when their tokens are missing or about to expire. The tokens are stored in collection variables, not in the environment file.

## Folders

**Reference folders** (Store, Auth, Catalog, Addresses, Cart, Orders, Payments, Admin: …) have one request per endpoint, with example bodies, every query parameter documented in the Params tab, and light tests. Requests save the ids they create (`orderId`, `newProductId`, …), so the next request in the folder uses them.

**Flow folders** are scenarios. Each one sets up its own data and checks the result of every step:

| Flow | What it proves |
|---|---|
| 1 · Buy, pay and deliver | Stock is reserved at checkout, the cart empties, every status move works and wrong ones get 409, the sales report grows by the order total, and the four order emails reach Mailpit |
| 2 · Pay with Stripe | Only a correctly signed webhook marks the order paid, a retried event changes nothing, and a forged signature gets 400 |
| 3 · Cancel and restock | Cancelling a pending order returns the stock; a cancelled order can't be cancelled again or paid |
| 4 · Forgot password | The reset link is read from Mailpit; the new password works, the old one doesn't, and the link works only once |
| 5 · Refresh tokens | Tokens rotate on refresh; replaying an old refresh token signs out every session |
| 6 · Admin catalog | Subcategories, unique names, deletion rules, archived products hidden from customers, no stale cached prices |
| 7 · Security and errors | 401, 403, 404 for another customer's order, validation errors per field, malformed JSON, bad webhooks |

To run them: right-click the collection (or one flow) → **Run** → **Run E-commerce Platform API**. Flows create orders and products in your dev database, with unique names, so they can be run again and again.

## Optional setup

| For | Do this |
|---|---|
| Flows 1 and 4 (emails) | Mailpit must be running (it is part of `docker compose`). Flow 4 reads the reset link from it. |
| Flow 2 (Stripe) | Run the app with Stripe test keys, and set `stripeWebhookSecret` in the environment to the same value as the server's `STRIPE_WEBHOOK_SECRET`. Without both, the Runner skips to Flow 3. |
| Paymob webhook request | Set `paymobHmacSecret` (= server `PAYMOB_HMAC_SECRET`) and the `paymobOrderId` collection variable (the `provider_session_id` of the payment). Without them the request is skipped. |
| Upload an image | Set Postman's working directory (**Settings → General → Working directory**) to the repository root, or pick any JPEG/PNG/WebP in the request's Body tab. |

Put secrets in the environment's **current value** only. Postman doesn't export or sync current values, so the secrets stay on your machine.

## How the webhook requests are signed

The payment webhooks only accept requests signed with the provider's secret, so the pre-request scripts sign test events the same way the providers do:

```js
// Stripe: Stripe-Signature: t=<timestamp>,v1=HMAC-SHA256(secret, "<timestamp>.<body>")
const signature = CryptoJS.HmacSHA256(timestamp + '.' + body, secret).toString(CryptoJS.enc.Hex);

// Paymob: ?hmac=HMAC-SHA512(secret, the values of 20 fixed transaction fields, concatenated in order)
pm.collectionVariables.set('paymobHmac', CryptoJS.HmacSHA512(concatenated, secret).toString(CryptoJS.enc.Hex));
```

The Stripe flow reads the Checkout Session id from the `checkoutUrl`, as real Stripe URLs contain it (`https://checkout.stripe.com/c/pay/cs_test_…`).

## Verified

All 141 requests were run against a fresh database with Stripe (stripe-mock), Paymob (WireMock) and Mailpit: 272 assertions passed and 0 failed. The only requests skipped were those whose secrets were deliberately left unset.
