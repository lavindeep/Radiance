# Handoff — Where this is and what's next

This document is the **single entry point** for continuing the Radiance 1.20.1 backport on a new machine. It is committed to the repo so `git clone` is the only setup step needed.

## Current state (2026-05-11, end of Checkpoint C Java-structure session)

**Checkpoint C Java structure SHIPPED. PRD G4 CLEARED. PRD G6 still PARTIAL. PRD G7 PENDING.** The buffer abstraction (`RadianceBufferHandle` / `RadianceBufferAdapter` / `RadianceVertexConsumer`) lands; `BufferProxy` and `ChunkProxy` are out of `src/deferred/` and ported to 1.20.1 with the JNI surface no longer referencing MC's `BuiltBuffer`/`VertexConsumer`. Four new mixins promoted (`RenderLayerMixins`, `ChunkBuilderBuiltChunkMixins`, `BuiltChunkStorageMixins`, `ClientChunkManagerMixins`). `WorldRendererCoreMixins` written fresh against 1.20.1 yarn but parked outside `ENABLED_MIXINS` because `ChunkProxy.rebuildSingle` is stubbed (`UnsupportedOperationException`) and turning the mixin on would cancel vanilla terrain rendering and throw on first chunk. MCVR shipped 64 stub JNI exports for the missing `Options.nativeSet*` methods (commit `2e88626`), neutralising the Checkpoint B `UnsatisfiedLinkError` fallback. **Pipeline.buildNative C++ crash remains unresolved** — that's user-driven Phase 0 work (requires `runClient` + Vulkan validation layers to drive the throw-site bisect).

- **Branch:** `checkpoint/checkpoint-c` cut from Checkpoint B head `fbae970`. 9 commits on top — see `git log --oneline checkpoint/checkpoint-b..checkpoint/checkpoint-c`.
- **Plan file:** `docs/superpowers/plans/2026-05-11-checkpoint-c.md` (committed at `0cc42c7`).
- **Mixins promoted to `ENABLED_MIXINS`** (10 active): the four alpha-0 resource trackers + `GLXMixins` + `GlStateManagerMixins` + `RenderLayerMixins` + `ChunkBuilderBuiltChunkMixins` + `BuiltChunkStorageMixins` + `ClientChunkManagerMixins`.
- **Mixins ported + guarded but NOT enabled** (commented out in `ENABLED_MIXINS` with explanation): `WindowMixins`, `MinecraftClientMixins`, `RenderSystemMixins` (waiting on Pipeline.buildNative fix); `WorldRendererCoreMixins` (waiting on `ChunkProxy.rebuildSingle` implementation).
- **Mixins explicitly deferred to Checkpoint D** (still in `src/deferred/java/`): `BuiltBufferMixins`, `ChunkBuilderMixins`, `SectionBuilderMixins`, `GameRendererMixins`, `BufferRendererMixins`, plus the PBR-vertex chain (`PBRVertexConsumer` + 4 siblings, `BlockModelRendererMixins`, `FluidRendererMixins`). Rationale in `KNOWN-ISSUES.md`.
- **G4 evidence:** `Get-ChildItem -Recurse src/main/java/com/radiance/client/proxy/ | Select-String 'BuiltBuffer|VertexConsumer'` matches only `RadianceBufferAdapter.java` (the boundary), `RadianceVertexConsumer.java` (the type itself), `RadianceBufferHandle.java` (Javadoc), and `ChunkProxy.java` (TODO comments — no symbol use).
- **G6 blocker (unchanged from Checkpoint B):** `Pipeline.buildNative(long)` throws uncaught C++ exception in `core.dll`. Phase 0 of the Checkpoint C plan (Tasks 2–4) walks the bisect; needs user-driven `runClient` cycles.
- **G7 blocker:** `ChunkProxy.rebuildSingle` is stubbed. 1.21+'s `SectionBuilder.RenderData` shape doesn't exist in 1.20.1; the 1.20.1 equivalent (`ChunkBuilder.BuiltChunk.RebuildTask.render`) needs to be intercepted via a new mixin or re-implemented. Phase 3 of the Checkpoint C plan (Tasks 18–20).
- **Test suite:** 25 tests passing (23 from Checkpoint B + 2 new for `RadianceBufferAdapter`).
- **MCVR side:** branch `mc/1.20.1` head `2e88626` — 64 stub JNI exports added for Options. Refreshed `core.dll` and `core.lib` sit in `natives/windows/` (gitignored). If you rebuild MCVR elsewhere, refresh the natives manually.
- **Branch backup:** push to `origin/checkpoint/checkpoint-c` when ready (no PR per user — branch stays on personal fork until MCVR maintainers see a viable product).

