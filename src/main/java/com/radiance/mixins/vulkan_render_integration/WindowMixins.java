package com.radiance.mixins.vulkan_render_integration;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.RadianceState;
import com.radiance.client.proxy.vulkan.WindowProxy;
import net.minecraft.client.WindowEventHandler;
import net.minecraft.client.WindowSettings;
import net.minecraft.client.util.MonitorTracker;
import net.minecraft.client.util.Window;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class WindowMixins {

    @Final
    @Shadow
    private long handle;

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 0), remap = false)
    public void cancelGLFWWindowHint0(int hint, int value) {
        if (!RadianceState.isRendererPathActive()) {
            GLFW.glfwWindowHint(hint, value);
            return;
        }
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 1, remap = false))
    public void cancelGLFWWindowHint1(int hint, int value) {
        if (!RadianceState.isRendererPathActive()) {
            GLFW.glfwWindowHint(hint, value);
            return;
        }
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 2, remap = false))
    public void cancelGLFWWindowHint2(int hint, int value) {
        if (!RadianceState.isRendererPathActive()) {
            GLFW.glfwWindowHint(hint, value);
            return;
        }
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 3, remap = false))
    public void cancelGLFWWindowHint3(int hint, int value) {
        if (!RadianceState.isRendererPathActive()) {
            GLFW.glfwWindowHint(hint, value);
            return;
        }
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 4, remap = false))
    public void cancelGLFWWindowHint4(int hint, int value) {
        if (!RadianceState.isRendererPathActive()) {
            GLFW.glfwWindowHint(hint, value);
            return;
        }
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V", ordinal = 5, remap = false))
    public void cancelGLFWWindowHint5(int hint, int value) {
        if (!RadianceState.isRendererPathActive()) {
            GLFW.glfwWindowHint(hint, value);
            return;
        }
    }

    @Inject(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwWindowHint(II)V",
            ordinal = 5,
            shift = At.Shift.AFTER,
            remap = false))
    public void addNewGLFWWindowHint(WindowEventHandler eventHandler,
        MonitorTracker monitorTracker,
        WindowSettings settings,
        String fullscreenVideoMode,
        String title,
        CallbackInfo ci) {
        if (!RadianceState.isRendererPathActive()) return;
        GLFW.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwMakeContextCurrent(J)V", remap = false))
    public void cancelGLFWMakeContextCurrent(long window) {
        if (!RadianceState.isRendererPathActive()) {
            GLFW.glfwMakeContextCurrent(window);
            return;
        }
    }

    @Redirect(method =
        "<init>(Lnet/minecraft/client/WindowEventHandler;Lnet/minecraft/client/util/MonitorTracker;"
            +
            "Lnet/minecraft/client/WindowSettings;Ljava/lang/String;Ljava/lang/String;)V",
        at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/opengl/GL;createCapabilities()Lorg/lwjgl/opengl/GLCapabilities;",
            remap = false))
    public GLCapabilities cancelGLCreateCapabilities() {
        if (!RadianceState.isRendererPathActive()) {
            return GL.createCapabilities();
        }
        return null;
    }

    // Note: 1.21+ Window.<init> calls RenderSystem.maxSupportedTextureSize() and
    // GLFW.glfwSetWindowSizeLimits() during construction. Neither exists in 1.20.1's Window —
    // both redirects removed.

    @Inject(method = "onFramebufferSizeChanged(JII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/WindowEventHandler;onResolutionChanged()V"))
    public void framebufferSizeChanged(long window, int width, int height, CallbackInfo ci) {
        if (!RadianceState.isRendererPathActive()) return;
        WindowProxy.onFramebufferSizeChanged();
    }

}
