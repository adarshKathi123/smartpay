# SmartPay

A small **digital wallet backend**: people can register, add money to a wallet, send money to each other, and see their history. An admin can freeze and unfreeze accounts.

> **Important:** all money in SmartPay is **simulated**. This is a learning and portfolio project, not a real payment system.

Built with **Java 21, Spring Boot 4, Spring Security (JWT) and MySQL**.

---

## What can it do?

| Feature | In plain words |
|---|---|
| Register and log in | Create an account, log in, and get a token that proves who you are for 1 hour |
| Wallet | Every user has one wallet (created automatically the first time it is used) |
| Add test money | Put simulated money into your own wallet |
| Send money | Transfer money to another user by their user id |
| Safe retries | If a request is sent twice by mistake, the money only moves once |
| History | See your sent and received money, newest first, a page at a time |
| Admin tools | An admin can freeze or unfreeze a user. Every change is saved in an audit log |
| Clear errors | Every error comes back in the same JSON format, including "not logged in" and "not allowed" |
| API docs | Built-in Swagger page where you can try every endpoint |

---

## How it is built

```
Browser / Swagger / curl
          |
          v
   Controller        receives the request, checks the input
          |
          v
    Service          the business rules (is the user frozen? enough balance?)
          |
          v
   Repository        talks to the database
          |
          v
  MySQL (smartpay_db)
```

Code folders (all inside `src/main/java/com/smartpay`):

| Folder | What lives there |
|---|---|
| `controller` | The web endpoints |
| `service` | The business rules, including `TransferProcessor` which moves the money |
| `repository` | Database access (Spring Data JPA) |
| `entity` | The classes that match database tables |
| `dto` | The shapes of requests and responses |
| `security` | Login tokens (JWT) and access rules |
| `exception` | The standard error format |
| `config` | Swagger / OpenAPI setup |

The project is a **modular monolith**: one application, split into clear layers.

---

## How to run it

### 1. What you need

- Java 21
- MySQL 8.0.16 or newer
- Nothing else. The project includes Maven (`mvnw`), so you do not need to install it.

### 2. Create the database

```bash
mysql -u root -p < docs/sql/schema.sql
```

This creates the database `smartpay_db` and its four tables: `users`, `wallets`, `transactions`, `audit_logs`.
Run it **once** on a fresh database. (The app never changes the tables itself; it only checks that they match.)

### 3. Set two secrets

The app reads two values from **environment variables**. The names are listed in `.env.example`:

| Variable | What it is |
|---|---|
| `DB_PASSWORD` | Your MySQL password (the app logs in as user `root`) |
| `JWT_SECRET` | A long random text (at least 32 characters) used to sign login tokens |

Set them in the same terminal you will start the app from.

**Windows PowerShell**
```powershell
$env:DB_PASSWORD = "your-mysql-password"
$env:JWT_SECRET  = "put-a-long-random-text-of-at-least-32-characters-here"
```

**macOS / Linux**
```bash
export DB_PASSWORD="your-mysql-password"
export JWT_SECRET="put-a-long-random-text-of-at-least-32-characters-here"
```

> Never commit real passwords. The `.env` file is ignored by Git on purpose.

### 4. Start the app

```bash
./mvnw spring-boot:run          # Windows: .\mvnw.cmd spring-boot:run
```

The app runs at **http://localhost:8081**.
Open **http://localhost:8081/swagger-ui.html** to see and try all endpoints.

### 5. Make someone an admin (optional)

There is no "become admin" endpoint on purpose. Register a normal user first, then run this in MySQL:

```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';
```

---

## Try it in 5 minutes

On Windows PowerShell, write `curl.exe` instead of `curl`.

**1. Register two users**
```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Asha","email":"asha@example.com","password":"password123"}'
```
Do the same for a second user (for example Ravi). Passwords need at least 8 characters.

**2. Log in** (you get an `accessToken`)
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"asha@example.com","password":"password123"}'
```

**3. Add test money** (replace `TOKEN` with the accessToken)
```bash
curl -X POST http://localhost:8081/api/wallet/deposit \
  -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -d '{"amount": 500.00}'
```

**4. Send money to user id 2**
```bash
curl -X POST http://localhost:8081/api/transfers \
  -H "Authorization: Bearer TOKEN" \
  -H "Idempotency-Key: my-first-transfer-001" \
  -H "Content-Type: application/json" \
  -d '{"receiverUserId": 2, "amount": 100.00}'
