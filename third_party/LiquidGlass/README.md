# LiquidGlass upstream core

Source: https://github.com/Abdullajon1881/LiquidGlass
Commit: `72ad05c49628ea2270116b2943629cd81c0cc496`.
License: Apache-2.0, Copyright 2026 LiquidGlass contributors.

The five files in `app/src/main/java/dev/liquidglass/core` are unmodified copies of upstream `liquidglass-core/src/main/kotlin/dev/liquidglass/core`. `UPSTREAM.sha256` records their hashes. The complete license is retained here and inside the APK at `assets/licenses/LiquidGlass-Apache-2.0.txt`.

Flow Widgets' separate `LiquidGlassWidgetBackground` adapter uses `GlassMath.sdRoundedBox` and `GlassMath.lensProfile` on a generated, diffused light field to create bounded CPU-rendered bitmap backgrounds for RemoteViews. This is not the upstream Compose/View live renderer or its AGSL tier. The included AGSL source and the remaining core utilities are preserved as upstream source; the widget adapter does not execute that shader.

Upstream reports Maven Central publishing as not yet released. Source vendoring pins the implementation and avoids an undeclared Maven-local build prerequisite. No upstream build script or source file was modified. The adapter, bitmap cache and reflection surfaces are original Flow Widgets code. No wallpaper is read, captured, blurred or refracted.
