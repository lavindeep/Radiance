package com.radiance.mixin_related.extensions.vulkan_render_integration;

import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.world.ClientWorld;

/**
 * Mixin extension on {@link net.minecraft.client.render.chunk.ChunkBuilder} that exposes
 * private state Radiance needs for its custom chunk rebuild path.
 *
 * <p>Note (1.20.1 port): 1.21+ introduced {@code SectionBuilder} as a separate type that owns
 * the section-build entrypoint. 1.20.1 has no such class — the section-build logic lives
 * directly on {@code ChunkBuilder.BuiltChunk.RebuildTask}. The original
 * {@code neoVoxelRT$getSectionBuilder()} accessor has therefore been removed for now;
 * the equivalent will be reintroduced in the checkpoint-c-runtime phase once the
 * 1.20.1 chunk-rebuild path lands.
 *
 * <p>The 1.21 type {@code BlockBufferAllocatorStorage} maps to 1.20.1's
 * {@link BlockBufferBuilderStorage} (still under {@code render.chunk.}).
 */
public interface IChunkBuilderExt {

    ClientWorld neoVoxelRT$getWorld();

    BlockBufferBuilderStorage neoVoxelRT$getBuffers();

    // TODO(checkpoint-c-runtime): reintroduce a section-builder accessor once the 1.20.1
    // ChunkBuilder.BuiltChunk.RebuildTask integration point is written. 1.21's
    // SectionBuilder type has no 1.20.1 equivalent; the build call lives directly on
    // RebuildTask.render() in 1.20.1, so the future signature will look different.
}
