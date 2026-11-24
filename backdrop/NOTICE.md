# AndroidLiquidGlass / Backdrop

This module contains an Android-only adaptation of:

- Project: Kyant0/AndroidLiquidGlass
- Source: https://github.com/Kyant0/AndroidLiquidGlass
- Upstream module: `backdrop`
- License: Apache License 2.0 (see `LICENSE`)
- Copyright 2025 Kyant

The source was adapted to Jetpack Compose 1.7 and Android-only packaging for
MockRun. The backdrop capture, Gaussian blur, vibrancy, highlight, shadow and
AGSL refraction algorithms remain the upstream implementation. The MPP
expect/actual shells and the optional `kyant-shapes` dependency were replaced
with Android-only equivalents so the material can run on the app's existing
Kotlin 2.0 / Compose 1.7 toolchain.
