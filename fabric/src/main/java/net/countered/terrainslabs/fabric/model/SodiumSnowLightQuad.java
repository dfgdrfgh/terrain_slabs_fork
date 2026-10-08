package net.countered.terrainslabs.fabric.model;

import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/** A lighting-only view; the baked geometry is never mutated. */
public final class SodiumSnowLightQuad implements ModelQuadView {
    private ModelQuadView original;
    private int flags;

    public void set(ModelQuadView original) {
        this.original = original;
        flags = ModelQuadFlags.getQuadFlags(this, original.getLightFace());
    }

    public void clear() { original = null; }

    @Override public float getX(int i) { return original.getX(i); }
    @Override public float getY(int i) { return original.getY(i) + 0.5f; }
    @Override public float getZ(int i) { return original.getZ(i); }
    @Override public int getColor(int i) { return original.getColor(i); }
    @Override public float getTexU(int i) { return original.getTexU(i); }
    @Override public float getTexV(int i) { return original.getTexV(i); }
    @Override public int getVertexNormal(int i) { return original.getVertexNormal(i); }
    @Override public int getFaceNormal() { return original.getFaceNormal(); }
    @Override public int getLight(int i) { return original.getLight(i); }
    @Override public int getFlags() { return flags; }
    @Override public int getColorIndex() { return original.getColorIndex(); }
    @Override public TextureAtlasSprite getSprite() { return original.getSprite(); }
    @Override public Direction getLightFace() { return original.getLightFace(); }
}
