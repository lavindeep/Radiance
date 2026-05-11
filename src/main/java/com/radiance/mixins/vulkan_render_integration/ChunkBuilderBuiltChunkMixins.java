package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceState;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import java.util.stream.Collector;
import java.util.stream.Stream;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkBuilder.BuiltChunk.class)
public class ChunkBuilderBuiltChunkMixins implements IChunkBuilderBuiltChunkExt {

    // 1.20.1 yarn (build.10) leaves the parent ChunkBuilder reference as the
    // intermediary name `field_20833` (verified via javap on
    // minecraft-merged-1.20.1-...build.10-v2.jar). No yarn rename has occurred,
    // so the @Shadow target keeps the intermediary name.
    @Shadow
    @Final
    ChunkBuilder field_20833;

    @Unique
    public ChunkBuilder neoVoxelRT$getChunkBuilder() {
        return field_20833;
    }

    // 1.20.1 BuiltChunk.<init> calls Stream.collect(Collector) at offset 64
    // (storing the result into the `buffers:Ljava/util/Map;` field). Verified
    // via javap -c. The redirect target is valid for 1.20.1.
    @Redirect(method = "<init>",
        at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;collect(Ljava/util/stream/Collector;)Ljava/lang/Object;"))
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object cancelCollect(Stream<?> stream, Collector<?, ?, ?> collector) {
        // PRD §4.7 guard: forward to the original Stream.collect when Radiance
        // is not active so vanilla can construct its buffers map normally.
        // Raw call because the @Redirect callsite handler has wildcard types
        // that don't roundtrip through Stream<T>.collect(Collector<? super T, A, R>).
        if (!RadianceState.isRendererActive()) return ((Stream) stream).collect((Collector) collector);
        return null;
    }

    @Inject(method = "clear()V", at = @At(value = "TAIL"))
    private void addToRebuildGridClear(CallbackInfo ci) {
        // PRD §4.7 guard. Non-cancellable @Inject — early return leaves the
        // vanilla TAIL path intact.
        if (!RadianceState.isRendererActive()) return;
        ChunkBuilder.BuiltChunk self = (ChunkBuilder.BuiltChunk) (Object) this;
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "scheduleRebuild(Z)V", at = @At(value = "TAIL"))
    private void addToRebuildGridScheduleRebuild(CallbackInfo ci) {
        // PRD §4.7 guard. Non-cancellable @Inject.
        if (!RadianceState.isRendererActive()) return;
        ChunkBuilder.BuiltChunk self = (ChunkBuilder.BuiltChunk) (Object) this;
        ChunkProxy.enqueueRebuild(self);
    }

    @Inject(method = "delete()V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;clear()V",
            shift = At.Shift.AFTER),
        cancellable = true)
    public void cancelVertexConsumerDelete(CallbackInfo ci) {
        // PRD §4.7 guard: when Radiance is inactive, do NOT cancel — let
        // vanilla delete() complete (it disposes the VertexBuffers in
        // `buffers`). Short-circuit vanilla cleanup only when the Vulkan
        // renderer owns the GPU resources.
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }
}
