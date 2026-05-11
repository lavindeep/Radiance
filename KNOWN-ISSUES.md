# Known Issues

## Checkpoint B — alpha-1 partial

### Vulkan boot path is wired but blocked on Pipeline.buildNative C++ crash

`WindowMixins` and `MinecraftClientMixins` are ported to 1.20.1 shapes, guarded per PRD §4.7, and ready to enable. The Vulkan boot path itself is proven working when both are added to `MixinPlugin.ENABLED_MIXINS`:

- `RendererProxy.initRenderer` returns success (Vulkan instance/device/swapchain come up).
- `RenderSystem.apiDescription` is set to `Vulkan 1.4`.
- `RadianceState` transitions `BOOT_OK → RENDERER_ACTIVE`.
- All three G6 log lines fire as specified in PRD §6.

However, the very next call — `Pipeline.buildNative(long)` invoked from `Pipeline.build()` — throws an uncaught C++ exception in `core.dll+0x141e4c`, crashing the JVM via `EXCEPTION_UNCAUGHT_CXX_EXCEPTION`. The C++ entry point is `Java_com_radiance_client_pipeline_Pipeline_buildNative` → `Renderer::framework()->pipeline()->buildWorldPipelineBlueprint(params)` at `src/core/render/pipeline.cpp`.

Plausible causes (not yet bisected):
- DLSS module's `[Pipeline] dlss module skipped: NGX initialization/query failed.` log line appears before the crash. Subsequent modules in the default RT → tone-mapping → post graph may depend on DLSS's output images; their `setOrCreateInputImages` returns `false` and `pipeline.cpp` throws `"Input image not set properly"` (line 150).
- Alternative: ray-tracing module's `setOrCreateOutputImages` could be failing on this hardware (RTX 5070 Ti, driver 596.21).

Triage approach:
1. Enable Vulkan validation layers (`VK_INSTANCE_LAYERS=VK_LAYER_KHRONOS_validation`) for the next `runClient` to see if VUID errors precede the throw.
2. Add a `std::cerr` line at each throw site in MCVR's `pipeline.cpp` to identify which exact module-wiring step fails.
3. Either harden the default-pipeline assembler in `Pipeline.assembleDefault()` (Java side) to omit modules whose dependents fail, or fix MCVR's `buildWorldPipelineBlueprint` to fail-open per module.

To reproduce the crash (or test fixes), add to `MixinPlugin.ENABLED_MIXINS`:

```java
"com.radiance.mixins.vulkan_render_integration.WindowMixins",
"com.radiance.mixins.vulkan_render_integration.MinecraftClientMixins"
```

Otherwise the branch runs as alpha-0-equivalent (vanilla GL rendering, all Phase A guards in place).

### GameRendererMixins deferred to Checkpoint C

1.20.1's `GameRenderer.renderWorld(float, long, MatrixStack)`, `renderHand(MatrixStack, Camera, float)`, and `render(float, long, boolean)` have different signatures than the 1.21+ shapes the deferred mixin targets. Most methods would need to be rewritten against 1.20.1's API. The only piece that survives strip-down is `IGameRendererExt.neoVoxelRT$getRotationMatrix()`, which isn't worth a standalone mixin. Will be addressed in Checkpoint C alongside `WorldRendererCoreMixins`.

### BufferRendererMixins deferred to Checkpoint C

1.20.1's `BufferRenderer.drawWithGlobalProgram` takes `BufferBuilder$BuiltBuffer` (inner class). The deferred mixin uses standalone `BuiltBuffer` (1.21+). Plus `BufferProxy` is in MCVR's deferred-class CMake exclusion list — the JNI target for the redirect body doesn't exist yet. G6 doesn't require it; main-menu primitives flow through `DrawContext`.

### Options.nativeSetTonemappingMode and many other native setters not exported by MCVR

`Options.java` declares ~50 `nativeSetX(...)` methods. MCVR's `mc/1.20.1` branch only implements 6 of them. The first call to an unimplemented native (`nativeSetTonemappingMode`) inside `Options.readOptions` used to throw `UnsatisfiedLinkError`, which `initializeNativeBackedServices` caught and converted to `RENDERER_DISABLED`, blocking Vulkan boot.

Worked around in commit `07aa44f` by catching `UnsatisfiedLinkError` inside `Options.readOptions` itself — first missing setter aborts the rest of the option pushes but state stays `BOOT_OK`. MCVR uses its own defaults for unpushed options.

Long-term fix: add stub JNI exports for the remaining ~44 option setters in MCVR's `src/core/middleware/com_radiance_client_option_Options.cpp` so all Java-side option pushes have a JNI target (no-op is fine until the corresponding option is actually wired through Vulkan).

### libxess*.dll (~125 MB) tech debt

MCVR's CMake `install(FILES ${XESS_RUNTIME_DLLS} DESTINATION ${MCVR_INSTALL_LIB_DIR})` drops three libxess DLLs (~125 MB combined) into `src/main/resources/`, where Gradle's `processResources` then bundles them into the mod jar. Inflates the jar from ~30 MB to ~110 MB. Now ignored in `.gitignore` so they aren't committed, but still bundled at build time. Fix either by:
1. Filtering libxess in Gradle `processResources`.
2. Fixing MCVR's `install` rule to drop XeSS DLLs into `natives/windows/` directly.
