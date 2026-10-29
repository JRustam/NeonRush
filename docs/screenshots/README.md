# Screenshot provenance

Captured on **2026-10-28** from the actual Neon Rush Android app on **Medium Phone API 36**, Android 16, ARM64 (`sdk_gphone64_arm64`). Each PNG is 1080 × 2400 pixels.

- `main-menu.png`: app's main menu.
- `gameplay.png`: a live run approximately 2.8 simulated seconds after Start Run.
- `game-over.png`: the same run after a natural collision, following pause/resume and an Activity background/foreground check.

`GameSmokeTest` uses Compose's `captureToImage()` on the app root, rendered by the emulator. The Compose test clock advances the normal simulation; scores, hazards and screens are not injected or mocked. The captures contain the app surface without Android system-bar contents. No generated images, reconstructed UI, retouching, or compositing were used.

## Reproduce

Connect an unlocked test device/emulator, put `adb` on PATH, and run from the project root:

```sh
./tools/capture_screenshots.sh
```

Alternatively, set `ADB_BIN` to the full path to your SDK's `platform-tools/adb`. Additional script arguments are passed to Gradle, for example `-Pkotlin.compiler.execution.strategy=in-process`.

The script installs the app and test APK, runs the real navigation test, and pulls the screenshots into this directory. The test restores temporary volume changes but can raise the test device's high score. Use a test emulator. Random obstacle positions and the final score vary between captures.

The explicit instrumentation command is intentional: some Gradle connected-test runners remove test installations and their external files during cleanup. Pulling immediately after instrumentation preserves the genuine captures.
