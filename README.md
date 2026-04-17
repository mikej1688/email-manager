# Email Manager

A comprehensive multi-account email management system with AI-powered classification, spam detection, and intelligent notifications.

## ⚡ Quick Start

### Prerequisites
- Java 17 or higher
- Node.js 14+ and npm
- Google Cloud account (for Gmail OAuth)

### 1. Setup Gmail OAuth (Required for Gmail accounts)

Follow the **[OAuth Quick Start Guide](./OAUTH_QUICKSTART.md)** to:
1. Get Google OAuth credentials
2. Set environment variables
3. Connect your Gmail account

**Important**: Gmail now requires OAuth 2.0 authentication. See [detailed setup instructions](./GMAIL_OAUTH_SETUP.md).

### 2. Configure Environment Variables

Copy the example file and fill in your credentials:
```bash
cp .env.example .env
```

Edit `.env` with your OAuth credentials from Google Cloud Console:
```
GMAIL_CLIENT_ID=your-client-id.apps.googleusercontent.com
GMAIL_CLIENT_SECRET=your-client-secret
```

### 3. Run the Application

Windows:
```bash
setup.bat          # First time setup
start-backend.bat  # Start backend server
start-frontend.bat # Start frontend (in another terminal)
```

Linux/Mac:
```bash
# Backend
./mvnw spring-boot:run

# Frontend (in another terminal)
cd frontend
npm install
npm start
```

### 4. Connect Your Gmail Account

1. Open http://localhost:3000
2. Go to "Account Management"
3. Click "**+ Connect Gmail (OAuth)**"
4. Enter your Gmail address and authorize with Google

Done! Your emails will start syncing automatically. 🎉

## Features

### 🔐 Secure Authentication
- **OAuth 2.0** for Gmail (required by Google)
- Automatic token refresh
- No password storage needed
- Support for multiple Gmail accounts

### 🔐 Multi-Account Support
- Support for Gmail, Yahoo Mail, Outlook, and generic IMAP/SMTP providers
- OAuth2 authentication for Gmail
- Secure credential storage

### 🤖 Intelligent Classification
- **Hybrid Approach**: Combines rule-based and AI-powered classification
- Automatically categorizes emails by importance (Urgent, High, Normal, Low)
- Smart spam and phishing detection
- Customizable classification rules

### 📅 Smart Management
- Automatic deadline and due date extraction
- Email categorization (Inbox, Important, Social, Promotions, etc.)
- Folder organization and management
- Mark emails by importance and status

### 🔔 Notifications
- Real-time notifications for urgent emails
- Deadline approaching alerts
- Phishing and spam detection warnings
- Daily email summaries

### 📊 Dashboard & Analytics
- Overview of all email accounts
- Unread email counts
- Importance-based filtering
- Account-wise statistics

## Technology Stack

### Backend
- **Java 17** with **Spring Boot 3.2.4**
- **Spring Data JPA** for database operations
- **H2 Database** (embedded, can be switched to PostgreSQL/MySQL)
- **Gmail API** for Gmail integration
- **Jakarta Mail** for IMAP/SMTP support
- **OpenAI API** for AI-powered classification (optional)
- **Spring Security** for authentication
- **Quartz Scheduler** for periodic email synchronization

### Frontend
- **React 18** with React Router
- **Axios** for API calls
- Modern responsive UI design

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Node.js 16+ and npm (for frontend)
- (Optional) OpenAI API key for AI classification
- (Optional) Gmail OAuth credentials for Gmail integration

## Installation & Setup

### 1. Clone or Download the Project

```bash
cd C:\workspace_vscode\email-manager
```

### 2. Backend Setup

#### Configure Application Properties

Edit `src/main/resources/application.properties`:

```properties
# For Gmail OAuth (optional)
gmail.client.id=YOUR_GMAIL_CLIENT_ID
gmail.client.secret=YOUR_GMAIL_CLIENT_SECRET

# For AI classification (optional)
openai.api.key=YOUR_OPENAI_API_KEY
openai.enabled=true

# Database (H2 by default, or configure PostgreSQL/MySQL)
spring.datasource.url=jdbc:h2:file:./data/emailmanager
```

#### Build and Run Backend

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`

### 3. Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Start the development server
npm start
```

The frontend will start on `http://localhost:3000`

## Usage Guide

### Adding Email Accounts

1. Navigate to **Accounts** page
2. Click **+ Add Account**
3. Fill in the account details:
   - **Gmail**: Only email address is needed (OAuth will handle authentication)
   - **Yahoo/Outlook/Others**: Provide IMAP/SMTP settings and password

