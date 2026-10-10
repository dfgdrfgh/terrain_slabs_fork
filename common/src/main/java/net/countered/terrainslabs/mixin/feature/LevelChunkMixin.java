package net.countered.terrainslabs.mixin.feature;

import net.countered.terrainslabs.generation.SurfaceGrassSlabs;
import net.countered.terrainslabs.platform.PlatformConfigHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
    // A chunk becomes tickable only after its surrounding chunks are full.
    // Decorating a single chunk is too early: neighboring features can still
    // write back into its interior and change grass slabs into dirt slabs.
    @Inject(method = "postProcessGeneration", at = @At("RETURN"))
    private void terrain_slabs$finishSurfaceGrass(CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (chunk.getLevel() instanceof ServerLevel level && PlatformConfigHooks.isSlabGenerationEnabled()) {
            SurfaceGrassSlabs.finishDecoration(level, chunk);
        }
    }
}
