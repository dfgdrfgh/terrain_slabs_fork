package net.countered.terrainslabs.util;

import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IcebergSlabCompatibilityTest {
    private static Block packedIce;
    private static Block blueIce;
    private static Block snow;
    private static Block stone;

    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Construct isolated native slab fixtures after vanilla bootstrap. Restore both registry
        // fields immediately; the fixtures are never registered or added to production code.
        Field frozen = MappedRegistry.class.getDeclaredField("frozen");
        Field holders = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        frozen.setAccessible(true);
        holders.setAccessible(true);
        Object oldFrozen = frozen.get(BuiltInRegistries.BLOCK);
        Object oldHolders = holders.get(BuiltInRegistries.BLOCK);
        try {
            frozen.set(BuiltInRegistries.BLOCK, false);
            holders.set(BuiltInRegistries.BLOCK, new IdentityHashMap<>());
            packedIce = new CustomSlab(BlockBehaviour.Properties.ofFullCopy(Blocks.PACKED_ICE));
            blueIce = new CustomSlab(BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ICE));
            snow = new CustomSlab(BlockBehaviour.Properties.ofFullCopy(Blocks.SNOW_BLOCK));
            stone = new CustomSlab(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE));
        } finally {
            holders.set(BuiltInRegistries.BLOCK, oldHolders);
            frozen.set(BuiltInRegistries.BLOCK, oldFrozen);
        }
    }

    static Stream<Arguments> icebergSlabs() {
        return Stream.of(packedIce, blueIce, snow).flatMap(block ->
                Stream.of(SlabType.values()).flatMap(type ->
                        Stream.of(false, true).map(wet -> Arguments.of(block, type, wet))));
    }

    @ParameterizedTest
    @MethodSource("icebergSlabs")
    void generatedSlabsParticipateInCarvingAndOverlappingPlacement(Block block, SlabType type, boolean wet) {
        BlockState state = slab(block, type, wet, true);
        assertTrue(isIcebergSlab(state), "Carving must recognize generated iceberg slabs");
        BlockState placement = placement(state);
        Block expected = wet ? Blocks.WATER : block == snow ? Blocks.SNOW_BLOCK : Blocks.ICE;
        assertTrue(placement.is(expected), "An overlapping iceberg must be allowed to fill the slab cell");
        assertTrue(state.is(block), "The compatibility query must not change the actual slab state");
        assertEquals(type, state.getValue(SlabBlock.TYPE));
        assertEquals(wet, state.getValue(SlabBlock.WATERLOGGED));
    }

    @ParameterizedTest
    @MethodSource("icebergSlabs")
    void playerPlacedSlabsRemainOutsideIcebergGeneration(Block block, SlabType type, boolean wet) {
        BlockState state = slab(block, type, wet, false);
        assertFalse(isIcebergSlab(state));
        assertSame(state, placement(state));
    }

    @Test
    void leavesOtherGeneratedMaterialsAndVanillaStatesUnchanged() {
        for (BlockState state : new BlockState[]{slab(stone, SlabType.BOTTOM, false, true),
                Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(),
                Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SNOW.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState()}) {
            assertFalse(isIcebergSlab(state));
            assertSame(state, placement(state));
        }
    }

    @Test
    void topRimIsSupportedByFullIceAboveEvenWithAirBelow() {
        BlockPos pos = new BlockPos(15, 70, 15);
        BlockGetter level = levelWithSupport(pos, Blocks.PACKED_ICE.defaultBlockState());
        assertTrue(level.getBlockState(pos.below()).isAir());
        assertTrue(IcebergSlabCompatibility.topSlabHasSupport(level, pos));
        assertTrue(IcebergSlabCompatibility.isTopSlab(slab(packedIce, SlabType.TOP, false, true)));
        assertFalse(IcebergSlabCompatibility.topSlabHasSupport(levelWithSupport(pos, Blocks.AIR.defaultBlockState()), pos));
        assertFalse(IcebergSlabCompatibility.topSlabHasSupport(levelWithSupport(pos, Blocks.WATER.defaultBlockState()), pos));
    }

    private static BlockGetter levelWithSupport(BlockPos pos, BlockState support) {
        return (BlockGetter) Proxy.newProxyInstance(BlockGetter.class.getClassLoader(),
                new Class<?>[]{BlockGetter.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getBlockState")) {
                        return args[0].equals(pos.above()) ? support : Blocks.AIR.defaultBlockState();
                    }
                    throw new AssertionError("Unexpected world operation: " + method.getName());
                });
    }

    private static boolean isIcebergSlab(BlockState state) {
        return IcebergSlabCompatibility.isGeneratedIcebergSlab(state, packedIce, blueIce, snow);
    }

    private static BlockState placement(BlockState state) {
        return IcebergSlabCompatibility.placementState(state, packedIce, blueIce, snow);
    }

    private static BlockState slab(Block block, SlabType type, boolean wet, boolean generated) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type)
                .setValue(SlabBlock.WATERLOGGED, wet).setValue(CustomSlab.GENERATED, generated);
    }
}
