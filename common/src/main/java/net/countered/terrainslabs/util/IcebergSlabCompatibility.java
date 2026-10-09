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

/** Checks generated ice rims after all terrain features have finished. */
public final class IcebergSlabCompatibility {
    // Packed postprocessing positions use only the low twelve bits. Tag our entries so
    // supported waterlogged rims do not acquire extra vanilla fluid ticks from the queue.
    public static final int POSTPROCESS_MARKER = 0x8000;
    private IcebergSlabCompatibility() {
    }

    public static boolean isGeneratedIcebergSlab(BlockState state) {
        if (!state.hasProperty(CustomSlab.GENERATED) || !state.getValue(CustomSlab.GENERATED)) return false;
        return isGeneratedIcebergSlab(state, ModBlocksRegistry.PACKED_ICE_SLAB.get(),
                ModBlocksRegistry.BLUE_ICE_SLAB.get(), ModBlocksRegistry.SNOW_SLAB.get());
    }

    static boolean isGeneratedIcebergSlab(BlockState state, Block packedIce, Block blueIce, Block snow) {
        return (state.is(packedIce) || state.is(blueIce) || state.is(snow))
                && state.hasProperty(CustomSlab.GENERATED) && state.getValue(CustomSlab.GENERATED);
    }

    public static void removeUnsupportedSlab(LevelAccessor level, BlockPos pos) {
        removeUnsupportedSlab(level, pos, ModBlocksRegistry.PACKED_ICE_SLAB.get(),
                ModBlocksRegistry.BLUE_ICE_SLAB.get(), ModBlocksRegistry.SNOW_SLAB.get());
    }

    static void removeUnsupportedSlab(LevelAccessor level, BlockPos pos,
                                      Block packedIce, Block blueIce, Block snow) {
        if (level.isOutsideBuildHeight(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!isGeneratedIcebergSlab(state, packedIce, blueIce, snow)) return;
        SlabType type = state.getValue(SlabBlock.TYPE);
        if (type == SlabType.DOUBLE) return;
        BlockPos supportPos = type == SlabType.TOP ? pos.above() : pos.below();
        BlockState support = level.getBlockState(supportPos);
        if (!support.isAir() && !support.is(Blocks.WATER)) return;
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 2);
        if (type == SlabType.BOTTOM && !level.isOutsideBuildHeight(pos.above())
                && level.getBlockState(pos.above()).is(Blocks.SNOW)) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
