# Discord RPC (Kizzy-style) — Status

Branch: `feature/discord-rpc-kizzy`
Direction: token-based (Kizzy-style), not the official Discord SDK — pivoted back from the earlier `feature/discord-rpc-sdk` attempt.

## Current phase
Planning / scope defined. No implementation pushed yet.

## Scope decided so far
- Presence shows only while actively reading a chapter (not app-wide).
- Incognito: reuse Rokku's existing global + per-extension incognito, not a separate per-category system.
- 18+ toggle: suppresses RPC when reading 18+ content. Flag source: **from the extension** (extension-provided genre/tag), not a manually assigned category.
- Multi-account, fully custom activity text, and RPC cover art are all in scope (see AGENTS.md).
- Avoid the third-party image proxy the reference implementation depends on.

## Reference material
- [Hiirbaf/yokai@ad11af2](https://github.com/Hiirbaf/yokai/commit/ad11af21ac50d02db24a93c72db92245ae67e745) — Kizzy-style base (WebSocket gateway, multi-account, per-category incognito, proxy-based cover art). Already covers multi-account and incognito-style suppression, but not per-extension incognito, free-text activity, or the 18+ toggle.
