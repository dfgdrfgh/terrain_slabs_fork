package net.countered.terrainslabs.mixin.feature;

import net.countered.terrainslabs.generation.SurfaceGrassSlabs;
import net.countered.terrainslabs.platform.PlatformConfigHooks;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {
    @Inject(method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V", at = @At("RETURN"))
    private void terrain_slabs$finishSurfaceGrass(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structures, CallbackInfo ci) {
        if (PlatformConfigHooks.isSlabGenerationEnabled()) {
            SurfaceGrassSlabs.finishDecoration(level, chunk);
        }
    }
}
