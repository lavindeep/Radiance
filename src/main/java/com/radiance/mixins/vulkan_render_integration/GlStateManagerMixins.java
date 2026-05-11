package com.radiance.mixins.vulkan_render_integration;

import com.mojang.blaze3d.platform.GlStateManager;
import com.radiance.client.RadianceState;
import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.DrawCommandProxy;
import com.radiance.client.proxy.vulkan.PipelineStateProxy;
import com.radiance.client.proxy.vulkan.TextureProxy;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlStateManager.class)
public class GlStateManagerMixins {

    // Counter for fake VBO/VAO/framebuffer IDs handed to vanilla MC. Vanilla code only
    // stores them as opaque handles and compares for equality — never derefs into GL state
    // (which doesn't exist on this thread). Negative IDs would clash with vanilla's "0 means
    // none" sentinel, so start at 1 and grow monotonically.
    @org.spongepowered.asm.mixin.Unique
    private static final java.util.concurrent.atomic.AtomicInteger neoVoxelRT$fakeGlHandleCounter =
        new java.util.concurrent.atomic.AtomicInteger(1);

    // region <Texture state — Vulkan owns textures, vanilla GL calls must be no-ops post-init>
    @Inject(method = "_genTexture()I",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectGenTexture(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(TextureProxy.generateTextureId());
    }

    @Inject(method = "_genTextures([I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectGenTextures(int[] textures, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        for (int i = 0; i < textures.length; i++) {
            textures[i] = TextureProxy.generateTextureId();
        }
        ci.cancel();
    }

    @Inject(method = "_bindTexture(I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectBindTexture(int texture, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_deleteTexture(I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectDeleteTexture(int texture, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_deleteTextures([I)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectDeleteTextures(int[] textures, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_pixelStore(II)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectPixelStore(int pname, int param, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_texParameter(III)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectTexParameterI(int target, int pname, int param, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_texParameter(IIF)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectTexParameterF(int target, int pname, float param, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_texImage2D(IIIIIIIILjava/nio/IntBuffer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectTexImage2D(int target, int level, int internalFormat, int width,
        int height, int border, int format, int type, java.nio.IntBuffer pixels,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_texSubImage2D(IIIIIIIIJ)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false)
    private static void redirectTexSubImage2D(int target, int level, int xOffset, int yOffset,
        int width, int height, int format, int type, long pixels, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }
    // endregion

