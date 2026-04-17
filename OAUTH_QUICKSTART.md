# Email Manager - OAuth 2.0 Quick Start

## What Changed?

The application now uses **OAuth 2.0** for Gmail authentication instead of username/password. This is more secure and is required by Google.

## What You Need to Do

### 1. Get Google OAuth Credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project (or select existing)
3. Enable Gmail API
4. Create OAuth 2.0 credentials
5. Set redirect URI to: `http://localhost:8080/api/oauth/callback`

**Detailed instructions**: See [GMAIL_OAUTH_SETUP.md](./GMAIL_OAUTH_SETUP.md)

### 2. Set Environment Variables

Before starting the application, set these environment variables with your credentials:

**Windows PowerShell:**
```powershell
$env:GMAIL_CLIENT_ID="your-client-id.apps.googleusercontent.com"
$env:GMAIL_CLIENT_SECRET="your-client-secret"
```

**Windows CMD:**
```cmd
set GMAIL_CLIENT_ID=your-client-id.apps.googleusercontent.com
set GMAIL_CLIENT_SECRET=your-client-secret
```

**Linux/Mac:**
```bash
export GMAIL_CLIENT_ID="your-client-id.apps.googleusercontent.com"
export GMAIL_CLIENT_SECRET="your-client-secret"
```

### 3. Start the Application

```bash
# Start backend
./start-backend.bat

# Start frontend (in another terminal)
./start-frontend.bat
```

### 4. Connect Your Gmail Account

1. Open http://localhost:3000
2. Go to "Account Management"
3. Click "**+ Connect Gmail (OAuth)**"
4. Enter your Gmail address
5. Click "**Authorize with Google**"
6. You'll be redirected to Google
7. Grant the requested permissions
8. You'll be redirected back - Done! ✅

## Troubleshooting

### Error: "Request is missing required authentication credential"

This means OAuth credentials are not configured. Make sure:
- ✅ Environment variables are set
- ✅ Backend server was restarted after setting variables
- ✅ Gmail API is enabled in Google Cloud Console

### Error: "redirect_uri_mismatch"

The redirect URI doesn't match. Ensure in Google Cloud Console:
- Redirect URI is exactly: `http://localhost:8080/api/oauth/callback`
- No trailing slash
- Exact port number

### Still Having Issues?

Check the detailed guide: [GMAIL_OAUTH_SETUP.md](./GMAIL_OAUTH_SETUP.md)

## Why OAuth 2.0?

- 🔒 **More Secure**: No need to store passwords
- 🔑 **Token-based**: Uses temporary access tokens
- 🔄 **Auto-refresh**: Tokens refresh automatically
- ✅ **Google Required**: Gmail API requires OAuth 2.0
- 🚫 **No More App Passwords**: Simpler than managing app-specific passwords