## What's done

- **Checkpoint 0b** — Java foundation, 1.20.1 yarn migration, compile-quarantine of 66 deferred files, JUnit 5, `RadianceState`, `RadianceBufferHandle`, JNI `handshake`/`validateAbi`. SHIPPED in merge `a456701`. See `docs/PLAN.md` Part 3.
- **Checkpoint 0c+A** — MCVR `mc/1.20.1` branch built with §4.3.1 handshake decoder; `core.dll` produced; alpha-0 gate G1+G3-recovery CLEARED. SHIPPED on `checkpoint/0c-a` head `bb7aea1`. See `docs/PLAN.md` Part 4 status note and `BUILD-WINDOWS.md`.
- **Checkpoint B (PARTIAL)** — 7 boot-path mixins ported to 1.20.1 + guarded per PRD §4.7; `RadianceState.isRendererPathActive()` added; `Options.readOptions` made tolerant of missing native setters. Vulkan boot path verified working at runtime (G6 log criteria met). `Window`/`MinecraftClient` allowlist promotion staged but reverted because Pipeline.buildNative crashes. SHIPPED on `checkpoint/checkpoint-b` head `91731a7` (docs commit at end of session).
- **Checkpoint C Java structure (PARTIAL)** — buffer abstraction landed (G4 CLEARED); 4 new world/chunk mixins promoted; `WorldRendererCoreMixins` written fresh against 1.20.1 yarn (parked outside `ENABLED_MIXINS` pending runtime rebuild path); `ChunkProxy` ported with `rebuildSingle` stubbed for the runtime phase; MCVR Options stub JNI exports added (64 setters, MCVR commit `2e88626`). G6 + G7 still pending — both require user-driven `runClient` work. SHIPPED on `checkpoint/checkpoint-c` head (see `git log --oneline -1`).

## Next steps (user-driven Phase 0 + Phase 3 of `docs/superpowers/plans/2026-05-11-checkpoint-c.md`)