    @Inject(method = "_activeTexture(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectActiveTexture(int texture, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    // region <PipelineStateProxy.ViewportState>
    @Inject(method = "_disableScissorTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableScissorTest(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ViewportState.setScissorEnabled(false);
        ci.cancel();
    }

    @Inject(method = "_enableScissorTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableScissorTest(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ViewportState.setScissorEnabled(true);
        ci.cancel();
    }

    @Inject(method = "_scissorBox(IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectScissorBox(int x, int y, int width, int height, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ViewportState.setScissor(x, y, width, height);
        ci.cancel();
    }

    @Inject(method = "_viewport(IIII)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectViewport(int x, int y, int width, int height, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ViewportState.setViewport(x, y, width, height);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ColorBlendState>
    @Inject(method = "_disableBlend()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableBlend(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.setBlendEnable(false);
        ci.cancel();
    }

    @Inject(method = "_enableBlend()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableBlend(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.setBlendEnable(true);
        ci.cancel();
    }

    @Inject(method = "_blendFunc(II)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendFunc(int srcFactor, int dstFactor, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.glSetBlendFuncCombined(srcFactor, dstFactor);
        ci.cancel();
    }

    @Inject(method = "_blendFuncSeparate(IIII)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendFuncSeparate(int srcFactorRGB,
        int dstFactorRGB,
        int srcFactorAlpha,
        int dstFactorAlpha,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.glSetBlendFuncSeparate(srcFactorRGB, srcFactorAlpha,
            dstFactorRGB, dstFactorAlpha);
        ci.cancel();
    }

    @Inject(method = "_blendEquation(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendEquation(int mode, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.glSetBlendOpCombined(mode);
        ci.cancel();
    }

    @Inject(method = "_colorMask(ZZZZ)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectBlendEquation(boolean red, boolean green, boolean blue,
        boolean alpha, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.glSetColorWriteMask(red, green, blue, alpha);
        ci.cancel();
    }

    @Inject(method = "_enableColorLogicOp()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableColorLogicOp(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(true);
        ci.cancel();
    }

    @Inject(method = "_disableColorLogicOp()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableColorLogicOp(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.setColorLogicOpEnable(false);
        ci.cancel();
    }

    @Inject(method = "_logicOp(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectLogicOp(int op, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ColorBlendState.glSetColorLogicOp(op);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.DepthStencilState>
    @Inject(method = "_disableDepthTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableDepthTest(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.DepthStencilState.setDepthTestEnable(false);
        ci.cancel();
    }

    @Inject(method = "_enableDepthTest()V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableDepthTest(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.DepthStencilState.setDepthTestEnable(true);
        ci.cancel();
    }

    @Inject(method = "_depthFunc(I)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V",
            shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDepthFunc(int func, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.DepthStencilState.glSetDepthCompareOp(func);
        ci.cancel();
    }

    @Inject(method = "_depthMask(Z)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDepthMask(boolean mask, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.DepthStencilState.setDepthWriteEnable(mask);
        ci.cancel();
    }

    @Inject(method = "_stencilFunc(III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilFunc(int func, int ref, int mask, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.DepthStencilState.glSetStencilFunc(func, ref, mask);
        ci.cancel();
    }

    @Inject(method = "_stencilMask(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilMask(int mask, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.DepthStencilState.vkSetStencilWriteMask(mask);
        ci.cancel();
    }

    @Inject(method = "_stencilOp(III)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectStencilMask(int sfail, int dpfail, int dppass, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.DepthStencilState.glSetStencilOp(sfail, dpfail, dppass);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.RasterizationState>
    @Inject(method = "_enableCull()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnableCull(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.RasterizationState.glSetCullMode(GL11.GL_BACK);
        ci.cancel();
    }

    @Inject(method = "_disableCull()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisableCull(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.RasterizationState.vkSetCullMode(
            VulkanConstants.VkCullMode.VK_CULL_MODE_NONE.getValue());
        ci.cancel();
    }

    @Inject(method = "_polygonMode(II)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectPolygonMode(int face, int mode, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        /*
          @Warning no vulkan equivalent implementation
         */
        PipelineStateProxy.RasterizationState.glSetPolygonMode(mode);
        ci.cancel();
    }

    @Inject(method = "_enablePolygonOffset()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectEnablePolygonOffset(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, true);
        ci.cancel();
    }

    @Inject(method = "_disablePolygonOffset()V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectDisablePolygonOffset(CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.RasterizationState.glSetPolygonOffsetEnable(GL11.GL_FILL, false);
        ci.cancel();
    }

    @Inject(method = "_polygonOffset(FF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectPolygonOffset(float factor, float units, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.RasterizationState.glSetPolygonOffset(factor, units);
        ci.cancel();
    }
    // endregion

    // region <PipelineStateProxy.ClearState>
    @Inject(method = "_clearColor(FFFF)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearColor(float red, float green, float blue, float alpha,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ClearState.setClearColor(red, green, blue, alpha);
        ci.cancel();
    }

    @Inject(method = "_clearDepth(D)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearDepth(double depth, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ClearState.setClearDepth(depth);
        ci.cancel();
    }

    @Inject(method = "_clearStencil(I)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThread()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClearStencil(int stencil, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        PipelineStateProxy.ClearState.setClearStencil(stencil);
        ci.cancel();
    }
    // endregion

    // region <DrawCommandProxy.Overlay>
    @Inject(method = "_clear(IZ)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;assertOnRenderThreadOrInit()V", shift = At.Shift.AFTER),
        cancellable = true,
        remap = false)
    private static void redirectClear(int mask, boolean getError, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        DrawCommandProxy.Overlay.glClear(mask);
        ci.cancel();
    }
    // endregion

    @Redirect(method = "_getString(I)Ljava/lang/String;", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glGetString(I)Ljava/lang/String;", remap = false))
    private static String redirectGetString(int name) {
        if (!RadianceState.isRendererActive()) {
            return GL11.glGetString(name);
        }
        return "Vulkan 1.4";
    }

    // region <Vanilla GL stubs — vanilla MC issues these to a missing GL context; we must
    // short-circuit them all when Radiance owns rendering. Handle generators return fake IDs
    // that are unique and non-zero so vanilla's "0 means none" sentinel logic still works.>

    @Inject(method = "_glGenBuffers()I", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGenBuffers(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(neoVoxelRT$fakeGlHandleCounter.getAndIncrement());
    }

    @Inject(method = "_glGenVertexArrays()I", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGenVertexArrays(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(neoVoxelRT$fakeGlHandleCounter.getAndIncrement());
    }

    @Inject(method = "_glBindBuffer(II)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBindBuffer(int target, int buffer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glBindVertexArray(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBindVertexArray(int array, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glBufferData(ILjava/nio/ByteBuffer;I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBufferDataBB(int target, java.nio.ByteBuffer data, int usage,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glBufferData(IJI)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBufferDataSize(int target, long size, int usage,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUnmapBuffer(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUnmapBuffer(int target, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glDeleteBuffers(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlDeleteBuffers(int buffer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glDeleteVertexArrays(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlDeleteVertexArrays(int array, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glCopyTexSubImage2D(IIIIIIII)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlCopyTexSubImage2D(int target, int level, int xOffset,
        int yOffset, int x, int y, int width, int height, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glBindFramebuffer(II)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBindFramebuffer(int target, int framebuffer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glBlitFrameBuffer(IIIIIIIIII)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBlitFrameBuffer(int srcX0, int srcY0, int srcX1, int srcY1,
        int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glBindRenderbuffer(II)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBindRenderbuffer(int target, int renderbuffer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glDeleteRenderbuffers(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlDeleteRenderbuffers(int renderbuffer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glDeleteFramebuffers(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlDeleteFramebuffers(int framebuffer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glRenderbufferStorage(IIII)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlRenderbufferStorage(int target, int internalFormat,
        int width, int height, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glFramebufferRenderbuffer(IIII)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlFramebufferRenderbuffer(int target, int attachment,
        int renderbufferTarget, int renderbuffer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glFramebufferTexture2D(IIIII)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlFramebufferTexture2D(int target, int attachment,
        int textureTarget, int texture, int level, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glDrawPixels(IIIIJ)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlDrawPixels(int width, int height, int format, int type,
        long pixels, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_vertexAttribPointer(IIIZIJ)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceVertexAttribPointer(int index, int size, int type,
        boolean normalized, int stride, long pointer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_vertexAttribIPointer(IIIIJ)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceVertexAttribIPointer(int index, int size, int type, int stride,
        long pointer, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_enableVertexAttribArray(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceEnableVertexAttribArray(int index, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_disableVertexAttribArray(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceDisableVertexAttribArray(int index, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_drawElements(IIIJ)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceDrawElements(int mode, int count, int type, long indices,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_readPixels(IIIIIILjava/nio/ByteBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceReadPixelsBB(int x, int y, int width, int height, int format,
        int type, java.nio.ByteBuffer pixels, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_readPixels(IIIIIIJ)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceReadPixelsPtr(int x, int y, int width, int height, int format,
        int type, long pixels, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_getError()I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGetError(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(0);
    }

    @Inject(method = "_getInteger(I)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGetInteger(int pname, CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(0);
    }

    @Inject(method = "_getActiveTexture()I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGetActiveTexture(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(0);
    }

    @Inject(method = "_getTexLevelParameter(III)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGetTexLevelParameter(int target, int level, int pname,
        CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(0);
    }

    @Inject(method = "_getTexImage(IIIIJ)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGetTexImage(int target, int level, int format, int type,
        long pixels, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUseProgram(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUseProgram(int program, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glGetUniformLocation(ILjava/lang/CharSequence;)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGetUniformLocation(int program, CharSequence name,
        CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(-1);
    }

    @Inject(method = "_glGetAttribLocation(ILjava/lang/CharSequence;)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGetAttribLocation(int program, CharSequence name,
        CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(-1);
    }

    @Inject(method = "_glBindAttribLocation(IILjava/lang/CharSequence;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBindAttribLocation(int program, int index, CharSequence name,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform1i(II)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform1i(int location, int value, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform1(ILjava/nio/IntBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform1IntBuf(int location, java.nio.IntBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform1(ILjava/nio/FloatBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform1FloatBuf(int location, java.nio.FloatBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform2(ILjava/nio/IntBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform2IntBuf(int location, java.nio.IntBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform2(ILjava/nio/FloatBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform2FloatBuf(int location, java.nio.FloatBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform3(ILjava/nio/IntBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform3IntBuf(int location, java.nio.IntBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform3(ILjava/nio/FloatBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform3FloatBuf(int location, java.nio.FloatBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform4(ILjava/nio/IntBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform4IntBuf(int location, java.nio.IntBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniform4(ILjava/nio/FloatBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniform4FloatBuf(int location, java.nio.FloatBuffer value,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniformMatrix2(IZLjava/nio/FloatBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniformMatrix2(int location, boolean transpose,
        java.nio.FloatBuffer value, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniformMatrix3(IZLjava/nio/FloatBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniformMatrix3(int location, boolean transpose,
        java.nio.FloatBuffer value, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "_glUniformMatrix4(IZLjava/nio/FloatBuffer;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlUniformMatrix4(int location, boolean transpose,
        java.nio.FloatBuffer value, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }
    // endregion

    // region <Non-underscore GL stubs — vanilla MC's older API surface, still calls GL directly>

    // mapBuffer returns a writable ByteBuffer that vanilla fills with index/vertex data.
    // We can't hand back the real GL mapping (no GL context). Instead return a scratch
    // direct ByteBuffer per thread; vanilla writes into it, calls glUnmapBuffer (no-op),
    // and we discard the data. 16 MiB is enough for vanilla's largest shape-index buffer.
    @org.spongepowered.asm.mixin.Unique
    private static final ThreadLocal<java.nio.ByteBuffer> neoVoxelRT$scratchMapBuffer =
        ThreadLocal.withInitial(() -> {
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocateDirect(16 * 1024 * 1024);
            b.order(java.nio.ByteOrder.nativeOrder());
            return b;
        });

    @Inject(method = "mapBuffer(II)Ljava/nio/ByteBuffer;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceMapBuffer(int target, int access,
        CallbackInfoReturnable<java.nio.ByteBuffer> cir) {
        if (!RadianceState.isRendererActive()) return;
        java.nio.ByteBuffer scratch = neoVoxelRT$scratchMapBuffer.get();
        scratch.clear();
        cir.setReturnValue(scratch);
    }

    @Inject(method = "glGenFramebuffers()I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGenFramebuffers(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(neoVoxelRT$fakeGlHandleCounter.getAndIncrement());
    }

    @Inject(method = "glGenRenderbuffers()I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGenRenderbuffers(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(neoVoxelRT$fakeGlHandleCounter.getAndIncrement());
    }

    // GL_FRAMEBUFFER_COMPLETE = 0x8CD5. Returning this keeps vanilla's framebuffer
    // completeness check happy when our fake framebuffers are queried.
    @Inject(method = "glCheckFramebufferStatus(I)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlCheckFramebufferStatus(int target,
        CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(0x8CD5);
    }

    @Inject(method = "getBoundFramebuffer()I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGetBoundFramebuffer(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(0);
    }

    @Inject(method = "glActiveTexture(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlActiveTextureNoUnderscore(int texture, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "glBlendFuncSeparate(IIII)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlBlendFuncSeparateNoUnderscore(int srcRgb, int dstRgb,
        int srcAlpha, int dstAlpha, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    // Shader/program stubs — vanilla still compiles shaders during boot for the legacy
    // game-renderer pipeline. We accept everything and report "linked"/"compiled" OK so
    // vanilla doesn't refuse to continue. The shaders are never actually used.
    @Inject(method = "glCreateShader(I)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlCreateShader(int type, CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(neoVoxelRT$fakeGlHandleCounter.getAndIncrement());
    }

    @Inject(method = "glCreateProgram()I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlCreateProgram(CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(neoVoxelRT$fakeGlHandleCounter.getAndIncrement());
    }

    @Inject(method = "glAttachShader(II)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlAttachShader(int program, int shader, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "glDeleteShader(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlDeleteShader(int shader, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "glDeleteProgram(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlDeleteProgram(int program, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "glLinkProgram(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlLinkProgram(int program, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "glCompileShader(I)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlCompileShader(int shader, CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    @Inject(method = "glShaderSource(ILjava/util/List;)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlShaderSource(int shader, java.util.List<String> source,
        CallbackInfo ci) {
        if (!RadianceState.isRendererActive()) return;
        ci.cancel();
    }

    // Return 1 (GL_TRUE) so vanilla's GL_LINK_STATUS / GL_COMPILE_STATUS queries pass.
    @Inject(method = "glGetProgrami(II)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGetProgrami(int program, int pname,
        CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(1);
    }

    @Inject(method = "glGetShaderi(II)I",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGetShaderi(int shader, int pname,
        CallbackInfoReturnable<Integer> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue(1);
    }

    @Inject(method = "glGetShaderInfoLog(II)Ljava/lang/String;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGetShaderInfoLog(int shader, int maxLength,
        CallbackInfoReturnable<String> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue("");
    }

    @Inject(method = "glGetProgramInfoLog(II)Ljava/lang/String;",
        at = @At("HEAD"), cancellable = true, remap = false)
    private static void radianceGlGetProgramInfoLog(int program, int maxLength,
        CallbackInfoReturnable<String> cir) {
        if (!RadianceState.isRendererActive()) return;
        cir.setReturnValue("");
    }
    // endregion
}
