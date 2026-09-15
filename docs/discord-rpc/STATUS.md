# Discord RPC (Kizzy-style) — Status

Branch: `feature/discord-rpc-kizzy`
Direction: token-based (Kizzy-style), not the official Discord SDK — pivoted back from the earlier `feature/discord-rpc-sdk` attempt.

## Current phase
Settings UI scaffold (Agent 3) pushed. Core RPC engine (Agent 1) and accounts/auth (Agent 2) not started yet.

## Scope decided so far
- Presence shows only while actively reading a chapter (not app-wide).
- Incognito: reuse Rokku's existing global + per-extension incognito, not a separate per-category system.
- 18+ toggle: suppresses RPC when reading 18+ content. Flag source: **from the extension** (extension-provided genre/tag), not a manually assigned category.
- Multi-account, fully custom activity text, and RPC cover art are all in scope (see AGENTS.md).
- Avoid the third-party image proxy the reference implementation depends on.

## Reference material
- [Hiirbaf/yokai@ad11af2](https://github.com/Hiirbaf/yokai/commit/ad11af21ac50d02db24a93c72db92245ae67e745) — Kizzy-style base (WebSocket gateway, multi-account, per-category incognito, proxy-based cover art). Already covers multi-account and incognito-style suppression, but not per-extension incognito, free-text activity, or the 18+ toggle.

## Progress log

### Agent 3 (Integration & Privacy)
- Added `SettingsConnectionsController` — new top-level "Connections" entry in Settings (`SettingsMainController`), flat, not nested under an existing screen.
- Added `DiscordRpcPreferences` (`data/connections/discord/`) backing the screen: enable toggle, custom activity text, suppress-in-incognito, suppress-for-18+, show-cover-art. Registered in `PreferenceModule`.
- "Accounts" row is a placeholder (toast) pending Agent 2's account management screen — replace the `onClick` in `SettingsConnectionsController` once that screen exists.
- Not wired to anything real yet: no RPC engine to read `enabled()` from, no reader lifecycle hook, no actual suppression logic. That's blocked on Agent 1's service/model.
