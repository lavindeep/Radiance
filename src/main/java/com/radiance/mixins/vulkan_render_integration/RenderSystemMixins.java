package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.RadianceState;
import com.radiance.client.proxy.vulkan.RendererProxy;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
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
    private static MatrixStack modelViewStack;

    @Shadow(remap = false)
    private static Matrix4f textureMatrix;

    @Inject(method = "maxSupportedTextureSize()I", at = @At(value = "HEAD"), cancellable = true, remap = false)
    private static void redirectMaxSupportedTextureSize(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        int maxImageSize = RendererProxy.maxSupportedTextureSize();
        cir.setReturnValue(maxImageSize);
    }

    /**
     * 1.20.1's {@code RenderSystem.setShader(Supplier<net.minecraft.client.gl.ShaderProgram>)}.
     * When vanilla swaps active shaders we tell MCVR's pipeline manager to bind the matching
     * Vulkan pipeline. The proper shader-name -> pipeline-type mapping table is deferred:
     * for now we route every shader to pipeline 0 (default overlay pipeline). MCVR ignores
     * unknown pipeline ids and falls back to its default, so this is safe; the important
     * thing is that {@code bindOverlayPipeline} is called once per shader swap so the C++
     * side knows a state change happened before the next drawOverlay arrives.
     *
     * Note 1.20.1 uses {@code net.minecraft.client.gl.ShaderProgram} (NOT {@code Shader} like
     * pre-1.17 or {@code ShaderProgramKey} like 1.21+).
     *
     * TODO(checkpoint-c+): build a shader-name -> Radiance pipeline-type lookup
     * (position_tex / position_color_tex -> textured, position_color -> solid, etc.).
     */
    @Inject(method = "setShader(Ljava/util/function/Supplier;)V",
        at = @At("HEAD"), remap = false)
    private static void radianceSetShader(
        java.util.function.Supplier<net.minecraft.client.gl.ShaderProgram> supplier,
        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        RendererProxy.bindOverlayPipeline(0);
    }

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
