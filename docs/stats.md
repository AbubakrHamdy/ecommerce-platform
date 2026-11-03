# Admin statistics

Three read-only reports for the admin dashboard: a sales summary, revenue over time, and top products. All of them are computed by PostgreSQL from the orders table, so the numbers are always current.

## What counts as revenue

| Order status | Counted as a sale? | Why |
|---|---|---|
| `PAID`, `PROCESSING`, `SHIPPED`, `DELIVERED` | Yes | The customer has paid and the order is still going ahead |
| `PENDING` | No | Not paid yet; it may expire |
| `CANCELLED` | No | Cancelled before shipping; stock was returned |

- Amounts are the **order totals and item prices at checkout** (the snapshots), not today's product prices.
- Only orders in the **store's current currency** are counted. Amounts in different currencies can't be added up, and orders placed before a currency change keep their own currency.
- An order is counted on the day it was **placed**, not the day it was paid or shipped. Sales don't move between days as orders progress.

## Days are store days

A "day" means a calendar day in the store's time zone, set with `STORE_TIME_ZONE` (IANA id, default `Africa/Cairo`). An order placed at 23:30 in Cairo belongs to that day, even though it is already the next day in UTC.

```
STORE_TIME_ZONE=Africa/Cairo
  1 March in Cairo  =  29 Feb 22:00 UTC  ..  1 March 22:00 UTC
```

- An invalid zone id stops the app at startup with a clear message. A blank value means UTC.
- `GET /api/v1/store` includes `timeZone`, so the dashboard can label its charts.
- Daylight saving is handled by PostgreSQL and Java's time zone database.

## Endpoints

All endpoints need an **admin** token (customers get 403). `from` and `to` are calendar days (`2026-10-01`), **both inclusive**. When both are missing, the report covers the last 30 days up to today. When only one is given, the period is the 30 days starting or ending on it.

| Method | Path | Parameters |
|---|---|---|
| `GET` | `/api/v1/admin/stats/summary` | `from`, `to` (up to 5 years) |
| `GET` | `/api/v1/admin/stats/revenue` | `from`, `to`, `granularity=DAY\|WEEK\|MONTH` (default `DAY`) |
| `GET` | `/api/v1/admin/stats/top-products` | `from`, `to`, `limit` (1–50, default 5), `by=REVENUE\|UNITS` (default `REVENUE`) |

### Summary

```http
GET /api/v1/admin/stats/summary?from=2026-10-01&to=2026-10-31
```

```json
{
  "from": "2026-10-01",
  "to": "2026-10-31",
  "currency": "EGP",
  "revenue": 7099.94,
  "paidOrders": 31,
  "averageOrderValue": 229.03,
  "ordersByStatus": { "PENDING": 1, "PAID": 2, "PROCESSING": 0, "SHIPPED": 0, "DELIVERED": 29, "CANCELLED": 1 }
}
```

`ordersByStatus` lists every status, including the ones that aren't sales, so the admin can see how many orders were abandoned or cancelled. Amounts use the currency's own decimals (`0.00` for EGP, `0` for JPY).

### Revenue over time

```http
GET /api/v1/admin/stats/revenue?from=2026-10-24&to=2026-11-03&granularity=WEEK
```

```json
{
  "from": "2026-10-24",
  "to": "2026-11-03",
  "currency": "EGP",
  "granularity": "WEEK",
  "points": [
    { "date": "2026-10-19", "paidOrders": 2, "revenue": 210.00 },
    { "date": "2026-10-26", "paidOrders": 0, "revenue": 0.00 },
    { "date": "2026-11-02", "paidOrders": 3, "revenue": 2759.94 }
  ]
}
```

24 October is a Saturday, so the first point is the week of Monday 19 October, counting only the 24th and 25th.

- There is **one point per bucket, including empty ones** (revenue `0.00`), so a chart can plot the list as it is.
- `date` is the first day of the bucket: the day itself, the **Monday** of the week, or the 1st of the month.
- The first and last buckets only count days inside `from`..`to`. A month cut by the range shows a partial month.
- `DAY` covers up to 366 days. For longer periods, use `WEEK` or `MONTH` (up to 5 years).

