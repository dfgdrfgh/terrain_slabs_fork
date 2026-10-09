package net.countered.terrainslabs.util;

import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IceSlabGenerationCleanupTest {
    private static Block packedIceSlab;
    private static Block blueIceSlab;
    private static final BlockPos SUPPORT = new BlockPos(15, 70, 15);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        packedIceSlab = new CustomSlab(BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE));
        blueIceSlab = new CustomSlab(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ICE));
    }

    static Stream<Arguments> rims() {
        return Stream.of(packedIceSlab, blueIceSlab).flatMap(block ->
                Stream.of(SlabType.BOTTOM, SlabType.TOP).flatMap(type ->
                        Stream.of(false, true).map(waterlogged -> Arguments.of(block, type, waterlogged))));
    }

    @ParameterizedTest
    @MethodSource("rims")
    void removesGeneratedRimAndRestoresItsFluid(Block block, SlabType type, boolean waterlogged) {
        TestLevel level = new TestLevel();
        BlockPos rim = type == SlabType.BOTTOM ? SUPPORT.above() : SUPPORT.below();
        level.states.put(SUPPORT, Blocks.WATER.defaultBlockState());
        level.states.put(rim, slab(block, type, waterlogged, true));
        IceSlabGenerationCleanup.removeAtSupport(level.view, SUPPORT, packedIceSlab, blueIceSlab);
        assertSame(waterlogged ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                level.states.get(rim));
        assertSame(Blocks.WATER.defaultBlockState(), level.states.get(SUPPORT));
        assertEquals(1, level.writes);
    }

    @ParameterizedTest
    @EnumSource(SlabType.class)
    void leavesPlayerPlacedIceUntouched(SlabType type) {
        TestLevel level = new TestLevel();
        BlockState playerSlab = slab(packedIceSlab, type, false, false);
        level.states.put(SUPPORT.above(), playerSlab);
        level.states.put(SUPPORT.below(), playerSlab);
        IceSlabGenerationCleanup.removeAtSupport(level.view, SUPPORT, packedIceSlab, blueIceSlab);
        assertSame(playerSlab, level.states.get(SUPPORT.above()));
        assertSame(playerSlab, level.states.get(SUPPORT.below()));
        assertEquals(0, level.writes);
    }

    @Test
    void leavesDoubleAndOppositeFacingRimsUntouched() {
        TestLevel level = new TestLevel();
        BlockState bottom = slab(packedIceSlab, SlabType.BOTTOM, false, true);
        BlockState top = slab(blueIceSlab, SlabType.TOP, false, true);
        level.states.put(SUPPORT.above(), top);
        level.states.put(SUPPORT.below(), bottom);
        IceSlabGenerationCleanup.removeAtSupport(level.view, SUPPORT, packedIceSlab, blueIceSlab);
        assertEquals(0, level.writes);
        level.states.put(SUPPORT.above(), slab(packedIceSlab, SlabType.DOUBLE, false, true));
        level.states.put(SUPPORT.below(), slab(blueIceSlab, SlabType.DOUBLE, false, true));
        IceSlabGenerationCleanup.removeAtSupport(level.view, SUPPORT, packedIceSlab, blueIceSlab);
        assertEquals(0, level.writes);
    }

    @Test
    void leavesOtherMaterialsUntouched() {
        TestLevel level = new TestLevel();
        BlockState other = new CustomSlab(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE))
                .defaultBlockState().setValue(CustomSlab.GENERATED, true);
        level.states.put(SUPPORT.above(), other);
        level.states.put(SUPPORT.below(), Blocks.PACKED_ICE.defaultBlockState());
        IceSlabGenerationCleanup.removeAtSupport(level.view, SUPPORT, packedIceSlab, blueIceSlab);
        assertSame(other, level.states.get(SUPPORT.above()));
        assertSame(Blocks.PACKED_ICE.defaultBlockState(), level.states.get(SUPPORT.below()));
        assertEquals(0, level.writes);
    }

    @Test
    void removesSnowLeftAboveRemovedBottomRim() {
        TestLevel level = new TestLevel();
        level.states.put(SUPPORT.above(), slab(packedIceSlab, SlabType.BOTTOM, false, true));
        level.states.put(SUPPORT.above(2), Blocks.SNOW.defaultBlockState());
        IceSlabGenerationCleanup.removeAtSupport(level.view, SUPPORT, packedIceSlab, blueIceSlab);
        assertSame(Blocks.AIR.defaultBlockState(), level.states.get(SUPPORT.above()));
        assertSame(Blocks.AIR.defaultBlockState(), level.states.get(SUPPORT.above(2)));
        assertEquals(2, level.writes);
    }

    @Test
    void doesNotReadOutsideBuildHeight() {
        TestLevel level = new TestLevel();
        IceSlabGenerationCleanup.removeAtSupport(level.view, new BlockPos(0, 0, 0), packedIceSlab, blueIceSlab);
        IceSlabGenerationCleanup.removeAtSupport(level.view, new BlockPos(0, 255, 0), packedIceSlab, blueIceSlab);
        assertEquals(2, level.reads);
        assertEquals(0, level.writes);
    }

    private static BlockState slab(Block block, SlabType type, boolean waterlogged, boolean generated) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type)
                .setValue(SlabBlock.WATERLOGGED, waterlogged).setValue(CustomSlab.GENERATED, generated);
    }

    private static final class TestLevel {
        final Map<BlockPos, BlockState> states = new HashMap<>();
        int reads;
        int writes;
        final LevelAccessor view = (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isOutsideBuildHeight": {
                            int y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                            return y < 0 || y >= 256;
                        }
                        case "getBlockState": {
                            BlockPos pos = (BlockPos) args[0];
                            if (pos.getY() < 0 || pos.getY() >= 256) throw new AssertionError("out-of-bounds read");
                            reads++;
                            return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                        }
                        case "setBlock":
                            writes++;
                            states.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                            assertEquals(Block.UPDATE_CLIENTS, args[2]);
                            return true;
                        default:
                            throw new AssertionError("Unexpected world operation: " + method.getName());
                    }
                });
    }
}
