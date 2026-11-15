# Cloud backup architecture

## Current implementation: Storage Access Framework

IronPulse uses Android's Storage Access Framework (SAF) to write backups to a folder the
athlete chooses.

1. **Choose folder** launches `ActivityResultContracts.OpenDocumentTree()`
   (`ACTION_OPEN_DOCUMENT_TREE`).
2. Android's DocumentsUI shows the installed document providers, including Google Drive and
   OneDrive when their apps/providers are available.
3. The selected tree URI is kept with
   `ContentResolver.takePersistableUriPermission(READ | WRITE)`.
4. The URI and provider authority are stored in device-local SharedPreferences, outside the
   `.ironpulse` archive.
5. `CloudBackupRunner` exports the existing `.ironpulse` archive to the app cache.
6. The archive is written to a `.partial` document in the chosen tree, read back, size checked,
   SHA-256 compared, then renamed to its final timestamped name.
7. WorkManager runs automatic backups. The worker and the manual action share a mutex, and the
   content resolver handles the actual provider write.

There is no Google Drive API, no OAuth client, and no OAuth token in the current build.

## The false "Google Drive is not installed" message

The earlier UI called:

```kotlin
context.packageManager.getPackageInfo("com.google.android.apps.docs", 0)
```

On Android 11 and later, package visibility filters this query. An app targeting API 30+
cannot see another installed package unless it declares that package in `<queries>`, uses an
intent query that matches a component, or holds `QUERY_ALL_PACKAGES`. The query therefore
threw `NameNotFoundException` on devices where Drive was installed, and the UI inferred that
Drive was unavailable.

Checking for the Drive application package was also the wrong test. Drive access comes from
the Drive document provider appearing in DocumentsUI, not from the Drive launcher app being
visible to IronPulse. The package check was removed. The app now delegates provider selection
to Android and describes the picker neutrally. The picker itself was always correct; the
message was the false negative.

## Why OAuth / Drive API was not selected for this build

The intended experience is automatic backups without asking the athlete to open Drive. The
current supported Android authorization APIs do not make that possible in a serverless app:

- Google recommends `AuthorizationClient` from Google Identity Services for authorization,
  with an Android OAuth client bound to the package name and signing SHA-1.
- `AuthorizationClient.authorize()` returns a one-hour access token. It can be called again in
  later sessions without user interaction while the grant remains, but it is activity-based.
  A WorkManager worker has no Activity to launch the consent resolution.
- Offline access requires `requestOfflineAccess(serverClientId)`, an authorization code, and a
  **server-side** exchange for a refresh token. Google explicitly advises against storing
  long-lived refresh tokens on the device. This task also forbids adding a backend.
- `drive.file` is the least-privilege Drive scope. It grants access to files the app created or
  files the user selected through Google Picker. It cannot list or write an arbitrary existing
  folder the user chooses. `drive.appdata` is hidden app-specific storage, not a visible
  `IronPulse` folder.
- A Google Cloud project, Android OAuth client, consent screen, SHA-1 registration and
  potentially a Web client ID for offline access are not configured in this repository. No
  `google-services.json` or OAuth client ID is present.

SAF already gives the athlete the intended visible-folder experience, supports Google Drive
and other providers, survives restarts, and lets WorkManager write in the background without a
server. Removing the false package check fixes the actual problem on the RedMagic 10 Pro.

## If OAuth is added later

The minimum external setup is:

- A Google Cloud project with the Drive API enabled.
- An Android OAuth client for `com.aistudio.fitness.ironpulse` with the SHA-1 of every signing
  certificate used for testing and release.
- A consent screen configured for `https://www.googleapis.com/auth/drive.file`.
- A Web application client ID if offline access and server-side refresh tokens are required.
- A backend that exchanges the authorization code and stores the refresh token; the app must
  not embed a client secret.

The app side would use `AuthorizationClient` for the foreground connection and the Drive REST
API for file operations. A serverless OAuth version could work while the app is open and the
access token is valid, but it would not meet the current automatic-background requirement.
That is why the shipped architecture remains SAF.

## Test status

- SAF folder selection, persisted permission, local document-provider writes, read-back
  verification, retention, disconnect and failure handling were tested on an Android 16 emulator.
- The 5.0.0 release build was validated end to end: a folder was connected, automatic backups were
  enabled, Back up now produced a verified timestamped archive, and the last-backup row updated.
- Real Google Drive synchronization was not tested because Drive is not installed on the
  emulator. The picker and write path are the same for any document provider; a real-device
  check is still required.
- OAuth was not implemented or tested.
