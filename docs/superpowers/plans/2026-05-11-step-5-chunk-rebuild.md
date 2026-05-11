# Step 5 Implementation Plan — ChunkProxy.rebuildSingle for 1.20.1

> **For agentic workers:** REQUIRED SUB-SKILL: `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Get vanilla superflat creative-world **terrain to render through Vulkan** (G7 visual). This is the chunk-rebuild + per-section-buffer pipeline that was stubbed in Checkpoint C Phase 1. After this lands, enable `WorldRendererCoreMixins` and verify terrain paints.

**Architecture:** 1.20.1's chunk-build path is `ChunkBuilder.BuiltChunk.RebuildTask.render(...)` returning a `ChunkBuilder.ChunkData` (with a `Map<RenderLayer, BufferBuilder.BuiltBuffer>` of per-layer buffers). 1.21+'s `SectionBuilder.RenderData` doesn't exist in 1.20.1. Approach: write a mixin into `RebuildTask.render(...)` at TAIL that captures the result, walks the buffers, uploads each to MCVR via `BufferProxy.createAndUploadVertexIndexBuffer`, then calls `ChunkProxy.rebuildSingle(originX, originY, originZ, index, geometryCount, ...pointer args...)` to register the section's geometry with MCVR's chunk storage.

**Tech Stack:** Same as Checkpoint C. Plus MCVR C++ (need to un-exclude `ChunkProxy.cpp` and reconcile JNI signature mismatches).

---

## Context

State at end of Checkpoint C autonomous session:
- ✅ Pipeline.buildNative C++ crash fixed.
- ✅ Boot path stable — JVM uptime 3+ minutes, all G6 log lines fire.
- ✅ Buffer abstraction in place (`RadianceBufferHandle`, `RadianceBufferAdapter`, `BufferProxy.createAndUploadVertexIndexBuffer(handle, vertexData, indexData)`).
- ✅ `ChunkProxy` ported to 1.20.1 but `rebuildSingle(...)` and the overload `rebuildSingle(ChunkRendererRegion, ChunkBuilder, IChunkBuilderExt, BuiltChunk, BlockBufferBuilderStorage, boolean)` throw `UnsupportedOperationException`.
- ✅ `ChunkBuilderBuiltChunkMixins` + `BuiltChunkStorageMixins` + `ClientChunkManagerMixins` enabled.
- ✅ `WorldRendererCoreMixins` written (parked outside `ENABLED_MIXINS` because enabling without working `rebuildSingle` cancels vanilla terrain and immediately throws).
- ✅ MCVR `BufferProxy.cpp` un-excluded from CMake build — JNI surface for buffer ops works.
- ❌ MCVR `ChunkProxy.cpp` still excluded from build.
- ❌ Java `initNative(int)` (1 arg) vs C++ `initNative(JNIEnv*, jclass, jint, jint, jint, jint, jint)` (5 args). JNI doesn't type-check; calling Java's 1-arg version would push 1 int and let C++ read 4 garbage ints. Must reconcile.
- ❌ Vanilla menu doesn't paint (red screen) — but JVM is stable. World rendering depends on chunk rebuild working first.

PRD G7 spec (`docs/PLAN.md` §10):
> Superflat terrain renders through the Vulkan path. F3 (in-world) reports a Vulkan API description. Place/break works.

Branch: `checkpoint/checkpoint-c`. Will continue on the same branch (no new branch needed) until Step 5 lands, then ship as alpha-2.

---

## File Structure

**Java repo (`C:\Users\lavin\Documents\Projects\Radiance-1201`):**

Phase A (signature reconciliation):
- Modify: `src/main/java/com/radiance/client/proxy/world/ChunkProxy.java` — change `initNative(int)` to `initNative(int chunkNum, int sizeX, int sizeY, int sizeZ, int bottomSectionCoord)`. Capture sizeX/Y/Z somewhere (probably via `BuiltChunkStorageMixins`).
- Modify: `src/main/java/com/radiance/mixins/vulkan_render_integration/BuiltChunkStorageMixins.java` — expand the `@ModifyVariable(method=createChunks)` hook to pass sizeX/Y/Z + bottomSectionCoord to ChunkProxy.init().

Phase B (RebuildTask intercept):
- Create: `src/main/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderRebuildTaskMixins.java` — new mixin targeting `net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask`. Intercepts `render(float, float, float, ChunkRendererRegion)` (verify exact signature) at TAIL, captures the resulting `ChunkData`, walks its `buffers` map, sends each to ChunkProxy.
- Modify: `src/main/resources/radiance.mixins.json` — add the new mixin to `client` array.
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java` — append to `ENABLED_MIXINS`.

