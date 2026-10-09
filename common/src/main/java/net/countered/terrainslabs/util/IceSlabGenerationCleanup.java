package net.countered.terrainslabs.util;

import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.countered.terrainslabs.registries.ModBlocksRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Removes generated iceberg rims when a later world-generation write removes their support. */
public final class IceSlabGenerationCleanup {
    private IceSlabGenerationCleanup() {
    }

    public static void removeAtSupport(LevelAccessor level, BlockPos support) {
        removeAtSupport(level, support, ModBlocksRegistry.PACKED_ICE_SLAB.get(),
                ModBlocksRegistry.BLUE_ICE_SLAB.get());
    }

    static void removeAtSupport(LevelAccessor level, BlockPos support, Block packedIceSlab, Block blueIceSlab) {
        removeSlab(level, support.above(), SlabType.BOTTOM, packedIceSlab, blueIceSlab);
        removeSlab(level, support.below(), SlabType.TOP, packedIceSlab, blueIceSlab);
    }

    private static void removeSlab(LevelAccessor level, BlockPos pos, SlabType type,
                                   Block packedIceSlab, Block blueIceSlab) {
        if (level.isOutsideBuildHeight(pos)) return;
        BlockState state = level.getBlockState(pos);
        if ((!state.is(packedIceSlab) && !state.is(blueIceSlab))
                || !state.hasProperty(CustomSlab.GENERATED) || !state.getValue(CustomSlab.GENERATED)
                || state.getValue(SlabBlock.TYPE) != type) return;

        BlockState replacement = state.getValue(SlabBlock.WATERLOGGED)
                ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
        if (level.setBlock(pos, replacement, Block.UPDATE_CLIENTS) && type == SlabType.BOTTOM) {
            BlockPos snowPos = pos.above();
            if (!level.isOutsideBuildHeight(snowPos) && level.getBlockState(snowPos).is(Blocks.SNOW)) {
                level.setBlock(snowPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
