package net.countered.terrainslabs.neoforge.model;

import org.embeddedt.embeddium.impl.model.quad.ModelQuadView;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFlags;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** A lighting-only view; the baked geometry is never mutated. */
public final class EmbeddiumSnowLightQuad implements ModelQuadView {
    private ModelQuadView original;
    private int flags;

    public void set(ModelQuadView original) {
        this.original = original;
        flags = ModelQuadFlags.getQuadFlags(this, original.getLightFace(), original.getFlags());
    }

    public void clear() { original = null; }

    @Override public float getX(int i) { return original.getX(i); }
    @Override public float getY(int i) { return original.getY(i) + 0.5f; }
    @Override public float getZ(int i) { return original.getZ(i); }
    @Override public int getColor(int i) { return original.getColor(i); }
    @Override public float getTexU(int i) { return original.getTexU(i); }
    @Override public float getTexV(int i) { return original.getTexV(i); }
    @Override public int getForgeNormal(int i) { return original.getForgeNormal(i); }
    @Override public int getComputedFaceNormal() { return original.getComputedFaceNormal(); }
    @Override public int getLight(int i) { return original.getLight(i); }
    @Override public int getFlags() { return flags; }
    @Override public int getColorIndex() { return original.getColorIndex(); }
    @Override public TextureAtlasSprite getSprite() { return original.getSprite(); }
    @Override public Direction getLightFace() { return original.getLightFace(); }
}
