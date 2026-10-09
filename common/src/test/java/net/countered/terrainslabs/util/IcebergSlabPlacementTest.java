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

class IcebergSlabPlacementTest {
    private static Block packedIce;
    private static Block blueIce;
    private static Block snow;
    private static Block stone;
    private static final BlockPos POS = new BlockPos(15, 70, 15);

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

    static Stream<Arguments> materialsAndTypes() {
        return Stream.of(packedIce, blueIce, snow).flatMap(block ->
                Stream.of(SlabType.BOTTOM, SlabType.TOP).map(type -> Arguments.of(block, type)));
    }

    @ParameterizedTest
    @MethodSource("materialsAndTypes")
    void disconnectedCandidatesNeverWriteAirWaterOrExistingTerrain(Block block, SlabType type) {
        for (Block empty : new Block[]{Blocks.AIR, Blocks.WATER}) {
            Map<BlockPos, BlockState> states = initial(block, type, empty);
            Map<BlockPos, BlockState> before = Map.copyOf(states);
            assertFalse(place(states, block, type));
            assertEquals(before, states, "A disconnected proposal must never enter the world or erase terrain");
        }
    }

    @ParameterizedTest
    @MethodSource("materialsAndTypes")
    void attachesToFullIcebergMaterials(Block block, SlabType type) {
        for (Block anchor : new Block[]{Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK}) {
            Map<BlockPos, BlockState> states = initial(block, type, Blocks.AIR);
            BlockPos support = type == SlabType.TOP ? POS.above() : POS.below();
            states.put(support, anchor.defaultBlockState());
            assertTrue(place(states, block, type));
            assertTrue(states.get(POS).is(block));
            assertTrue(states.get(POS).getValue(CustomSlab.GENERATED));
            assertEquals(type, states.get(POS).getValue(SlabBlock.TYPE));
            assertSame(anchor.defaultBlockState(), states.get(support));
        }
    }

    @ParameterizedTest
    @MethodSource("materialsAndTypes")
    void preservesSideAttachmentsOnlyWhereSlabFacesActuallyTouch(Block block, SlabType type) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            Map<BlockPos, BlockState> states = initial(block, type, Blocks.WATER);
            states.put(POS.relative(direction), Blocks.PACKED_ICE.defaultBlockState());
            assertTrue(place(states, block, type), "Solid iceberg side anchors should keep the rim");

            states = initial(block, type, Blocks.AIR);
            states.put(POS.relative(direction), slab(blueIce, type, false, true));
            assertTrue(place(states, block, type));

