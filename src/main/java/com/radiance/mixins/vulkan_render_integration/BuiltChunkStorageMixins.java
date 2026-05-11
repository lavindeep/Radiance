package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceState;
import com.radiance.client.proxy.world.ChunkProxy;
import net.minecraft.client.render.BuiltChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BuiltChunkStorage.class)
public class BuiltChunkStorageMixins {

    @Inject(method = "clear()V", at = @At(value = "HEAD"))
    public void clearChunkProxy(CallbackInfo ci) {
        // PRD §4.7 guard: only touch the Vulkan-side chunk grid when Radiance
        // is active. Non-cancellable @Inject — early return leaves vanilla
        // clear() to run normally.
        if (!RadianceState.isRendererActive()) return;
        ChunkProxy.clear();
    }

    // In 1.20.1 BuiltChunkStorage.createChunks (verified via javap -c on the
    // 1.20.1 yarn build.10 jar), the first int STORE is `istore_2` at offset
    // 42, after `sizeX * sizeY * sizeZ` — i.e. the chunk-count local. The
    // existing `at = STORE, ordinal = 0` correctly intercepts this store.
    @ModifyVariable(method = "createChunks(Lnet/minecraft/client/render/chunk/ChunkBuilder;)V", at = @At(value = "STORE"), ordinal = 0)
    private int initChunkRebuildGrid(int i) {
        // PRD §4.7 guard: pass the original chunk count through when Radiance
        // is inactive so vanilla sizing/array allocation is unaffected.
        if (!RadianceState.isRendererActive()) return i;
        ChunkProxy.init(i);
        return i;
    }
}
