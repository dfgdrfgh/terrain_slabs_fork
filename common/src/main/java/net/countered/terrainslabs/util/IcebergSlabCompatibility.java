package net.countered.terrainslabs.util;

import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.countered.terrainslabs.registries.ModBlocksRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/** Lets overlapping iceberg features work with terrain slabs as part of their ice geometry. */
public final class IcebergSlabCompatibility {
    private IcebergSlabCompatibility() {
    }

    public static boolean isGeneratedIcebergSlab(BlockState state) {
        // Most iceberg checks are on ordinary blocks; avoid registry lookups for those.
        if (!state.hasProperty(CustomSlab.GENERATED) || !state.getValue(CustomSlab.GENERATED)) return false;
        return isGeneratedIcebergSlab(state, ModBlocksRegistry.PACKED_ICE_SLAB.get(),
                ModBlocksRegistry.BLUE_ICE_SLAB.get(), ModBlocksRegistry.SNOW_SLAB.get());
    }

    static boolean isGeneratedIcebergSlab(BlockState state, Block packedIce, Block blueIce, Block snow) {
        return (state.is(packedIce) || state.is(blueIce) || state.is(snow))
                && state.hasProperty(CustomSlab.GENERATED) && state.getValue(CustomSlab.GENERATED);
    }

    public static BlockState placementState(BlockState state) {
        if (!isGeneratedIcebergSlab(state)) return state;
        return placementState(state, ModBlocksRegistry.PACKED_ICE_SLAB.get(),
                ModBlocksRegistry.BLUE_ICE_SLAB.get(), ModBlocksRegistry.SNOW_SLAB.get());
    }

    static BlockState placementState(BlockState state, Block packedIce, Block blueIce, Block snow) {
        if (!isGeneratedIcebergSlab(state, packedIce, blueIce, snow)) return state;
        if (state.getValue(SlabBlock.WATERLOGGED)) return Blocks.WATER.defaultBlockState();
        return state.is(snow) ? Blocks.SNOW_BLOCK.defaultBlockState() : Blocks.ICE.defaultBlockState();
    }

    public static boolean topSlabHasSupport(BlockGetter level, BlockPos pos) {
        // A generated top slab hangs from the full block above it, rather than resting below it.
        return level.getBlockState(pos.above()).isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static boolean isTopSlab(BlockState state) {
        return state.getValue(SlabBlock.TYPE) == SlabType.TOP;
    }
}
