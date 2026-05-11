package com.radiance.client.proxy.buffer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RadianceBufferAdapterTest {

    @Test
    void fromRawProducesExpectedHandle() {
        RadianceBufferHandle h = RadianceBufferAdapter.fromRaw(
            /* vertexCount         */ 4,
            /* indexCount          */ 6,
            /* vertexFormatOrdinal */ 0,
            /* indexTypeOrdinal    */ 0,
            /* drawModeOrdinal     */ 4,
            /* hasData             */ true,
            /* centroidArrayPtr    */ 0L,
            /* centroidArrayLen    */ 0);
        assertEquals(4, h.vertexCount);
        assertEquals(6, h.indexCount);
        assertEquals(0, h.vertexFormatOrdinal);
        assertEquals(0, h.indexTypeOrdinal);
        assertEquals(4, h.drawModeOrdinal);
        assertTrue(h.hasData);
        assertEquals(0L, h.centroidArrayPtr);
        assertEquals(0, h.centroidArrayLen);
    }

    @Test
    void fromRawWithoutData() {
        RadianceBufferHandle h = RadianceBufferAdapter.fromRaw(
            0, 0, 0, 0, 0, false, 0L, 0);
        assertFalse(h.hasData);
    }
}
