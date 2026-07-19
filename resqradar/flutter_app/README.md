# ResQRadar VE (Flutter)

Fork MIT de VitalFi / ResQRadar (Carlos Mundaray — Solvitco).

## Android

```bash
flutter pub get
flutter build apk --release
```

## iOS → LiveContainer

```bash
flutter build ios --release --no-codesign
# Empaquetar build/ios/iphoneos/Runner.app como IPA (Payload/)
```

En el iPhone: LiveContainer → **+** → `ResQRadar-VE-LiveContainer.ipa` (CI lo genera en `.github/workflows/resqradar-ios.yml`).

Sin radar Wi‑Fi en iOS (límite Apple). Ver `../COORDINACION.md`.
