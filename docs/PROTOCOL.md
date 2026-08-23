# MOMENTUM 4 control protocol notes

These notes describe only the narrow protocol surface used by OpenMomentum. The transport is Bluetooth Classic RFCOMM using service UUID:

```text
a2129ff3-081b-4c45-8afe-469d9c4842ec
```

## Frame format

Each RFCOMM frame is:

| Bytes | Meaning |
| --- | --- |
| `FF 03` | GAIA SPP sync marker |
| 2 bytes | big-endian feature-payload length; excludes the four-byte GAIA header |
| 2 bytes | vendor ID (`04 95`) |
| 2 bytes | big-endian command ID |
| remaining bytes | command payload |

The receiver discards bytes before the next sync marker, waits for a complete frame, and may emit several packets from one RFCOMM read.

## Commands used in milestone 1

| Operation | Request | Success response | Payload |
| --- | ---: | ---: | --- |
| Battery | `0603` | `0703` | response: percentage `0..100` |
| Set ANC sub-mode | `1A00` | `1B00` | `[mode, state]`; adaptive mode is `03` |
| Get ANC sub-modes | `1A01` | `1B01` | response: repeated `[mode, state]` pairs |
| Set balance | `1A02` | `1B02` | level `0..100` |
| Get balance | `1A03` | `1B03` | response: level `0..100` |
| Enable/disable ANC | `1A04` | `1B04` | boolean byte |
| Get ANC enabled | `1A05` | `1B05` | response: boolean byte |
| Transparent-hearing behavior | `1804` | `1904` | boolean byte |

A rejected write is reported as the success response ID with bit `0x0080` set. OpenMomentum treats unexpected sizes, out-of-range values, unexpected command IDs, and timeouts as failures.

## Control sequences

To select a manual balance, OpenMomentum sends these writes in order:

1. disable transparent-hearing playback behavior (`1804: 00`);
2. enable ANC (`1A04: 01`);
3. disable adaptive ANC (`1A00: 03 00`);
4. set balance (`1A02: level`).

The UI presents level `0` as maximum ANC and level `100` as maximum transparency. Turning noise control off disables transparent hearing and then sends `1A04: 00`.

Every sequence is followed by fresh battery, ANC-enabled, ANC-mode, and balance queries. Cached widget/tile state is updated only from that result or from an explicit error state.

## Safety rules for extensions

- Do not send unknown or destructive commands.
- Bound connection and response waits.
- Serialize operations so widget, tile, and activity writes cannot interleave.
- Validate vendor ID `0495`, expected response ID, payload length, and value range.
- Read the state back after every write sequence.
- Keep firmware updating outside this project until a separately audited implementation exists.
