# Discord RPC (Kizzy-style) — Agent Task Split

Three agents work on this branch (`feature/discord-rpc-kizzy`). Before starting any work, **always check the branch's latest commits** — other agents may have pushed since you last looked.

Reference implementation: [Hiirbaf/yokai@ad11af2](https://github.com/Hiirbaf/yokai/commit/ad11af21ac50d02db24a93c72db92245ae67e745) (Kizzy-style, token + WebSocket gateway). Use as a base, not a copy — see gaps in STATUS.md.

Code style: comments allowed, but concise — no filler. Commit messages: short prefixed title (`feat:`, `fix:`, `chore:`), details in the commit body.

---

## Agent 1 — Core RPC Engine

- WebSocket gateway client: identify, heartbeat, reconnect/resume, close.
- `Presence` / `Activity` serializable models.
- `DiscordRPCService`: lifecycle tied to the reader only — **start when entering ReaderActivity, stop when leaving it**. Not app-wide like the yokai reference (which keeps presence alive on Library/Browse/etc).
- Fully custom activity name/state (free text from settings), not a hardcoded per-screen enum.

## Agent 2 — Accounts & Auth

- Multi-account support: add/remove/switch active account.
- WebView-based token capture (Discord login), matching Rokku's existing non-Compose settings architecture.
- Token storage: encrypted at rest (not plaintext preferences like the reference implementation).
- Token validation beyond a bare regex check (e.g. verify against `/users/@me`).

## Agent 3 — Integration & Privacy

- Wire RPC into `ReaderActivity` (start/update/stop, chapter change updates).
- Suppression logic: Rokku's existing **global incognito** AND **per-extension incognito** (`isIncognitoModeForSource()`) — not per-category like the yokai reference.
- New **18+ toggle**: suppress RPC when reading content flagged 18+. Source of the flag is still open — see STATUS.md.
- Cover art for RPC assets — avoid the third-party proxy dependency (`kizzy-api.cjjdxhdjd.workers.dev`) used in the reference; find a self-contained approach.
- Settings UI: Connections screen (enable, status, custom activity text, incognito toggles, 18+ toggle, accounts entry point).