Phase C (ChunkProxy implementation):
- Modify: `src/main/java/com/radiance/client/proxy/world/ChunkProxy.java` — replace `UnsupportedOperationException` stubs with real implementations. Probably: `rebuildSingle(BuiltChunk, boolean)` is now a no-op (vanilla drives it); the work happens via the mixin intercept above. Or: keep the orchestration in `ChunkProxy.rebuild(Camera)` for prioritization, but have `rebuildSingle` directly invoke vanilla's `RebuildTask.render` and let the mixin capture results.

Phase D (Enable + verify):
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java` — promote `WorldRendererCoreMixins`.
- User-driven runClient: load superflat creative, walk around, observe.

**C++ repo (`C:\Users\lavin\Documents\Projects\MCVR`, branch `mc/1.20.1`):**

Phase 0 (MCVR build prep):
- Modify: `src/core/CMakeLists.txt` — remove the ChunkProxy.cpp exclusion line.
- Verify: `cmake --build` succeeds. If not, fix C++ compile errors.
- Possibly modify: `src/core/middleware/com_radiance_client_proxy_world_ChunkProxy.cpp` — JNI argument mismatch fixes if the build fails.

---

## Tasks

### Task 1 — Un-exclude ChunkProxy.cpp from MCVR build

**Files:**
- Modify: `C:\Users\lavin\Documents\Projects\MCVR\src\core\CMakeLists.txt`

- [ ] **Step 1: Remove the ChunkProxy exclusion.**
  ```
  list(FILTER SOURCE_FILES EXCLUDE REGEX "com_radiance_client_proxy_world_ChunkProxy\\.cpp$")
  ```
  Delete that line (or comment it out with a note pointing to Step 5 of this plan).

- [ ] **Step 2: Rebuild MCVR.**
  ```powershell
  cmake --build C:\Users\lavin\Documents\Projects\MCVR\build --config Release --parallel
  ```
  Outcomes:
  - **Builds clean:** proceed to Step 3.
  - **C++ compile error:** Most likely cause is a method signature in `chunks.hpp` or `chunks.cpp` that the C++ side calls but doesn't match Java's pointer-argument layout. Fix the C++ side, or stub the broken methods. Don't proceed until clean build.

- [ ] **Step 3: Verify JNI exports.**
  ```powershell
  dumpbin /exports C:\Users\lavin\Documents\Projects\MCVR\build\src\core\Release\core.dll | findstr "ChunkProxy"
  ```
  Expect: `initNative`, `updateSectionPosNative`, `rebuildSingle`, `isChunkReady`, `invalidateSingle`, `setChunkLights` (count varies).

- [ ] **Step 4: Refresh natives + commit.**
  ```powershell
  Copy-Item -Force "C:\Users\lavin\Documents\Projects\MCVR\build\src\core\Release\core.dll" "C:\Users\lavin\Documents\Projects\Radiance-1201\natives\windows\core.dll"
  Remove-Item -Force "C:\Users\lavin\Documents\Projects\mc-test\instance\radiance\core.dll" -ErrorAction SilentlyContinue
  git -C C:\Users\lavin\Documents\Projects\MCVR add src/core/CMakeLists.txt
  git -C C:\Users\lavin\Documents\Projects\MCVR commit -m "build(mc/1.20.1): un-exclude ChunkProxy.cpp for Step 5 chunk rebuild"
  ```

---

### Task 2 — Reconcile `initNative` JNI signature

**Files:**
- Modify: `src/main/java/com/radiance/client/proxy/world/ChunkProxy.java`
- Modify: `src/main/java/com/radiance/mixins/vulkan_render_integration/BuiltChunkStorageMixins.java`

The C++ side expects:
```cpp
JNIEXPORT void JNICALL Java_com_radiance_client_proxy_world_ChunkProxy_initNative(
    JNIEnv *, jclass, jint chunkNum, jint sizeX, jint sizeY, jint sizeZ, jint bottomSectionCoord);
