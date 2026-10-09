package net.countered.terrainslabs.mixin.feature;

import it.unimi.dsi.fastutil.shorts.ShortList;
import net.countered.terrainslabs.util.IcebergSlabCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinIceSlabPostprocessing {
    @Inject(method = "postProcessGeneration", at = @At("HEAD"))
    private void terrain_slabs$finishIceRims(CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        ShortList[] positions = chunk.getPostProcessing();
        for (int section = 0; section < positions.length; section++) {
            ShortList list = positions[section];
            if (list == null) continue;
            for (int index = list.size() - 1; index >= 0; index--) {
                short packed = list.getShort(index);
                if ((packed & IcebergSlabCompatibility.POSTPROCESS_MARKER) == 0) continue;
                BlockPos pos = ProtoChunk.unpackOffsetCoordinates(packed,
                        chunk.getSectionYFromSectionIndex(section), chunk.getPos());
                IcebergSlabCompatibility.removeUnsupportedSlab(chunk.getLevel(), pos);
                // Leave vanilla's own entries intact; consume only checks added by this mod.
                list.removeShort(index);
            }
        }
    }
}
