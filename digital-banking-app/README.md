# Digital Banking & Money Transfer (learning demo)

A Java 17 + Jakarta Servlet + JDBC + MySQL + Bootstrap starter for **simulated** transfers. It does not connect to real banks or move real money.

## Requirements
- JDK 17+
- Maven 3.9+
- MySQL Server + MySQL Workbench
- Apache Tomcat 10.1.x (Jakarta Servlet 6)
- VS Code with Extension Pack for Java (Maven support is useful)

## 1. Create the database
1. Open MySQL Workbench and connect to your local MySQL server.
2. Open `database/schema.sql` and execute it.
3. The app creates a zero-balance account when a new customer registers. Do not use real customer data.

## 2. Configure database credentials
Set environment variables before launching VS Code / its terminal.

Windows PowerShell:
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/digital_banking?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USER="root"
$env:DB_PASSWORD="YOUR_MYSQL_PASSWORD"
```
Replace `YOUR_MYSQL_PASSWORD`. The code has a deliberately unusable local default password so credentials are not silently assumed.

## 3. Build
From this folder:
```powershell
mvn clean package
```
Output: `target/digital-banking.war`

## 4. Deploy to Tomcat 10.1
- Copy `target/digital-banking.war` to Tomcat's `webapps` folder.
- Start Tomcat.
- Open `http://localhost:8080/digital-banking/`
- Use Register to create a test user, then sign in.

If Tomcat is installed elsewhere, use its own startup script. This project uses `jakarta.servlet.*`; it is not compatible with Tomcat 9 without changing dependencies/imports.

## Routes
- `GET /` or `/login` — login page
- `GET/POST /register` — create customer and account
- `GET/POST /login` — authenticate
- `POST /logout` — sign out
- `GET /dashboard` — account balance
- `GET/POST /transfer` — transfer to another app account number
- `GET /transactions` — transaction history

## Demo transfer flow
Register two test users in separate sessions/browser profiles. The dashboard shows each account number. Transfer a positive amount to the other account number. The transfer transaction locks both account rows, validates balance and limit, updates both balances, writes debit/credit ledger entries and records the transfer in one JDBC transaction.

## Security and demo limitations
- Passwords are BCrypt-hashed.
- Session ID is rotated after login.
- POST forms use a session CSRF token.
- SQL uses prepared statements.
- Transfers are atomic and lock accounts in sorted ID order to reduce deadlocks.
- Idempotency keys are unique in the database. The form generates a key per submission; a browser retry with the same key is rejected instead of being processed twice.
- This is an educational starter, not production banking software. Before real deployment, add HTTPS, rate limiting, account lockout, email/OTP verification, secure secrets management, proper audit/alerting, reconciliation, operational monitoring, and security review.
