package net.countered.terrainslabs.mixin.feature;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.countered.terrainslabs.block.ModSlabsMap;
import net.countered.terrainslabs.util.MixinHelper;
import net.countered.terrainslabs.util.IcebergSlabPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DiskFeature.class)
public class MixinDiskFeature {

    /**
     * After placing a disk feature, check the blocks above and below it. If they are slabs that don't match the disk's material, replace them with the correct slab type.
     */
    @WrapOperation(
            method = "placeColumn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
            )
    )
    private boolean countered$updateSlabsAroundDisk(WorldGenLevel level, BlockPos pos,
                                                   BlockState placedState, int flags,
                                                   Operation<Boolean> original) {
        boolean placed = original.call(level, pos, placedState, flags);
        if (!placed) return false;
        // Use the state actually written, not placeColumn's first BlockState
        // local (the material being removed). An air carve has no slab material.
        Block newSlab = ModSlabsMap.getSlabForBlock(placedState.getBlock());
        if (newSlab != null) {
            // Check ABOVE (y + 1)
            countered$checkAndReplaceDiskSlab(level, pos.above(), newSlab);
            // Check BELOW (y - 1)
            countered$checkAndReplaceDiskSlab(level, pos.below(), newSlab);
        }
        return true;
    }

    @Unique
    private void countered$checkAndReplaceDiskSlab(WorldGenLevel level, BlockPos targetPos, Block newSlabBlock) {
        if (level.isOutsideBuildHeight(targetPos.getY())) return;

        BlockState currentState = level.getBlockState(targetPos);

        if (currentState.getBlock() instanceof SlabBlock && !currentState.is(newSlabBlock)) {
            BlockState replacement = MixinHelper.withCopiedSlabProperties(currentState, newSlabBlock);
            if (IcebergSlabPlacement.deferPlacement(level, targetPos, replacement)) return;
            level.setBlock(targetPos, replacement, 2);
        }
    }
}
