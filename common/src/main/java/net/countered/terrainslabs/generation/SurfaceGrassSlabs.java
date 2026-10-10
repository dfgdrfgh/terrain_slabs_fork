package net.countered.terrainslabs.generation;

import java.util.ArrayList;
import java.util.List;
import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.countered.terrainslabs.registries.ModBlocksRegistry;
import net.countered.terrainslabs.util.MixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

public final class SurfaceGrassSlabs {
    private SurfaceGrassSlabs() {}

    public static boolean belongsToGrassSurface(WorldGenLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        if (!current.getFluidState().isEmpty() || !above.getFluidState().isEmpty()
                || !above.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) return false;
        // Grass terraces and corner slabs can be diagonal or one block higher/lower.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    BlockState neighbor = level.getBlockState(pos.offset(dx, dy, dz));
                    if (neighbor.is(Blocks.GRASS_BLOCK)
                            || (neighbor.is(ModBlocksRegistry.GRASS_SLAB.get())
                            && !neighbor.getValue(SlabBlock.WATERLOGGED))) return true;
                }
            }
        }
        return false;
    }

    private static boolean isGeneratedDirt(BlockState state) {
        return state.is(ModBlocksRegistry.DIRT_SLAB.get()) && state.getValue(CustomSlab.GENERATED)
                && !state.getValue(SlabBlock.WATERLOGGED);
    }

    public static void finishDecoration(WorldGenLevel level, ChunkAccess center) {
        List<BlockPos> grassPositions = new ArrayList<>();
        // Include the adjoining edge of neighboring chunks. Their slabs may
        // have been made before this chunk's grass decoration was available.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int cx = center.getPos().x + dx;
                int cz = center.getPos().z + dz;
                if (!level.hasChunk(cx, cz)) continue;
                ChunkAccess chunk = dx == 0 && dz == 0 ? center : level.getChunk(cx, cz);
                int minX = dx < 0 ? 15 : 0;
                int maxX = dx > 0 ? 0 : 15;
                int minZ = dz < 0 ? 15 : 0;
                int maxZ = dz > 0 ? 0 : 15;
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++) {
                    LevelChunkSection section = sections[index];
                    if (section == null || !section.maybeHas(SurfaceGrassSlabs::isGeneratedDirt)) continue;
                    int baseY = chunk.getMinBuildHeight() + index * 16;
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            for (int y = 0; y < 16; y++) {
                                if (!isGeneratedDirt(section.getBlockState(x, y, z))) continue;
                                BlockPos pos = new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z);
                                if (belongsToGrassSurface(level, pos)) grassPositions.add(pos);
                            }
                        }
                    }
                }
            }
        }
        // Snapshot the candidates so the finishing pass does not spread grass
        // through an entire dirt field as it traverses the sections.
        for (BlockPos pos : grassPositions) {
            BlockState state = level.getBlockState(pos);
            if (isGeneratedDirt(state)) {
                level.setBlock(pos, MixinHelper.withCopiedSlabProperties(state, ModBlocksRegistry.GRASS_SLAB.get()), 2);
            }
        }
    }
}
