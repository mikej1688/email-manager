# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

**Backend (Maven / Spring Boot):**
```bash
mvn clean install             # Build
mvn spring-boot:run           # Run on http://localhost:8080
mvn test                      # Run all tests
mvn test -Dtest=ClassName     # Run a single test class
```

**Frontend (React):**
```bash
cd frontend && npm install    # Install dependencies
cd frontend && npm start      # Run on http://localhost:3000 (proxies API to :8080)
cd frontend && npm test       # Run frontend tests
```

**Windows convenience scripts** (project root): `setup.bat`, `start-backend.bat`, `start-frontend.bat`, `start-with-oauth.bat`

**H2 Console (dev):** http://localhost:8080/h2-console — JDBC URL `jdbc:h2:file:./data/emailmanager`, user `sa`, no password.

## Architecture

Layered Spring Boot + React app. Backend is `src/main/java/com/emailmanager/`, frontend is `frontend/`.

### Backend layers

| Layer | Package | Role |
|---|---|---|
| Controllers | `controller/` | REST endpoints; no business logic |
| Services | `service/` | Business logic + scheduling |
| Repositories | `repository/` | Spring Data JPA interfaces |
| Entities | `entity/` | JPA domain models |
| Config | `config/` | Security, scheduler, encryption |

### Email provider abstraction

`service/email/EmailProviderService` is the strategy interface. Two implementations:
- `GmailService` — Gmail REST API + OAuth 2.0 token management
- `ImapEmailService` — Generic IMAP/SMTP for Yahoo, Outlook, and custom servers

`EmailSyncService` calls the appropriate provider and runs on a 5-minute Quartz/`@Scheduled` interval with a 5-thread pool.

### Classification pipeline

Incoming emails pass through two classifiers in sequence:
1. `RuleBasedClassificationService` — evaluates `ClassificationRule` entities (conditions + actions stored as JSON columns)
2. `AIClassificationService` — optional OpenAI call; only runs when enabled in config

`EmailClassificationService` orchestrates both and resolves conflicts. `SpamDetectionService` and `DueDateExtractionService` run as separate passes.

### Encryption

Sensitive `Email` columns (`subject`, `fromAddress`, `toAddresses`, `bodyPlainText`, `bodyHtml`) are encrypted at rest using `config/AttributeEncryptor` (AES-256-GCM JPA `@Converter`). **Because these fields are encrypted, JPQL/SQL `LIKE` queries won't work on them** — search is done by decrypting in memory after fetching.

### Key entities

- `Email` — 150+ columns; indexed on `account_id`, `receivedDate`, `isRead`, `importance`
- `EmailAccount` — multi-provider credentials; stores OAuth tokens and encrypted IMAP passwords; includes auto-refresh logic
- `EmailFolder` — per-account custom folders
- `ClassificationRule` — flexible rule engine; conditions/actions are JSON
- `Notification` — alert system for urgent/phishing/deadline events

### Schema management

`spring.jpa.hibernate.ddl-auto=update` — Hibernate manages schema automatically. No migration scripts.

## Configuration

`src/main/resources/application.properties` is the primary config. Notable settings:

- Gmail OAuth credentials come from environment variables (see `.env`); use `start-with-oauth.bat` to load them
- Email sync fetch limit: 50 emails per cycle
- CORS allowed origins: `localhost:3000`, `localhost:8080`
- Logs: `./logs/email-manager.log`
- Data: `./data/emailmanager` (H2 file-based persistence)

OAuth setup docs: `OAUTH_QUICKSTART.md` (3-step quick start), `GMAIL_OAUTH_SETUP.md` (full Google Cloud Console walkthrough).
