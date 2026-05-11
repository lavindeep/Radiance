package com.radiance.mixin_related;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinPluginTest {

    @Test
    void resourceTrackerCoreMixinsAreEnabled() {
        MixinPlugin plugin = new MixinPlugin();
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.NamespaceResourceManagerMixins"));
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.TextureManagerMixins"));
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.AbstractTextureMixins"));
        assertTrue(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vanilla_resource_tracker.NativeImageMixins"));
    }

    @Test
    void renderIntegrationMixinsAreNotYetEnabled() {
        MixinPlugin plugin = new MixinPlugin();
        // Mixins that are loaded structurally by radiance.mixins.json but kept out of the
        // ENABLED_MIXINS allowlist until their 1.20.1 port lands.
        assertFalse(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vulkan_render_integration.WorldRendererCoreMixins"));
        assertFalse(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.vulkan_render_integration.DrawContextMixins"));
    }

    @Test
    void unknownMixinIsNotEnabled() {
        MixinPlugin plugin = new MixinPlugin();
        assertFalse(plugin.shouldApplyMixin(
            "anyTarget",
            "com.radiance.mixins.never.DefinedMixins"));
    }
}
