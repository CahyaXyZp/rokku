# Discord RPC — Decision Log

1. Initial plan: port Discord RPC from Komikku into Rokku, settings under Tracking, presence only while reading, suppress on incognito (global + per-extension). Built on `feature/discord-rpc`.
2. Pivoted to official Discord Social SDK (OAuth2, native JNI bridge) on `feature/discord-rpc-sdk`. New "Connections" top-level settings entry (flat, not nested). Decided against multi-account for this approach — OAuth2 fits single-account better.
3. Pivoted back to Kizzy-style (token-based), but aiming to be more advanced than both the original Kizzy and the `Hiirbaf/yokai` reference. New branch: `feature/discord-rpc-kizzy`.
4. Confirmed for this branch: multi-account is back in scope (token-based auth makes it straightforward), presence only while reading, incognito reuses Rokku's existing global + per-extension mechanism, new 18+ suppression toggle, custom cover art without a third-party proxy dependency.
5. Work split across 3 agents (core engine / accounts & auth / integration & privacy) — see AGENTS.md.
