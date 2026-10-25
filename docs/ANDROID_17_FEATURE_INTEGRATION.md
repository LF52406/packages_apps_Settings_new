# Android 17 Settings integration notes

This fork carries separate, feature-scoped Settings changes on top of the
existing `17.0` branch. Each feature should be reviewed and reverted independently.

## Quick Settings style selector

- **Settings side:** `QuickSettingsStyleFragment`, notification settings entry,
  and translated labels. The per-user key is
  `Settings.System.mist_qs_style` (`0` = MistOS, `1` = standard Android QS).
- **Required SystemUI side:**
  [MistOS/stock QS implementation](https://github.com/LF52406/frameworks_base_qpr2/commit/0a65ff31e3b93a1ae871e7968f30dac121406520).
  Without this companion, Settings can store the choice but cannot change
  the control-center layout.
- **Companion fixes:**
  [QS/media-popup lifecycle](https://github.com/LF52406/frameworks_base_qpr2/commit/cb25db56e6ba74f0547a2ce51a7858cf09bf89bd).
  Those changes prevent transient SystemUI overlays and detached legacy media
  hosts from appearing over the new control center.
- The existing shade-panel mode remains independent of the style selection.

## Storage Refresh

- **Settings side:** manual maintenance dashboard, progress/result states,
  cooldown, translations, and a weekly chart of confirmed successful manual runs.
- **Required framework side:**
  [StorageManager maintenance callback](https://github.com/LF52406/frameworks_base_qpr2/commit/735e08d0350de72eb6e9c42071ff9ce714fad03e),
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

[LMOFreeform for Android 17](https://github.com/LF52406/packages_apps_LMOFreeform/commit/a103169c9d15bf4dfb9b5193c61181372cf9bd06)
implements virtual-display-backed floating **application** windows. It is
different from the SystemUI media/popup overlays and is not required for the
QS style selector to build.
