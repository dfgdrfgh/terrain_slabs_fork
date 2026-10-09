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

/** Clears generated ice rims after their supporting block is removed by world generation. */
public final class IcebergSlabCompatibility {
    private IcebergSlabCompatibility() {
    }

    static boolean isGeneratedIcebergSlab(BlockState state, Block packedIce, Block blueIce, Block snow) {
        return (state.is(packedIce) || state.is(blueIce) || state.is(snow))
                && state.hasProperty(CustomSlab.GENERATED) && state.getValue(CustomSlab.GENERATED);
    }

    public static boolean removesIcebergSupport(BlockState previous, BlockState replacement) {
        return (previous.is(Blocks.PACKED_ICE) || previous.is(Blocks.BLUE_ICE) || previous.is(Blocks.SNOW_BLOCK))
                && (replacement.isAir() || replacement.is(Blocks.WATER));
    }

    public static void removeUnsupportedSlabs(LevelAccessor level, BlockPos removedSupport) {
        removeUnsupportedSlabs(level, removedSupport, ModBlocksRegistry.PACKED_ICE_SLAB.get(),
                ModBlocksRegistry.BLUE_ICE_SLAB.get(), ModBlocksRegistry.SNOW_SLAB.get());
    }

    static void removeUnsupportedSlabs(LevelAccessor level, BlockPos removedSupport,
                                       Block packedIce, Block blueIce, Block snow) {
        removeAttachedSlab(level, removedSupport.above(), SlabType.BOTTOM, packedIce, blueIce, snow);
        removeAttachedSlab(level, removedSupport.below(), SlabType.TOP, packedIce, blueIce, snow);
    }

    private static void removeAttachedSlab(LevelAccessor level, BlockPos pos, SlabType attachedType,
                                          Block packedIce, Block blueIce, Block snow) {
        if (level.isOutsideBuildHeight(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!isGeneratedIcebergSlab(state, packedIce, blueIce, snow)
                || state.getValue(SlabBlock.TYPE) != attachedType) return;
        // The slab's own fluid decides the replacement: an underwater carving must not
        // flood a dry rim, and clearing a waterlogged rim must not leave an air pocket.
        level.setBlock(pos, state.getFluidState().createLegacyBlock(), 2);
        if (attachedType == SlabType.BOTTOM && !level.isOutsideBuildHeight(pos.above())
                && level.getBlockState(pos.above()).is(Blocks.SNOW)) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
