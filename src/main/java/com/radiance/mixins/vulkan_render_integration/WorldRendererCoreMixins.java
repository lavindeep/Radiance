package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceState;
import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * WorldRendererCoreMixins — 1.20.1 core terrain hooks.
 *
 * <p>Per Checkpoint C PRD §4.6 amendment, this is authored directly against
 * 1.20.1's {@link WorldRenderer} API rather than ported from the 1.21+ source
 * (which uses a substantially different terrain/sky/weather split). The three
 * inject points here cover the chunk/terrain core path only — sky, weather,
 * clouds, and world-border hooks are deferred to {@code
 * WorldRendererSkyWeatherMixins} in Checkpoint D.
 *
 * <p>Structural-only for this checkpoint: this mixin is intentionally not
 * promoted via {@code MixinPlugin.ENABLED_MIXINS} yet because
 * {@link ChunkProxy#rebuildSingle} is currently a stub that throws
 * {@link UnsupportedOperationException}. Reaching the rebuild drain in
 * {@code render(...)} at runtime would crash on first chunk rebuild. Promotion
 * is gated on the {@code checkpoint-c-runtime} phase landing the real
 * rebuild path and on user-driven {@code runClient} verification.
 *
 * <p>PRD §4.7 guard contract:
 * <ul>
 *   <li>{@code @Inject(cancellable=true)}: early-return when inactive so vanilla
 *   GL completes; otherwise run Radiance behavior then {@code ci.cancel()}.</li>
 *   <li>{@code @Inject(cancellable=false)}: early-return when inactive; otherwise
 *   run Radiance behavior alongside vanilla.</li>
 * </ul>
 */
@Mixin(WorldRenderer.class)
public abstract class WorldRendererCoreMixins {

    /**
     * 1.20.1 signature:
     * {@code private void renderLayer(RenderLayer, MatrixStack, double, double, double, Matrix4f)}.
     *
     * <p>When Radiance is active, route this per-layer terrain draw through the
     * Vulkan-side dispatcher and cancel the vanilla GL path. When inactive,
     * fall through so vanilla terrain rendering completes normally.
     */
    @Inject(
        method = "renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDDLorg/joml/Matrix4f;)V",
        at = @At("HEAD"),
        cancellable = true)
    private void radiance$renderLayer(RenderLayer layer,
        MatrixStack matrices,
        double cameraX,
        double cameraY,
        double cameraZ,
        Matrix4f projection,
        CallbackInfo ci) {
        // PRD §4.7 guard (cancellable): do not cancel when inactive — vanilla
        // GL completes the layer draw on its own.
        if (!RadianceState.isRendererActive()) return;
        ChunkProxy.dispatchLayer(layer, matrices, cameraX, cameraY, cameraZ, projection);
        ci.cancel();
    }

    /**
     * 1.20.1 signature:
     * {@code private void setupTerrain(Camera, Frustum, boolean, boolean)}.
     *
     * <p>Non-cancellable: vanilla still needs to walk visible chunks for its
     * own bookkeeping (built-chunk queue, etc.). On the Vulkan side we just
     * push the current frustum so the C++ chunk-culling layer stays in sync.
     */
    @Inject(
        method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V",
        at = @At("HEAD"))
    private void radiance$setupTerrain(Camera camera,
        Frustum frustum,
        boolean hasForcedFrustum,
        boolean spectator,
        CallbackInfo ci) {
        // PRD §4.7 guard (non-cancellable): early-return when inactive.
        if (!RadianceState.isRendererActive()) return;
        ChunkProxy.updateFrustum(camera, frustum);
    }

    /**
     * 1.20.1 signature:
     * {@code public void render(MatrixStack, float, long, boolean, Camera, GameRenderer, LightmapTextureManager, Matrix4f)}.
     *
     * <p>Non-cancellable: only used to drain the Radiance-side chunk rebuild
     * queue at frame start. Vanilla's full render path keeps running so other
     * subsystems that have not yet been Vulkan-bridged (sky/weather/clouds in
     * Checkpoint C scope) still produce their output.
     */
    @Inject(
        method = "render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V",
        at = @At("HEAD"))
    private void radiance$render(MatrixStack matrices,
        float tickDelta,
        long limitTime,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightmapTextureManager lightmapTextureManager,
        Matrix4f projectionMatrix,
        CallbackInfo ci) {
        // PRD §4.7 guard (non-cancellable): early-return when inactive.
        if (!RadianceState.isRendererActive()) return;
        ChunkProxy.rebuild(camera);
    }
}
