# Android 16 / 17 compatibility work

## Build baseline

Android 17/API 37 is a released platform. This project uses compile/target 37, minSdk 28 unchanged, AGP 9.1.1, Gradle 9.3.1 and JDK 17. These are a supported combination, not a claim that every dependency is the latest release. AGP supplies built-in Kotlin (default compiler 2.2.10); the redundant kotlin-android plugin is removed. KSP 2.3.10 replaces the old plugin that explicitly rejected built-in Kotlin. Yuki 1.2.0 remains pinned: its generated-code warnings are still a runtime/maintenance boundary, not suppressed by disabling the new AGP DSL or unsupported-SDK checks.

- [Android 17 release](https://android-developers.googleblog.com/2026/06/Android-17.html)
- [AGP 9.1.1 requirements and API 37 support](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [KSP 2.3.10, including AGP 9 R-class handling](https://github.com/google/ksp/releases/tag/2.3.10)

## UI behavior

Android 16 target 36 already mandates edge-to-edge and changes back dispatch. The existing root inset handling covers system bars, cutouts and IME without an opt-out. Management-page back now uses AndroidX OnBackPressedDispatcher; the start page defers to system back-to-home. This is not a custom predictive gesture animation implementation.

Android 17 removes the large-screen resizability escape hatch for target 37. The manifest now explicitly permits resizing, removes inherited orientation constraints and enables supported back callbacks. Activity recreation restores the management/start page, search, pending-only filter and GIF-quality selection; APNG/per-pack settings keep their existing preferences. Recreated activities reload authorized storage without launching another permission flow. Denied access still has an explicit retry entry point.

This does not redesign conversion into a persistent service: the old Activity's current pack can finish safely, while an unfinished batch does not automatically resume after destruction. Users can re-run incremental sync. Process death does not preserve a running native FFmpeg job.

Sources: [Android 16 target behavior](https://developer.android.com/about/versions/16/behavior-changes-16), [Android 17 target behavior](https://developer.android.com/about/versions/17/behavior-changes-17).

## Native libraries and runtime internals

API 37 makes static-final mutation unsupported and introduces a lock-free MessageQueue. App code does not modify these internals; the earlier global CursorWindow reflection was already removed. This does not certify the Xposed framework or injected libraries. Inspection of the pinned Yuki/FreeReflection bytecode found no specific static-final/MessageQueue mutation path, but ModuleApplication still attempts hidden-API unsealing. The transitive FreeReflection 3.1.0 conditional DexFile fallback lacked a read-only step. A direct 3.2.2 dependency overrides it without changing the unseal(Context) API or removing ModuleApplication initialization/channels. The official source diff and downloaded AAR both show setReadOnly before DexFile construction. This fixes the normal writable-file fallback issue but does not guarantee hidden-API access: the upstream code sets permissions after writing and ignores the return value, and unseal failures are caught. Device verification remains required. See [official 3.1.0–3.2.2 diff](https://github.com/tiann/FreeReflection/compare/3.1.0...3.2.2). Hook behavior also depends on the host application's target SDK and installed framework, not merely this module's target.

FFmpegKit's inspected NativeLoader uses System.loadLibrary for packaged libraries, not writable downloaded native code. Native libraries are explicitly packaged uncompressed for direct loading from the installed APK. `python tools/check-native-packaging.py <apk>` checks ZIP 16 KiB alignment and every AArch64 ELF LOAD segment, failing on missing libraries, compression or incompatible alignment. It checks prerequisites only; 4 KiB/16 KiB device loading and actual conversions still require runtime testing. Existing arm64-only support is unchanged.

Sources: [Android 17 target behavior](https://developer.android.com/about/versions/17/behavior-changes-17), [Android 14 DCL rules](https://developer.android.com/about/versions/14/behavior-changes-14), [16 KiB page-size guidance](https://developer.android.com/guide/practices/page-sizes).

## Memory, storage and unaffected features

Android 17 can enforce per-app memory limits independently of target SDK. Existing bounded conversion (maximum eight, two on low-RAM devices), single-threaded native jobs, source checks and bounded Hook parsing remain. System low-memory checks are not an app-specific memory budget or a guarantee against termination. Concurrent video conversion needs device memory/thermal testing; no memory-limiter opt-out or unmeasured performance claim is added.

All-files access belongs to the module UID, not the injected Telegram host. The host's Context-derived cache paths remain; shared exports/locks can still fail under scoped-storage ownership rules. Android/media is shared storage, but upgrading target SDK does not grant arbitrary access to another app's Android/data. No new storage/network permission or destructive data migration is introduced. This app has no local-network, SMS, contacts, widget or custom-notification feature requiring the corresponding new permissions/limits.

Sources: [Android 17 all-app changes](https://developer.android.com/about/versions/17/behavior-changes-all), [all-files access boundaries](https://developer.android.com/training/data-storage/manage-all-files).

## Device acceptance checklist (not executed in the cloud)

- API 28 baseline; Android 16/API 36 and Android 17/API 37, including a 16 KiB device.
- Gesture/three-button back, IME, cutout, light/dark bars; rotate and resize at 600dp+, split screen, large text.
- Open management, set all filters, rotate, return/back, recreate with permission denied or while Settings is open.
- GIF/APNG alpha/duration and native loading; eight-worker video load, app-memory-limit termination and incremental retry.
- Hook under the actual Telegram/fork/LSPosed versions; multiple accounts, storage permission denial and concurrent exports.

Build/test success is not a substitute for this matrix. CI remains manual-only, and this work does not create a release or publish an APK.
