# Discord RPC (Kizzy-style) — Status

Branch: `feature/discord-rpc-kizzy`
Direction: token-based (Kizzy-style), not the official Discord SDK — pivoted back from the earlier `feature/discord-rpc-sdk` attempt.

## Current phase
Core RPC engine (Agent 1) and settings scaffold (Agent 3) both have a first pass in. Accounts/auth (Agent 2) not started — currently the hard blocker for anything end-to-end.

## Scope decided so far
- Presence shows only while actively reading a chapter (not app-wide).
- Incognito: reuse Rokku's existing global + per-extension incognito, not a separate per-category system.
- 18+ toggle: suppresses RPC when reading 18+ content. Flag source: **from the extension** (extension-provided genre/tag on the manga), not a manually assigned category, and **not** a whole-source flag — see ⚠️ below.
- Multi-account, fully custom activity text, and RPC cover art are all in scope (see AGENTS.md).
- Avoid the third-party image proxy the reference implementation depends on.

⚠️ **Known drift to fix**: `discord_rpc_suppress_18plus_summary` (Agent 3's strings commit) currently reads "reading a series from a source flagged as 18+" — that's source-level, but the decision is per-manga (extension-provided genre/tag), since many sources mix 18+ and non-18+ titles. Needs a string + suppression-check fix in Agent 3's integration work.

## Reference material
- [Hiirbaf/yokai@ad11af2](https://github.com/Hiirbaf/yokai/commit/ad11af21ac50d02db24a93c72db92245ae67e745) — Kizzy-style base (WebSocket gateway, multi-account, per-category incognito, proxy-based cover art). Already covers multi-account and incognito-style suppression, but not per-extension incognito, free-text activity, or the 18+ toggle.

## Progress log

### Agent 1 (Core RPC Engine)
- Added `DiscordRpcModels.kt`: Gateway payload/opcode models, `PresenceUpdate`/`Activity` (with `Activity.forReading(...)` builder for the reading-presence shape).
- Added `DiscordGatewayClient.kt`: raw WebSocket gateway client — identify, heartbeat (with jitter + ack tracking), resume-on-reconnect, exponential backoff. Exposes `GatewayState` as a `StateFlow`.
- Added `DiscordRpcService.kt`: the facade the reader will call (`onChapterOpened`/`onChapterClosed`). Deliberately takes a pre-computed `suppressed: Boolean` rather than knowing about incognito/18+ itself — that logic belongs to Agent 3.
- Builds its own dedicated `OkHttpClient` (infinite read timeout, since gateway is a persistent socket) rather than assuming a shared `NetworkHelper` API — code search on this repo didn't turn up one, so this avoids guessing a wrong integration point. If Rokku has a shared client convention, swap it in.
- Not yet wired: nothing constructs `DiscordRpcService` or calls it. Needs a Koin binding (see `PreferenceModule`/similar for the pattern Agent 3 already used) and a caller from the reader.
- Not yet handled: no unit tests, no verification against a real Discord connection (can't run a build in this environment) — flag for review before merge.

### Agent 3 (Integration & Privacy)
- Added `SettingsConnectionsController` — new top-level "Connections" entry in Settings (`SettingsMainController`), flat, not nested under an existing screen.
- Added `DiscordRpcPreferences` (`data/connections/discord/`) backing the screen: enable toggle, custom activity text, suppress-in-incognito, suppress-for-18+, show-cover-art. Registered in `PreferenceModule`.
- "Accounts" row is a placeholder (toast) pending Agent 2's account management screen — replace the `onClick` in `SettingsConnectionsController` once that screen exists.
- Still blocked on: real RPC engine to read `enabled()` from (now exists, needs wiring), reader lifecycle hook, actual suppression logic, and the 18+ scope fix noted above.
