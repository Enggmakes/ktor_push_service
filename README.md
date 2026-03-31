# GreenHarvest Ktor Push Notification Service

This is a lightweight Ktor HTTP server that receives internal webhook requests from the Flutter app and forwards them to mobile devices using Firebase Cloud Messaging (FCM).

## How to Deploy to Render.com

1. **Upload Code to GitHub:** Push this entire `ktor-push-service` folder to a new GitHub repository.
2. **Connect to Render:** Log into Render.com, click **New > Web Service**, and connect your Github Repository.
3. **Choose Environment:** Render should automatically detect the `Dockerfile` and `render.yaml`. Let it use the Docker environment.
4. **Link Firebase Credentials (CRITICAL):**
   - Go to your Firebase Console -> Project Settings -> Service Accounts.
   - Click "Generate new private key". A `.json` file will download (this is your admin SDK key).
   - In the Render Dashboard for your new web service, go to **Environment > Secret Files**.
   - Create a new secret file named `/etc/secrets/firebase-adminsdk.json` and optionally paste the contents of your downloaded JSON key inside.

**Done!** Every time you push an update to your `main` branch, Render will rebuild and deploy the push notification service automatically.

## How it works:
When the Flutter app (or Supabase Edge Function) calls `POST /api/v1/notify`, this script reads the target ID, constructs a Firebase Message targeted to that user's notification "topic", and dispatches it over FCM to reach their phone instantly.
