package com.radiance.client.proxy.buffer;

import com.radiance.client.constant.Constants;
import net.minecraft.client.render.BufferBuilder;

/**
 * Single source of truth for converting MC's BuiltBuffer to a RadianceBufferHandle.
 * Targets 1.20.1's {@link BufferBuilder.BuiltBuffer} shape:
 *   - {@code getParameters()} returns a {@link BufferBuilder.DrawParameters} record
 *     exposing {@code vertexCount()}, {@code indexCount()}, {@code format()},
 *     {@code mode()}, {@code indexType()}.
 *   - The backing vertex buffer is obtained via {@code getVertexBuffer()} (1.20.1
 *     splits vertex/index buffers; the unified {@code getBuffer()} is 1.21+).
 *
 * Centroid arrays are not populated here; sort-aware callers pass them through
 * {@link RadianceBufferHandle} directly when they have one.
 */
public final class RadianceBufferAdapter {

    private RadianceBufferAdapter() {
    }

    public static RadianceBufferHandle from(BufferBuilder.BuiltBuffer buf) {
        BufferBuilder.DrawParameters params = buf.getParameters();
        java.nio.ByteBuffer vertexBuffer = buf.getVertexBuffer();
        boolean hasData = vertexBuffer != null && vertexBuffer.hasRemaining();
        return new RadianceBufferHandle(
            params.vertexCount(),
            params.indexCount(),
            Constants.VertexFormats.getValue(params.format()),
            Constants.IndexTypes.getValue(params.indexType()),
            Constants.DrawModes.getValue(params.mode()),
            hasData,
            0L,
            0);
    }

    static RadianceBufferHandle fromRaw(int vertexCount, int indexCount,
                                        int vertexFormatOrdinal, int indexTypeOrdinal,
                                        int drawModeOrdinal, boolean hasData,
                                        long centroidArrayPtr, int centroidArrayLen) {
        return new RadianceBufferHandle(vertexCount, indexCount, vertexFormatOrdinal,
            indexTypeOrdinal, drawModeOrdinal, hasData, centroidArrayPtr, centroidArrayLen);
    }
}
