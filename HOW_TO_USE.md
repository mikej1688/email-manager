# How to Use Email Manager - Complete Guide

## 📋 Table of Contents
1. [Introduction](#introduction)
2. [System Requirements](#system-requirements)
3. [Initial Setup](#initial-setup)
4. [Starting the Application](#starting-the-application)
5. [First-Time Configuration](#first-time-configuration)
6. [Using the Application](#using-the-application)
7. [Advanced Features](#advanced-features)
8. [Common Tasks](#common-tasks)
9. [Troubleshooting](#troubleshooting)

---

## Introduction

Email Manager is an intelligent multi-account email management system that helps you:
- Manage multiple email accounts from one interface
- Automatically classify and prioritize emails
- Detect spam and phishing attempts
- Extract due dates and deadlines
- Receive smart notifications
- Organize emails efficiently

---

## System Requirements

Before you begin, ensure you have:

### Required:
- **Windows OS** (Windows 10 or later)
- **Java 17 or higher** - [Download here](https://adoptium.net/)
- **Maven 3.6+** - [Download here](https://maven.apache.org/download.cgi)
- **Node.js 16+** - [Download here](https://nodejs.org/)
- **npm** (comes with Node.js)

### Optional:
- **OpenAI API Key** - For AI-powered email classification
- **Gmail OAuth Credentials** - For Gmail integration

### Verify Installation:
Open PowerShell or Command Prompt and run:
```powershell
java -version    # Should show Java 17 or higher
mvn -version     # Should show Maven 3.6+
node -version    # Should show Node 16+
npm -version     # Should show npm version
```

---

## Initial Setup

### Step 1: Navigate to Project Directory

Open PowerShell or Command Prompt:
```powershell
cd C:\workspace_vscode\email-manager
```

### Step 2: Run Automated Setup

Run the setup script (recommended for first-time setup):
```powershell
setup.bat
```

This script will:
- Build the backend application
- Install frontend dependencies
- Display startup instructions

**OR** do manual setup:

#### Manual Backend Setup:
```powershell
mvn clean install
```

#### Manual Frontend Setup:
```powershell
cd frontend
npm install
cd ..
```

---

## Starting the Application

You need to start both the backend and frontend servers.

### Option 1: Using Batch Files (Easiest)

**Terminal 1 - Start Backend:**
```powershell
start-backend.bat
```

**Terminal 2 - Start Frontend:**
```powershell
start-frontend.bat
```

### Option 2: Manual Start

**Terminal 1 - Start Backend:**
```powershell
mvn spring-boot:run
```
Wait for message: `Started EmailManagerApplication in X seconds`

**Terminal 2 - Start Frontend:**
```powershell
cd frontend
npm start
```

### Access the Application

Once both servers are running:
- **Frontend UI**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **H2 Database Console**: http://localhost:8080/h2-console

**Default Login Credentials:**
- Username: `admin`
- Password: `admin123`

---

## First-Time Configuration

### 1. Access the Application

Open your web browser and go to: **http://localhost:3000**

You should see the Email Manager dashboard with:
- Dashboard
- Emails
- Accounts
- Notifications (navigation tabs)

### 2. Configure Your First Email Account

Since you don't have any accounts configured initially:

1. **Click on "Accounts"** tab in the navigation bar
2. **Click "+ Add Account"** button
3. **Fill in the form:**

#### For Gmail:
- **Email Address**: your.email@gmail.com
- **Display Name**: My Gmail Account
- **Provider**: Select "GMAIL" from dropdown
- **Password**: Your Gmail app password (see [Gmail Setup Guide](#gmail-setup) below)

**Important**: For Gmail, you need to use an "App Password" instead of your regular password.

#### For Other Providers (Yahoo, Outlook, Generic IMAP):
- **Email Address**: your.email@provider.com
- **Display Name**: My Email Account
- **Provider**: Select appropriate provider
- **IMAP Server**: e.g., imap.gmail.com, imap.mail.yahoo.com, outlook.office365.com
- **IMAP Port**: Usually 993 (for SSL/TLS)
- **SMTP Server**: e.g., smtp.gmail.com
- **SMTP Port**: Usually 587 (for TLS) or 465 (for SSL)
- **Password**: Your email password or app-specific password

4. **Click "Add Account"** button

### 3. Sync Your Emails

After adding an account:
1. Find your account in the list
2. Click the **"Sync"** button next to it
3. Wait for synchronization to complete
4. You'll see a success message when done

**Note**: Initial sync may take a few minutes depending on your email volume.

---

## Using the Application

### Dashboard View

The Dashboard provides an overview of your email management system:

1. **Statistics Cards:**
   - **Email Accounts**: Total number of configured accounts
   - **Unread Emails**: Count of unread emails across all accounts
   - **Urgent Emails**: Count of emails marked as urgent

2. **Active Accounts List:**
   - Shows all configured email accounts
   - Displays account name, email address, and provider

**What to do:**
- Check your statistics at a glance
- Monitor unread and urgent emails
- Verify all accounts are active

### Emails View

The Emails view shows your emails from all accounts:

1. **Filter Options:**
   - Filter by account
   - Filter by importance (Urgent, High, Normal, Low)
   - Filter by read/unread status
   - Search emails

2. **Email List:**
   - See sender, subject, date
   - View importance badges
   - Check read/unread status
   - Classification category

3. **Email Actions:**
   - Click on an email to read it
   - Mark as read/unread
   - Delete emails
   - Change importance level
   - Move to different folders

**Common Actions:**
- **View all emails**: Go to Emails tab
- **Filter by urgency**: Use importance filter dropdown
- **Search**: Use search box to find specific emails
- **Mark as read**: Click on unread email

### Account Management

Manage all your email accounts in one place:

1. **View All Accounts:**
   - See all configured email accounts
   - Check account status and provider

2. **Add New Account:**
   - Click "+ Add Account"
   - Fill in credentials
   - Submit form

3. **Sync Account:**
   - Click "Sync" button for an account
   - Fetches latest emails from server
   - Updates email database

4. **Delete Account:**
   - Click "Delete" button
   - Confirm deletion
   - All associated emails will be removed

**Best Practices:**
- Sync accounts regularly (or enable auto-sync)
- Use descriptive display names
- Keep credentials secure

### Notifications

Stay informed about important emails:

1. **Notification Types:**
   - **Urgent Email Alerts**: When high-priority emails arrive
   - **Deadline Alerts**: When emails contain upcoming due dates
   - **Spam/Phishing Warnings**: Security alerts
   - **Daily Summaries**: Overview of daily email activity

2. **View Notifications:**
   - Click "Notifications" tab
   - See unread notification count in badge
   - Read notification details

3. **Manage Notifications:**
   - Mark as read
   - Dismiss notifications
   - Click to view related email

**Tips:**
- Check notifications regularly
- Act on urgent notifications promptly
- Review spam warnings carefully

---

## Advanced Features

### 1. AI-Powered Email Classification

Enable AI to automatically classify your emails intelligently.

#### Setup:

1. **Get OpenAI API Key:**
   - Sign up at https://platform.openai.com/
   - Generate an API key
   - Copy the key

2. **Configure Application:**
   - Stop the backend server (Ctrl+C in backend terminal)
   - Edit `src/main/resources/application.properties`
   - Update these lines:
     ```properties
     openai.api.key=your-openai-api-key-here
     openai.enabled=true
     ```
   - Save the file
   - Restart backend: `mvn spring-boot:run`

#### How It Works:
- AI analyzes email content
- Determines importance level
- Classifies into categories
- Works alongside rule-based classification

### 2. Gmail OAuth Integration

Use OAuth instead of app passwords for Gmail (more secure).

#### Setup:

1. **Create Google Cloud Project:**
   - Go to https://console.cloud.google.com/
   - Create a new project
   - Enable Gmail API

2. **Create OAuth Credentials:**
   - Go to Credentials section
   - Create OAuth 2.0 Client ID
   - Add redirect URI: `http://localhost:8080/oauth2/callback/google`
   - Download credentials JSON

3. **Configure Application:**
   - Edit `src/main/resources/application.properties`
   - Update:
     ```properties
     gmail.client.id=your-client-id
     gmail.client.secret=your-client-secret
     ```
   - Restart backend

4. **Authenticate:**
   - Add Gmail account through UI
   - Follow OAuth flow
   - Grant permissions

### 3. Custom Classification Rules

Create custom rules for automatic email classification.

#### Using SQL (Advanced):

1. **Access H2 Console:**
   - Go to http://localhost:8080/h2-console
   - JDBC URL: `jdbc:h2:file:./data/emailmanager`
   - Username: `sa`
   - Password: (leave empty)

2. **Add Classification Rules:**
   ```sql
   INSERT INTO classification_rule (name, condition_field, condition_operator, condition_value, action_type, action_value, priority, enabled) 
   VALUES ('Mark Boss Emails as Urgent', 'SENDER', 'CONTAINS', 'boss@company.com', 'SET_IMPORTANCE', 'URGENT', 1, true);
   ```

3. **Example Rules:**
   ```sql
   -- Mark emails from specific domain as important
   INSERT INTO classification_rule (name, condition_field, condition_operator, condition_value, action_type, action_value, priority, enabled) 
   VALUES ('Company Emails', 'SENDER', 'ENDS_WITH', '@mycompany.com', 'SET_IMPORTANCE', 'HIGH', 2, true);
   
   -- Move newsletters to promotions
   INSERT INTO classification_rule (name, condition_field, condition_operator, condition_value, action_type, action_value, priority, enabled) 
   VALUES ('Newsletters', 'SUBJECT', 'CONTAINS', 'newsletter', 'MOVE_TO_FOLDER', 'PROMOTIONS', 3, true);
   ```

### 4. Configure Automatic Email Sync

Set up automatic periodic synchronization.

Edit `src/main/resources/application.properties`:
```properties
# Sync every 5 minutes (default)
email.sync.interval.minutes=5

# Fetch up to 50 emails per sync
email.sync.fetch.limit=50
```

**Note**: Restart backend after changing configuration.

### 5. Spam and Phishing Detection

Automatic spam detection is enabled by default and uses:
- Sender reputation analysis
- Content pattern matching
- Link analysis
- Keyword detection

Emails detected as spam will be:
- Marked with spam badge
- Moved to spam folder
- Trigger a notification

---

## Common Tasks

### Task 1: Add a New Gmail Account

1. **Enable 2-Factor Authentication** on your Google account
2. **Generate App Password:**
   - Go to https://myaccount.google.com/security
   - Select "2-Step Verification"
   - Scroll to "App passwords"
   - Generate password for "Mail" app
   - Copy the 16-character password
3. **In Email Manager:**
   - Go to Accounts tab
   - Click "+ Add Account"
   - Email: your.email@gmail.com
   - Provider: GMAIL
   - Password: Paste app password (no spaces)
   - Click "Add Account"
4. **Sync emails**: Click "Sync" button

### Task 2: Check Urgent Emails Daily

1. Open http://localhost:3000
2. Look at Dashboard → "Urgent Emails" count
3. Click "Emails" tab
4. Filter by Importance → Select "Urgent"
5. Review and respond to urgent emails

### Task 3: Find Emails with Deadlines

1. Go to Notifications tab
2. Look for "Deadline Alert" notifications
3. Click on notification to view email
4. The due date is automatically extracted and displayed
5. Take action before deadline

### Task 4: Clean Up Spam Emails

1. Go to Emails tab
2. Filter by Folder → Select "Spam"
3. Review emails marked as spam
4. Delete confirmed spam
5. If legitimate, mark as "Not Spam" (moves back to inbox)

### Task 5: Export Email Data

Use H2 Console to export data:

1. Go to http://localhost:8080/h2-console
2. Login with credentials
3. Run query: `SELECT * FROM email`
4. Click "Export" button
5. Choose format (CSV, JSON, etc.)

---

## Troubleshooting

### Problem: Backend won't start

**Error**: "Port 8080 already in use"
- **Solution**: 
  ```powershell
  # Find process using port 8080
  netstat -ano | findstr :8080
  # Kill the process
  taskkill /PID <process-id> /F
  ```

**Error**: "Java version not supported"
- **Solution**: Install Java 17 or higher from https://adoptium.net/

### Problem: Cannot add Gmail account

**Error**: "Authentication failed"
- **Solution**: 
  - Use App Password, not regular password
  - Ensure 2FA is enabled
  - Check password has no spaces
  - Verify email address is correct

### Problem: Emails not syncing

**Symptoms**: Sync button doesn't fetch new emails
- **Solutions**:
  1. Check account credentials are correct
  2. Verify IMAP server settings
  3. Check backend logs: `logs/email-manager.log`
  4. Ensure internet connection is active
  5. Try deleting and re-adding account

### Problem: Frontend shows "Cannot connect to backend"

**Error**: Network error or 404
- **Solutions**:
  1. Verify backend is running on port 8080
  2. Check backend terminal for errors
  3. Visit http://localhost:8080/api/accounts
  4. Restart both frontend and backend

### Problem: AI classification not working

**Symptoms**: All emails show same importance
- **Solutions**:
  1. Verify OpenAI API key is correct
  2. Check `openai.enabled=true` in config
  3. Restart backend after config changes
  4. Check OpenAI account has credits
  5. Review logs for API errors

### Problem: Database errors

**Error**: "Table not found" or database corruption
- **Solutions**:
  1. Stop backend
  2. Delete `data/` folder
  3. Restart backend (recreates database)
  4. Re-add email accounts
  5. Re-sync emails

### Getting Help

Check log files for detailed error messages:
- Backend logs: `logs/email-manager.log`
- Frontend logs: Browser console (F12 → Console tab)

---

## Configuration Reference

### Common Configuration Options

Edit `src/main/resources/application.properties`:

```properties
# Server Port
server.port=8080

# Database Location
spring.datasource.url=jdbc:h2:file:./data/emailmanager

# Security Credentials
spring.security.user.name=admin
spring.security.user.password=admin123

# Email Sync Settings
email.sync.interval.minutes=5
email.sync.fetch.limit=50

# OpenAI (optional)
openai.api.key=sk-...
openai.enabled=true

# Gmail OAuth (optional)
gmail.client.id=your-client-id
gmail.client.secret=your-client-secret

# Logging Level
logging.level.com.emailmanager=DEBUG
```

### Email Provider Settings

Common IMAP/SMTP settings:

**Gmail:**
- IMAP: imap.gmail.com:993
- SMTP: smtp.gmail.com:587

**Yahoo:**
- IMAP: imap.mail.yahoo.com:993
- SMTP: smtp.mail.yahoo.com:587

**Outlook/Hotmail:**
- IMAP: outlook.office365.com:993
- SMTP: smtp.office365.com:587

---

## Best Practices

1. **Security:**
   - Use app-specific passwords
   - Don't share credentials
   - Change default admin password
   - Enable OAuth when possible

2. **Email Management:**
   - Set up classification rules early
   - Sync regularly
   - Review spam folder periodically
   - Act on urgent notifications promptly

3. **Performance:**
   - Limit sync fetch size for large mailboxes
   - Increase sync interval if not needed frequently
   - Archive old emails regularly
   - Monitor disk space (database grows with emails)

4. **Maintenance:**
   - Check logs occasionally
   - Backup database folder (`data/`)
   - Keep application updated
   - Review and update classification rules

---

## Quick Reference Card

### Startup Commands
```powershell
# Backend
mvn spring-boot:run

# Frontend  
cd frontend && npm start
```

### Access URLs
- Frontend: http://localhost:3000
- Backend: http://localhost:8080
- Database: http://localhost:8080/h2-console

### Default Credentials
- Username: `admin`
- Password: `admin123`

### Key Shortcuts
- Dashboard: Home page overview
- Emails: View and manage emails
- Accounts: Add/remove accounts
- Notifications: View alerts

---

## Next Steps

Now that you know how to use Email Manager:

1. ✅ Add your email accounts
2. ✅ Sync your emails
3. ✅ Set up classification rules
4. ✅ Enable AI classification (optional)
5. ✅ Configure notifications
6. ✅ Customize settings to your needs

**Happy Email Managing! 📧**

---

*Last Updated: April 15, 2026*
*Version: 1.0.0*