```

But Java declares:
```java
public static native void initNative(int numChunks);
```

JNI does NOT type-check at runtime — Java pushes 1 int, C++ reads 5, the 4 trailing reads pull garbage from the stack. Either both sides expand, or both sides shrink.

**Choose: expand Java to match C++.** The C++ side needs the chunk-grid dimensions to size its own per-section storage. Java has these via `BuiltChunkStorage`.

- [ ] **Step 1: Read 1.20.1 yarn `BuiltChunkStorage`** via `javap -p` on the loom-cached jar. Find the fields holding `sizeX`, `sizeY`, `sizeZ`, and the `bottomSectionCoord` (or equivalent — may be `getBottomSectionCoord()` on World).

- [ ] **Step 2: Update Java native declaration.**
  In `ChunkProxy.java`:
  ```java
  public static native void initNative(int chunkNum, int sizeX, int sizeY, int sizeZ,
      int bottomSectionCoord);

  public static void init(int chunkNum, int sizeX, int sizeY, int sizeZ,
      int bottomSectionCoord) {
      clear();
      resetWorldLoadSmoothing();
      initNative(chunkNum, sizeX, sizeY, sizeZ, bottomSectionCoord);
  }
  ```
  Drop the old 1-arg `init(int)`.

- [ ] **Step 3: Update BuiltChunkStorageMixins.**
  Current state: `@ModifyVariable(method = "createChunks(...)", at = @At(value = "STORE"), ordinal = 0)` captures only `i` (the total chunk count).

  Change to a richer mixin: `@Inject` at HEAD of `createChunks` or at TAIL of `<init>` that has access to all dimension fields via `@Shadow`. Pseudocode:
  ```java
  @Shadow private int sizeX;
  @Shadow private int sizeY;
  @Shadow private int sizeZ;

  @Inject(method = "createChunks(Lnet/minecraft/client/render/chunk/ChunkBuilder;)V", at = @At("RETURN"))
  private void radianceInitChunkProxy(ChunkBuilder chunkBuilder, CallbackInfo ci) {
      if (!RadianceState.isRendererActive()) return;
      BuiltChunkStorage self = (BuiltChunkStorage)(Object) this;
      int chunkNum = sizeX * sizeY * sizeZ;
      int bottomSectionCoord = MinecraftClient.getInstance().world.getBottomSectionCoord();
      ChunkProxy.init(chunkNum, sizeX, sizeY, sizeZ, bottomSectionCoord);
  }
  ```
  Verify the @Shadow fields exist by their canonical (intermediary or yarn-mapped) names. In 1.20.1, `BuiltChunkStorage` has fields for grid dimensions — check via javap.

- [ ] **Step 4: Regenerate JNI headers + build.**
  ```powershell
  $env:JAVA_HOME = (Get-Item "C:\Program Files\Eclipse Adoptium\jdk-21*\").FullName
  ./gradlew compileJava
  ```
  This regenerates `src/main/native/include/com_radiance_client_proxy_world_ChunkProxy.h` with the new 5-arg signature. MCVR's next build will compile against that.

- [ ] **Step 5: Rebuild MCVR + refresh.**
  Same as Task 1 Step 2-4.

- [ ] **Step 6: Commit Java side.**
  ```
  git add src/main/java/com/radiance/client/proxy/world/ChunkProxy.java src/main/java/com/radiance/mixins/vulkan_render_integration/BuiltChunkStorageMixins.java
  git commit -m "fix(proxy): reconcile ChunkProxy.initNative signature with MCVR (5 ints) + capture grid dims in BuiltChunkStorageMixins"
  ```

---

### Task 3 — Write `ChunkBuilderRebuildTaskMixins`

**Files:**
- Create: `src/main/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderRebuildTaskMixins.java`
- Modify: `src/main/resources/radiance.mixins.json`
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java`

Goal: intercept vanilla's per-section chunk build and capture the resulting buffers. 1.20.1's `ChunkBuilder.BuiltChunk.RebuildTask.render(...)` is the place. It returns a `RebuildTask.RenderData` (inner class) with a `chunkData` field of type `ChunkBuilder.ChunkData`.

- [ ] **Step 1: Verify 1.20.1 RebuildTask shape.**
  ```powershell
  $env:JAVA_HOME = (Get-Item "C:\Program Files\Eclipse Adoptium\jdk-21*\").FullName
  $jar = Get-ChildItem "$env:USERPROFILE\.gradle\caches\fabric-loom\minecraftMaven" -Recurse -Filter 'minecraft-merged-*1.20.1*.jar' | Select-Object -First 1
  & "$env:JAVA_HOME\bin\javap" -p -classpath $jar.FullName 'net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask'
  ```
  Note the `render(...)` method signature. Probably:
  ```
  protected RenderData render(float cameraX, float cameraY, float cameraZ, ChunkRendererRegion region, MatrixStack matrices, Set<RenderLayer> initializedLayers, ...)
  ```
  Or it may be `protected ChunkData render(...)`. Use whatever javap shows.

  Then inspect `RebuildTask$RenderData` (or `ChunkBuilder$ChunkData`) for the `buffers` field — it should be a `Map<RenderLayer, BufferBuilder.BuiltBuffer>` or similar.

- [ ] **Step 2: Add access widener entries** for any private fields/classes we need to read:
  ```
  accessible class net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask
  accessible class net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask$RenderData
  accessible field net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask$RenderData buffers Ljava/util/Map;
  accessible field net/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask$RenderData chunkData Lnet/minecraft/client/render/chunk/ChunkBuilder$ChunkData;
  ```
  (Adjust based on what javap reveals.)

- [ ] **Step 3: Write the mixin.**
  Skeleton:
  ```java
  package com.radiance.mixins.vulkan_render_integration;

  import com.radiance.client.RadianceState;
  import com.radiance.client.constant.Constants;
  import com.radiance.client.proxy.buffer.RadianceBufferAdapter;
  import com.radiance.client.proxy.buffer.RadianceBufferHandle;
  import com.radiance.client.proxy.vulkan.BufferProxy;
  import com.radiance.client.proxy.world.ChunkProxy;
  import net.minecraft.client.render.BufferBuilder;
  import net.minecraft.client.render.RenderLayer;
  import net.minecraft.client.render.chunk.ChunkBuilder;
  import net.minecraft.util.math.BlockPos;
  import org.spongepowered.asm.mixin.Mixin;
  import org.spongepowered.asm.mixin.injection.At;
  import org.spongepowered.asm.mixin.injection.Inject;
  import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

  import java.util.Map;

  @Mixin(targets = "net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask")
  public class ChunkBuilderRebuildTaskMixins {

      @Inject(method = "render(FFFLnet/minecraft/client/render/chunk/ChunkRendererRegion;Lnet/minecraft/client/util/math/MatrixStack;Ljava/util/Set;...)Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk$RebuildTask$RenderData;",
          at = @At("RETURN"))
      private void radianceUploadChunkBuffers(/* args */, CallbackInfoReturnable<...> cir) {
          if (!RadianceState.isRendererActive()) return;

          var renderData = cir.getReturnValue();
          if (renderData == null) return;

          // Extract this RebuildTask's BuiltChunk owner (via reflection or @Shadow on RebuildTask's outer field).
          // 1.20.1 RebuildTask should have an outer reference to the BuiltChunk.

          Map<RenderLayer, BufferBuilder.BuiltBuffer> buffers = /* renderData.buffers or chunkData.buffers */;

          for (Map.Entry<RenderLayer, BufferBuilder.BuiltBuffer> entry : buffers.entrySet()) {
              RenderLayer layer = entry.getKey();
              BufferBuilder.BuiltBuffer buffer = entry.getValue();
              RadianceBufferHandle handle = RadianceBufferAdapter.from(buffer);
              BufferProxy.VertexIndexBufferHandle vh = BufferProxy.createAndUploadVertexIndexBuffer(
                  handle, buffer.getVertexBuffer(), buffer.getSortedBuffer());

              // Now call ChunkProxy.rebuildSingle(originX, originY, originZ, index, ...) with this buffer's data
              // — but rebuildSingle is the 10-arg overload; we need to assemble the geometry-info pointer arrays.
              // See Task 4 for that work; this task is just about capturing the buffers.
          }
      }
  }
  ```

  This is partial — the actual marshalling into `rebuildSingle` is Task 4.

- [ ] **Step 4: Add to JSON manifest + allowlist.**
  In `radiance.mixins.json` `client` array: `"vulkan_render_integration.ChunkBuilderRebuildTaskMixins"`.
  In `MixinPlugin.ENABLED_MIXINS`: `"com.radiance.mixins.vulkan_render_integration.ChunkBuilderRebuildTaskMixins"`.

- [ ] **Step 5: Compile.**
  `./gradlew compileJava` — expect clean. If `RebuildTask` is package-private, the AW entries above are required.

- [ ] **Step 6: Commit.**
  ```
  git add -A
  git commit -m "feat(mixin): ChunkBuilderRebuildTaskMixins captures 1.20.1 per-section chunk buffers at RebuildTask.render TAIL"
  ```

---

### Task 4 — Marshal captured buffers into `ChunkProxy.rebuildSingle`

**Files:**
- Modify: `src/main/java/com/radiance/client/proxy/world/ChunkProxy.java` (un-stub `rebuildSingle`)
- Modify: `src/main/java/com/radiance/mixins/vulkan_render_integration/ChunkBuilderRebuildTaskMixins.java` (call `ChunkProxy.rebuildSingle` from inject)

The native `rebuildSingle` (per the deferred Java declaration) takes ~10 args:

```java
private static native void rebuildSingle(int originX,
    int originY,
    int originZ,
    long index,
    int geometryCount,
    long geometryTypes,
    long geometryGroupNames,
    long geometryTextures,
    long vertexFormats,
    long vertexCounts,
    long vertexAddrs);
```

(Exact arg list: look up the actual native declaration in the deferred file; the comment-blocked stub in the active ChunkProxy.java references it.)

The `long`-typed args are off-heap pointers to int/long arrays. For one BufferBuilder.BuiltBuffer per layer, we need to:
- Pack `geometryTypes[]`, `vertexFormats[]`, etc. into direct ByteBuffers
- Pass their addresses via `MemoryUtil.memAddress(...)`
- `geometryCount` is the number of layers (= number of entries in the buffers map)

- [ ] **Step 1: Read the deferred ChunkProxy.java's rebuildSingle implementation** (from before the stub) — it's in git history. `git show HEAD:src/deferred/java/com/radiance/client/proxy/world/ChunkProxy.java` before the move, or check older revisions. The 1.21 implementation is the blueprint.

  The relevant block was at lines ~316-405 of the deferred file — walks the buffers map, allocates a `Geometry` array, packs name/type/texture/format/vertex-count/vertex-addr per layer, then calls `rebuildSingle(...)` once per BuiltChunk.

- [ ] **Step 2: Port that marshalling to 1.20.1 buffer access.**
  Replace `BuiltBuffer.getBuffer()` with `BuiltBuffer.getVertexBuffer()` (1.20.1 split vertex/index).
  Replace `BuiltBuffer.getDrawParameters()` with `BuiltBuffer.getParameters()`.
  Other field accesses follow Constants.* lookups already in place.

- [ ] **Step 3: Add helper method to ChunkProxy.**
  ```java
  public static void uploadSectionGeometry(ChunkBuilder.BuiltChunk builtChunk,
      Map<RenderLayer, BufferBuilder.BuiltBuffer> buffers) {
      if (buffers.isEmpty()) {
          // empty section — nothing to upload
          return;
      }
      BlockPos origin = builtChunk.getOrigin();
      long index = builtChunk.index;  // long; see existing usage in enqueueRebuild
      int geometryCount = buffers.size();

      // Allocate packed arrays for native side
      ByteBuffer geometryTypes = MemoryUtil.memAllocInt(geometryCount).flip();
      ByteBuffer geometryGroupNames = ...;  // long ptrs to UTF8 name strings
      ByteBuffer geometryTextures = ...;
      ByteBuffer vertexFormats = MemoryUtil.memAllocInt(geometryCount).flip();
      ByteBuffer vertexCounts = MemoryUtil.memAllocInt(geometryCount).flip();
      ByteBuffer vertexAddrs = MemoryUtil.memAllocLong(geometryCount).flip();

      int i = 0;
      for (var entry : buffers.entrySet()) {
          RenderLayer layer = entry.getKey();
          BufferBuilder.BuiltBuffer buffer = entry.getValue();
          BufferBuilder.DrawParameters params = buffer.getParameters();

          geometryTypes.putInt(i * 4, Constants.GeometryTypes.getGeometryType(layer));
          // ... etc
          vertexFormats.putInt(i * 4, Constants.VertexFormats.getValue(params.format()));
          vertexCounts.putInt(i * 4, params.vertexCount());
          vertexAddrs.putLong(i * 8, MemoryUtil.memAddress(buffer.getVertexBuffer()));

          i++;
      }

      rebuildSingle(origin.getX(), origin.getY(), origin.getZ(), index, geometryCount,
          MemoryUtil.memAddress(geometryTypes),
          MemoryUtil.memAddress(geometryGroupNames),
          MemoryUtil.memAddress(geometryTextures),
          MemoryUtil.memAddress(vertexFormats),
          MemoryUtil.memAddress(vertexCounts),
          MemoryUtil.memAddress(vertexAddrs));

      // Free the packed arrays (MCVR copies what it needs synchronously)
      MemoryUtil.memFree(geometryTypes);
      // ... etc
  }
  ```

  Important: `MemoryUtil.memFree` AFTER `rebuildSingle` returns. If MCVR copies asynchronously, this is wrong — verify the C++ side does its copy in-line.

- [ ] **Step 4: Wire from mixin.**
  In `ChunkBuilderRebuildTaskMixins`, after the buffers map is captured:
  ```java
  ChunkProxy.uploadSectionGeometry(builtChunkRef, buffersMap);
  ```

  Need to access the BuiltChunk that owns this RebuildTask. RebuildTask is an inner class of BuiltChunk, so it has a synthetic outer reference field (`this$0` or named field). Use `@Shadow` on that field, or extract it via `(BuiltChunk) ((Object) this).getClass().getEnclosingInstance()` — Mixin doesn't directly support that. Easiest: add an `IRebuildTaskExt` interface with a `neoVoxelRT$getBuiltChunk()` method, mixin shadows the synthetic outer reference, exposes it.

- [ ] **Step 5: Build + commit.**

---

### Task 5 — Enable `WorldRendererCoreMixins`

**Files:**
- Modify: `src/main/java/com/radiance/mixin_related/MixinPlugin.java`

- [ ] **Step 1: Append to ENABLED_MIXINS.**
  ```java
  "com.radiance.mixins.vulkan_render_integration.WorldRendererCoreMixins"
  ```

- [ ] **Step 2: Verify build.**

- [ ] **Step 3: Commit (don't runClient yet).**

---

### Task 6 — User-driven runClient verification (G7)

**Files:** none modified during this task.

- [ ] **Step 1: First runClient.**
  ```powershell
  Remove-Item -Force "C:\Users\lavin\Documents\Projects\mc-test\instance\radiance\core.dll" -ErrorAction SilentlyContinue
  $env:VK_INSTANCE_LAYERS = "VK_LAYER_KHRONOS_validation"
  $env:JAVA_HOME = (Get-Item "C:\Program Files\Eclipse Adoptium\jdk-21*\").FullName
  ./gradlew runClient
  ```

  Click through Mojang splash → main menu (still red, that's the menu rendering gap from Step 7, not Step 5) → Singleplayer → Create New World → Superflat → Game Mode: Creative → Create.

- [ ] **Step 2: Classify outcome.**
  - **A — terrain renders.** F3 shows "Vulkan 1.4". Walk 100 blocks, place/break. G7 cleared.
  - **B — black world.** Vulkan side received chunk buffers but isn't drawing them. Possible: WorldRendererCoreMixins didn't cancel right, or ChunkProxy.dispatchLayer needs implementation. Add `[radiance] ChunkProxy.dispatchLayer fired` log to confirm.
  - **C — UnsatisfiedLinkError or NPE.** Iterate.
  - **D — JVM crash in MCVR.** C++ side's chunk-rebuild path has bug. Add std::cerr to MCVR/src/core/render/chunks.cpp.

- [ ] **Step 3: Triage loop.** Each fix in its own commit.

- [ ] **Step 4: Pass criteria.**
  - Superflat creative loads without crash.
  - Terrain visible (not black, not red).
  - F3 reports Vulkan API description.
  - Walk 100 blocks. Place/break works.
  - 5-minute soak no crash.

  When all pass: G7 cleared.

---

### Task 7 — Docs + branch finish

**Files:**
- Modify: `docs/PLAN.md` — mark G7 CLEARED with evidence.
- Modify: `docs/HANDOFF.md` — refresh current-state.
- Modify: `KNOWN-ISSUES.md` — remove items resolved; add new ones discovered.
- Modify: `gradle.properties` — bump to `0.3.0-alpha-2`.

- [ ] **Step 1-4:** Docs updates + version bump + final `./gradlew build` + commit per the Checkpoint C plan pattern.

- [ ] **Step 5: Push.**
  ```
  git push -u origin checkpoint/checkpoint-c
  git -C C:\Users\lavin\Documents\Projects\MCVR push -u origin mc/1.20.1
  ```

---

## Triage Reference

Common gotchas during Step 5 execution:

**JNI signature mismatches** — When you change a Java native method's argument list, `compileJava` regenerates the JNI header, but MCVR's existing `Java_..._method` implementation still has the OLD argument list. Both sides must change in lockstep. Symptom: silent stack corruption or `EXCEPTION_ACCESS_VIOLATION` reading wild addresses.

**Direct buffer lifetime** — `MemoryUtil.memAlloc*` returns off-heap memory that JNI can read. If the C++ side stores the pointer for later use, you can't `memFree` until it's done. Symptom: random crashes some frames later.

**Mixin shadow field obfuscation** — In 1.20.1 yarn, some field names are still intermediary (`field_NNNNN`). Always verify via `javap -p` on the loom jar before declaring `@Shadow`. Symptom: `InvalidMixinException @Shadow field X was not located`.

**Vanilla calls cancelled but MC asserts presence** — Our GL stubs return fake IDs. Vanilla sometimes checks `id != 0` and acts surprised. So far this hasn't been a problem but watch the `latest.log` for `WARN`/`ERROR` lines that mention texture/buffer IDs.

---

## Self-Review

**1. Scope.** This plan covers ONLY chunk rebuild for terrain (G7 path). It does NOT cover:
- Menu rendering (Step 7's `drawOverlay` JNI gap — still open).
- Entity/particle rendering (Checkpoint D).
- Sky/weather/cloud rendering (Checkpoint D).
- Lighting / RT / DLSS (Checkpoints D-E).

**2. Realistic effort.** 2-4 hours focused work assuming:
- MCVR ChunkProxy.cpp compiles cleanly on first try (50% likely).
- The RebuildTask intercept mixin works on first compile (40% likely — yarn signatures usually need one iteration).
- ChunkProxy.uploadSectionGeometry marshals correctly (30% likely — pointer arg marshalling is finicky).
- WorldRendererCoreMixins routes terrain draws to Vulkan correctly (50% likely — depends on ChunkProxy.dispatchLayer being a real implementation, not a no-op).

Expect at least 5-10 runClient iterations to converge.

**3. Open architectural questions.**
- ChunkProxy.dispatchLayer is currently a no-op shim (added in Checkpoint C Task 17). For terrain to actually draw, this needs to issue a Vulkan draw command for the per-layer geometry. Likely needs another native method on RendererProxy or DrawCommandProxy (similar gap to drawOverlay).
- The DLSS-off path may have additional chunk-render quirks. Document if hit.

**4. Plan dependencies.**
- Task 1 (MCVR un-exclude) is independent of Tasks 2-4.
- Tasks 2-4 build on each other strictly.
- Task 5 (enable WorldRendererCoreMixins) depends on Tasks 1-4 landing.
- Task 6 depends on everything above.

---

## Critical Files Index

- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\client\proxy\world\ChunkProxy.java` — stub methods to replace.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\mixins\vulkan_render_integration\BuiltChunkStorageMixins.java` — expand to capture grid dims.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\main\java\com\radiance\mixins\vulkan_render_integration\WorldRendererCoreMixins.java` — already written; just needs to be enabled and `dispatchLayer` implemented.
- `C:\Users\lavin\Documents\Projects\MCVR\src\core\CMakeLists.txt` — un-exclude ChunkProxy.cpp.
- `C:\Users\lavin\Documents\Projects\MCVR\src\core\middleware\com_radiance_client_proxy_world_ChunkProxy.cpp` — existing JNI implementations; reference for marshalling args.
- `C:\Users\lavin\Documents\Projects\MCVR\src\core\render\chunks.hpp` / `chunks.cpp` — the C++ chunk-storage implementation.
- `C:\Users\lavin\Documents\Projects\Radiance-1201\src\deferred\java\com\radiance\client\proxy\world\ChunkProxy.java` (in git history pre-move) — the 1.21 reference implementation of the marshalling logic, in stubbed-out form in `git show febfb11:src/main/java/com/radiance/client/proxy/world/ChunkProxy.java` (or look at the commented-out stub bodies that already exist in the active file).

---

## Execution

Plan complete. Two execution options:

1. **Subagent-Driven** — fresh subagent per task, review between. Good for the mechanical Task 1 (CMake edit) and Task 3 (mixin write) parts; Task 4 (marshalling) benefits from human oversight on each iteration.

2. **Inline Execution** — execute in a follow-up session with `superpowers:executing-plans`. Recommended for Tasks 4-6 where runClient iteration is needed.

When you start the next session, paste this plan path: `docs/superpowers/plans/2026-05-11-step-5-chunk-rebuild.md`.