### Top products

```http
GET /api/v1/admin/stats/top-products?limit=3&by=REVENUE
```

```json
{
  "from": "2026-10-05",
  "to": "2026-11-03",
  "currency": "EGP",
  "rankedBy": "REVENUE",
  "products": [
    { "productId": 3, "productName": "Wireless Mouse", "unitsSold": 30, "revenue": 4369.99 },
    { "productId": 1, "productName": "ProBook 14 Laptop", "unitsSold": 3, "revenue": 2699.97 },
    { "productId": 4, "productName": "Classic Cotton T-Shirt", "unitsSold": 2, "revenue": 29.98 }
  ]
}
```

- `REVENUE` shows what earns the most. `UNITS` shows what sells the most, which matters for restocking.
- Rows are grouped by **product id** and show the product's **current** name, so a renamed product stays one row.
- Ties are broken by the other measure, then by product id, so the order is stable.

### Errors

Bad input returns `400` with a message:

| Request | Message |
|---|---|
| `from` after `to` | `'from' (2026-10-10) must not be after 'to' (2026-10-01)` |
| A daily series of 397 days | `The period is 397 days; at most 366 are allowed here` |
| `limit=0` | `'limit' must be between 1 and 50` |
| `granularity=YEAR`, `from=not-a-date` | `Invalid value for parameter 'granularity'` |

## How it works

```java
// ReportPeriod: inclusive store days -> the exact half-open range of instants to query
ReportPeriod.of(from, to, zone, maxDays)
    start = from.atStartOfDay(zone)        // 1 March 00:00 Cairo = 29 Feb 22:00 UTC
    end   = to.plusDays(1).atStartOfDay(zone)
```

- **Summary**: one JPQL `group by status` query returns the count and total for each status. Java then adds up the sale statuses and fills missing statuses with zero.
- **Revenue over time**: a native query does the grouping, because JPQL can't truncate a date in another time zone. `created_at` holds UTC, so it is converted twice: `at time zone 'UTC'` turns it into an instant, and `at time zone :zone` turns that into store time. `date_trunc` then cuts it to the day, week or month. Java adds the empty buckets.

  ```sql
  select cast(date_trunc(:unit, (o.created_at at time zone 'UTC') at time zone :zone) as date) as bucket,
         count(*), sum(o.total_amount)
  from orders o
  where o.currency = :currency and o.status in (:statuses)
    and o.created_at >= :start and o.created_at < :end
  group by bucket order by bucket
  ```

- **Top products**: JPQL over `order_items` joined to the order and the product, with `Limit` applied in SQL.
- Every query filters on a range of `created_at`, which the existing `(status, created_at)` index covers.
- The list of sale statuses lives in one place, `OrderStatus.isSale()`, and every query uses it.

## Decisions

- **No caching.** These are a few aggregate queries that run on an admin page. A cache would show stale numbers right after an order is paid, and the store would have to evict it on every order change. Caching can be added if the order table grows large.
- **Computed on demand, not stored.** Daily totals kept in a separate table would need updating on every status change and could drift from the orders. Grouping the orders when asked is always correct.
- **Grouped by order date, not payment date.** It is simpler and stable, and for this store payment follows checkout within minutes.

## Tests

`StatsServiceIntegrationTest` runs on real PostgreSQL with orders dated March 2020, so the seed data never falls in the reported days. It checks:

- Exact sums, with `PENDING`, `CANCELLED` and other-currency orders left out.
- The Cairo midnight boundary: 21:59:59 UTC is still 1 March, 22:00:00 UTC is 2 March.
- Empty days filled with zero, week buckets that start on Monday, and calendar months.
- Ranking by revenue and by units, with a limit.
- Period and limit validation.

`ReportPeriodTest` covers the default 30 days and the conversion to UTC instants.
