package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceState;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.buffer.RadianceBufferAdapter;
import com.radiance.client.proxy.buffer.RadianceBufferHandle;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes vanilla {@link BufferRenderer#drawWithGlobalProgram(BufferBuilder.BuiltBuffer)} calls
 * into MCVR's {@code RendererProxy.drawOverlay(...)} when Radiance is active. Without this,
 * the main menu / GUI never paints because all vanilla GL draws are stubbed out and nothing
 * else feeds Vulkan.
 *
 * 1.20.1 deltas vs. the 1.21 deferred version:
 *   - Target signature is {@code (Lnet/minecraft/client/render/BufferBuilder$BuiltBuffer;)V}
 *     (1.21 promoted BuiltBuffer to a standalone class).
 *   - {@code BuiltBuffer.release()} is the lifecycle call in 1.20.1 (1.21 has {@code close()}).
 *   - {@code BuiltBuffer.getParameters()} returns {@code BufferBuilder.DrawParameters}
 *     (1.21 renamed to {@code getDrawParameters()}).
 *   - {@code BufferProxy.createAndUploadVertexIndexBuffer} now takes the explicit
 *     (RadianceBufferHandle, vertexBuffer, sortedIndexBuffer) tuple instead of a BuiltBuffer.
 */
@Mixin(BufferRenderer.class)
public class BufferRendererMixins {

    @Inject(method = "drawWithGlobalProgram(Lnet/minecraft/client/render/BufferBuilder$BuiltBuffer;)V",
        at = @At("HEAD"),
        cancellable = true)
    private static void rewriteDrawWithGlobalProgram(BufferBuilder.BuiltBuffer buffer,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;

        RadianceBufferHandle handle = RadianceBufferAdapter.from(buffer);
        // 1.20.1 BuiltBuffer doesn't separately expose a "sorted index buffer" — the unified
        // index buffer is reachable via getIndexBuffer(). For non-sorted draws we pass null
        // so BufferProxy falls back to its built-in sequential index path.
        BufferProxy.VertexIndexBufferHandle vh = BufferProxy.createAndUploadVertexIndexBuffer(
            handle,
            buffer.getVertexBuffer(),
            null);

        BufferProxy.updateOverlayDrawUniform();

        BufferBuilder.DrawParameters params = buffer.getParameters();
        RendererProxy.drawOverlay(
            vh.vertexId,
            vh.indexId,
            RendererProxy.getPipelineType(),
            params.indexCount(),
            Constants.IndexTypes.getValue(params.indexType()));

        buffer.release();

        ci.cancel();
    }
}
