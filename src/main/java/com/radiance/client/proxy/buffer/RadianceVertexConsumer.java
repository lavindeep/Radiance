package com.radiance.client.proxy.buffer;

/**
 * Radiance-owned vertex consumer surface. Replaces every JNI/proxy reference to MC's
 * {@code net.minecraft.client.render.VertexConsumer} so the contract does not depend on
 * a specific MC version.
 *
 * <p>The method set mirrors the abstract surface of yarn-mapped {@code VertexConsumer}
 * for MC 1.20.1. Note in particular: 1.20.1 uses {@code vertex(double, double, double)}
 * whereas 1.21+ switched to {@code vertex(float, float, float)} — this interface follows
 * the 1.20.1 baseline. The default helper overloads on {@code VertexConsumer}
 * (e.g. {@code color(float,float,float,float)}, {@code vertex(Matrix4f,...)},
 * {@code quad(...)}) are intentionally omitted; they are derived helpers and not part
 * of the JNI/proxy contract.
 *
 * <p>Implementations land in Checkpoint D, when {@code PBRVertexConsumer} is ported
 * from the deferred 1.21 source and wired against this interface.
 */
public interface RadianceVertexConsumer {

    /**
     * Begin a new vertex with the given position. Subsequent attribute calls
     * ({@link #color}, {@link #texture}, {@link #overlay}, {@link #light}, {@link #normal})
     * apply to this vertex until {@link #next()} is invoked.
     *
     * <p>1.20.1 takes {@code double} coordinates; do not narrow to {@code float}.
     */
    RadianceVertexConsumer vertex(double x, double y, double z);

    /**
     * Set the vertex color. Components are in the {@code [0, 255]} range.
     */
    RadianceVertexConsumer color(int red, int green, int blue, int alpha);

    /**
     * Set the primary texture (UV) coordinates.
     */
    RadianceVertexConsumer texture(float u, float v);

    /**
     * Set the entity overlay (damage/hurt flash) UV coordinates. Packed sampler indices.
     */
    RadianceVertexConsumer overlay(int u, int v);

    /**
     * Set the lightmap UV coordinates (sky/block light).
     */
    RadianceVertexConsumer light(int u, int v);

    /**
     * Set the vertex normal. Components should be in the {@code [-1, 1]} range.
     */
    RadianceVertexConsumer normal(float x, float y, float z);

    /**
     * Finish the current vertex. Validates that all required attributes for the
     * underlying format have been written, then advances the write cursor.
     */
    void next();

    /**
     * Install a fixed color that overrides per-vertex color until {@link #unfixColor()}
     * is called. Components are in the {@code [0, 255]} range.
     */
    void fixedColor(int red, int green, int blue, int alpha);

    /**
     * Clear any fixed color previously set via {@link #fixedColor(int, int, int, int)}.
     */
    void unfixColor();
}
