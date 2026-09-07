# Native Android Integration Verification

Date: 2026-09-07. Workspace: `D:\课表`. Target: Android 15 / API 35 emulator (`emulator-5554`). This is a debug APK delivery, not a signed store release.

## Delivered Behavior

- Kotlin/Compose provides the native timetable, course/exam editors, settings, full-day overrides and import review. Room v2 stores records and dataset metadata; an explicit v1 migration preserves existing data. DataStore stores app preferences.
- `ScheduleResolver` applies actual dates, semester weeks, odd/even rules and explicit week sets for the timetable, reminders, widget and MCP. A date override selects the replacement weekday while retaining the actual date's semester week.
- Reminder lead time defaults to 10 minutes. Exact-alarm access, notification permission, daily renewal, boot/date/time/timezone/package/permission changes and app resume are handled. Delayed, undelivered reminders before class starts are requeued; already delivered reminders are deduplicated.
- The Glance widget reads persisted data and shows the next three occurrences, including date, start time, course name and split location. It can open the native app and refresh without notifications enabled.
- Local OCR offers PP-OCRv6 tiny (6.3 MB) and small (31.2 MB). Downloads begin only through the explicit download action, use private no-backup storage and verify pinned file sizes/SHA-256 before activation. The APK includes inference libraries but no model weights. Vendored official Kotlin sources retain license and revision in `ocr/paddle/UPSTREAM.md`.
- Local OCR and configurable OpenAI Chat Completions image import produce editable drafts. Course name, teacher, weekday, periods, parity, explicit weeks, split location and notes are reviewed before transactional append. Unparsed course text is retained in notes.
- Official MCP Kotlin SDK exposes authenticated stateless Streamable HTTP at `/mcp`. Twelve tools query or edit courses, exams and overrides through the repository. An optional app confirmation queue protects writes; clear always requires confirmation. Tokens are encrypted locally, can be rotated, and are excluded from backup. The service is off by default and uses an Android foreground service when enabled.
- Web/PWA remains available. Full JSON exchange retains native fields and dataset metadata. Web automatic mode applies imported date overrides and explicit/parity weeks; manual week filters remain template views. Native-only fields have limited editing in the legacy Web forms.

## Build and Automated Evidence

Environment: Microsoft JDK 21.0.12, Gradle 8.14.3, Android SDK platform 36. Kotlin 2.4 metadata requires the explicit R8 9.1.43 and Room processor metadata dependency. Commands run through `D:\kebiao-build\apk\android`, a junction to the same workspace.

| Check | Result | Local evidence |
| --- | --- | --- |
| JVM domain, JSON, drafts, MCP, reminders, ViewModel | 64 tests, 0 failures, 0 errors | `app/build/test-results/testDebugUnitTest/TEST-*.xml` |
| APK and instrumentation APK | Build successful | `build/native-final-build.log` |
| Device suite | 22 discovered: 21 passed, 1 assumption skip, 0 failures | `build/device-tests.log` |
| Installed tiny OCR with Wi-Fi and mobile data disabled | 1 passed; network restored afterward | `build/offline-ocr-test.log` |
| Final system-bar theme adjustment: APK + lint | Build successful; 0 lint errors, 83 warnings | `build/native-ui-final-build.log`, `app/build/reports/lint-results-debug.txt` |
| Web contract and date resolution | 5 passed | `node --test tests/schedule-contract.test.cjs` |
| Both Web HTML inline scripts and contract | Syntax parse passed | Node syntax checks |
| npm dependency audit, including development dependencies | 0 vulnerabilities | `npm audit --json` in `apk/` |

The skipped device test is `cancelledSmallDownloadNeverActivates`: the suite first installed small successfully, so the cancellation test preserved that installation. Cancellation passed in the earlier 21-test run before small was installed. Tiny and small inference tests verify generated Chinese text, weekday, classroom, confidence bounds and boxes within the image. These are inference smoke tests, not a timetable accuracy benchmark.

Device tests also exercise Room migration/reopen/rollback and metadata preservation, actual CIO HTTP initialization and tools, unauthorized/Host/Origin rejection, write confirmation and Chinese request payloads, alarm identity, delayed alarm replacement and database-driven widget snapshots. The final system-bar-only change was rebuilt and checked visually; it did not change these domain/transport implementations.

Remaining lint warnings include legacy resources, manifest ordering, dependency update suggestions, platform ExifInterface and style/version-catalog recommendations. They are not a claim of zero static-analysis findings. Generated logs and APKs are ignored by Git; screenshots and this report are tracked.

## Manual Device Checks

- Added the actual widget through Pixel Launcher's Widgets menu: three future Physics occurrences display date/time and Science A101, with no overlapping rows. Tapping the widget opens the app.
- Inspected portrait light theme and landscape dark theme. Timetable columns scroll horizontally, periods scroll vertically and navigation stays visible. System-bar icon contrast follows the chosen theme.
- Enabled MCP through settings, observed a `connectedDevice` foreground service, pressed Home and verified the service remained active. Disabled MCP after QA; final service dump contains no `McpService`.
- First-use OCR chooser exposes the two local models plus OpenAI/defer choices. Model selection does not itself download files; explicit download does. The model list uses labelled radio semantics and the dialog body scrolls in constrained height.

Screenshots:

- [Native timetable](assets/2026-09-07-native-timetable.png)
- [OCR and MCP settings](assets/2026-09-07-native-mcp.png)
- [Landscape dark theme](assets/2026-09-07-native-landscape-dark.png)
- [Launcher widget](assets/2026-09-07-widget.png)

## Verification Limits

- No physical phone was attached. OEM background restrictions, real boot delivery, battery use and notification punctuality across devices still require phone acceptance testing. Emulator service survival does not prove every OEM's behavior.
- No real OpenAI API Key was supplied; request/response validation and reviewed persistence were tested, but no billable live provider request was performed.
- No anonymized real timetable corpus was supplied. Complex merged cells, photos with perspective, colored small text, course omission rates and field accuracy remain unbenchmarked. The implementation follows the pinned official Android RGB preprocessing; parity against BGR model YAML on colored samples remains open.
- Automatic approval rejected the host-port-forward/network-transition verification. Device-local HTTP protocol tests passed; host-to-emulator LAN connectivity and Wi-Fi handover were not claimed as verified.
- Browser policy rejected the local Web preview. Web contract and syntax checks passed; no fresh browser visual verification was completed.

## Delivery

APK: `apk/android/app/build/outputs/apk/debug/app-debug.apk`. Open `apk/android` in Android Studio and select JDK 21. Local SDK path belongs only in ignored `local.properties`. The current multi-ABI debug APK is large because it contains ONNX Runtime and OpenCV native libraries; model weights download separately.
