# Gmail OAuth 2.0 Setup Guide

This guide explains how to set up Gmail OAuth 2.0 authentication for the Email Manager application.

## Prerequisites

- A Google account
- Access to Google Cloud Console
- Your application running on `http://localhost:8080` (backend) and `http://localhost:3000` (frontend)

## Step 1: Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click on "Select a project" → "New Project"
3. Name your project (e.g., "Email Manager")
4. Click "Create"

## Step 2: Enable Gmail API

1. In your project dashboard, go to "APIs & Services" → "Library"
2. Search for "Gmail API"
3. Click on "Gmail API" and click "Enable"

## Step 3: Configure OAuth Consent Screen

1. Go to "APIs & Services" → "OAuth consent screen"
2. Select "External" user type (for testing) and click "Create"
3. Fill in the required fields:
   - **App name**: Email Manager
   - **User support email**: Your email
   - **Developer contact information**: Your email
4. Click "Save and Continue"
5. On the "Scopes" page, click "Add or Remove Scopes"
   - Add the scope: `https://www.googleapis.com/auth/gmail.modify` (or `gmail.readonly` for read-only access)
   - Recommended: Add `https://mail.google.com/` for full Gmail access
6. Click "Update" and then "Save and Continue"
7. On the "Test users" page:
   - Click "Add Users"
   - Add your Gmail address that you want to test with
   - Click "Save and Continue"
8. Review and click "Back to Dashboard"

## Step 4: Create OAuth 2.0 Credentials

1. Go to "APIs & Services" → "Credentials"
2. Click "Create Credentials" → "OAuth client ID"
3. Select application type: "Web application"
4. Name it: "Email Manager Web Client"
5. Under "Authorized redirect URIs", add:
   ```
   http://localhost:8080/api/oauth/callback
   ```
6. Click "Create"
7. **IMPORTANT**: Copy the **Client ID** and **Client Secret** - you'll need these!

## Step 5: Configure Application

### Option A: Environment Variables (Recommended)

Create a `.env` file in your project root or set environment variables:

```bash
# Windows PowerShell
$env:GMAIL_CLIENT_ID="your-client-id-here.apps.googleusercontent.com"
$env:GMAIL_CLIENT_SECRET="your-client-secret-here"
$env:GMAIL_REDIRECT_URI="http://localhost:8080/api/oauth/callback"

# Windows CMD
set GMAIL_CLIENT_ID=your-client-id-here.apps.googleusercontent.com
set GMAIL_CLIENT_SECRET=your-client-secret-here
set GMAIL_REDIRECT_URI=http://localhost:8080/api/oauth/callback

# Linux/Mac
export GMAIL_CLIENT_ID="your-client-id-here.apps.googleusercontent.com"
export GMAIL_CLIENT_SECRET="your-client-secret-here"
export GMAIL_REDIRECT_URI="http://localhost:8080/api/oauth/callback"
```

### Option B: Update application.properties

Edit `src/main/resources/application.properties`:

```properties
gmail.client.id=your-client-id-here.apps.googleusercontent.com
gmail.client.secret=your-client-secret-here
gmail.redirect.uri=http://localhost:8080/api/oauth/callback
```

**⚠️ WARNING**: Never commit credentials to version control! Add `application.properties` to `.gitignore` if using this method.

## Step 6: Test OAuth Flow

### Backend Test

1. Start your backend server:
   ```bash
   ./start-backend.bat
   ```

2. Test the OAuth status endpoint:
   ```bash
   curl http://localhost:8080/api/oauth/status
   ```

### Frontend Integration

1. Start your frontend:
   ```bash
   ./start-frontend.bat
   ```

2. In the Account Management page:
   - Enter your Gmail address
   - Click "Connect Gmail Account"
   - You'll be redirected to Google's authorization page
   - Grant the requested permissions
   - You'll be redirected back to the application
   - Your account should now be connected!

## Step 7: Verify Connection

1. Go to the Account Management page
2. You should see your Gmail account listed with status "Active"
3. Click "Test Connection" to verify
4. Check the backend logs for successful authentication

## OAuth Flow Endpoints

The following endpoints are now available:

- **Start OAuth**: `GET /api/oauth/gmail/authorize?email=your@gmail.com`
  - Returns the Google authorization URL
  - Redirect user to this URL

- **OAuth Callback**: `GET /api/oauth/callback?code=xxx&state=email`
  - Handled automatically by Google after user authorization
  - Exchanges code for access/refresh tokens
  - Redirects to frontend with success/error status

- **Check Status**: `GET /api/oauth/status`
  - Returns OAuth configuration status

## Troubleshooting

### Error: "redirect_uri_mismatch"
- Ensure the redirect URI in Google Cloud Console exactly matches: `http://localhost:8080/api/oauth/callback`
- No trailing slashes
- Exact protocol (http vs https)
- Exact port number

### Error: "invalid_client"
- Check that `GMAIL_CLIENT_ID` and `GMAIL_CLIENT_SECRET` are correctly set
- Verify credentials in Google Cloud Console

### Error: "access_denied"
- User denied permission
- Check that your email is added as a test user in OAuth consent screen

### Error: "Token expired"
- The application will automatically refresh tokens
- If refresh fails, re-authenticate through the OAuth flow

### Error: "Request is missing required authentication credential"
- Your access token is invalid or expired
- Click "Reconnect" to re-authenticate
- Check that refresh token is stored in the database

## Production Deployment

For production deployment:

1. **Use HTTPS**: Update redirect URI to use `https://`
2. **Publish OAuth App**: In Google Cloud Console, publish your OAuth consent screen for production
3. **Secure Credentials**: Use secure secrets management (Azure Key Vault, AWS Secrets Manager, etc.)
4. **Database Encryption**: Encrypt tokens in the database
5. **Update Redirect URI**: Change to your production domain

Example production configuration:
```properties
gmail.redirect.uri=https://yourdomain.com/api/oauth/callback
```

## Security Best Practices

1. ✅ **Never commit credentials** to version control
2. ✅ **Use environment variables** for sensitive data
3. ✅ **Enable HTTPS** in production
4. ✅ **Rotate credentials** regularly
5. ✅ **Implement token encryption** in database
6. ✅ **Add rate limiting** to OAuth endpoints
7. ✅ **Monitor OAuth logs** for suspicious activity
8. ✅ **Implement token revocation** when users disconnect accounts

## Additional Resources

- [Google OAuth 2.0 Documentation](https://developers.google.com/identity/protocols/oauth2)
- [Gmail API Documentation](https://developers.google.com/gmail/api)
- [OAuth 2.0 Scopes](https://developers.google.com/gmail/api/auth/scopes)
