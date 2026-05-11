# Known Issues

## Checkpoint C — alpha-2 partial (Java structure landed; runtime verification deferred)

### Pipeline.buildNative C++ crash (unresolved — Phase 0 runtime work)

`WindowMixins`, `MinecraftClientMixins`, `RenderSystemMixins` are ported, guarded per PRD §4.7, and ready to enable. The Vulkan boot path itself was proven working in Checkpoint B testing (`RendererProxy.initRenderer` returns success, `RenderSystem.apiDescription` = "Vulkan 1.4", `RadianceState` reaches `RENDERER_ACTIVE`, all three G6 log lines fire).

`Pipeline.buildNative(long)` then throws an uncaught C++ exception in `core.dll+0x141e4c`, crashing the JVM via `EXCEPTION_UNCAUGHT_CXX_EXCEPTION`. The C++ entry point is `Java_com_radiance_client_pipeline_Pipeline_buildNative` → `Renderer::framework()->pipeline()->buildWorldPipelineBlueprint(params)` at `MCVR/src/core/render/pipeline.cpp`. The four throw sites are lines 53, 104, 135, and 150. The likely culprit is the DLSS-skip cascade — `[Pipeline] dlss module skipped: NGX initialization/query failed.` precedes the crash on the test hardware (RTX 5070 Ti, driver 596.21).

Java-side `Pipeline.assembleDefault()` already routes around DLSS when `Options.dlssDEnabled && isNativeModuleAvailable("...dlss...")` is false. The crash therefore comes from MCVR's C++ pipeline builder either ignoring the Java-side wiring or unconditionally requesting DLSS images.

To re-enable the boot mixins, add to `MixinPlugin.ENABLED_MIXINS`:
```java
"com.radiance.mixins.vulkan_render_integration.WindowMixins",
"com.radiance.mixins.vulkan_render_integration.MinecraftClientMixins",
"com.radiance.mixins.vulkan_render_integration.RenderSystemMixins"
```

Triage path (from `docs/superpowers/plans/2026-05-11-checkpoint-c.md` Phase 0):
1. Instrument MCVR `pipeline.cpp` at each throw site with `std::cerr` traces identifying the failing module/image.
2. Run with `VK_INSTANCE_LAYERS=VK_LAYER_KHRONOS_validation` to surface VUIDs.
3. Either harden `Pipeline.assembleDefault` (Java) to drop downstream-of-DLSS modules, OR fix MCVR `buildWorldPipelineBlueprint` to fail-open per module.

Otherwise the branch runs at Checkpoint-B-equivalent behavior at runtime (vanilla GL rendering, 10 active mixins for resource tracking + minor render integration).

### ChunkProxy.rebuildSingle is stubbed

Both `rebuildSingle(BuiltChunk, boolean)` and `rebuildSingle(ChunkRendererRegion, ChunkBuilder, IChunkBuilderExt, BuiltChunk, BlockBufferBuilderStorage, boolean)` throw `UnsupportedOperationException` with `TODO(checkpoint-c-runtime)`. The 1.21+ source used `SectionBuilder.RenderData` (a record with buffers/blockEntities/chunkOcclusionData fields) which doesn't exist in 1.20.1 — those fields are produced inline by `ChunkBuilder.BuiltChunk.RebuildTask.render(...)` which returns a private `RebuildTask` result type.

The 1.20.1 equivalent has to either (a) mixin into `RebuildTask.render` to intercept its result, or (b) re-implement section building from scratch. Touching this also revisits `IChunkBuilderExt`'s missing `neoVoxelRT$getSectionBuilder()` accessor.

This is the gating work for G7 (terrain renders through Vulkan in superflat creative). `WorldRendererCoreMixins` is written and registered in `radiance.mixins.json` but NOT in `MixinPlugin.ENABLED_MIXINS` to avoid runtime regression (enabling it without `rebuildSingle` would cancel vanilla terrain rendering and immediately throw on first chunk).

### PBR vertex pipeline deferred to Checkpoint D

