# DolRecomp for Android

Android front end (Jetpack Compose) around [DolRecomp](https://github.com/ExpansionPak/DolRecomp),
the GameCube / Wii / Wii U static recompiler. DolRecomp's own `main()` is compiled into
`libdolrecomp_jni.so` unmodified (renamed with `-Dmain=dolrecomp_main`) and runs in a separate
`:native` process, one job per process.

## Build (GitHub Actions, no PC needed)
1. Create a new GitHub repo.
2. Add file `.github/workflows/build-apk.yml` (paste the provided file) in the GitHub web UI.
3. Upload `dolrecomp-android.zip` to the repo root ("Add file → Upload files").
4. The workflow unpacks it, checks out DolRecomp (with submodules), builds, and publishes
   `app-release.apk` under Releases → "latest". Later builds are signed with the same key,
   so they install over the previous one.

Run the workflow manually to build a different DolRecomp branch/tag/commit.

## Layout
- `app/src/main/cpp` – CMake + JNI bridge (DolRecomp comes from `native/DolRecomp`, fetched by CI)
- `app/src/main/java/...` – UI, project store, service, runner
- `Options.kt` – one entry per DolRecomp flag; add new flags there

## Known gaps
- LLVM backend not built (needs LLVM 19/20 for Android).
- Wii disc extraction needs the external `wit` tool: ship an arm64 build as
  `app/src/main/jniLibs/arm64-v8a/libwit.so` and the app passes `--wit` automatically.
- Output is CPU-side C only; a playable port also needs a runtime (graphics/audio/input).
- The signing key in `app/debug.keystore` is public. Fine for personal use, don't publish to stores.

License: GPL-3.0 (see LICENSE), matching DolRecomp.
