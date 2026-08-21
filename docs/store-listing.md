# FrameScope — Play Store Listing

Internal reference. Do not edit the live Play Console listing without reviewing this document.

---

## App Title (30 chars max)

```
FrameScope – FPS Meter & Performance Overlay
```

*Keywords covered: FPS Meter, Performance Overlay*

---

## Short Description (80 chars max)

```
Real-time FPS meter and system stats overlay for Android games.
```

---

## Full Description

```
FrameScope is a lightweight performance overlay for Android that displays real-time system metrics on top of games and apps.

Built for gamers, developers, and power users, FrameScope provides accurate frame rate data and system telemetry without requiring root access. Powered by Shizuku for secure privileged access.

────────────────────────────

Key Features

• Real-time FPS monitoring
• Draggable overlay that works on top of any app
• CPU frequency monitoring
• RAM usage display
• Battery temperature tracking
• Network speed and ping measurement
• Customizable overlay appearance
• Multiple layout modes (Minimal, Compact, Expanded)

────────────────────────────

Accurate Frame Rate Monitoring

FrameScope reads presented-frame timestamps from Android's compositor (`SurfaceFlinger --latency`), so it measures the foreground layer without adding work to the monitored app's rendering threads.

────────────────────────────

Customizable Overlay

Choose how your overlay looks and behaves:

• Adjustable text size
• Opacity control
• Background color (Black, Navy, Charcoal, Transparent)
• Border style (Accent, Subtle, Ghost, None)
• Metric value color (White, Accent, Silver, Auto)
• Accent color selection
• Multiple display modes
• Drag and reposition anywhere on screen

All settings persist across restarts.

────────────────────────────

Powered by Shizuku

FrameScope uses the Shizuku API to securely access advanced system telemetry without requiring root. Shizuku must be installed and activated separately via wireless ADB or Sui (for rooted devices).

FrameScope does not bypass system protections and does not modify game behavior.

────────────────────────────

Privacy First

FrameScope does not collect personal data.
No accounts. No analytics. No background tracking.

Internet permission is used only for optional ping measurement.

────────────────────────────

Requirements

• Android 8.0 (API 26) or higher
• Shizuku installed and running

FrameScope is designed as a monitoring tool for performance insight and diagnostics. It does not boost, modify, or alter game performance.
```

---

## Safe / Conservative Description (use if policy team requests)

```
FrameScope is a system monitoring overlay for Android that displays frame rate and device telemetry in real time.

It provides diagnostic visibility into device performance while running games or applications.

FrameScope does not modify system behavior, adjust performance parameters, or alter any application functionality. It is strictly a monitoring utility.
```

---

## Privacy Policy URL

```
https://superj0107.github.io/FrameScope/privacy-policy
```

Enable GitHub Pages in repo Settings → Pages → Source: `docs/` folder.

---

## ASO Keywords

### Primary (in title + description)
- FPS meter
- FPS monitor
- FPS counter
- Performance overlay
- Game FPS

### Secondary (use naturally in description)
- Real-time FPS
- Frame rate monitor
- Android FPS
- Gaming overlay
- System performance monitor
- Game performance stats

### Title Variations (A/B test later — don't change early)
- FrameScope – FPS Monitor & Overlay
- FrameScope FPS Meter for Android
- FrameScope – Game Performance Monitor

---

## Screenshot Copy

Each screenshot = one benefit statement.

| Screen | Overlay text |
|--------|-------------|
| Overlay on game | "Real-Time FPS Monitoring" |
| Drag demo | "Drag Anywhere on Screen" |
| Appearance screen | "Fully Customizable Overlay" |
| Permissions screen | "Powered by Shizuku — No Root Required" |
| Expanded mode | "CPU · RAM · Temp · Network · Ping" |

Avoid: blank dark screens, jargon walls, tiny unreadable text.

---

## Pre-Launch Checklist

- [x] "Performance Adjustments" button renamed to "Diagnostics" — no boost language
- [ ] Privacy policy hosted at GitHub Pages URL above
- [ ] Privacy policy URL entered in Play Console
- [ ] Tested on Android 8.0 (API 26) — minimum SDK
- [ ] Tested on Android 14/15 — latest
- [ ] Verified graceful behavior when Shizuku is not installed (no crash)
- [ ] Verified Shizuku requirement is clearly explained in onboarding
- [ ] No language in app suggesting FPS boosting or game modification
- [ ] Reviewed all permissions — each has a stated purpose
- [ ] App content rating questionnaire completed in Play Console

---

## Review Strategy

First 50 reviews define early ranking.  
Ask beta testers to naturally mention:
- "FPS meter"
- stability
- accuracy

Do **not** incentivise reviews. Against Play policy.

---

## Versioning

- `v1.4.0` — quality, privacy, release, and metric accuracy fixes (current)
- Plan next minor release — Diagnostics panel and additional metrics
- Tag every release: `git tag -a v1.x.x -m "description" && git push --tags`
