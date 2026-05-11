package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.RadianceState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLayer.class)
public class RenderLayerMixins {

    @Shadow
    @Final
    @Mutable
    private static RenderLayer LIGHTNING;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void replaceLightning(CallbackInfo ci) {
        // PRD §4.7 guard: bail out cleanly when Radiance is not active. <clinit> for
        // RenderLayer typically fires very early in MC boot (likely before
        // RENDERER_ACTIVE is reached), so in practice this body will rarely execute —
        // but the guard is correct per the §4.7 mechanical rule for @Inject mixins.
        if (!RadianceState.isRendererActive()) return;

        LIGHTNING =
            RenderLayer.of("lightning",
                VertexFormats.POSITION_TEXTURE_COLOR,
                VertexFormat.DrawMode.QUADS,
                1536,
                false,
                true,
                RenderLayer.MultiPhaseParameters.builder()
                    .program(RenderLayer.LIGHTNING_PROGRAM)
                    .writeMaskState(RenderLayer.ALL_MASK)
                    .transparency(RenderLayer.LIGHTNING_TRANSPARENCY)
                    .target(RenderLayer.WEATHER_TARGET)
                    .texture(new RenderPhase.Texture(
                        new Identifier("textures/block/lightning.png"),
                        false,
                        false))
                    .build(false));
    }
}
