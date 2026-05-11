package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.RadianceState;
import com.radiance.client.proxy.vulkan.RendererProxy;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSystem.class)
public abstract class RenderSystemMixins {

    @Shadow(remap = false)
    private static Matrix4f projectionMatrix;

    @Shadow(remap = false)
    private static Matrix4f savedProjectionMatrix;

    @Final
    @Shadow(remap = false)
    private static Matrix4fStack modelViewStack;

    @Shadow(remap = false)
    private static Matrix4f textureMatrix;

    @Inject(method = "maxSupportedTextureSize()I", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private static void redirectMaxSupportedTextureSize(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        int maxImageSize = RendererProxy.maxSupportedTextureSize();
        cir.setReturnValue(maxImageSize);
    }

    // TODO(checkpoint-c): re-add setShader hook against 1.20.1's setShader(Supplier<ShaderProgram>) signature

    @Redirect(method = "flipFrame(J)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSwapBuffers(J)V", remap = false))
    private static void cancelSwapBuffers(long window) {
        if (!RadianceState.isRendererActive()) {
            org.lwjgl.glfw.GLFW.glfwSwapBuffers(window);
            return;
        }
        // existing body (empty — swap is intentionally skipped when Vulkan owns presentation)
    }

    @Redirect(method = "renderCrosshair(I)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/GLX;_renderCrosshair(IZZZ)V"))
    private static void cancelDrawCrossAirForNow(int size, boolean drawX, boolean drawY,
        boolean drawZ) {
        if (!RadianceState.isRendererActive()) {
            com.mojang.blaze3d.platform.GLX._renderCrosshair(size, drawX, drawY, drawZ);
            return;
        }
        // existing body (empty — Vulkan owns crosshair rendering when active)
    }
}