```
Run the exact same command again. You get the same result back and **no money moves a second time**. That is the idempotency key at work.

**5. See your history**
```bash
curl "http://localhost:8081/api/transactions?page=0&size=20" -H "Authorization: Bearer TOKEN"
```

---

## The endpoints

All endpoints except register and login need the header `Authorization: Bearer <token>`.

| Method | URL | Who | What it does |
|---|---|---|---|
| POST | `/api/auth/register` | anyone | Create an account |
| POST | `/api/auth/login` | anyone | Log in, get a token (valid 1 hour) |
| GET | `/api/users/me` | logged in | Your own profile |
| GET | `/api/wallet` | logged in | Your wallet and balance |
| POST | `/api/wallet/deposit` | logged in | Add simulated money to your own wallet |
| POST | `/api/transfers` | logged in | Send money to another user (needs `Idempotency-Key` header) |
| GET | `/api/transactions` | logged in | Your history, newest first (`page` starts at 0, `size` 1 to 50) |
| POST | `/api/admin/users/{id}/freeze` | admin | Freeze a user |
| POST | `/api/admin/users/{id}/unfreeze` | admin | Unfreeze a user |

**Rules for amounts:** from 0.01 to 100000.00, with at most 2 decimal places.

**Rules enforced on transfers**
- You cannot send money to yourself.
- You cannot send more than your balance (`422`).
- A frozen user cannot send or receive money (`403`).
- The `Idempotency-Key` is required, up to 100 characters. Reusing a key for a *different* request gives `409`.

**Every error looks the same:**
```json
{
  "timestamp": "2026-10-08T10:15:30",
  "status": 422,
  "error": "...",
  "message": "Insufficient balance",
  "path": "/api/transfers"
}
```

---

## Why transfers are safe (design decisions)

This is the most important part of the project.

| Problem | How SmartPay solves it |
|---|---|
| Two transfers use the same money at the same moment | Each wallet row is **locked** (`SELECT ... FOR UPDATE`) while money moves, so one transfer waits for the other. |
| Two opposite transfers (A to B and B to A) block each other forever (a deadlock) | The wallet of the **smaller user id is always locked first**, so both transfers lock in the same order. |
| Money leaves one wallet but never arrives in the other | Everything happens in **one database transaction**: both wallets change and the record is saved, or nothing changes. |
| A request is sent twice (slow network, double click) | The **Idempotency-Key** is stored with a database UNIQUE rule. A repeat returns the original result and moves no money. |
| Rounding errors with money | Amounts are stored as **DECIMAL(19,2)**, never as floating point numbers. |
| A balance going below zero | The application checks it, and the database also has a `CHECK (balance >= 0)` rule as a second safety net. |
| Passwords leaking | Passwords are stored as **BCrypt hashes**, never as plain text. |
| An old token keeps working after a role change | The admin check reads the role **from the database**, not from the token. |

---

## Running the tests

```bash
./mvnw test          # Windows: .\mvnw.cmd test
```

- There are **57 tests**: unit tests for the services and integration tests against a real MySQL database.
- The integration tests use a **separate database** called `smartpay_test_db`. It is created automatically, so your real data in `smartpay_db` is never touched.
- A concurrency test sends many transfers at once to prove the locking works.
- MySQL must be running, and `DB_PASSWORD` and `JWT_SECRET` must be set in the same terminal.

---

## Database

Four tables (see `docs/sql/schema.sql`):

- `users`: name, email, password hash, role (`USER` or `ADMIN`), status (`ACTIVE` or `FROZEN`)
- `wallets`: one wallet per user, with balance and currency (INR)
- `transactions`: every deposit and transfer, with a unique reference id and idempotency key
- `audit_logs`: who froze or unfroze whom, and when

---

## Honest limits and what is not built yet

This project is intentionally small. To be clear about what it is **not**:

- The money is simulated, and any user can add test money to their own wallet. A real system would not allow that.
- Only successful transactions are stored. There are no pending or failed transaction states yet.
- There is no email verification, password reset, or login attempt limit.
- There are no withdrawals, notifications, or admin endpoints to list all users and transactions.

**Planned next (not built yet):** Docker Compose setup, a simple web page frontend, and automated builds (CI).

---

## Tech stack

Java 21, Spring Boot 4.0.8, Spring Web MVC, Spring Data JPA (Hibernate), Spring Security with JWT (OAuth2 resource server), Bean Validation, springdoc OpenAPI (Swagger UI), MySQL 8, JUnit 5 and MockMvc, Maven.
