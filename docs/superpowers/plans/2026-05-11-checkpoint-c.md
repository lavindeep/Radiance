# Checkpoint C Implementation Plan — World/Chunk/Buffer Bridge + Pipeline Crash Fix

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Close out the open work from Checkpoint B (Pipeline.buildNative C++ crash; ~44 missing Options JNI exports) and ship Checkpoint C per PRD §4.6 / §10 — buffer abstraction wire-up (G4), world/chunk/buffer mixin promotion, fresh `WorldRendererCoreMixins`, and superflat-world Vulkan render (G7). Re-enables `WindowMixins` + `MinecraftClientMixins` + `RenderSystemMixins` (clearing G6 fully), then makes the leap from main-menu render to in-world render.

**Architecture:** Phased delivery so Java work isn't blocked on MCVR iteration.

- **Phase 0** — MCVR bisect + fix Pipeline crash, add 44 stub Options JNI exports; re-enable boot mixins; clear G6 fully.
- **Phase 1** — Java-only: buffer abstraction wire-up (RadianceBufferAdapter, RadianceVertexConsumer interface, PBRVertexConsumer migration, proxy signature swap). Compile + unit-test only — no `runClient` dependency on Vulkan stack.
- **Phase 2** — Java-only: port + guard 7 world/chunk/buffer mixins; write fresh `WorldRendererCoreMixins` against 1.20.1 yarn. Compile-clean; no runtime enable yet.
- **Phase 3** — Runtime enable + verify G4 + G7 (superflat creative, terrain renders through Vulkan, F3 shows "Vulkan 1.4").
- **Phase 4** — Docs + version bump + branch finish.

**Tech Stack:** Same as Checkpoint B — Fabric Loader 0.16.10 / Yarn 1.20.1+build.10 / Loom 1.11-SNAPSHOT / Mixin 0.8.5 / JDK 17 toolchain / JDK 21 Gradle daemon. Plus MCVR C++ (MSVC 2022, Vulkan SDK 1.3.x, CMake 3.27+).

---

## Context

Checkpoint B shipped as `partial`. `WindowMixins` + `MinecraftClientMixins` + `RenderSystemMixins` are ported, guarded per PRD §4.7, but commented out of `MixinPlugin.ENABLED_MIXINS` because the Vulkan boot path crashes in MCVR's `Pipeline::buildWorldPipelineBlueprint()` immediately after `RadianceState` transitions to `RENDERER_ACTIVE`. Two MCVR throw sites are the candidate culprits:

- `MCVR/src/core/render/pipeline.cpp:135` — `throw std::runtime_error("Output image not set properly");`
- `MCVR/src/core/render/pipeline.cpp:150` — `throw std::runtime_error("Input image not set properly");`

