# BuildyngBlocks

Central package for non-mod-specific custom blocks in a modular Minecraft project (Minecraft Java
Edition). Part of a suite of intercommunicating mods that expose public APIs so other mods — the
user's own and third parties' — can integrate. Unlike every other mod in the suite, this one is
deliberately not tied to any single system (settlements, quests, economy, etc.) — each block it
ships is a standalone, generic building/utility block.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/buildyngblocks-context),
not part of this one — it's listed in `.gitignore` here and must never be committed to this repo.
It's cloned as a subdirectory at `context/` for local convenience. If this directory is missing
(e.g. a fresh clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/buildyngblocks-context.git context
```

- `context/design-document.md` — the authoritative design spec, including the user's own original
  per-block requests verbatim and the key interpretive/derived decisions. Start here for anything
  about intended shape or scope.
- `context/decisions.md` — dated log of decisions made during implementation that extend or
  override the design document, with rationale.
- `context/classes/` — one short markdown file per implemented block/system.

**Keep this system current as you work** — same discipline as every other mod in this suite:
update design-document.md/decisions.md when a decision is made, update classes/ when a class's
public surface changes, commit+push context/ separately from this repo's own commits.

## Status

Scaffolded 2026-11-09 (see [context/decisions.md](context/decisions.md)):
- Loader: **NeoForge**, Minecraft **26.1.2**, Java **25**
- Group ID: `com.github.cerealklla.buildyngblocks` / Mod ID: `buildyngblocks`
- **Required dependency on Nuclyus** (the suite's foundation mod) — no other sibling-mod
  dependencies; this mod is meant to stay standalone.

**Block 1: Roller Door — core mechanics + GUI built** (see
[context/classes/RollerDoor.md](context/classes/RollerDoor.md), full spec and derivation in
[context/design-document.md](context/design-document.md)) — a hanging, chest-fed, redstone-driven
door built from real player-chosen blocks. v1 visual is a plain stone cube per spec; v2's
wheel-and-chain look is explicit future work, not started. Built in stages: scaffold → core
chain/redstone/state-machine mechanics (instant block placement per step, no animation yet) → GUI
(27-slot free-grid storage, block-items-only slot filter, standard vanilla chest texture reuse) →
**smooth slide animation (not yet built — needs the exact block-model-rendering API for this MC
version confirmed against real decompiled vanilla source first)** → polish (break-cleanup done).
`./gradlew build` green standalone, whole-suite (17 mods) `sync-mods.sh` rebuild + `runServer`
boot-smoke-test clean. **Not yet live-tested in a real client** — placing a door, loading storage,
toggling redstone, and the chain/LIFO/obstruction/neighbor-sync behavior all still need a real
`runClient` session.

See [context/classes/](context/classes/) for per-class reference.
