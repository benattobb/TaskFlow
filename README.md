# TaskFlow

An Android/Pixel task-capture app: type a task naturally, parse it on device, then create both a Google Task and (when dated) a matching Google Calendar event. It includes capture, voice, focus, and compact **Type a task** home-screen widgets, plus a user-started active-focus notification that supported Pixel/Android system surfaces can promote.

## Cost and privacy

Natural-language extraction is local and has no API key, subscription, or per-use cost. It understands `today`, `tomorrow`, `day after tomorrow`, `this weekend`, weekdays, dates such as `12 Sep`, numeric UK-style dates such as `12/09`, times such as `3pm` or `3:30 pm`, and common repeats such as `every day at 9pm`. It does not send task text to an AI service. Google is contacted only after **Add and sync task** is tapped.

This is intentionally local language intelligence rather than a cloud LLM: a genuinely open-ended AI model cannot be included at zero ongoing cost without shipping a downloaded model and supporting only capable devices.

## Install TaskFlow

Download the current **TaskFlow APK** from the repository's [Releases page](https://github.com/benattobb/TaskFlow/releases), open it on an Android phone, and choose **Install**. Android may ask you to allow installs from the browser or file manager you used to download it.

There are no API keys to create or paste. On first use, tap **Connect Google** and approve the Google Tasks and Calendar permissions for the account you want to use. Your task text remains on your phone until you choose to sync it.

## Maintainer Google setup

1. Create a Google Cloud project and enable **Google Tasks API** and **Google Calendar API**.
2. Configure the OAuth consent screen and add the Tasks and Calendar scopes requested by the app.
3. Create Android OAuth clients for `com.example.taskflow`: one for development and one for the public release signing certificate. The user never needs either key or client ID.
4. Open this folder in Android Studio, allow Gradle to sync, and run on a Pixel or Android device.

The app requests the narrowest Google scopes available for creating Tasks and Calendar events. It does not read Calendar data. It checks up to 100 open Google Tasks that it previously created only to make a retry safe and avoid duplicate tasks.

## Widgets and Pixel At a Glance

Add widgets from the Pixel launcher’s **Widgets** picker:

- **TaskFlow quick capture** — type or speak from a medium card.
- **TaskFlow type a task** — a compact 2×1 white card that opens a focused typing surface and keyboard.
- **TaskFlow voice capture** — hands-free capture and an editable confirmation notification.
- **TaskFlow focus** — current focus task with suspend/resume controls.

TaskFlow cannot add its own card directly to Pixel’s private **At a Glance** surface. It creates standard Google Calendar events, which are eligible for At a Glance’s **Upcoming** category when that category is enabled. Google decides when an event is timely enough to surface.

## Pixel live surface

Tap **Start focus** only when beginning an active task. On Android 16+ it explicitly requests promotion as a Live Update; the system and the user's notification settings decide whether it is displayed on Pixel live surfaces. The persistent list/capture experience lives in the home-screen widget.

## Build from source

```bash
./gradlew lint testDebugUnitTest assembleDebug
```

The resulting debug APK is at `app/build/outputs/apk/debug/app-debug.apk`. Maintainers can provide `release-signing.properties` locally to sign a release; it is intentionally ignored by Git. The public APK is published on GitHub Releases, so normal users do not need Android Studio, API keys, or a Google Cloud project.

## Security and privacy

- Google OAuth tokens are obtained on demand from Google Play services and are never written to app storage or logs.
- All Google API calls are HTTPS-only, have connection/read timeouts, reject redirects, and only target the official Tasks and Calendar hosts.
- Calendar event IDs are deterministic per TaskFlow-created Google Task, so retrying a request does not create a second Calendar event.
- App widgets and internal capture activities are not exported to other apps. Pending intents are immutable.
- Focus-task text stays in app-private storage. Android backups of TaskFlow data and clear-text network traffic are disabled.

See [SECURITY.md](SECURITY.md) for responsible disclosure guidance.
