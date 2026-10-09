package net.countered.terrainslabs.util;

import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.ShortTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ProtoChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
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
            // Vanilla initializes these caches for registered blocks during bootstrap.
            // Isolated fixtures need the same initialization for native fluid queries.
            for (Block block : new Block[]{packedIce, blueIce, snow, stone}) {
                block.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
            }
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
    void recognizesGeneratedRimsForSupportCleanup(Block block, SlabType type, boolean wet) {
        BlockState state = slab(block, type, wet, true);
        assertTrue(isIcebergSlab(state), "Support cleanup must recognize generated ice rims");
        assertTrue(state.is(block), "The compatibility query must not change the actual slab state");
        assertEquals(type, state.getValue(SlabBlock.TYPE));
        assertEquals(wet, state.getValue(SlabBlock.WATERLOGGED));
    }

    @ParameterizedTest
    @MethodSource("icebergSlabs")
    void playerPlacedSlabsRemainOutsideSupportCleanup(Block block, SlabType type, boolean wet) {
        BlockState state = slab(block, type, wet, false);
        assertFalse(isIcebergSlab(state));
    }

    @Test
    void leavesOtherGeneratedMaterialsAndVanillaStatesUnchanged() {
        for (BlockState state : new BlockState[]{slab(stone, SlabType.BOTTOM, false, true),
                Blocks.PACKED_ICE.defaultBlockState(), Blocks.BLUE_ICE.defaultBlockState(),
                Blocks.SNOW_BLOCK.defaultBlockState(), Blocks.SNOW.defaultBlockState(),
                Blocks.WATER.defaultBlockState(), Blocks.AIR.defaultBlockState()}) {
            assertFalse(isIcebergSlab(state));
        }
    }

    static Stream<Arguments> attachedSlabs() {
        return Stream.of(packedIce, blueIce, snow).flatMap(block ->
                Stream.of(SlabType.BOTTOM, SlabType.TOP).flatMap(type ->
                        Stream.of(false, true).flatMap(wet ->
                                Stream.of(Blocks.AIR, Blocks.WATER).map(carved -> Arguments.of(block, type, wet, carved)))));
    }

    @ParameterizedTest
    @MethodSource("attachedSlabs")
    void carvingSupportAlsoClearsSlabsOutsideTheCarvedVolume(Block block, SlabType type, boolean wet, Block carved) {
        BlockPos support = new BlockPos(15, 70, 15);
        BlockPos rim = type == SlabType.BOTTOM ? support.above() : support.below();
        Map<BlockPos, BlockState> states = new HashMap<>();
        Block supportBlock = block == packedIce ? Blocks.PACKED_ICE : block == blueIce ? Blocks.BLUE_ICE : Blocks.SNOW_BLOCK;
        states.put(support, supportBlock.defaultBlockState());
        states.put(rim, slab(block, type, wet, true));
        if (type == SlabType.BOTTOM) states.put(rim.above(), Blocks.SNOW.defaultBlockState());
        LevelAccessor level = mutableLevel(states, support);

        carveSupport(level, states, support, carved.defaultBlockState());

        assertTrue(states.get(rim).is(wet ? Blocks.WATER : Blocks.AIR),
                "Removing a full iceberg block must not strand its attached slab beyond the carve boundary");
        assertTrue(states.get(support).is(carved), "The original feature write must be preserved");
        if (type == SlabType.BOTTOM) assertTrue(states.get(rim.above()).isAir(), "Snow must not float above a cleared rim");
    }

    @ParameterizedTest
    @MethodSource("attachedSlabs")
    void carvingDoesNotRemovePlayerPlacedSlabs(Block block, SlabType type, boolean wet, Block carved) {
        BlockPos support = new BlockPos(0, 70, 0);
        BlockPos rim = type == SlabType.BOTTOM ? support.above() : support.below();
        BlockState original = slab(block, type, wet, false);
        Map<BlockPos, BlockState> states = new HashMap<>();
        states.put(support, Blocks.PACKED_ICE.defaultBlockState());
        states.put(rim, original);
        carveSupport(mutableLevel(states, support), states, support, carved.defaultBlockState());
        assertSame(original, states.get(rim));
    }

    @Test
    void rimsAttachedToOtherFullBlocksAndOtherMaterialsRemainIntact() {
        BlockPos support = new BlockPos(16, 70, 16);
        Map<BlockPos, BlockState> states = new HashMap<>();
        states.put(support, Blocks.BLUE_ICE.defaultBlockState());
        BlockState upperRim = slab(packedIce, SlabType.TOP, false, true);
        BlockState lowerRim = slab(blueIce, SlabType.BOTTOM, true, true);
        states.put(support.above(), upperRim);
        states.put(support.above(2), Blocks.PACKED_ICE.defaultBlockState());
        states.put(support.below(), lowerRim);
        states.put(support.below(2), Blocks.BLUE_ICE.defaultBlockState());
        LevelAccessor level = mutableLevel(states, support);
        carveSupport(level, states, support, Blocks.AIR.defaultBlockState());
        assertSame(upperRim, states.get(support.above()));
        assertSame(lowerRim, states.get(support.below()));

        BlockState stoneRim = slab(stone, SlabType.BOTTOM, false, true);
        states.put(support, Blocks.PACKED_ICE.defaultBlockState());
        states.put(support.above(), stoneRim);
        carveSupport(level, states, support, Blocks.WATER.defaultBlockState());
        assertSame(stoneRim, states.get(support.above()));
    }

    @Test
    void laterFeaturesCanRestoreSupportBeforeTheFinalCheck() {
        BlockPos support = new BlockPos(15, 70, 15);
        BlockPos rim = support.above();
        Map<BlockPos, BlockState> states = new HashMap<>();
        BlockState original = slab(blueIce, SlabType.BOTTOM, true, true);
        states.put(rim, original);
        states.put(support, Blocks.BLUE_ICE.defaultBlockState());
        LevelAccessor level = mutableLevel(states, support);
        states.put(support, Blocks.AIR.defaultBlockState());
        assertSame(original, states.get(rim), "Feature generation must still see the original slab");
        states.put(support, Blocks.PACKED_ICE.defaultBlockState());
        IcebergSlabCompatibility.removeUnsupportedSlab(level, rim, packedIce, blueIce, snow);
        assertSame(original, states.get(rim), "A later restored support must keep the rim intact");
    }

    @Test
    void taggedPostprocessingOffsetsRetainCoordinatesThroughNativeNbt() {
        for (int x : new int[]{-17, -1, 0, 15, 16}) {
            for (int y : new int[]{-64, -1, 0, 70, 319}) {
                BlockPos pos = new BlockPos(x, y, -x);
                short packed = (short) (ProtoChunk.packOffsetCoordinates(pos)
                        | IcebergSlabCompatibility.POSTPROCESS_MARKER);
                short stored = ShortTag.valueOf(packed).getAsShort();
                assertNotEquals(0, stored & IcebergSlabCompatibility.POSTPROCESS_MARKER);
                assertEquals(pos, ProtoChunk.unpackOffsetCoordinates(stored,
                        SectionPos.blockToSectionCoord(y), new ChunkPos(pos)));
            }
        }
    }

    @Test
    void clearingAnOrphanDoesNotRemoveFullTerrainAboveIt() {
        BlockPos support = new BlockPos(15, 70, 15);
        for (Block terrain : new Block[]{Blocks.STONE, Blocks.DIRT, Blocks.GRAVEL,
                Blocks.MOSSY_COBBLESTONE, Blocks.COAL_ORE, Blocks.PACKED_ICE}) {
            Map<BlockPos, BlockState> states = new HashMap<>();
            states.put(support, Blocks.PACKED_ICE.defaultBlockState());
            states.put(support.above(), slab(packedIce, SlabType.BOTTOM, false, true));
            BlockState fullTerrain = terrain.defaultBlockState();
            states.put(support.above(2), fullTerrain);
            carveSupport(mutableLevel(states, support), states, support, Blocks.AIR.defaultBlockState());
            assertTrue(states.get(support.above()).isAir());
            assertSame(fullTerrain, states.get(support.above(2)), "Cleanup must leave full terrain intact");
        }
    }

    private static void carveSupport(LevelAccessor level, Map<BlockPos, BlockState> states, BlockPos support, BlockState carved) {
        states.put(support, carved);
        IcebergSlabCompatibility.removeUnsupportedSlab(level, support.above(), packedIce, blueIce, snow);
        IcebergSlabCompatibility.removeUnsupportedSlab(level, support.below(), packedIce, blueIce, snow);
    }

    private static LevelAccessor mutableLevel(Map<BlockPos, BlockState> states, BlockPos support) {
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isOutsideBuildHeight")) return false;
                    if (method.getName().equals("getBlockState") || method.getName().equals("setBlock")) {
                        BlockPos pos = (BlockPos) args[0];
                        assertEquals(support.getX(), pos.getX(), "Cleanup must not load adjacent chunks");
                        assertEquals(support.getZ(), pos.getZ(), "Cleanup must stay in the changed block's column");
                        if (method.getName().equals("getBlockState")) return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                        BlockState previous = states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                        assertTrue(isIcebergSlab(previous) || previous.is(Blocks.SNOW),
                                "Cleanup must never write over a full terrain block or another slab material");
                        BlockState replacement = (BlockState) args[1];
                        assertTrue(replacement.isAir() || replacement.is(Blocks.WATER),
                                "Cleanup must not place full ice or change surrounding terrain materials");
                        states.put(pos.immutable(), (BlockState) args[1]);
                        return true;
                    }
                    throw new AssertionError("Unexpected world operation: " + method.getName());
                });
    }

    private static boolean isIcebergSlab(BlockState state) {
        return IcebergSlabCompatibility.isGeneratedIcebergSlab(state, packedIce, blueIce, snow);
    }

    private static BlockState slab(Block block, SlabType type, boolean wet, boolean generated) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type)
                .setValue(SlabBlock.WATERLOGGED, wet).setValue(CustomSlab.GENERATED, generated);
    }
}
