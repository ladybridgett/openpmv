# OpenMomentum

OpenMomentum is an unofficial, open-source Android controller for the Sennheiser MOMENTUM 4 Wireless.

The first milestone is intentionally small:

- read the headset battery directly;
- show whether the private control channel is reachable;
- adjust the continuous ANC-to-transparency balance;
- turn noise control off;
- expose the same controls through a home-screen widget;
- provide a Quick Settings tile that cycles **ANC → Transparency → Off**.

Everything happens locally. There is no account, analytics SDK, cloud backend, root requirement, or Shizuku requirement.

> [!WARNING]
> This is an early, hardware-unverified implementation of an undocumented protocol. Keep the official Smart Control app installed until the control path has been tested against several MOMENTUM 4 firmware versions. Do not use OpenMomentum for firmware updates.

## Build

Requirements:

- Android Studio with JDK 17;
- Android SDK 36;
- a MOMENTUM 4 already paired in Android's Bluetooth settings.

Open the project in Android Studio, allow Gradle sync to finish, and run the `app` configuration. The app supports Android 8.0 (API 26) and later; Android 12 and later ask for the Nearby devices permission at runtime.

Command-line builds use the included wrapper:

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

MOMENTUM 4 exposes a private GAIA control service over Bluetooth Classic RFCOMM. OpenMomentum opens that service only for a bounded operation, sends a GAIA request, validates the response, then closes the socket. State-changing operations are serialized and followed by an authoritative readback.

The service UUID, framing, and feature command identifiers are documented in [docs/PROTOCOL.md](docs/PROTOCOL.md). Protocol behavior was independently ported from the MIT-licensed [M4 Companion](https://github.com/Zhengyang-Liu/m4-companion) project; see [NOTICE](NOTICE).

## Current limitations

- MOMENTUM 4 only; other Sennheiser models are deliberately rejected.
- Exactly one paired device whose name contains `MOMENTUM 4` is expected.
- The control protocol is private and may change with headset firmware.
- A widget or tile cannot request Bluetooth permission. Launch the main app once after installation.
- Android may stop a background widget action if the OEM applies unusually strict background limits.
- The app does not scan, pair, update firmware, manage multipoint peers, or edit EQ yet.

If the control channel cannot open, close the official Smart Control app and retry; two apps may compete for the same RFCOMM service.

## Contributing

Please keep protocol code separate from Android UI code, add byte-level tests for every new command, and never log paired-device names or addresses in bug reports. Hardware reports should include Android version, MOMENTUM 4 firmware version, the requested action, and a redacted error message.

## Trademark and warranty

OpenMomentum is independent and is not affiliated with, authorized by, endorsed by, or supported by Sennheiser or Sonova. Sennheiser and MOMENTUM are trademarks of their respective owners and are used only to identify compatible hardware.

The software is provided without warranty under the MIT License.
