package net.countered.terrainslabs.mixin.feature;

import net.countered.terrainslabs.util.IcebergSlabPlacement;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinIceSlabPlacement {
    @Inject(method = "postProcessGeneration", at = @At("HEAD"))
    private void terrain_slabs$placeConnectedIceRims(CallbackInfo ci) {
        IcebergSlabPlacement.finishQueuedPlacements((LevelChunk) (Object) this);
    }
}
