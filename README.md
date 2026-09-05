# JARVIS Native Voice v0.1

Cost-effective Android voice client for JARVIS.

## What this version does

- Android native SpeechRecognizer for speech-to-text.
- Android TextToSpeech for spoken replies.
- Local intent router handles simple commands without AI API calls.
- AI endpoint is called only for commands that local rules cannot answer.
- AI API URL can be changed from inside the app.
- GitHub Actions builds the APK; Android Studio is not required.

## Current local commands

- Time / সময় / কয়টা বাজে
- Date / তারিখ / আজ কত তারিখ
- Hello / হ্যালো / Hi / JARVIS
- Thanks / ধন্যবাদ
- Stop speaking / চুপ

## AI API request format

The app sends a JSON POST body similar to:

```json
{
  "text": "user message",
  "message": "user message",
  "input": "user message"
}
```

It accepts common response keys such as:

- `reply`
- `response`
- `message`
- `text`
- `answer`
- `output`

If your existing JARVIS backend uses a different endpoint or JSON structure, update
`MainActivity.java` after confirming the real API contract.

## Build APK using GitHub only

1. Upload all files/folders from this ZIP into the root of your GitHub repository.
2. Keep the folder structure exactly as provided.
3. Open your repository on GitHub.
4. Go to **Actions**.
5. Open **Build JARVIS APK**.
6. Click **Run workflow** if it did not start automatically.
7. After it finishes, open the run.
8. Download the artifact named `JARVIS-Native-Voice-v0.1-debug`.
9. Extract the ZIP and install `app-debug.apk` on Android.

## Important

This is a debug APK workflow. It is suitable for testing. A signed release APK/AAB
can be added after the voice/API integration is confirmed.
