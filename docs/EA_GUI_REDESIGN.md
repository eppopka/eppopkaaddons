# `/ea` GUI redesign specification

Status: Phase 8 complete

## Product direction

- Replace the AetherOS desktop metaphor with a single centered product-style dashboard.
- Use three regions: category sidebar, module list, and contextual settings inspector.
- Remove Orbit mode and floating multi-window navigation from the final screen.
- Default to a restrained dark theme inspired by modern desktop productivity software.
- Keep every theme color configurable through presets and a custom palette.
- Show the Minecraft world behind a configurable dim and blur treatment. Default blur: 50%.

## Interaction contract

- Left-clicking a module toggles it.
- Right-clicking a module opens its settings inspector.
- Escape closes the topmost settings surface or overlay first.
- Escape with no child surface open starts the screen exit transition.
- The optional exit delay is 180 ms by default and can be disabled.
- Selected category, search text, per-category list scroll, and inspector scroll persist.
- A fresh configuration opens a first-launch setup wizard before the dashboard.

## Responsive layout

The GUI continues to use the project's 960 × 540 logical coordinate system.

- The dashboard is centered with an 18 px safe margin.
- Normal layout: 164 px sidebar, flexible module column, 272 px inspector.
- Compact layout: inspector becomes an overlay above the module column.
- The shell never exceeds 920 × 500 logical pixels.
- Module and settings regions own their scroll state; the shell itself does not scroll.

## Motion principles

- One monotonic frame clock drives all motion.
- Animations are reversible and preserve their current value when targets change.
- Presets: Snappy, Smooth, Relaxed, and Custom.
- Decorative motion obeys Reduced Motion; essential state feedback remains.
- No per-frame object allocation is required by the motion primitives.
- Screen exit is 180 ms by default and remains responsive to immediate-close settings.

## Delivery boundaries

The production screen uses the centered responsive shell, contextual inspector, first-launch
wizard, centralized motion clock, rounded surfaces, configurable blur, persistent navigation,
keyboard controls, delayed exit, and offline-safe runtime data fallbacks. Legacy Orbit and
floating-window implementations have been removed.
