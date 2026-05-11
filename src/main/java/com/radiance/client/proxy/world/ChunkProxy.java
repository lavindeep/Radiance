package com.radiance.client.proxy.world;

import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferBuilderStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;

/**
 * ChunkProxy — Radiance's chunk-rebuild / GPU-side chunk lifecycle bridge.
 *
 * <p>1.20.1 port note: the actual rebuild path through {@code SectionBuilder.build(...)}
 * does not exist in 1.20.1 — that class was introduced in 1.21. In 1.20.1, the
 * equivalent geometry-build logic lives directly on
 * {@code ChunkBuilder.BuiltChunk.RebuildTask.render(...)}. Method bodies that depended
 * on {@code SectionBuilder.RenderData} are stubbed with
 * {@link UnsupportedOperationException} so callers compile; runtime support is
 * deferred to the {@code checkpoint-c-runtime} phase (lights up G7 when verified
 * end-to-end via {@code runClient}).
 *
 * <p>The 1.21 type {@code BlockBufferAllocatorStorage} maps to 1.20.1's
 * {@link BlockBufferBuilderStorage}; the storage's {@code reset()}/{@code clear()}
 * usage is shape-compatible for our scoping helper.
 */
public class ChunkProxy {

    public static final ChunkBuilder.ChunkData PROCESSED = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    public static final ChunkBuilder.ChunkData TERRAIN_EMPTY = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    private static final Map<Integer, ChunkBuilder.BuiltChunk> rebuildQueue = new ConcurrentHashMap<>();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static final int numNormalChunkRebuildThreads = 1;
    private static final int numImportantChunkRebuildThreads = 1;
    private static final long worldLoadSmoothDurationNanos = TimeUnit.SECONDS.toNanos(4);
    private static final int maxImportantTasksPerFrameWarmup = 1;
    private static final int maxImportantTasksPerFrameNormal = 1;
    private static final double importantDistanceSqWarmup = 256.0;
    private static final double importantDistanceSqNormal = 768.0;
    private static volatile long smoothImportantUntilNanos = 0L;
    private static final ExecutorService
        importantChunkRebuildExecutor =
        Executors.newFixedThreadPool(numImportantChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    private static final ThreadLocal<BlockBufferBuilderStorage>
        blockBufferAllocatorStorageThreadLocal =
        ThreadLocal.withInitial(BlockBufferBuilderStorage::new);
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(
        numNormalChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

    public static native void initNative(int numChunks);

    public static void init(int numChunks) {
        clear();
        resetWorldLoadSmoothing();
        initNative(numChunks);
    }

    private static void resetWorldLoadSmoothing() {
        smoothImportantUntilNanos = System.nanoTime() + worldLoadSmoothDurationNanos;
    }

    private static boolean inWorldLoadSmoothingWindow() {
        return System.nanoTime() < smoothImportantUntilNanos;
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final BlockBufferBuilderStorage s = blockBufferAllocatorStorageThreadLocal.get();
        s.reset();
        return s::clear;
    }

    public static void clear() {
        waitImportantChunkRebuild();

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(numNormalChunkRebuildThreads,
            r -> {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });

        rebuildQueue.clear();
        rebuildTasks.clear();
    }

    public static void enqueueRebuild(ChunkBuilder.BuiltChunk chunk) {
        rebuildQueue.put(chunk.index, chunk);
    }

    public static void rebuild(Camera camera) {

        BlockPos blockPos = camera.getBlockPos();
        boolean smoothing = inWorldLoadSmoothingWindow();
        int maxImportantTasksPerFrame = smoothing ?
            maxImportantTasksPerFrameWarmup :
            maxImportantTasksPerFrameNormal;
        double importantDistanceSq = smoothing ? importantDistanceSqWarmup : importantDistanceSqNormal;
        int importantTaskCount = 0;

        for (ChunkBuilder.BuiltChunk builtChunk : rebuildQueue.values()) {
            if (builtChunk == null) {
                continue;
            }

            if (builtChunk.needsRebuild() && builtChunk.shouldBuild()) {
                builtChunk.cancelRebuild();

                BlockPos
                    chunkCenterPos =
                    builtChunk.getOrigin()
                        .add(8, 8, 8);
                boolean forceImportant = builtChunk.needsImportantRebuild();
                boolean shouldPrioritize = forceImportant ||
                    chunkCenterPos.getSquaredDistance(blockPos) < importantDistanceSq;

                boolean isImportant = shouldPrioritize &&
                    (forceImportant || importantTaskCount < maxImportantTasksPerFrame);

                if (isImportant) {
                    Future<?> rebuildTask = importantChunkRebuildExecutor.submit(() -> {
                        rebuildSingle(builtChunk, true);
                    });
                    rebuildTasks.add(rebuildTask);
                    importantTaskCount++;
                } else {
                    backgroundChunkRebuildExecutor.execute(() -> {
                        rebuildSingle(builtChunk, false);
                    });
                }
            }
        }

        rebuildQueue.clear();
    }

    public static void waitImportantChunkRebuild() {
        if (rebuildTasks.isEmpty()) {
            return;
        }

        for (Future<?> rebuildTask : rebuildTasks) {
            try {
                rebuildTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        rebuildTasks.clear();
    }

    /**
     * Per-layer terrain dispatch shim used by {@code WorldRendererCoreMixins} when
     * Radiance is active. Currently a no-op pending the Vulkan-side draw path.
     *
     * <p>TODO(checkpoint-c-runtime): forward to Vulkan side via native dispatch.
     * Once {@link #rebuildSingle(ChunkBuilder.BuiltChunk, boolean)} is implemented
     * and chunks are uploaded to the Vulkan side, this should issue a per-layer
     * draw via {@code BufferProxy} or a dedicated {@code DrawCommandProxy} method.
     */
    @SuppressWarnings("unused")
    public static void dispatchLayer(RenderLayer layer,
        MatrixStack matrices,
        double cameraX,
        double cameraY,
        double cameraZ,
        Matrix4f projection) {
        // No-op for Checkpoint C structural scope.
    }

    /**
     * Frustum-update shim used by {@code WorldRendererCoreMixins} when Radiance
     * is active. Currently a no-op pending the Vulkan-side culling integration.
     *
     * <p>TODO(checkpoint-c-runtime): push frustum state into the Vulkan side so
     * the C++ chunk-culling layer can use it for visibility tests.
     */
    @SuppressWarnings("unused")
    public static void updateFrustum(Camera camera, Frustum frustum) {
        // No-op for Checkpoint C structural scope.
    }

    @SuppressWarnings("unused")
    private static void rebuildSingle(ChunkBuilder.BuiltChunk builtChunk, boolean important) {
        // TODO(checkpoint-c-runtime): rewrite for 1.20.1 ChunkBuilder.BuiltChunk.RebuildTask.render
        // output shape. 1.21's SectionBuilder.RenderData (a record with buffers/blockEntities/
        // chunkOcclusionData/noCullingBlockEntities fields) does not exist in 1.20.1 — those
        // fields are produced inline by RebuildTask.render(...) which returns a private
        // RebuildTask result type. Currently a stub so callers compile. G7 (terrain rendering)
        // requires this to be implemented and verified end-to-end via runClient + superflat world.
        //
        // The original path constructed a ChunkRendererRegion via ChunkRendererRegionBuilder,
        // grabbed the per-thread BlockBufferBuilderStorage, then handed everything to the
        // SectionBuilder. The 1.20.1 equivalent has to either (a) mixin into RebuildTask.render
        // to intercept its result, or (b) re-implement section building from scratch.
        //
        // Touching anything in this method body should also revisit IChunkBuilderExt — see the
        // TODO there for the missing section-builder accessor.
        throw new UnsupportedOperationException(
            "ChunkProxy.rebuildSingle(BuiltChunk, boolean): 1.20.1 chunk-rebuild path not yet implemented");
    }

    @SuppressWarnings("unused")
    private static void rebuildSingle(ChunkRendererRegion chunkRendererRegion,
        ChunkBuilder chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        ChunkBuilder.BuiltChunk builtChunk,
        BlockBufferBuilderStorage storage,
        boolean important) {
        // TODO(checkpoint-c-runtime): rewrite for 1.20.1 ChunkBuilder.BuiltChunk.RebuildTask.render
        // output shape. This overload encapsulated the 1.21 SectionBuilder.build(...) call plus
        // the per-RenderLayer buffer packing into the off-heap arrays passed to the native
        // rebuildSingle entrypoint. In 1.20.1, equivalent geometry comes from RebuildTask itself
        // and uses BuiltBuffer.getVertexBuffer() (no unified getBuffer()), no SortedBuffer
        // accessor on BuiltBuffer, and BuiltBuffer.release() instead of close().
        //
        // When implemented, use:
        //   - com.radiance.client.proxy.buffer.RadianceBufferAdapter#from(BufferBuilder.BuiltBuffer)
        //     to produce a RadianceBufferHandle.
        //   - com.radiance.client.proxy.vulkan.BufferProxy#createAndUploadVertexIndexBuffer
        //     for upload, passing buf.getVertexBuffer() (and null for sorted index data — 1.20.1's
        //     BuiltBuffer does not expose a sorted-buffer accessor).
        //   - com.mojang.blaze3d.systems.VertexSorter.byDistance(...) for camera-relative sort
        //     (still present in 1.20.1).
        //   - net.minecraft.client.texture.MissingSprite.getMissingSpriteId() — same name in
        //     1.20.1.
        throw new UnsupportedOperationException(
            "ChunkProxy.rebuildSingle(ChunkRendererRegion, ChunkBuilder, IChunkBuilderExt, BuiltChunk, BlockBufferBuilderStorage, boolean): "
                + "1.20.1 chunk-rebuild path not yet implemented");
    }

    private static native void rebuildSingle(int originX,
        int originY,
        int originZ,
        long index,
        int size,
        long geometryTypes,
        long geometryTextures,
        long vertexFormats,
        long vertexCounts,
        long vertices,
        boolean important);

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    public static native void invalidateSingle(long index);

    private static native void setChunkLights(long chunkIndex, int lightCount, long lightDataPtr);
}