- **Push the branch** — `git push -u origin checkpoint/checkpoint-c` to back up off the Windows machine. Push MCVR `mc/1.20.1` too. No PR yet.
- **Phase 0: Fix Pipeline.buildNative C++ crash** (clears G6 fully). Approach: enable Vulkan validation layers (`VK_INSTANCE_LAYERS=VK_LAYER_KHRONOS_validation`) on next `runClient`; add `std::cerr` lines at each `throw` site in `MCVR/src/core/render/pipeline.cpp` (lines 53, 104, 135, 150) to bisect which module-wiring step fails. Most likely culprit: `setOrCreateInputImages` returning false on a downstream module after DLSS is skipped (DLSS unavailable → NGX init fail → consumers can't read DLSS output → throw `"Input image not set properly"`). Either harden `Pipeline.assembleDefault` (Java) to omit downstream-dependents when DLSS skips, or make MCVR `buildWorldPipelineBlueprint` fail-open per module. Then re-enable `WindowMixins` + `MinecraftClientMixins` + `RenderSystemMixins` in `MixinPlugin.ENABLED_MIXINS`. See plan Tasks 2–7.
- **Phase 3: Implement `ChunkProxy.rebuildSingle` for 1.20.1** (clears G7). The 1.20.1 equivalent of 1.21's `SectionBuilder.RenderData` is the result of `ChunkBuilder.BuiltChunk.RebuildTask.render(...)`. Either mixin into `RebuildTask.render` to intercept its output, or re-implement section building. Then enable `WorldRendererCoreMixins` in `ENABLED_MIXINS`. Verify by loading a vanilla superflat creative world; terrain should render through Vulkan; F3 should show "Vulkan 1.4". See plan Tasks 18–20.
- **Checkpoint D** target after C completes: entities, particles, sky, weather, clouds, settings essentials → beta-1 / G8. Will need to land the deferred PBR vertex chain (Task 8 in this session's plan was deferred entirely — see `KNOWN-ISSUES.md`).

## What was queued (historical — preserved for reference)

**Implementation Checkpoint 0c+A** — build MCVR's `mc/1.20.1` branch with the §4.3.1 handshake decoder against the current Java head, clear PRD G1+G3-recovery (alpha-0 release gate). All work physically lives on the Windows box.

Authoritative task list is in `docs/PLAN.md` Part 4. Quick summary:

| Task | Description |
|---|---|
| W1 | Verify Windows baseline (Win 10/11 x64, 50 GB free) |
| W2 | Install Visual Studio 2022 Build Tools (Desktop C++ workload) |
| W3 | Install Vulkan SDK 1.3.x (LunarG) |
| W4 | Install CMake 3.27+ (standalone, in PATH) |
| W5 | Install Git for Windows |
| W6 | Install Temurin JDK 17 |
| W7 | Clone Radiance at HEAD `095273e` (or newer) on the Windows box |
| W8 | Regenerate JNI headers; stale-header wipe + spot-check |
| W9 | Clone MCVR with submodules |
| **W10** | **Create MCVR `mc/1.20.1` branch and implement the §4.3.1 handshake decoder (the C++ scope-of-work)** |
| W11 | Configure + build MCVR; verify `dumpbin /exports` shows `handshake` + `validateAbi` |
| W12 | Install MCVR outputs into `natives/windows/` (NOT `src/main/resources/`) |
| W13 | Build the Radiance jar; per-artifact jar verification; wipe `..\mc-test\instance\radiance`; `runClient` smoke test asserting `RadianceState.BOOT_OK` |
| W14 | Author `BUILD-WINDOWS.md` capturing the actual versions you installed |
| W15 | PRD bookkeeping — mark G1+G3-recovery cleared in `docs/PLAN.md` |

## Pass criterion (PRD G1+G3-recovery)

- `core.dll` built from MCVR `mc/1.20.1` branch on user's Windows machine against the current 1.20.1 Java headers.
- `RendererProxy.handshake(12001, Constants.dumpOrdinals())` returns `0` against the head Java commit.
- `RadianceState` transitions `UNINITIALIZED → BOOT_OK`.
- Java `runClient` reaches the main menu via vanilla GL (no renderer mixins active).
- `shaders/` and `modules/` extracted successfully (their absence sets `RENDERER_DISABLED` which fails the gate).
- Optional natives (`core.lib`, Streamline DLLs) may be absent — that's fine, they log WARN.

## Locked §4.3.1 ordinal-table spec (the C++ decoder contract)

`Constants.dumpOrdinals(): long[]` emits this self-describing table. MCVR's C++ decoder must match exactly:

**Header (3 longs):**
```
[0]  ORDINAL_TABLE_MAGIC   = 0x5241445F4F524453  ("RAD_ORDS")
[1]  ORDINAL_TABLE_VERSION = 1
[2]  ORDINAL_TABLE_SECTION_COUNT = 5
```

**Sections (always 5, in this fixed order):**

| Section ID | Constant | Source enum | Active count | Notes |
|---|---|---|---|---|
| 1 | `SECTION_VERTEX_FORMATS` | `Constants.VertexFormats` | 10 | Active first, then reserved entries from `RESERVED_VERTEX_FORMAT_ORDINALS = {10L, 11L, 12L}` (slots vacated by the 1.21→1.20 backport). Total 13 entries → payload 39 longs. |
| 2 | `SECTION_DRAW_MODES` | `Constants.DrawModes` | 8 | All `ENTRY_ACTIVE`. |
| 3 | `SECTION_INDEX_TYPES` | `Constants.IndexTypes` | 2 | All `ENTRY_ACTIVE`. |
| 4 | `SECTION_GEOMETRY_TYPES` | `Constants.GeometryTypes` | 8 | All `ENTRY_ACTIVE`. |
| 5 | `SECTION_RAY_TRACING_FLAGS` | `Constants.RayTracingFlags` | 8 | All `ENTRY_ACTIVE`. |

**Per-section layout:**
```
[s+0]  section-id (1..5, must match table above)
[s+1]  payload-length-in-longs (= 3 × entry-count)
[s+2 .. s+1+payload-length]  entry triples: [entryId, abiValue, flags]
       flags = ENTRY_ACTIVE   (0L)
       flags = ENTRY_RESERVED (1L)
```

For active enum entries: `entryId` = enum ordinal index (loop counter 0..N-1), `abiValue` = `enum.getValue()`. For reserved vertex format entries: `entryId` = `abiValue` = `reservedOrdinal` (10, 11, 12). Vertex section emits active entries first, then reserved entries.

**Recommended C++ mismatch return codes** (not enforced by Java, but useful for debugging):
- `1` — magic mismatch
- `2` — version mismatch
- `3` — section count mismatch
- `4` — unknown section ID / wrong section order
- `5` — section payload-length mismatch / overrun / trailing data
- `6` — entry value mismatch
- `7` — reserved-entry semantics violated

For v1.0, native should accept only `mcVersionId == 12001` (MC 1.20.1). Java's `LinkageError` catch in `RadianceClient.performHandshake` means a missing C++ symbol is graceful (logs and sets `INIT_FAILED`), not a JVM crash — but `BOOT_OK` is what clears the gate.

The full layout, validation requirements, and a C++ reference shape live in `docs/PLAN.md` Part 4 W10.

## Bootstrapping a Windows Claude Code session

When you sit down at the Windows box:

1. Clone the repo (after installing Git for Windows per W5):
   ```cmd
   cd C:\Users\%USERNAME%\Projects
   git clone https://github.com/lavindeep/Radiance.git Radiance-1201
   cd Radiance-1201
   ```
2. Verify you're at HEAD `095273e` (or later):
   ```cmd
   git log --oneline -5
   ```
3. Install Claude Code on Windows (or share the worktree with a Mac session). Open Claude Code in `C:\Users\%USERNAME%\Projects\Radiance-1201`.
4. Tell Claude Code: **"Read docs/HANDOFF.md and docs/PLAN.md Part 4 (Checkpoint 0c+A). We're starting at W1."**
5. Walk through W1–W6 (toolchain installs) one task at a time. Most can be checked with `--version` commands; Claude Code can verify each install before moving to the next.
6. At W10 (the C++ decoder), have Claude Code generate the JNIEXPORT boilerplate and validation arithmetic per `docs/PLAN.md` Part 4 W10 Step 3. You focus on MCVR-side conventions and the reference-table values.

## Files of authority

- **`docs/PRD.md` equivalent:** `docs/PLAN.md` Part 1 (Engineering PRD — locked decisions, gates, FRs, OQs). The PRD is the contract.
- **`docs/PLAN.md`:** the whole plan, including PRD (Part 1), Checkpoint mapping (Part 2), 0b ship status (Part 3), and the executable W1–W15 (Part 4).
- **`docs/HANDOFF.md`:** this file. Read this first; it gets you oriented in 5 minutes.
- **`CLAUDE.md`:** project-level instructions for Claude Code; already reflects MC 1.20.1 baseline post-M0.

## Files of truth (code)

- `src/main/java/com/radiance/client/RadianceClient.java` — `onInitializeClient` → `initializeNativeRenderer` → `performHandshake`. The handshake call site.
- `src/main/java/com/radiance/client/constant/Constants.java` — `dumpOrdinals()` emits the §4.3.1 structured table. The Java source of truth for the JNI ABI.
- `src/test/java/com/radiance/client/constant/ConstantsDumpOrdinalsTest.java` — fixture for the C++ decoder. Run with `--info` and add a temporary `System.out.println` per W10 Step 3 to extract the reference table.
- `src/main/java/com/radiance/client/RadianceState.java` — `BOOT_OK` is the alpha-0 pass criterion.
- `src/main/resources/radiance.mixins.json` — `_deferred_until_implemented` array documents which mixins are quarantined under `src/deferred/java/`.
- `src/main/resources/radiance.accesswidener` — vanilla classes opened up. Post-0b, no 1.21+-only entries remain.
- `build.gradle` — Loom 1.11-SNAPSHOT, Java 17, `runClient --gameDir ../mc-test/instance`, top-level `*.h` cleanup before `compileJava`.

## If something is unclear

The plan went through six review passes; specifics matter. Re-read the relevant section of `docs/PLAN.md` before improvising. Particularly:

- **§4.3.1** (structured ordinal-table spec) — the C++ decoder contract.
- **§4.6** (renderer bring-up path) — what's enabled in alpha-0/1/2 etc. `WorldRendererMixins` is deferred WHOLE; fresh `WorldRendererCoreMixins` written in Checkpoint C, fresh `WorldRendererSkyWeatherMixins` in Checkpoint D.
- **§4.7** (RadianceState + per-injection-type mixin guard patterns) — the contract every renderer mixin must follow once promoted.
- **§10** (Engineering Risk Gates) — G1+G3-recovery is the alpha-0 gate now (G1, G1-recovery, G3 are superseded).
- **Part 4 W10** — the C++ handshake decoder, with reference shape and 9-point validation list.
- **Part 4 W13** — the smoke-test pass criterion (`BOOT_OK`, not just "main menu reached") and the per-artifact jar verification.

## Decisions explicitly off the table

- **OSS-collaborator outreach** — retired in the sixth pass. User does all C++ work with Claude Code as a pair-programming assistant. No public posts will be made.
- **Mixed-version MCVR builds** (1.21.4 native against 1.20.1 Java) — explicitly prohibited per PRD §4.2; the ordinal tables would silently mismatch.
- **HDR, DLSS-G, FSR3-FG, PBR, Linux, macOS** — out of scope for v1.0 (PRD §9 NG-table).

## Strategic fallback (if 0c+A stalls past 4 weeks of focused effort)

Pivot to forking VulkanMod: drop RT entirely and ship a Vulkan-rasterization-only 1.20.1 mod. Not in scope for this checkpoint, just flagged.