### Viewing Emails

1. Go to **Emails** page
2. Select an account from the dropdown
3. Use filters to view:
   - All emails
   - Unread only
   - Urgent emails
   - High priority emails

### Managing Notifications

1. Navigate to **Notifications** page
2. View urgent email alerts, phishing warnings, deadline reminders
3. Mark individual or all notifications as read

### Creating Classification Rules

Use the REST API to create custom classification rules:

```bash
POST /api/rules
Content-Type: application/json

{
  "name": "Mark HR Emails as Important",
  "ruleType": "IMPORTANCE",
  "matchCondition": "ANY",
  "conditions": "[{\"field\":\"from\",\"operator\":\"contains\",\"value\":\"hr@company.com\"}]",
  "actions": "[{\"type\":\"set_importance\",\"value\":\"HIGH\"}]",
  "priority": 10,
  "isActive": true
}
```

## API Endpoints

### Email Accounts
- `GET /api/accounts` - Get all accounts
- `GET /api/accounts/active` - Get active accounts
- `POST /api/accounts` - Add new account
- `PUT /api/accounts/{id}` - Update account
- `DELETE /api/accounts/{id}` - Delete account
- `POST /api/accounts/{id}/sync` - Sync account
- `POST /api/accounts/sync-all` - Sync all accounts

### Emails
- `GET /api/emails` - Get all emails (paginated)
- `GET /api/emails/account/{accountId}` - Get emails by account
- `GET /api/emails/account/{accountId}/unread` - Get unread emails
- `GET /api/emails/account/{accountId}/importance/{level}` - Get emails by importance
- `PUT /api/emails/{id}/mark-read` - Mark email as read
- `PUT /api/emails/{id}/star` - Star email

### Notifications
- `GET /api/notifications` - Get all notifications
- `GET /api/notifications/unread` - Get unread notifications
- `PUT /api/notifications/{id}/mark-read` - Mark notification as read
- `PUT /api/notifications/mark-all-read` - Mark all as read

### Classification Rules
- `GET /api/rules` - Get all rules
- `GET /api/rules/active` - Get active rules
- `POST /api/rules` - Create new rule
- `PUT /api/rules/{id}` - Update rule
- `DELETE /api/rules/{id}` - Delete rule

## Email Synchronization

The system automatically syncs all active email accounts every 5 minutes. You can also trigger manual sync:
- Per account: Use the "Sync" button in Account Management
- All accounts: POST to `/api/accounts/sync-all`

## Classification System

### Rule-Based Classification
Rules can match emails based on:
- **Subject** keywords
- **Sender** email/domain
- **Body** content
- **Recipients**

Actions include:
- Set importance level
- Categorize email
- Mark as spam/phishing
- Move to folder
- Send notification

### AI-Powered Classification
When enabled (requires OpenAI API key):
- Analyzes email content for context and urgency
- Provides intelligent importance scoring
- Generates classification explanations

### Spam & Phishing Detection
- Keyword-based detection
- URL analysis for suspicious links
- Sender domain verification
- Pattern matching for common phishing tactics

## Security Considerations

⚠️ **Important for Production:**
1. Implement proper OAuth2 authentication
2. Encrypt stored passwords (currently stored plaintext for development)
3. Use HTTPS for all communications
4. Configure proper CORS settings
5. Implement rate limiting
6. Regular security audits

## Database

### H2 Console (Development)
Access at: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:file:./data/emailmanager`
- Username: `sa`
- Password: (leave empty)

### Switching to PostgreSQL/MySQL
Update `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/emailmanager
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
```

## Troubleshooting

### Gmail OAuth Issues
1. Enable Gmail API in Google Cloud Console
2. Create OAuth 2.0 credentials
3. Add authorized redirect URI: `http://localhost:8080/oauth2/callback`

### Email Not Syncing
- Check account status in Account Management
- Verify IMAP/SMTP settings
- Check logs for authentication errors

### Frontend Not Loading
- Ensure backend is running on port 8080
- Check proxy settings in `frontend/package.json`
- Clear browser cache

## Future Enhancements

- [ ] Email search functionality
- [ ] Attachment handling
- [ ] Email templates
- [ ] Advanced analytics and reporting
- [ ] Mobile application
- [ ] Email threading/conversation view
- [ ] Calendar integration for deadlines
- [ ] Two-factor authentication
- [ ] Multi-language support

## License

This project is for educational/personal use.

## Support

For issues and questions, please check the logs in `logs/email-manager.log`

---

**Built with ❤️ using Spring Boot and React**
