package net.countered.terrainslabs.util;

import it.unimi.dsi.fastutil.shorts.ShortList;
import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.countered.terrainslabs.registries.ModBlocksRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Holds proposed ice rims until terrain features finish; never removes existing blocks. */
public final class IcebergSlabPlacement {
    // Native offsets occupy the low twelve bits. The remaining four persist with the
    // chunk and describe a placement request, without putting a slab in the world yet.
    public static final int PLACEMENT_MARKER = 0x8000;
    private static final int TOP = 0x4000;
    private static final int BLUE_ICE = 0x2000;
    private static final int SNOW = 0x1000;

    private IcebergSlabPlacement() {
    }

    public static boolean deferPlacement(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(CustomSlab.GENERATED) || !state.getValue(CustomSlab.GENERATED)
                || !(level instanceof WorldGenRegion region)) return false;
        int material;
        if (state.is(ModBlocksRegistry.PACKED_ICE_SLAB.get())) material = 0;
        else if (state.is(ModBlocksRegistry.BLUE_ICE_SLAB.get())) material = BLUE_ICE;
        else if (state.is(ModBlocksRegistry.SNOW_SLAB.get())) material = SNOW;
        else return false;
        SlabType type = state.getValue(SlabBlock.TYPE);
        // Retinting a complete terrain block is not an exposed iceberg rim.
        // Keep its original material instead of introducing ice before later carving.
        if (type == SlabType.DOUBLE) return true;
        ChunkAccess chunk = region.getChunk(pos);
        chunk.addPackedPostProcess(packPlacement(pos, type, material), chunk.getSectionIndex(pos.getY()));
        return true;
    }

    static short packPlacement(BlockPos pos, SlabType type, int material) {
        return (short) (ProtoChunk.packOffsetCoordinates(pos) | PLACEMENT_MARKER
                | (type == SlabType.TOP ? TOP : 0) | material);
    }

    public static boolean isTop(short packed) {
        return (packed & TOP) != 0;
    }

    private record Placement(BlockPos pos, short packed) {
    }

    public static void finishQueuedPlacements(LevelChunk chunk) {
        List<Placement> top = new ArrayList<>();
        List<Placement> bottom = new ArrayList<>();
        ShortList[] positions = chunk.getPostProcessing();
        for (int section = 0; section < positions.length; section++) {
            ShortList list = positions[section];
            if (list == null) continue;
            for (int index = list.size() - 1; index >= 0; index--) {
                short packed = list.getShort(index);
                if ((packed & PLACEMENT_MARKER) == 0) continue;
                BlockPos pos = ProtoChunk.unpackOffsetCoordinates(packed,
                        chunk.getSectionYFromSectionIndex(section), chunk.getPos());
                (isTop(packed) ? top : bottom).add(new Placement(pos, packed));
                list.removeShort(index);
            }
        }
        // Convert ceiling rims from above first, so lower candidates see their
        // support's actual touching face. Floor additions use those final faces.
        top.sort(Comparator.comparingInt((Placement p) -> p.pos().getY()).reversed());
        bottom.sort(Comparator.comparingInt(p -> p.pos().getY()));
        for (Placement p : top) placeQueued(chunk.getLevel(), p.pos(), p.packed());
        for (Placement p : bottom) placeQueued(chunk.getLevel(), p.pos(), p.packed());
    }

    public static void placeQueued(LevelAccessor level, BlockPos pos, short packed) {
        Block packedIce = ModBlocksRegistry.PACKED_ICE_SLAB.get();
        Block blueIce = ModBlocksRegistry.BLUE_ICE_SLAB.get();
        Block snow = ModBlocksRegistry.SNOW_SLAB.get();
        Block slab = (packed & SNOW) != 0 ? snow : (packed & BLUE_ICE) != 0 ? blueIce : packedIce;
        placeIfConnected(level, pos, slab, isTop(packed) ? SlabType.TOP : SlabType.BOTTOM,
                packedIce, blueIce, snow, neighbor -> pendingTopPlacement(level, neighbor));
    }

    private static boolean pendingTopPlacement(LevelAccessor level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return false;
        ChunkAccess chunk = server.getChunk(pos);
        ShortList positions = chunk.getPostProcessing()[chunk.getSectionIndex(pos.getY())];
        if (positions == null) return false;
        int offset = ProtoChunk.packOffsetCoordinates(pos);
        for (short packed : positions) {
            if ((packed & (PLACEMENT_MARKER | TOP | 0x0fff)) == (PLACEMENT_MARKER | TOP | offset)) return true;
        }
        return false;
    }

    static boolean placeIfConnected(LevelAccessor level, BlockPos pos, Block slab, SlabType type,
                                    Block packedIce, Block blueIce, Block snow,
                                    Predicate<BlockPos> pendingTop) {
        if (level.isOutsideBuildHeight(pos)) return false;
        BlockState current = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());
        boolean conversion = current.getBlock() instanceof SlabBlock
                && current.hasProperty(CustomSlab.GENERATED) && current.getValue(CustomSlab.GENERATED)
                && current.getValue(SlabBlock.TYPE) == type;
        if (type == SlabType.TOP) {
            // A carve may have removed this proposed rim. Never refill that carve or
            // turn a later feature's different material into an ice slab.
            Block full = slab == packedIce ? Blocks.PACKED_ICE : slab == blueIce ? Blocks.BLUE_ICE : Blocks.SNOW_BLOCK;
            if ((!current.is(full) && !conversion)
                    || below.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) return false;
        } else {
            if ((!current.isAir() && !current.is(Blocks.WATER) && !current.is(Blocks.SNOW) && !conversion)
                    || !above.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) return false;
        }

        BlockState support = type == SlabType.TOP ? above : below;
        SlabType touchingFace = type == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP;
        boolean connected = isFullIcebergBlock(support)
                || isGeneratedIceSlab(support, packedIce, blueIce, snow)
                && (support.getValue(SlabBlock.TYPE) == touchingFace || support.getValue(SlabBlock.TYPE) == SlabType.DOUBLE);
        boolean wet = type == SlabType.BOTTOM
                ? current.is(Blocks.WATER) || above.is(Blocks.WATER) : false;
        boolean waterBeside = false;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighbor = level.getBlockState(neighborPos);
            waterBeside |= neighbor.is(Blocks.WATER);
            if (isFullIcebergBlock(neighbor)) {
                // A full side anchor in another chunk may still become a TOP slab.
                // That would not touch a BOTTOM rim, so do not depend on it.
                if (type == SlabType.TOP || !pendingTop.test(neighborPos)) connected = true;
            } else if (isGeneratedIceSlab(neighbor, packedIce, blueIce, snow)
                    && (neighbor.getValue(SlabBlock.TYPE) == type || neighbor.getValue(SlabBlock.TYPE) == SlabType.DOUBLE)) {
                connected = true;
            }
        }
        if (!connected) return false;
        if (type == SlabType.TOP) wet = below.is(Blocks.WATER) && waterBeside;
        if (conversion) wet = current.getValue(SlabBlock.WATERLOGGED);
        BlockState state = slab.defaultBlockState().setValue(CustomSlab.GENERATED, true)
                .setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, wet);
        return level.setBlock(pos, state, 2);
    }

    private static boolean isFullIcebergBlock(BlockState state) {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean isGeneratedIceSlab(BlockState state, Block packedIce, Block blueIce, Block snow) {
        return (state.is(packedIce) || state.is(blueIce) || state.is(snow))
                && state.hasProperty(CustomSlab.GENERATED) && state.getValue(CustomSlab.GENERATED);
    }
}
