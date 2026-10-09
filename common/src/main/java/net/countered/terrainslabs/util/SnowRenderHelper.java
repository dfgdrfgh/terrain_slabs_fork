package net.countered.terrainslabs.util;

import net.countered.terrainslabs.api.SlabHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Position-aware render checks for snow lowered onto slabs. */
public final class SnowRenderHelper {
    private SnowRenderHelper() { }

    public static boolean isOffsetSnow(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof SnowLayerBlock
                && SlabHelper.isOffsetOntop(level, pos, state);
    }
    /**
     * State-only face caches cannot account for snow's position-dependent offset.
     * Keep its horizontal faces when either snow layer is lowered; an adjacent
     * layer at the original height cannot cover the same face.
     */
    public static boolean shouldKeepSnowSide(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        if (!face.getAxis().isHorizontal() || !(state.getBlock() instanceof SnowLayerBlock)) {
            return false;
        }
        if (isOffsetSnow(level, pos, state)) {
            return true;
        }
        BlockPos neighborPos = pos.relative(face);
        return isOffsetSnow(level, neighborPos, level.getBlockState(neighborPos));
    }
}
