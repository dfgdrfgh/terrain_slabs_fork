package net.countered.terrainslabs.generation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
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
                    BlockPos neighborPos = pos.offset(dx, dy, dz);
                    if (!level.hasChunk(neighborPos.getX() >> 4, neighborPos.getZ() >> 4)) continue;
                    BlockState neighbor = level.getBlockState(neighborPos);
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
        Queue<BlockPos> grassPositions = new ArrayDeque<>();
        Set<BlockPos> queued = new HashSet<>();
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
                                if (belongsToGrassSurface(level, pos) && queued.add(pos)) grassPositions.add(pos);
                            }
                        }
                    }
                }
            }
        }
        // Finish the connected exposed steps too. A single snapshot leaves
        // another dirt seam beside each newly restored grass slab. Only visit
        // chunks already available; never load more terrain for this repair.
        while (!grassPositions.isEmpty()) {
            BlockPos pos = grassPositions.remove();
            BlockState state = level.getBlockState(pos);
            if (isGeneratedDirt(state) && belongsToGrassSurface(level, pos)) {
                level.setBlock(pos, MixinHelper.withCopiedSlabProperties(state, ModBlocksRegistry.GRASS_SLAB.get()), 2);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        for (int dy = -1; dy <= 1; dy++) {
                            BlockPos neighbor = pos.offset(dx, dy, dz);
                            if (level.hasChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                                    && isGeneratedDirt(level.getBlockState(neighbor))
                                    && queued.add(neighbor)) grassPositions.add(neighbor);
                        }
                    }
                }
            }
        }
    }
}