`PBRVertexConsumer.java` and 4 sibling files (`PBRVertexFormatElements`, `PBRVertexFormats`, `StorageVertexConsumerProvider`, `StorageOutlineVertexConsumerProvider`) remain in `src/deferred/java/`. The 1.21+ `BufferAllocator` type they depend on does not exist in 1.20.1 — the API was reshaped in 1.21. A faithful port requires:
- Replacing `BufferAllocator` with a self-managed `MemoryUtil.nmemAlloc` byte region + growth logic.
- Rebuilding the global VertexFormatElement registry (`PBR_X.id()` / `.getBit()`) since 1.20.1's `VertexFormatElement` lacks the registry API.
- Resolving `BufferBuilder.BuiltBuffer`'s package-private constructor — PBR currently cannot return a vanilla `BuiltBuffer`, so an alternate `PBRBuiltBuffer` shape needs to be designed.

Total churn estimated ~300 LOC across the 5 files plus a design decision on the new return type. This belongs in Checkpoint D alongside the PBR-shading pipeline.

Transitive deferrals:
- `BuiltBufferMixins.java` — uses `PBR_POS` element fallback. Without PBR formats, the mixin is redundant.
- `BlockModelRendererMixins.java` — uses `PBRVertexConsumer`.
- `FluidRendererMixins.java` — uses `PBRVertexConsumer`.

### ChunkBuilderMixins + SectionBuilderMixins deferred to Checkpoint D

1.21 introduced a separate `SectionBuilder` class (chunk-section build pipeline) and `BlockBufferAllocatorStorage` (per-thread allocator pool). Neither exists in 1.20.1 — section building happens inline in `ChunkBuilder.BuiltChunk.RebuildTask.render`. The `ChunkBuilderMixins` @Shadow fields target nonexistent symbols; `SectionBuilderMixins` targets the nonexistent class. Both stay in `src/deferred/java/`.

Re-enabling them requires either:
- Retargeting at `ChunkBuilder.BuiltChunk.RebuildTask` and rewriting the inject bodies against 1.20.1's RebuildTask shape, OR
- A combined section-build mixin that bypasses vanilla's section-rebuild flow entirely.

### RenderLayerMixins guard timing

`RenderLayerMixins.replaceLightning` injects at TAIL of `RenderLayer.<clinit>`. The PRD §4.7 guard (`isRendererActive()`) was applied mechanically, but `RenderLayer.<clinit>` runs very early in MC boot — long before `RadianceState` reaches `RENDERER_ACTIVE`. Net effect under current init order: the LIGHTNING render layer replacement never fires.

If the design intent is "replace LIGHTNING whenever Radiance is installed", the right answer is either (a) unconditional replacement (remove the guard), or (b) lazy replacement triggered from `RadianceClient` post-handshake via reflection or a dedicated init hook. Resolve in Checkpoint D or E when the actual lightning RT path is wired.

### libxess*.dll (~125 MB) tech debt

MCVR's CMake `install(FILES ${XESS_RUNTIME_DLLS} DESTINATION ${MCVR_INSTALL_LIB_DIR})` drops three libxess DLLs (~125 MB combined) into `src/main/resources/`. Gradle's `processResources` bundles them into the mod jar, inflating from ~30 MB to ~110 MB. Ignored in `.gitignore` so they aren't committed, but still bundled at build time. Fix either by:
1. Filtering libxess in Gradle `processResources`.
2. Fixing MCVR's `install` rule to drop XeSS DLLs into `natives/windows/` directly (gitignored, but not double-bundled).

### Options stubs landed; full wiring is per-option Checkpoint scope

MCVR commit `2e88626` adds 64 stub JNI exports for the missing `nativeSet*` methods declared in `Options.java`. `Options.readOptions` no longer trips `UnsatisfiedLinkError` — the Checkpoint B workaround swallow remains as defense-in-depth, but is effectively unreachable now. Per-option wiring (translating each Java-side push into a Vulkan/MCVR side state change) is per-option Checkpoint scope (e.g., DLSS quality wires in Checkpoint E, tone mapping in C/D).
