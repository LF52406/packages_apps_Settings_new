# Android 17 Settings integration notes

This fork carries separate, feature-scoped Settings changes on top of the
existing `17.0` branch. Each feature should be reviewed and reverted independently.

## Quick Settings style selector

- **Settings side:** `QuickSettingsStyleFragment`, notification settings entry,
  and translated labels. The per-user key is
  `Settings.System.mist_qs_style` (`0` = MistOS, `1` = standard Android QS).
- **Required SystemUI side:**
  [MistOS/stock QS implementation](https://github.com/LF52406/frameworks_base_qpr2/commit/0129f037ef0cbc06cd6fcd3c759b1fb11b2e1d3e).
  Without this companion, Settings can store the choice but cannot change
  the control-center layout.
- **Companion fixes:**
  [QS/media-popup lifecycle](https://github.com/LF52406/frameworks_base_qpr2/commit/3e2f89ebb9d622f9c996fcda9c0b5deb8b6f1631).
  Those changes prevent transient SystemUI overlays and detached legacy media
  hosts from appearing over the new control center.
- The existing shade-panel mode remains independent of the style selection.
- **Landscape layout:** [landscape QS header and icon spacing](https://github.com/LF52406/frameworks_base_qpr2/commit/1d149a85544dc88f800ba7fa67e7c3501ee998b3).

## Mobile status bar indicator changes

- [Cellular RAT indicators](https://github.com/LF52406/frameworks_base_qpr2/commit/7f4ea6c0efbc1565be8c8c0159d709752099d0d0)
  track the registered radio technology even when Wi-Fi is the default data path.
- [IMS / VoLTE status](https://github.com/LF52406/frameworks_base_qpr2/commit/db22927c3dd14ff2a8e04261a9bab936a2c24657)
  reports IMS registration and VoLTE availability independently of the RAT indicator.


## Storage Refresh

- **Settings side:** manual maintenance dashboard, progress/result states,
  cooldown, translations, and a weekly chart of confirmed successful manual runs.
- **Required framework side:**
  [StorageManager maintenance callback](https://github.com/LF52406/frameworks_base_qpr2/commit/c0d03312019790d90c95791b151b3d2b5d9bfb81),
  which exposes permission-checked `IStorageManager.runIdleMaintenanceWithCallback`.
- This is vold-backed GC/TRIM maintenance, **not** fragmentation measurement
  or a claim that a filesystem was defragmented. Keep completion and failure
  labels tied to actual callback results.

## Pocket Mode

- Settings UI writes `Settings.System.pocket_judge` and is gated by
  `config_pocketModeSupported`.
- A compatible system-server pocket-detection service must consume that
  setting. Settings alone cannot implement pocket detection.

## Security patch date layout

- The about-phone layout adjustment is a standalone Settings-only change
  for localized text lengths and large font sizes.

## Distinct floating-window implementation

[LMOFreeform for Android 17](https://github.com/LF52406/packages_apps_LMOFreeform/commit/50ca291586106b3da57c6bb0ebc0d1c77499d3ec)
implements virtual-display-backed floating **application** windows. It is
different from the SystemUI media/popup overlays and is not required for the
QS style selector to build.