DLSS skip cascade is the likely trigger (NGX init fails on RTX 5070 Ti / driver 596.21, downstream modules can't wire input from `dlss.processed` because that output never materialized). Java's `Pipeline.assembleDefault()` already routes around DLSS when `Options.dlssDEnabled && isNativeModuleAvailable("...dlss...")` is false (`Pipeline.java:435-442`), so the Java-side default is already DLSS-skip-safe. The crash must therefore come from MCVR's C++ pipeline builder failing to honor the Java-side wiring (most likely: it's reading the YAML/disk pipeline instead of the in-memory `assembleDefault` graph, OR a hardcoded module is unconditionally requesting DLSS images, OR one of the *non-DLSS* modules — `tone_mapping` / `post_render` / `ray_tracing` — has a wiring requirement Java isn't honoring).

Beyond the crash: `Options.readOptions` declares ~50 `nativeSet*` methods, but MCVR's `mc/1.20.1` branch only exports 6. The first missing setter (`nativeSetTonemappingMode`) used to throw `UnsatisfiedLinkError` mid-readOptions; Checkpoint B commit `07aa44f` swallows it, but a real fix is stub exports in MCVR.

Per PRD §10 G4 is gated on the buffer abstraction: `grep -r 'BuiltBuffer\|VertexConsumer' src/main/java/com/radiance/client/proxy/` should match only `RadianceBufferAdapter`. Currently `RadianceBufferAdapter.java` is a placeholder; the actual `from(BuiltBuffer)` lands in this checkpoint.

PRD §10 G7 is gated on F3 in-world showing the Vulkan API description (already enforced by `RendererProxy.initRenderer` overwriting `RenderSystem.apiDescription = "Vulkan 1.4"`). The harder requirement is that a superflat creative world *actually renders* through Vulkan — that means the world/chunk/buffer mixins (`WorldRendererCoreMixins`, `BuiltBufferMixins`, `RenderLayerMixins`, `ChunkBuilderMixins`, `ChunkBuilderBuiltChunkMixins`, `BuiltChunkStorageMixins`, `ClientChunkManagerMixins`, `SectionBuilderMixins`) must be enabled, and the Java→C++ buffer transport must be using `RadianceBufferHandle.toByteBuffer()` end to end.

**Branch:** all work on `checkpoint/checkpoint-c`, cut from `checkpoint/checkpoint-b` head `fbae970`.

---

## File Structure

**Java repo (`C:\Users\lavin\Documents\Projects\Radiance-1201`):**

Phase 0 (MCVR fix + Options stubs + re-enable):
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java` — add 3 boot mixins back to allowlist.

Phase 1 (buffer abstraction):
- Modify: `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferAdapter.java` — add `from(BuiltBuffer)`.
- Modify: `src/main/java/com/radiance/client/proxy/buffer/RadianceVertexConsumer.java` — add methods.
- Move: `src/deferred/java/com/radiance/client/vertex/PBRVertexConsumer.java` → `src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java` (and 4 sibling files).
- Move: `src/deferred/java/com/radiance/client/proxy/vulkan/BufferProxy.java` → `src/main/java/com/radiance/client/proxy/vulkan/BufferProxy.java`.
- Move: `src/deferred/java/com/radiance/client/proxy/world/ChunkProxy.java` → `src/main/java/com/radiance/client/proxy/world/ChunkProxy.java`.
- Move: `src/deferred/java/com/radiance/client/proxy/world/EntityProxy.java` → `src/main/java/com/radiance/client/proxy/world/EntityProxy.java`.
- Create: `src/test/java/com/radiance/client/proxy/buffer/RadianceBufferAdapterTest.java`.

Phase 2 (world mixins):
- Move + rewrite (7 files): `BuiltBufferMixins`, `RenderLayerMixins`, `ChunkBuilderMixins`, `ChunkBuilderBuiltChunkMixins`, `BuiltChunkStorageMixins`, `SectionBuilderMixins`, plus the already-`client`-listed `ClientChunkManagerMixins` (it's in `radiance.mixins.json` but not yet in `ENABLED_MIXINS`).
- Create: `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererCoreMixins.java` — fresh write against 1.20.1.
- Modify: `src/main/resources/radiance.mixins.json` — move 7 entries from `_deferred_until_implemented` into `client`.
- Modify: `src/main/resources/radiance.accesswidener` — add field/method openers for `WorldRenderer` private state, `ChunkBuilder.BuiltChunk` data, `RenderPhase` constants (collect during the port).
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java` — promote 8 mixins.

Phase 3 (verification):
- No source changes. `runClient` + superflat world load + Select-String log checks.

Phase 4 (docs + ship):
- Modify: `KNOWN-ISSUES.md` — remove the Pipeline.buildNative entry; trim Options stubs entry; possibly add new Checkpoint D items.
- Modify: `docs/PLAN.md` — mark G4, G6, G7 CLEARED + Checkpoint C status note.
- Modify: `docs/HANDOFF.md` — refresh current-state section.
- Modify: `gradle.properties` — bump `mod_version` to `0.3.0-alpha-2`.

**C++ repo (`C:\Users\lavin\Documents\Projects\MCVR`, branch `mc/1.20.1`):**

Phase 0 (Pipeline fix):
- Modify: `src/core/render/pipeline.cpp` — add `std::cerr` traces at each throw site identifying which module/image failed; if root cause is wiring not the throw, fix that. Likely candidates: a hardcoded module enumeration that doesn't read the Java-side `assembleDefault` graph, or DLSS being implicitly required even when Java omits it.

Phase 0 (Options stubs):
- Modify: `src/core/middleware/com_radiance_client_option_Options.cpp` — add ~44 stub `JNIEXPORT void JNICALL Java_com_radiance_client_option_Options_nativeSetXxx(...) {}` exports. Use the canonical Java source list as input.

---

## Tasks

### Task 1 — Branch + baseline

**Files:** none modified yet.

- [ ] **Step 1: Cut branch from Checkpoint B head.**
  ```powershell
  git checkout checkpoint/checkpoint-b
  git pull
  git checkout -b checkpoint/checkpoint-c
  ```

- [ ] **Step 2: Verify clean baseline.**
  Run: `./gradlew test`. Expect: 23 tests pass.
  Run: `./gradlew runClient`. Reach main menu via vanilla GL (6 mixins active). Close.

- [ ] **Step 3: No commit. Known-good starting state.**

---

## Phase 0 — Unblock G6 (Pipeline crash + Options stubs + re-enable boot mixins)

### Task 2 — Capture the Pipeline.buildNative crash signature

**Files:** none modified yet.

- [ ] **Step 1: Re-enable Window+MinecraftClient temporarily.**
  Edit `src/main/java/com/radiance/mixin_related/MixinPlugin.java`. Add to `ENABLED_MIXINS`:
  ```java
  "com.radiance.mixins.vulkan_render_integration.WindowMixins",
  "com.radiance.mixins.vulkan_render_integration.MinecraftClientMixins"
  ```

- [ ] **Step 2: Enable Vulkan validation + verbose loader output.**
  ```powershell
  $env:VK_INSTANCE_LAYERS = "VK_LAYER_KHRONOS_validation"
  $env:VK_LOADER_DEBUG = "all"
  ./gradlew runClient
  ```

- [ ] **Step 3: Capture artifacts.**
  Copy `C:\Users\lavin\Documents\Projects\mc-test\instance\logs\latest.log` and any `hs_err_pid*.log` into a scratch folder outside the repo. Note the last C++ throw text and any preceding `[VUID-…]` validation messages.

- [ ] **Step 4: Revert the temp enable.**
  ```powershell
  git checkout src/main/java/com/radiance/mixin_related/MixinPlugin.java
  ```

- [ ] **Step 5: No commit. We just have crash evidence now.**

---

### Task 3 — Instrument MCVR pipeline.cpp throw sites

**Files:**
- Modify: `C:\Users\lavin\Documents\Projects\MCVR\src\core\render\pipeline.cpp`

- [ ] **Step 1: Add std::cerr trace at each throw.**
  At line 135 (the "Output image not set properly" site), prefix the throw with:
  ```cpp
  std::cerr << "[radiance-mcvr] buildWorldPipelineBlueprint: setOrCreateOutputImages failed for module index "
            << i << " name=" << worldModules_[i]->name() << std::endl;
  ```
  (If `name()` doesn't exist, use whatever accessor is available — `getName()`, `moduleName`, etc. Identify the failing module identifier.)

  At line 150 (the "Input image not set properly" site), prefix with the analogous trace including which input image name failed if the call exposes that. If `setOrCreateInputImages` only returns `bool`, refactor to a tracing version: walk `inputImages` and print `inputImages[k].name()` plus whether the producer module exists.

  At lines 53 and 104 (the other two throws), add a single `std::cerr` line each so they don't appear as silent mystery crashes either.

- [ ] **Step 2: Rebuild MCVR.**
  ```powershell
  cmake --build C:/Users/lavin/Documents/Projects/MCVR/build --config Release --parallel
  cmake --install C:/Users/lavin/Documents/Projects/MCVR/build --config Release
  ```
  Then move `core.dll` from `MCVR/src/main/resources/` into `Radiance-1201/natives/windows/`:
  ```powershell
  Move-Item -Force C:/Users/lavin/Documents/Projects/MCVR/src/main/resources/core.dll C:/Users/lavin/Documents/Projects/Radiance-1201/natives/windows/core.dll
  ```

- [ ] **Step 3: Force re-extraction.**
  ```powershell
  Remove-Item -Force C:/Users/lavin/Documents/Projects/mc-test/instance/radiance/core.dll
  ```

- [ ] **Step 4: Re-run with boot mixins enabled (temp).**
  Re-apply the temp enable from Task 2 Step 1, then `./gradlew runClient`. Capture the std::cerr output from `logs/latest.log` (it flushes through SLF4J via JVM stderr capture; if absent, run from a console and copy the terminal stdout).

- [ ] **Step 5: Identify failing module + image.**
  From the trace, you'll have one of:
  - A module name in the `assembleDefault` graph that fails `setOrCreateOutputImages` (means MCVR can't create one of its declared outputs — Vulkan resource creation issue).
  - A module name + missing input image (means upstream producer didn't run or didn't expose that image — Java/C++ graph mismatch).

  Record the finding here in the plan as evidence and proceed to Task 4 (the fix is conditional on the finding).

- [ ] **Step 6: Revert temp enable, commit MCVR instrumentation.**
  ```powershell
  git checkout src/main/java/com/radiance/mixin_related/MixinPlugin.java
  ```
  In MCVR repo:
  ```powershell
  git -C C:/Users/lavin/Documents/Projects/MCVR add src/core/render/pipeline.cpp
  git -C C:/Users/lavin/Documents/Projects/MCVR commit -m "debug: instrument pipeline.cpp throw sites for module-wiring triage"
  ```

---

### Task 4 — Fix the Pipeline.buildNative root cause

This task is branched. Pick the right path based on Task 3 Step 5 evidence.

**Files (branch A — Java-side graph fix):**
- Modify: `src/main/java/com/radiance/client/pipeline/Pipeline.java`

**Files (branch B — MCVR fail-open):**
- Modify: `C:\Users\lavin\Documents\Projects\MCVR\src\core\render\pipeline.cpp`

**Files (branch C — MCVR module fix):**
- Modify: a specific module source file in `C:\Users\lavin\Documents\Projects\MCVR\src\core\render\modules\`

- [ ] **Step 1: Choose branch.**
  - **Branch A** if a Java module from `Pipeline.assembleDefault()` declares an output the C++ side doesn't recognize, or vice versa. Most likely cause: the YAML module definition includes a DLSS-only input that's required regardless of whether `dlssDEnabled`. Fix: edit `assembleDefault` to omit that input wiring when DLSS is absent (or fix the YAML in `src/main/resources/modules/<module>.yaml`).
  - **Branch B** if the throw is `Input image not set properly` on a module whose missing input is *optional* in the design (i.e., the module would still produce useful output without it). Fix: in `pipeline.cpp:147-150` change `if (!result) throw...` to `if (!result) std::cerr << "warn: skipping module " << i << std::endl; continue;` and also remove the module from the live `worldModules_` list so downstream consumers don't try to read its outputs.
  - **Branch C** if the throw is `Output image not set properly` due to a Vulkan resource creation failure (image format/extent mismatch). Fix the specific module's `createOutputImages` implementation in MCVR — usually a hardcoded format/extent that doesn't match the swapchain.

- [ ] **Step 2: Implement the chosen fix.** Each branch is its own ~30-min surgical edit. Don't overscope — fix only what Task 3 evidence points to.

- [ ] **Step 3: Rebuild MCVR (if Branch B or C) or rebuild Java (if Branch A). Re-run runClient with boot mixins temporarily enabled.**
  Expect: three G6 log lines fire AND `Pipeline.buildNative` returns without throwing. JVM stays alive; main menu renders.

- [ ] **Step 4: If still crashing, return to Task 3 (instrument the next throw site).**

- [ ] **Step 5: Once main menu renders:**
  - Revert temp `MixinPlugin.java` change.
  - Commit the fix on the appropriate repo (Java side OR MCVR side) with message like `fix(pipeline): omit DLSS-only inputs from default graph when DLSS unavailable` or `fix(mcvr-pipeline): fail-open per module on missing input image`.
  - If MCVR side, also commit the new `core.dll` to `natives/windows/` in the Radiance repo with `chore(natives): refresh core.dll after pipeline.buildNative fix`.

---

### Task 5 — Inventory missing Options JNI exports

**Files:**
- Read-only: `src/main/java/com/radiance/client/option/Options.java`
- Read-only: `C:\Users\lavin\Documents\Projects\MCVR\src\core\middleware\com_radiance_client_option_Options.cpp`

- [ ] **Step 1: List all Java-side native setters.**
  ```powershell
  Select-String -Path C:/Users/lavin/Documents/Projects/Radiance-1201/src/main/java/com/radiance/client/option/Options.java -Pattern '^\s*public static native void (nativeSet\w+)\(([^)]*)\);' | ForEach-Object { $_.Matches.Groups[1].Value + "(" + $_.Matches.Groups[2].Value + ")" } | Sort-Object -Unique
  ```
  Save this list as `phase0-options-java-natives.txt` in scratch (do not commit to repo).

- [ ] **Step 2: List MCVR-side exports.**
  ```powershell
  Select-String -Path C:/Users/lavin/Documents/Projects/MCVR/src/core/middleware/com_radiance_client_option_Options.cpp -Pattern 'Java_com_radiance_client_option_Options_(nativeSet\w+)' | ForEach-Object { $_.Matches.Groups[1].Value } | Sort-Object -Unique
  ```

- [ ] **Step 3: Diff. The missing set is what Task 6 stubs.** Confirm count is ~44.

---

### Task 6 — Add stub Options JNI exports in MCVR

**Files:**
- Modify: `C:\Users\lavin\Documents\Projects\MCVR\src\core\middleware\com_radiance_client_option_Options.cpp`

- [ ] **Step 1: For each missing setter, append a no-op export.**
  Pattern (use the exact JNI mangling — `JNIEXPORT void JNICALL` plus the `Java_<package>_<class>_<method>` symbol, with the JNI signature for the parameter list — `Z` for boolean, `F` for float, `I` for int, etc.):
  ```cpp
  extern "C" JNIEXPORT void JNICALL
  Java_com_radiance_client_option_Options_nativeSetTonemappingMode(
      JNIEnv*, jclass, jint /*mode*/, jboolean /*save*/) {
      // stub — wired in a later checkpoint
  }
  ```
  Repeat for the ~44 missing setters identified in Task 5 Step 3. Match the Java argument types: each Java `native` declaration has a JNI signature you can read off (`(IZ)V`, `(FZ)V`, etc.).

- [ ] **Step 2: Rebuild + install MCVR.**
  ```powershell
  cmake --build C:/Users/lavin/Documents/Projects/MCVR/build --config Release --parallel
  cmake --install C:/Users/lavin/Documents/Projects/MCVR/build --config Release
  Move-Item -Force C:/Users/lavin/Documents/Projects/MCVR/src/main/resources/core.dll C:/Users/lavin/Documents/Projects/Radiance-1201/natives/windows/core.dll
  Remove-Item -Force C:/Users/lavin/Documents/Projects/mc-test/instance/radiance/core.dll
  ```

- [ ] **Step 3: Smoke test.**
  Run `./gradlew runClient` (still with Phase 0 boot mixins NOT enabled in `ENABLED_MIXINS`). Expect: no new `UnsatisfiedLinkError` warnings in `logs/latest.log`. The `Options.readOptions: native setter missing` warning from Checkpoint B should be gone.

- [ ] **Step 4: Commit on MCVR.**
  ```powershell
  git -C C:/Users/lavin/Documents/Projects/MCVR add src/core/middleware/com_radiance_client_option_Options.cpp
  git -C C:/Users/lavin/Documents/Projects/MCVR commit -m "feat(options): add stub JNI exports for all Java-side nativeSet* methods"
  ```

- [ ] **Step 5: Commit new core.dll in Radiance.**
  ```powershell
  git add natives/windows/core.dll
  git commit -m "chore(natives): refresh core.dll with Options JNI stubs + pipeline fix"
  ```

---

### Task 7 — Re-enable boot mixins permanently

**Files:**
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java`

- [ ] **Step 1: Promote 3 boot mixins.**
  Replace the commented-out block with three live entries:
  ```java
  "com.radiance.mixins.vulkan_render_integration.WindowMixins",
  "com.radiance.mixins.vulkan_render_integration.MinecraftClientMixins",
  "com.radiance.mixins.vulkan_render_integration.RenderSystemMixins"
  ```
  Remove the explanatory comment about Pipeline.buildNative blocking re-enable (it's no longer accurate).

- [ ] **Step 2: Run full G6 verification.**
  ```powershell
  ./gradlew runClient
  ```
  Reach main menu. Close. Verify all three log lines:
  ```powershell
  Select-String -Path C:/Users/lavin/Documents/Projects/mc-test/instance/logs/latest.log -Pattern 'RendererProxy\.initRenderer returned successfully', "RenderSystem\.apiDescription set to 'Vulkan 1.4'", 'RadianceState transition: BOOT_OK -> RENDERER_ACTIVE'
  ```
  Expect: 3 matches.

- [ ] **Step 3: 5-minute soak at main menu.**
  `./gradlew runClient`, idle on main menu 5 min. No crash, no recurring exceptions in `latest.log`.

- [ ] **Step 4: Commit.**
  ```powershell
  git commit -am "feat(alpha-1): re-enable WindowMixins+MinecraftClientMixins+RenderSystemMixins; G6 fully cleared"
  ```

---

## Phase 1 — Buffer abstraction wire-up (G4)

### Task 8 — Move PBRVertexConsumer + 4 sibling vertex classes back to main

**Files:**
- Move: `src/deferred/java/com/radiance/client/vertex/PBRVertexConsumer.java` → `src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java`
- Move: `src/deferred/java/com/radiance/client/vertex/PBRVertexFormatElements.java` → main
- Move: `src/deferred/java/com/radiance/client/vertex/PBRVertexFormats.java` → main
- Move: `src/deferred/java/com/radiance/client/vertex/StorageVertexConsumerProvider.java` → main
- Move: `src/deferred/java/com/radiance/client/vertex/StorageOutlineVertexConsumerProvider.java` → main

- [ ] **Step 1: Read each file first to inventory 1.21+-only imports.**
  Likely candidates: `VertexConsumer` calls that changed signature in 1.21 (e.g., `.next()` was removed; 1.20.1 still requires `.next()`). `VertexFormatElement` enum renames.

- [ ] **Step 2: Move all five files.**
  ```powershell
  git mv src/deferred/java/com/radiance/client/vertex/PBRVertexConsumer.java src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java
  git mv src/deferred/java/com/radiance/client/vertex/PBRVertexFormatElements.java src/main/java/com/radiance/client/vertex/PBRVertexFormatElements.java
  git mv src/deferred/java/com/radiance/client/vertex/PBRVertexFormats.java src/main/java/com/radiance/client/vertex/PBRVertexFormats.java
  git mv src/deferred/java/com/radiance/client/vertex/StorageVertexConsumerProvider.java src/main/java/com/radiance/client/vertex/StorageVertexConsumerProvider.java
  git mv src/deferred/java/com/radiance/client/vertex/StorageOutlineVertexConsumerProvider.java src/main/java/com/radiance/client/vertex/StorageOutlineVertexConsumerProvider.java
  ```

- [ ] **Step 3: Verify build, fix yarn renames in place.**
  Run: `./gradlew compileJava`. For every error, fix the offending symbol with the 1.20.1 yarn equivalent. Common fixes:
  - `VertexFormatElement.Usage.<NAME>` enum renames between versions — check 1.20.1 yarn-mapped `VertexFormatElement.Usage`.
  - `VertexFormat.DrawMode` / `VertexFormatElement.DataType` membership.
  - `VertexConsumer.next()` may be called in 1.20.1 but absent in 1.21+ — re-add if needed.

- [ ] **Step 4: Commit.**
  ```powershell
  git commit -am "feat(vertex): port PBRVertexConsumer + sibling vertex classes to 1.20.1"
  ```

---

### Task 9 — Add `RadianceVertexConsumer` interface methods (PRD §4.4)

**Files:**
- Modify: `src/main/java/com/radiance/client/proxy/buffer/RadianceVertexConsumer.java`
- Modify: `src/main/java/com/radiance/client/vertex/PBRVertexConsumer.java`

- [ ] **Step 1: Read 1.20.1 `net.minecraft.client.render.VertexConsumer`.**
  Note its method surface (`vertex`, `color`, `texture`, `overlay`, `light`, `normal`, `next`).

- [ ] **Step 2: Define `RadianceVertexConsumer` as a superset.**
  The intent (PRD §4.4) is to let JNI surface depend on Radiance types, not MC types. Replace the empty interface with:
  ```java
  public interface RadianceVertexConsumer {
      RadianceVertexConsumer vertex(float x, float y, float z);
      RadianceVertexConsumer color(int r, int g, int b, int a);
      RadianceVertexConsumer texture(float u, float v);
      RadianceVertexConsumer overlay(int u, int v);
      RadianceVertexConsumer light(int u, int v);
      RadianceVertexConsumer normal(float x, float y, float z);
      void next();
  }
  ```
  The exact set depends on which methods MCVR's JNI side reads — check `MCVR/src/core/middleware/*BufferProxy*.cpp` and `*ChunkProxy*.cpp` for `Call*Method` patterns referencing `vertex`/`color`/etc., and include all of them.

- [ ] **Step 3: Implement on `PBRVertexConsumer`.**
  Add `implements com.radiance.client.proxy.buffer.RadianceVertexConsumer` to the class header. The PBR consumer likely already has these methods (it wraps an upstream `VertexConsumer` and adds PBR extras); just make them return `RadianceVertexConsumer` where they currently return `VertexConsumer`.

- [ ] **Step 4: Build.**
  Run: `./gradlew compileJava`. Expected: clean.

- [ ] **Step 5: Commit.**
  ```powershell
  git commit -am "feat(buffer): define RadianceVertexConsumer surface; PBRVertexConsumer implements it"
  ```

---

### Task 10 — Implement `RadianceBufferAdapter.from(BuiltBuffer)`

**Files:**
- Modify: `src/main/java/com/radiance/client/proxy/buffer/RadianceBufferAdapter.java`
- Create: `src/test/java/com/radiance/client/proxy/buffer/RadianceBufferAdapterTest.java`

- [ ] **Step 1: Write a failing test first.**
  Create `RadianceBufferAdapterTest.java`:
  ```java
  package com.radiance.client.proxy.buffer;

  import org.junit.jupiter.api.Test;
  import static org.junit.jupiter.api.Assertions.*;

  class RadianceBufferAdapterTest {

      @Test
      void fromReturnsHandleWithExpectedShape() {
          // Construct a minimal BuiltBuffer-equivalent fixture and verify the conversion.
          // 1.20.1 BufferBuilder.BuiltBuffer is package-private to render package — use a
          // synthetic test record matching its shape.
          RadianceBufferHandle h = RadianceBufferAdapter.fromRaw(
              /* vertexCount */ 4,
              /* indexCount */ 6,
              /* vertexFormatOrdinal */ 0,  // POSITION_COLOR
              /* indexTypeOrdinal */ 0,     // SHORT
              /* drawModeOrdinal */ 4,      // QUADS
              /* hasData */ true,
              /* centroidArrayPtr */ 0L,
              /* centroidArrayLen */ 0);
          assertEquals(4, h.vertexCount);
          assertEquals(6, h.indexCount);
          assertEquals(true, h.hasData);
      }
  }
  ```

- [ ] **Step 2: Run — verify failure.**
  `./gradlew test --tests RadianceBufferAdapterTest`. Expected: compile error on `fromRaw`.

- [ ] **Step 3: Implement adapter.**
  Replace `RadianceBufferAdapter.java` body with:
  ```java
  package com.radiance.client.proxy.buffer;

  import com.radiance.client.constant.Constants;
  import net.minecraft.client.render.BufferBuilder;

  public final class RadianceBufferAdapter {

      private RadianceBufferAdapter() {}

      public static RadianceBufferHandle from(BufferBuilder.BuiltBuffer buf) {
          BufferBuilder.DrawParameters params = buf.getParameters();
          return new RadianceBufferHandle(
              params.vertexCount(),
              params.indexCount(),
              Constants.VertexFormats.getValue(params.format()),
              Constants.IndexTypes.getValue(params.indexType()),
              Constants.DrawModes.getValue(params.mode()),
              /* hasData */ buf.getBuffer() != null && buf.getBuffer().hasRemaining(),
              /* centroidArrayPtr */ 0L,   // populated by caller if sort centroids exist
              /* centroidArrayLen */ 0);
      }

      // Test-only entry point (no MC dependency).
      static RadianceBufferHandle fromRaw(int vertexCount, int indexCount,
              int vertexFormatOrdinal, int indexTypeOrdinal, int drawModeOrdinal,
              boolean hasData, long centroidArrayPtr, int centroidArrayLen) {
          return new RadianceBufferHandle(vertexCount, indexCount, vertexFormatOrdinal,
              indexTypeOrdinal, drawModeOrdinal, hasData, centroidArrayPtr, centroidArrayLen);
      }
  }
  ```
  Verify `Constants.VertexFormats.getValue(VertexFormat)`, `Constants.IndexTypes.getValue(VertexFormat.IndexType)`, `Constants.DrawModes.getValue(VertexFormat.DrawMode)` exist with those exact signatures. If not, use the existing variants from `BufferProxy.createAndUploadVertexIndexBuffer`.

- [ ] **Step 4: Run — verify pass.**
  `./gradlew test`. Expected: 24 tests pass (23 + 1 new).

- [ ] **Step 5: Commit.**
  ```powershell
  git commit -am "feat(buffer): RadianceBufferAdapter.from(BuiltBuffer); test fixture"
  ```

---

### Task 11 — Port BufferProxy to use RadianceBufferHandle.toByteBuffer()

**Files:**
- Move + modify: `src/deferred/java/com/radiance/client/proxy/vulkan/BufferProxy.java` → `src/main/java/com/radiance/client/proxy/vulkan/BufferProxy.java`

- [ ] **Step 1: Move file.**
  ```powershell
  git mv src/deferred/java/com/radiance/client/proxy/vulkan/BufferProxy.java src/main/java/com/radiance/client/proxy/vulkan/BufferProxy.java
  ```

- [ ] **Step 2: Replace `createAndUploadVertexIndexBuffer(BuiltBuffer)` with `(RadianceBufferHandle, ByteBuffer vertexData, ByteBuffer indexData)`.**
  The new signature reads the buffer descriptor from the handle (via `toByteBuffer()` if a JNI-marshalled descriptor is needed) and uploads the raw bytes. Replace the existing body:
  ```java
  public static VertexIndexBufferHandle createAndUploadVertexIndexBuffer(
          RadianceBufferHandle handle, ByteBuffer vertexData, ByteBuffer indexData) {
      int vertexSize = vertexData.remaining();
      int vertexId = allocateBuffer();
      initializeBuffer(vertexId, vertexSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
      queueUpload(vertexData, vertexSize, vertexId);

      int indexId = allocateBuffer();
      if (indexData != null) {
          int indexSize = indexData.remaining();
          initializeBuffer(indexId, indexSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
          queueUpload(indexData, indexSize, indexId);
      } else {
          int indexSize = handle.indexCount * indexTypeSize(handle.indexTypeOrdinal);
          initializeBuffer(indexId, indexSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
          buildIndexBuffer(indexId, handle.indexTypeOrdinal, handle.drawModeOrdinal,
              handle.vertexCount, handle.indexCount);
      }
      return new VertexIndexBufferHandle(vertexId, indexId);
  }

  private static int indexTypeSize(int indexTypeOrdinal) {
      // 0 = SHORT (2 bytes), 1 = INT (4 bytes) per Constants.IndexTypes
      return indexTypeOrdinal == 0 ? 2 : 4;
  }
  ```

- [ ] **Step 3: Remove the `import net.minecraft.client.render.BuiltBuffer` line.**

- [ ] **Step 4: Update all callers in the deferred ChunkProxy etc.** (skip if those files haven't been moved yet — Task 12 handles them).

- [ ] **Step 5: Build.**
  `./gradlew compileJava`. Expected: clean except for callers in still-deferred files.

- [ ] **Step 6: Commit.**
  ```powershell
  git commit -am "feat(buffer): BufferProxy.createAndUploadVertexIndexBuffer accepts (RadianceBufferHandle, ByteBuffer, ByteBuffer)"
  ```

---

### Task 12 — Port ChunkProxy and EntityProxy

**Files:**
- Move + modify: `src/deferred/java/com/radiance/client/proxy/world/ChunkProxy.java` → main
- Move + modify: `src/deferred/java/com/radiance/client/proxy/world/EntityProxy.java` → main

- [ ] **Step 1: Move both.**
  ```powershell
  git mv src/deferred/java/com/radiance/client/proxy/world/ChunkProxy.java src/main/java/com/radiance/client/proxy/world/ChunkProxy.java
  git mv src/deferred/java/com/radiance/client/proxy/world/EntityProxy.java src/main/java/com/radiance/client/proxy/world/EntityProxy.java
  ```

- [ ] **Step 2: Yarn-fix imports.**
  `BuiltBuffer` → `BufferBuilder.BuiltBuffer` (1.20.1 inner class form). `import net.minecraft.client.render.BuiltBuffer` → `import net.minecraft.client.render.BufferBuilder;` and reference as `BufferBuilder.BuiltBuffer`. Same for any `BuiltBuffer.DrawParameters` → `BufferBuilder.DrawParameters`.

- [ ] **Step 3: Convert every `BuiltBuffer` argument site to use the adapter.**
  Where `BufferProxy.createAndUploadVertexIndexBuffer(builtBuffer)` was called, become:
  ```java
  RadianceBufferHandle handle = RadianceBufferAdapter.from(builtBuffer);
  BufferProxy.createAndUploadVertexIndexBuffer(handle, builtBuffer.getBuffer(), builtBuffer.getSortedBuffer());
  ```

- [ ] **Step 4: Build.**
  `./gradlew compileJava`. Fix remaining 1.21+-only references per error message:
  - `ChunkBuilder.BuiltChunk` is the 1.20.1 type (no rename).
  - `BlockBufferAllocatorStorage` doesn't exist in 1.20.1; the equivalent is `BlockBufferBuilderStorage`. Substitute.
  - `SectionBuilder` is the 1.21+ name for what 1.20.1 calls `ChunkBuilder.BuiltChunk.RebuildTask` rendering logic. Inline the equivalent.

- [ ] **Step 5: Commit.**
  ```powershell
  git commit -am "feat(proxy): port ChunkProxy + EntityProxy to 1.20.1; use RadianceBufferAdapter"
  ```

---

### Task 13 — Verify buffer-abstraction grep (G4 evidence)

**Files:** none modified.

- [ ] **Step 1: Run the grep.**
  ```powershell
  Get-ChildItem -Recurse -File C:/Users/lavin/Documents/Projects/Radiance-1201/src/main/java/com/radiance/client/proxy/ | Select-String -Pattern 'BuiltBuffer|VertexConsumer' | Select-Object -ExpandProperty Filename | Sort-Object -Unique
  ```

- [ ] **Step 2: Validate.**
  Expected output: only `RadianceBufferAdapter.java` (and possibly `RadianceVertexConsumer.java` itself — that one is fine, it owns the type). Any other proxy match means a JNI surface still references MC types — fix before Task 18.

- [ ] **Step 3: No commit. This is just G4 evidence captured for the eventual PLAN.md status note.**

---

## Phase 2 — World mixins port

### Task 14 — Port BuiltBufferMixins + RenderLayerMixins

**Files:**
- Move + modify: `src/deferred/java/com/radiance/mixins/vulkan_render_integration/BuiltBufferMixins.java`
- Move + modify: `src/deferred/java/com/radiance/mixins/vulkan_render_integration/RenderLayerMixins.java`

- [ ] **Step 1: Move both.**
  ```powershell
  git mv src/deferred/java/com/radiance/mixins/vulkan_render_integration/BuiltBufferMixins.java src/main/java/com/radiance/mixins/vulkan_render_integration/BuiltBufferMixins.java
  git mv src/deferred/java/com/radiance/mixins/vulkan_render_integration/RenderLayerMixins.java src/main/java/com/radiance/mixins/vulkan_render_integration/RenderLayerMixins.java
  ```

- [ ] **Step 2: Rewrite BuiltBufferMixins for 1.20.1.**
  Target changes from `@Mixin(BuiltBuffer.class)` to `@Mixin(BufferBuilder.BuiltBuffer.class)`. Any `Lnet/minecraft/client/render/BuiltBuffer;` in `@At(target=...)` becomes `Lnet/minecraft/client/render/BufferBuilder$BuiltBuffer;`. Same for any inner-class refs.

- [ ] **Step 3: Rewrite RenderLayerMixins for 1.20.1.**
  Likely changes: `TriState` enum (1.21+) has no 1.20.1 equivalent — replace with `boolean`. Verify `RenderLayer.of(...)` signature matches 1.20.1 yarn.

- [ ] **Step 4: Apply PRD §4.7 guards.**
  Use `RadianceState.isRendererActive()` (post-init) on each inject/redirect/modifyArg per the table.

- [ ] **Step 5: Move JSON entries.**
  Edit `src/main/resources/radiance.mixins.json`: move `vulkan_render_integration.BuiltBufferMixins` and `vulkan_render_integration.RenderLayerMixins` from `_deferred_until_implemented` to `client`.

- [ ] **Step 6: Verify build.**
  `./gradlew compileJava`. Clean.

- [ ] **Step 7: Add to allowlist.**
  In `MixinPlugin.java`, append:
  ```java
  "com.radiance.mixins.vulkan_render_integration.BuiltBufferMixins",
  "com.radiance.mixins.vulkan_render_integration.RenderLayerMixins"
  ```

- [ ] **Step 8: runClient smoke.** Reach main menu. No `Mixin apply failed`.

- [ ] **Step 9: Commit.**
  ```powershell
  git commit -am "feat(mixin): port + enable BuiltBufferMixins + RenderLayerMixins for 1.20.1"
  ```

---

### Task 15 — Port ChunkBuilderMixins family

**Files:**
- Move + modify: `src/deferred/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderMixins.java`
- Move + modify: `src/deferred/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderBuiltChunkMixins.java`
- Move + modify: `src/deferred/java/com/radiance/mixins/vulkan_render_integration/BuiltChunkStorageMixins.java`
- Move + modify: `src/deferred/java/com/radiance/mixins/vulkan_render_integration/SectionBuilderMixins.java`
- Move + modify: `src/deferred/java/com/radiance/mixin_related/extensions/vulkan_render_integration/IChunkBuilderExt.java`

- [ ] **Step 1: Move all five.**
  ```powershell
  git mv src/deferred/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderMixins.java src/main/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderMixins.java
  git mv src/deferred/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderBuiltChunkMixins.java src/main/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderBuiltChunkMixins.java
  git mv src/deferred/java/com/radiance/mixins/vulkan_render_integration/BuiltChunkStorageMixins.java src/main/java/com/radiance/mixins/vulkan_render_integration/BuiltChunkStorageMixins.java
  git mv src/deferred/java/com/radiance/mixins/vulkan_render_integration/SectionBuilderMixins.java src/main/java/com/radiance/mixins/vulkan_render_integration/SectionBuilderMixins.java
  git mv src/deferred/java/com/radiance/mixin_related/extensions/vulkan_render_integration/IChunkBuilderExt.java src/main/java/com/radiance/mixin_related/extensions/vulkan_render_integration/IChunkBuilderExt.java
  ```

- [ ] **Step 2: Per-file 1.20.1 yarn fixups.**
  - `ChunkBuilderMixins` — 1.20.1 `ChunkBuilder` has no `BlockBufferAllocatorStorage`; use `BlockBufferBuilderStorage`. `SectionBuilder` injects in 1.21+ become injects in `ChunkBuilder.BuiltChunk.RebuildTask.render` in 1.20.1.
  - `ChunkBuilderBuiltChunkMixins` — confirm `@Mixin(ChunkBuilder.BuiltChunk.class)` target. AW may need `accessible class net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk` if currently missing.
  - `BuiltChunkStorageMixins` — `@Mixin(BuiltChunkStorage.class)` (1.20.1 has this class; verify exact yarn name).
  - `SectionBuilderMixins` — retarget at the 1.20.1 chunk-render path (the `BuiltChunk.RebuildTask.render` method is the rough equivalent of 1.21+'s `SectionBuilder.build`). May need to delete entirely if all behavior moved to `ChunkBuilderBuiltChunkMixins`.
  - `IChunkBuilderExt` — namespaced methods (`neoVoxelRT$...`) stay; check `@Shadow` field types match 1.20.1.

- [ ] **Step 3: Apply PRD §4.7 guards (use `isRendererActive`).**

- [ ] **Step 4: Move JSON entries.** All four mixin entries from `_deferred_until_implemented` to `client`.

- [ ] **Step 5: AW additions.**
  Likely additions to `radiance.accesswidener` (verify by error message):
  ```
  accessible class net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask
  accessible field net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk origin Lnet/minecraft/util/math/BlockPos;
  accessible method net/minecraft/client/render/BlockBufferBuilderStorage get (Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/BufferBuilder;
  ```

- [ ] **Step 6: Verify build.** `./gradlew compileJava`. Clean.

- [ ] **Step 7: Add to allowlist (4 entries).**

- [ ] **Step 8: runClient smoke (main menu only — no world load yet).** No Mixin apply errors.

- [ ] **Step 9: Commit.**
  ```powershell
  git commit -am "feat(mixin): port + enable ChunkBuilder family mixins for 1.20.1"
  ```

---

### Task 16 — Enable ClientChunkManagerMixins

**Files:**
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java`

- [ ] **Step 1: Read existing ClientChunkManagerMixins.**
  It's already in `radiance.mixins.json` `client` array and `src/main/java/`. Just needs to be added to `ENABLED_MIXINS`.

- [ ] **Step 2: Verify guards.**
  Walk the file. Add `RadianceState.isRendererActive()` (post-init) checks if missing.

- [ ] **Step 3: Append to allowlist.**
  ```java
  "com.radiance.mixins.vulkan_render_integration.ClientChunkManagerMixins"
  ```

- [ ] **Step 4: runClient smoke.** Main menu reaches.

- [ ] **Step 5: Commit.**
  ```powershell
  git commit -am "feat(mixin): enable ClientChunkManagerMixins"
  ```

---

### Task 17 — Write fresh WorldRendererCoreMixins for 1.20.1

**Files:**
- Create: `src/main/java/com/radiance/mixins/vulkan_render_integration/WorldRendererCoreMixins.java`
- Modify: `src/main/resources/radiance.mixins.json`
- Modify: `src/main/resources/radiance.accesswidener` (likely)
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java`

The 0b split did not happen — `WorldRendererMixins.java` (the whole upstream 1.21.4 file) remains in `src/deferred/java/`. Per PRD §4.6 amendment we write a fresh core-scope mixin from scratch against 1.20.1's `WorldRenderer` directly.

- [ ] **Step 1: Read 1.20.1 yarn `net.minecraft.client.render.WorldRenderer`.**
  Identify inject points needed for the core terrain/chunk path. Targets to consider (verify each exists in 1.20.1):
  - `render(MatrixStack, float, long, boolean, Camera, GameRenderer, LightmapTextureManager, Matrix4f)` — main render entry.
  - `renderLayer(RenderLayer, MatrixStack, double, double, double, Matrix4f)` — per-layer terrain submission.
  - `setupTerrain(Camera, Frustum, boolean, boolean)` — visibility prep.
  - `chunkRenderDispatcher` / `chunks` field shadows.

- [ ] **Step 2: Sketch inject set.**
  Minimal Checkpoint C scope:
  - `@Inject(at=HEAD, cancellable=true)` on `renderLayer` — when `RadianceState.isRendererActive()`, route to `ChunkProxy.rebuild(camera) + Vulkan dispatch` instead of vanilla GL draw. Cancel CI.
  - `@Inject(at=HEAD)` on `setupTerrain` — pass camera + frustum to `ChunkProxy.updateFrustum(...)` for the Vulkan side.
  - `@Redirect` on framebuffer beginWrite/endWrite — make Vulkan side own the world FBO output.
  - Skip the sky/weather/cloud injects — those are Checkpoint D (`WorldRendererSkyWeatherMixins`).

- [ ] **Step 3: Write the file.**
  Boilerplate:
  ```java
  package com.radiance.mixins.vulkan_render_integration;

  import com.radiance.client.RadianceState;
  import com.radiance.client.proxy.world.ChunkProxy;
  import net.minecraft.client.render.Camera;
  import net.minecraft.client.render.Frustum;
  import net.minecraft.client.render.RenderLayer;
  import net.minecraft.client.render.WorldRenderer;
  import net.minecraft.client.util.math.MatrixStack;
  import org.joml.Matrix4f;
  import org.spongepowered.asm.mixin.Mixin;
  import org.spongepowered.asm.mixin.injection.At;
  import org.spongepowered.asm.mixin.injection.Inject;
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

  @Mixin(WorldRenderer.class)
  public abstract class WorldRendererCoreMixins {

      @Inject(method = "renderLayer(...exact 1.20.1 signature...)V",
          at = @At("HEAD"), cancellable = true)
      private void radianceRenderLayer(RenderLayer layer, MatrixStack matrices,
              double cameraX, double cameraY, double cameraZ, Matrix4f projection,
              CallbackInfo ci) {
          if (!RadianceState.isRendererActive()) return;
          ChunkProxy.dispatchLayer(layer, matrices, cameraX, cameraY, cameraZ, projection);
          ci.cancel();
      }

      @Inject(method = "setupTerrain(...exact 1.20.1 signature...)V", at = @At("HEAD"))
      private void radianceSetupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum,
              boolean spectator, CallbackInfo ci) {
          if (!RadianceState.isRendererActive()) return;
          ChunkProxy.updateFrustum(camera, frustum);
      }
  }
  ```
  Fill in the exact 1.20.1 yarn signatures. Add `ChunkProxy.dispatchLayer(...)` / `ChunkProxy.updateFrustum(...)` methods as needed (or shim through existing `ChunkProxy.rebuild(camera)` if that's the closer fit).

- [ ] **Step 4: Add JSON entry + AW openers.**
  In `radiance.mixins.json` `client` array: append `"vulkan_render_integration.WorldRendererCoreMixins"`. Add to `radiance.accesswidener` any `@Shadow` field/method that compile reveals.

- [ ] **Step 5: Build.**
  `./gradlew compileJava`. Iterate on yarn signatures until clean.

- [ ] **Step 6: Add to allowlist.**

- [ ] **Step 7: runClient smoke.** Main menu reaches; no Mixin apply errors. (World render not tested yet — that's Task 18.)

- [ ] **Step 8: Commit.**
  ```powershell
  git commit -am "feat(mixin): WorldRendererCoreMixins (1.20.1 core terrain/chunk hook)"
  ```

---

## Phase 3 — Verification (G4 + G7)

### Task 18 — Load a vanilla superflat creative world

**Files:** none modified.

- [ ] **Step 1: runClient.**
  ```powershell
  ./gradlew runClient
  ```

- [ ] **Step 2: Click through to a fresh superflat world.**
  Main Menu → Singleplayer → Create New World → World Type: Superflat → Game Mode: Creative → Create.

- [ ] **Step 3: Classify outcome by `latest.log` tail + on-screen state.**
  - **Outcome A — black screen with no crash.** Native renderer is alive but world data isn't reaching Vulkan. Most likely cause: a mixin's @Inject(cancellable) is cancelling vanilla GL draw but Vulkan side never gets called. Check `ChunkProxy.rebuild` / `dispatchLayer` log statements (add `[radiance] dispatchLayer fired` log if missing). Re-runClient.
  - **Outcome B — JVM crashes with `EXCEPTION_ACCESS_VIOLATION` in core.dll.** MCVR-side bug in chunk submission. Add `std::cerr` traces to `MCVR/src/core/middleware/com_radiance_client_proxy_world_ChunkProxy.cpp` `rebuildSingle` entry and walk inputs. Iterate.
  - **Outcome C — JVM throws Java exception (NullPointerException, AssertionError).** Java-side mixin bug. Fix the offending mixin's guard or input handling.
  - **Outcome D — Terrain renders correctly.** Pass. F3 to confirm Vulkan API description.

- [ ] **Step 4: Test passes:**
  - Walk 100 blocks. No crash.
  - Place a torch on the floor. No crash.
  - Break the torch. No crash.
  - 5 minutes of standing/walking idle. No recurring exception > 5/minute.
  - F3 ON. Confirm the Vulkan API line shows the right description.

- [ ] **Step 5: Commit on Outcome D.**
  ```powershell
  git commit --allow-empty -m "verify(alpha-2): superflat creative renders through Vulkan; G7 cleared"
  ```

---

### Task 19 — Triage loop (if Task 18 didn't land Outcome D)

This repeats until Task 18 yields Outcome D.

- [ ] **Step 1: Read `latest.log` from bottom.** First ERROR or last line before exit.

- [ ] **Step 2: Categorize.**
  - Mixin apply failure → fix `@At(target=...)` for 1.20.1 yarn.
  - Missing accesswidener → add entry.
  - Native crash → MCVR iteration loop (instrument the relevant proxy `.cpp` entry).
  - NullPointerException in `dispatchLayer` → likely `MinecraftClient.getInstance().worldRenderer` field shadow returning null. Check `WorldRendererCoreMixins` shadow targets.

- [ ] **Step 3: Patch + re-runClient.** Each fix is its own commit.

- [ ] **Step 4: Fail-open escape hatch.**
  If a single fix exceeds 1 hour: drop the offending mixin from `ENABLED_MIXINS`, document in `KNOWN-ISSUES.md`, accept the partial. Checkpoint C ships with the largest stable subset, with G7 either CLEARED or PARTIAL depending on whether terrain rendered.

---

### Task 20 — Final F3 / G7 evidence

**Files:** none modified.

- [ ] **Step 1: With superflat world loaded, take an F2 screenshot.**
  Should produce a valid PNG (PRD §11.2 step 9).

- [ ] **Step 2: F3+T (resource reload).**
  Does not crash.

- [ ] **Step 3: Verify log line.**
  ```powershell
  Select-String -Path C:/Users/lavin/Documents/Projects/mc-test/instance/logs/latest.log -Pattern "RenderSystem\.apiDescription set to 'Vulkan 1.4'"
  ```
  Match count ≥ 1.

---

## Phase 4 — Docs + ship

### Task 21 — Update KNOWN-ISSUES.md

**Files:**
- Modify: `KNOWN-ISSUES.md`

- [ ] **Step 1: Remove fixed items.**
  Delete the entire `## Vulkan boot path is wired but blocked on Pipeline.buildNative C++ crash` section. Delete `## GameRendererMixins deferred to Checkpoint C` if Task 17 / world rendering doesn't require GameRenderer changes — otherwise keep with updated rationale.

- [ ] **Step 2: Trim Options stubs entry.**
  Update to reflect that ~44 stubs are now added in MCVR; remaining work is wiring them through Vulkan when individual options are implemented.

- [ ] **Step 3: Add new entries if surfaced during Phase 3.**
  - World rendering glitches (translucent water, etc.) per PRD §10 "translucent-water glitches are a beta-1 KNOWN-ISSUE".
  - Any mixin dropped via Task 19 escape hatch.
  - Nether/end not yet supported (Checkpoint D).

- [ ] **Step 4: Commit.**
  ```powershell
  git commit -am "docs(known-issues): close Checkpoint B pipeline crash + Options stubs; open Checkpoint D items"
  ```

---

### Task 22 — Update PLAN.md status note

**Files:**
- Modify: `docs/PLAN.md`

- [ ] **Step 1: Mark G4, G6, G7 CLEARED in §10 risk gates table.**
  Update each row with `CLEARED 2026-MM-DD via Checkpoint C` plus commit-SHA evidence.

- [ ] **Step 2: Append a Part-4-style "Checkpoint C SHIPPED" status note.**
  Sections: branch, mixins enabled (count + names), MCVR commits, evidence (grep result for G4, log lines for G6+G7), any deferred items.

- [ ] **Step 3: Commit.**
  ```powershell
  git commit -am "docs(plan): mark G4 + G6 + G7 CLEARED; Checkpoint C status note"
  ```

---

### Task 23 — Update HANDOFF.md

**Files:**
- Modify: `docs/HANDOFF.md`

- [ ] **Step 1: Rewrite current-state section.**
  - Branch: `checkpoint/checkpoint-c`
  - Latest head SHA
  - alpha-2 status: SHIPPED if G4+G6+G7 cleared, PARTIAL otherwise
  - Mixins active: count + categories
  - Next target: Checkpoint D (entities, particles, sky, weather, essentials settings screen → beta-1 / G8)

- [ ] **Step 2: Commit.**
  ```powershell
  git commit -am "docs(handoff): refresh current-state for Checkpoint C ship"
  ```

---

### Task 24 — Version bump + final build

**Files:**
- Modify: `gradle.properties`

- [ ] **Step 1: Bump `mod_version`.**
  Current: `0.2.0-alpha-1` (per Checkpoint B handoff). Bump to `0.3.0-alpha-2`.

- [ ] **Step 2: Full build.**
  ```powershell
  ./gradlew build
  ```
  Expected: produces `build/libs/Radiance-0.3.0-alpha-2.jar`. All tests pass (24 minimum).

- [ ] **Step 3: Commit.**
  ```powershell
  git commit -am "chore(release): bump mod_version to 0.3.0-alpha-2"
  ```

---

### Task 25 — Finish branch

- [ ] **Step 1: Invoke `superpowers:finishing-a-development-branch`.**

- [ ] **Step 2: Choose Option 2 (push only — no upstream PR per user instruction).**
  ```powershell
  git push -u origin checkpoint/checkpoint-c
  git -C C:/Users/lavin/Documents/Projects/MCVR push -u origin mc/1.20.1
  ```

- [ ] **Step 3: Report to user.**

---

## MCVR Iteration Loop (when any task triggers a C++ crash)

Trigger: JVM exits with `EXCEPTION_ACCESS_VIOLATION`, `std::abort`, `EXCEPTION_UNCAUGHT_CXX_EXCEPTION`, or Vulkan validation message in `latest.log` / `hs_err_pid*.log`.

**Repo:** `C:\Users\lavin\Documents\Projects\MCVR` on `mc/1.20.1`.

- [ ] **Step 1: Capture `latest.log` and `hs_err_pid*.log` to scratch.**

- [ ] **Step 2: Re-run with VK validation if not already enabled.**
  ```powershell
  $env:VK_INSTANCE_LAYERS = "VK_LAYER_KHRONOS_validation"
  ./gradlew runClient
  ```

- [ ] **Step 3: Identify failing call site.**
  - For known-throw crashes: re-instrument the throw site as in Task 3.
  - For access-violation crashes: `dumpbin /disasm core.dll` around the offset, or rebuild with `/Zi /DEBUG` (Release with debug info) and use Windows debugger.

- [ ] **Step 4: Patch MCVR source.**

- [ ] **Step 5: Rebuild + install.**
  ```powershell
  cmake --build C:/Users/lavin/Documents/Projects/MCVR/build --config Release --parallel
  cmake --install C:/Users/lavin/Documents/Projects/MCVR/build --config Release
  Move-Item -Force C:/Users/lavin/Documents/Projects/MCVR/src/main/resources/core.dll C:/Users/lavin/Documents/Projects/Radiance-1201/natives/windows/core.dll
  Remove-Item -Force C:/Users/lavin/Documents/Projects/mc-test/instance/radiance/core.dll
  ```

- [ ] **Step 6: Re-runClient from the failing task.**

- [ ] **Step 7: Commit MCVR fix on `mc/1.20.1` AND refresh-core-dll commit on Radiance.**

**Time-box:** ~15 min per iteration. After 5 iterations on the same task, write up findings in HANDOFF.md and degrade to fail-open.

---

## Self-Review

**1. Spec coverage.**
- G4 (`grep` for `BuiltBuffer|VertexConsumer` in proxy/ matches only adapter): Tasks 9, 10, 11, 12, 13.
- G6 full (3 log lines + main menu renders + 5-min soak): Tasks 4, 7.
- G7 (F3 shows Vulkan API + terrain renders): Tasks 17, 18, 20.
- PRD §4.6 boot-renderer wiring: Task 7 (re-enable RenderSystemMixins alongside Window+MinecraftClient).
- PRD §4.7 guards: applied per-mixin in Tasks 14, 15, 16, 17.
- BufferRendererMixins explicitly NOT promoted in Checkpoint C — its `BufferProxy` JNI surface is now in main, but BufferRenderer's draw hook needs the same `BuiltBuffer$BuiltBuffer` adapter pattern, which is best done after world rendering is verified. Add to Checkpoint D scope (note in HANDOFF.md, Task 23).
- GameRendererMixins similarly deferred — Checkpoint D.

**2. Placeholder scan.** No TBDs. Two "verify exact 1.20.1 yarn signature" notes in Task 17 — these are real yarn lookups the executor needs to do, not punted work. The Task 4 branching (A/B/C) is necessary because the right fix depends on Task 3's evidence — we can't choose blindly.

**3. Type consistency.**
- `RadianceBufferHandle.toByteBuffer()` returns 40-byte LE ByteBuffer per existing implementation; `BufferProxy.createAndUploadVertexIndexBuffer` in Task 11 reads the handle's fields directly (not via toByteBuffer marshalling) because Java→Java doesn't need serialization. JNI calls inside that method continue to pass `MemoryUtil.memAddress(ByteBuffer)`.
- `RadianceBufferAdapter.from(BufferBuilder.BuiltBuffer)` in Task 10 is the only producer; Task 12 ChunkProxy/EntityProxy callers consume it. Consistent.
- `RadianceVertexConsumer` interface in Task 9 has the same method set across declaration and `PBRVertexConsumer implements` — verified by `./gradlew compileJava`.

**4. Realistic effort.**
- Phase 0: 4-8 hours if pipeline crash is the obvious DLSS cascade; up to 2 days if it's a Vulkan resource creation issue.
- Phase 1: 1-2 days.
- Phase 2: 3-5 days (Task 15 ChunkBuilder family + Task 17 fresh WorldRendererCoreMixins are the time sinks).
- Phase 3: 1 day if Outcome D on first try; otherwise open-ended via Task 19. Cap at 5 iterations per failure pattern.
- Phase 4: half a day.
- Total: 1-3 weeks. PRD §10 allocated 4 weeks for Checkpoint C; this plan stays within budget.

**5. Risk ordering.**
- Phase 0 is critical-path because re-enabling Window+MinecraftClient is a prerequisite to running the renderer at all.
- Phases 1 and 2 are pure Java; even if Phase 3 fails entirely, Phases 0-2 ship as a meaningful intermediate state (alpha-1.5) — boot path lit, buffer abstraction landed, world mixins compile-ready but disabled.
- Phase 3 is the open-ended one. The fail-open escape hatch in Task 19 keeps the branch shippable.

---

## Critical Files Index

- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\client\RadianceState.java` — state machine.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\mixin_related\MixinPlugin.java` — allowlist.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\client\pipeline\Pipeline.java` — Java-side pipeline graph; `assembleDefault` at line 385.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\client\option\Options.java` — ~50 `nativeSet*` declarations.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\client\proxy\buffer\RadianceBufferHandle.java` — 40-byte serialization (done).
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\client\proxy\buffer\RadianceBufferAdapter.java` — currently stub.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\deferred\java\com\radiance\client\proxy\world\ChunkProxy.java` — 1.21+ BuiltBuffer caller; ported in Task 12.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\resources\radiance.mixins.json` — _deferred_until_implemented array.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\resources\radiance.accesswidener` — AW openers.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\docs\PLAN.md` — PRD §4.6, §4.7, §10.
- `C:\Users\lavin\Documents\Projects\MCVR\src\core\render\pipeline.cpp` — 4 throw sites (53, 104, 135, 150).
- `C:\Users\lavin\Documents\Projects\MCVR\src\core\middleware\com_radiance_client_option_Options.cpp` — 6 of ~50 setter exports.

---

## Execution

Plan complete. Two execution options:

1. **Subagent-Driven (recommended)** — one fresh subagent per task, two-stage review between tasks, fast iteration. Especially useful for Phase 2's mechanical mixin-port tasks (14, 15) and the open-ended Phase 3 triage (Task 19).

2. **Inline Execution** — execute in this session via `superpowers:executing-plans` with batch checkpoints. Better if the user wants to watch/learn each yarn rename or wants final say on every commit.

Subagent-driven is recommended because Phase 0's MCVR iteration is exploratory and benefits from fresh context per attempt, and Phase 2's repetitive 1.20.1 yarn fixups are exactly the kind of mechanical work subagents excel at.
