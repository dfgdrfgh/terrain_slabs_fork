package net.countered.terrainslabs.util;

import net.countered.terrainslabs.api.SlabHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps lighting eligibility identical to the actual model offset. */
public final class SnowRenderHelper {
    private SnowRenderHelper() { }

    public static boolean isOffsetSnow(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SnowLayerBlock
                && SlabHelper.isOffsetOntop(level, pos, state);
    }
}