            states = initial(block, type, Blocks.AIR);
            states.put(POS.relative(direction), slab(blueIce,
                    type == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP, false, true));
            Map<BlockPos, BlockState> before = Map.copyOf(states);
            assertFalse(place(states, block, type), "Opposite half slabs have no touching side face");
            assertEquals(before, states);
        }
    }

    @ParameterizedTest
    @MethodSource("materialsAndTypes")
    void usesFinalSupportFacesAndIgnoresPlayerPlacedAnchors(Block block, SlabType type) {
        BlockPos support = type == SlabType.TOP ? POS.above() : POS.below();
        SlabType face = type == SlabType.TOP ? SlabType.BOTTOM : SlabType.TOP;
        Map<BlockPos, BlockState> states = initial(block, type, Blocks.AIR);
        states.put(support, slab(packedIce, type, false, true));
        assertFalse(place(states, block, type));
        states.put(support, slab(packedIce, face, false, false));
        assertFalse(place(states, block, type));
        states.put(support, slab(packedIce, face, false, true));
        assertTrue(place(states, block, type));
    }

    @ParameterizedTest
    @MethodSource("materialsAndTypes")
    void ordinaryTerrainVegetationAndThinSnowCannotAnchorAnIceRim(Block block, SlabType type) {
        for (Block anchor : new Block[]{Blocks.STONE, Blocks.DIRT, Blocks.SNOW, Blocks.SHORT_GRASS}) {
            Map<BlockPos, BlockState> states = initial(block, type, Blocks.AIR);
            states.put(type == SlabType.TOP ? POS.above() : POS.below(), anchor.defaultBlockState());
            states.put(POS.east(), anchor.defaultBlockState());
            Map<BlockPos, BlockState> before = Map.copyOf(states);
            assertFalse(place(states, block, type));
            assertEquals(before, states);
        }
    }

    @ParameterizedTest
    @MethodSource("materialsAndTypes")
    void recomputesWaterloggingFromFinalWaterWithoutChangingWaterNeighbors(Block block, SlabType type) {
        for (boolean wet : new boolean[]{false, true}) {
            Map<BlockPos, BlockState> states = initial(block, type, wet ? Blocks.WATER : Blocks.AIR);
            states.put(type == SlabType.TOP ? POS.above() : POS.below(), Blocks.BLUE_ICE.defaultBlockState());
            if (wet) states.put(POS.east(), Blocks.WATER.defaultBlockState());
            assertTrue(place(states, block, type));
            assertEquals(wet, states.get(POS).getValue(SlabBlock.WATERLOGGED));
            if (wet) assertTrue(states.get(POS.east()).is(Blocks.WATER));
        }
    }

    @Test
    void topCandidateDoesNotRefillCarvedAirOrWaterOrReplaceAnotherFeaturesBlock() {
        for (Block replacement : new Block[]{Blocks.AIR, Blocks.WATER, Blocks.STONE, Blocks.DIRT,
                Blocks.GRAVEL, Blocks.MOSSY_COBBLESTONE, Blocks.BLUE_ICE}) {
            Map<BlockPos, BlockState> states = initial(packedIce, SlabType.TOP, Blocks.AIR);
            states.put(POS, replacement.defaultBlockState());
            states.put(POS.above(), Blocks.PACKED_ICE.defaultBlockState());
            Map<BlockPos, BlockState> before = Map.copyOf(states);
            assertFalse(place(states, packedIce, SlabType.TOP));
            assertEquals(before, states);
        }
    }

    @Test
    void bottomCandidateDoesNotReplaceSolidTerrainOrExistingSlabs() {
        for (BlockState replacement : new BlockState[]{Blocks.STONE.defaultBlockState(),
                Blocks.PACKED_ICE.defaultBlockState(), slab(stone, SlabType.TOP, false, true),
                slab(packedIce, SlabType.BOTTOM, true, false)}) {
            Map<BlockPos, BlockState> states = initial(packedIce, SlabType.BOTTOM, Blocks.AIR);
            states.put(POS, replacement);
            states.put(POS.below(), Blocks.PACKED_ICE.defaultBlockState());
            Map<BlockPos, BlockState> before = Map.copyOf(states);
            assertFalse(place(states, packedIce, SlabType.BOTTOM));
            assertEquals(before, states);
        }
    }

    @Test
    void laterFeatureRestoringSupportAllowsTheProposedRim() {
        Map<BlockPos, BlockState> states = initial(blueIce, SlabType.BOTTOM, Blocks.WATER);
        assertFalse(place(states, blueIce, SlabType.BOTTOM));
        assertTrue(states.get(POS).is(Blocks.WATER));
        states.put(POS.below(), Blocks.BLUE_ICE.defaultBlockState());
        assertTrue(place(states, blueIce, SlabType.BOTTOM));
    }

    @Test
    void pendingTopConversionInNeighboringChunkCannotAnchorBottomSideRim() {
        Map<BlockPos, BlockState> states = initial(packedIce, SlabType.BOTTOM, Blocks.WATER);
        states.put(POS.east(), Blocks.PACKED_ICE.defaultBlockState());
        Map<BlockPos, BlockState> before = Map.copyOf(states);
        assertFalse(IcebergSlabPlacement.placeIfConnected(level(states), POS, packedIce,
                SlabType.BOTTOM, packedIce, blueIce, snow, POS.east()::equals));
        assertEquals(before, states);
        states.put(POS.east(), slab(packedIce, SlabType.TOP, false, true));
        assertFalse(place(states, packedIce, SlabType.BOTTOM));
    }

    @Test
    void placementTagsRetainPositionTypeAndMaterialThroughNativeNbt() {
        for (int x : new int[]{-17, -1, 0, 15, 16}) {
            for (int y : new int[]{-64, -1, 0, 70, 319}) {
                for (SlabType type : new SlabType[]{SlabType.BOTTOM, SlabType.TOP}) {
                    for (int material : new int[]{0, 0x2000, 0x1000}) {
                        BlockPos pos = new BlockPos(x, y, -x);
                        short packed = IcebergSlabPlacement.packPlacement(pos, type, material);
                        short stored = ShortTag.valueOf(packed).getAsShort();
                        assertNotEquals(0, stored & IcebergSlabPlacement.PLACEMENT_MARKER);
                        assertEquals(type == SlabType.TOP, IcebergSlabPlacement.isTop(stored));
                        assertEquals(material, stored & 0x3000);
                        assertEquals(pos, ProtoChunk.unpackOffsetCoordinates(stored,
                                SectionPos.blockToSectionCoord(y), new ChunkPos(pos)));
                    }
                }
            }
        }
    }

    private static Map<BlockPos, BlockState> initial(Block block, SlabType type, Block vacant) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        Block full = block == packedIce ? Blocks.PACKED_ICE : block == blueIce ? Blocks.BLUE_ICE : Blocks.SNOW_BLOCK;
        states.put(POS, type == SlabType.TOP ? full.defaultBlockState() : vacant.defaultBlockState());
        states.put(POS.below(), vacant.defaultBlockState());
        return states;
    }

    private static boolean place(Map<BlockPos, BlockState> states, Block block, SlabType type) {
        return IcebergSlabPlacement.placeIfConnected(level(states), POS, block, type,
                packedIce, blueIce, snow, pos -> false);
    }

    private static LevelAccessor level(Map<BlockPos, BlockState> states) {
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isOutsideBuildHeight")) return false;
                    if (method.getName().equals("getBlockState")) {
                        BlockPos pos = (BlockPos) args[0];
                        assertTrue(Math.abs(pos.getX() - POS.getX()) <= 1 && Math.abs(pos.getZ() - POS.getZ()) <= 1,
                                "Placement must check only direct neighbors");
                        return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
                    }
                    if (method.getName().equals("setBlock")) {
                        assertEquals(POS, args[0], "The generator must write only the proposed slab");
                        BlockState state = (BlockState) args[1];
                        assertTrue(state.is(packedIce) || state.is(blueIce) || state.is(snow),
                                "There must be no air/water cleanup writes");
                        states.put(POS, state);
                        return true;
                    }
                    throw new AssertionError("Unexpected world operation: " + method.getName());
                });
    }

    private static BlockState slab(Block block, SlabType type, boolean wet, boolean generated) {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, type)
                .setValue(SlabBlock.WATERLOGGED, wet).setValue(CustomSlab.GENERATED, generated);
    }
}
