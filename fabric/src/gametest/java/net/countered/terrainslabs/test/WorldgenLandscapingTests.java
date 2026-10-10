package net.countered.terrainslabs.test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.countered.terrainslabs.generation.SlabFeature;
import net.countered.terrainslabs.generation.SurfaceGrassSlabs;
import net.countered.terrainslabs.registries.ModBlocksRegistry;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.RuleBasedBlockStateProvider;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;

public class WorldgenLandscapingTests implements FabricGameTest {
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void diskPreservesLandscapingAndUpdatesGeneratedSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (SlabType type : SlabType.values()) {
            for (Block block : List.of(Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_SLAB)) {
                BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
                BlockState authored = block.defaultBlockState().setValue(SlabBlock.TYPE, type)
                        .setValue(SlabBlock.WATERLOGGED, true);
                BlockState generated = ModBlocksRegistry.CUSTOM_STONE_SLAB.get().defaultBlockState()
                        .setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, true)
                        .setValue(CustomSlab.GENERATED, true);
                level.setBlock(center, Blocks.DIRT.defaultBlockState(), 2);
                level.setBlock(center.above(), authored, 2);
                level.setBlock(center.below(), generated, 2);
                placeDisk(level, center);
                helper.assertTrue(level.getBlockState(center).is(Blocks.CLAY), "Disk must place its material");
                helper.assertTrue(level.getBlockState(center.above()).equals(authored), "Disk changed WWOO slab " + block + " " + type);
                BlockState expected = ModBlocksRegistry.CLAY_SLAB.get().defaultBlockState()
                        .setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, true)
                        .setValue(CustomSlab.GENERATED, true);
                helper.assertTrue(level.getBlockState(center.below()).equals(expected), "Generated slab must change material and retain its properties");
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void diskPreservesPlacedTerrainSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockState placed = ModBlocksRegistry.CUSTOM_STONE_SLAB.get().defaultBlockState();
        level.setBlock(center, Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(center.above(), placed, 2);
        level.setBlock(center.below(), placed, 2);
        placeDisk(level, center);
        helper.assertTrue(level.getBlockState(center).is(Blocks.CLAY), "Disk must place its material");
        helper.assertTrue(level.getBlockState(center.above()).equals(placed), "Disk changed an unmarked upper slab");
        helper.assertTrue(level.getBlockState(center.below()).equals(placed), "Disk changed an unmarked lower slab");
        helper.succeed();
    }

    private static void placeDisk(ServerLevel level, BlockPos center) {
        DiskConfiguration config = new DiskConfiguration(
                new RuleBasedBlockStateProvider(BlockStateProvider.simple(Blocks.CLAY), List.of()),
                BlockPredicate.matchesBlocks(Blocks.DIRT), ConstantInt.of(0), 0);
        new DiskFeature(DiskConfiguration.CODEC).place(config, level,
                level.getChunkSource().getGenerator(), RandomSource.create(7L), center);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void undersidePassPreservesSlabsAndDecorativeFullBlocks(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(3, 3, 3));
        SlabFeature feature = new SlabFeature(NoneFeatureConfiguration.CODEC);
        Method eligible = SlabFeature.class.getDeclaredMethod("shouldPlaceTopSlab", WorldGenLevel.class, BlockPos.class);
        Method place = SlabFeature.class.getDeclaredMethod("placeTopSlab", WorldGenLevel.class, BlockPos.class);
        eligible.setAccessible(true);
        place.setAccessible(true);
        // A valid cave lip: the old pass would shrink every full-shape state
        // below the dirt ceiling, including WWOO's double slabs and tuff bricks.
        level.setBlock(center.above(), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(center.below(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(center.east(), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(center.east().below(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(center.west(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(center.west().above(), Blocks.DIRT.defaultBlockState(), 2);
        for (Block block : List.of(Blocks.POLISHED_TUFF_SLAB, Blocks.TUFF_BRICK_SLAB,
                Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF, Blocks.CHISELED_TUFF_BRICKS)) {
            for (SlabType type : SlabType.values()) {
                BlockState authored = block.defaultBlockState();
                if (authored.hasProperty(SlabBlock.TYPE)) authored = authored.setValue(SlabBlock.TYPE, type);
                level.setBlock(center, authored, 2);
                helper.assertTrue(!(boolean) eligible.invoke(feature, level, center), "Underside pass selected authored " + authored);
                place.invoke(feature, level, center);
                helper.assertTrue(level.getBlockState(center).equals(authored), "Underside pass replaced authored " + authored);
            }
        }
        level.setBlock(center, Blocks.STONE.defaultBlockState(), 2);
        helper.assertTrue((boolean) eligible.invoke(feature, level, center), "Natural terrain must still qualify");
        place.invoke(feature, level, center);
        helper.assertTrue(level.getBlockState(center).is(ModBlocksRegistry.DIRT_SLAB.get()), "Natural terrain should still get an underside slab");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_slabs_ore")
    public void orePreservesAuthoredSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(4, 5, 4));
        BlockState authored = Blocks.POLISHED_TUFF_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = -3; y <= 3; y++) level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 2);
                level.setBlock(center.offset(x, -1, z), authored, 2);
                level.setBlock(center.offset(x, 1, z), authored, 2);
            }
        }
        OreConfiguration config = new OreConfiguration(new BlockMatchTest(Blocks.STONE), Blocks.ANDESITE.defaultBlockState(), 64);
        boolean changed = false;
        for (int i = 0; i < 8; i++) {
            changed |= new OreFeature(OreConfiguration.CODEC).place(config, level,
                    level.getChunkSource().getGenerator(), RandomSource.create(i), center);
        }
        helper.assertTrue(changed, "Ore feature must actually place blocks");
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                helper.assertTrue(level.getBlockState(center.offset(x, -1, z)).equals(authored), "Ore replaced a lower authored slab");
                helper.assertTrue(level.getBlockState(center.offset(x, 1, z)).equals(authored), "Ore replaced an upper authored slab");
            }
        }
        helper.succeed();
    }

    // Always select the eastern neighbor, so propagation tests do not rely on
    // waiting for random ticks to happen to pick the fixture.
    private static RandomSource eastNeighborRandom() {
        return new LegacyRandomSource(0L) {
            private int coordinate;

            @Override
            public int nextInt(int bound) {
                int value = switch (coordinate++ % 3) {
                    case 0 -> 2;
                    case 1 -> 3;
                    default -> 1;
                };
                if (value >= bound) throw new AssertionError("Unexpected propagation random bound: " + bound);
                return value;
            }
        };
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void fullGrassSpreadsOntoDirtSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos target = source.east();
        level.setBlock(source, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(source.above(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getMaxLocalRawBrightness(source.above()) >= 9, "Fixture must have light for vanilla grass spreading");
            for (SlabType type : SlabType.values()) {
                for (boolean generated : List.of(false, true)) {
                    BlockState dirt = ModBlocksRegistry.DIRT_SLAB.get().defaultBlockState()
                            .setValue(SlabBlock.TYPE, type).setValue(CustomSlab.GENERATED, generated);
                    level.setBlock(target, dirt, 2);
                    level.getBlockState(source).randomTick(level, source, eastNeighborRandom());
                    BlockState expected = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState()
                            .setValue(SlabBlock.TYPE, type).setValue(CustomSlab.GENERATED, generated);
                    helper.assertTrue(level.getBlockState(target).equals(expected), "Full grass failed to spread onto " + dirt);
                }
            }
            level.setBlock(target, Blocks.DIRT.defaultBlockState(), 2);
            level.getBlockState(source).randomTick(level, source, eastNeighborRandom());
            helper.assertTrue(level.getBlockState(target).is(Blocks.GRASS_BLOCK), "Ordinary full-block spreading must still work");
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void slabGrassPreservesTargetShapeWhenSpreading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos target = source.east();
        BlockState grass = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState();
        level.setBlock(source, grass, 2);
        level.setBlock(source.above(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getMaxLocalRawBrightness(source.above()) >= 9, "Fixture must have light for slab grass spreading");
            for (SlabType type : SlabType.values()) {
                for (boolean generated : List.of(false, true)) {
                    BlockState dirt = ModBlocksRegistry.DIRT_SLAB.get().defaultBlockState()
                            .setValue(SlabBlock.TYPE, type).setValue(CustomSlab.GENERATED, generated);
                    level.setBlock(target, dirt, 2);
                    grass.randomTick(level, source, eastNeighborRandom());
                    BlockState expected = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState()
                            .setValue(SlabBlock.TYPE, type).setValue(CustomSlab.GENERATED, generated);
                    helper.assertTrue(level.getBlockState(target).equals(expected), "Slab grass changed target shape or marker: " + dirt);
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void grassDoesNotSpreadOntoWetOrCoveredSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos target = source.east();
        level.setBlock(source.above(), Blocks.AIR.defaultBlockState(), 2);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(level.getMaxLocalRawBrightness(source.above()) >= 9, "Fixture must have light so only the target condition blocks growth");
            for (BlockState grass : List.of(Blocks.GRASS_BLOCK.defaultBlockState(),
                    ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState())) {
                level.setBlock(source, grass, 2);
                BlockState wet = ModBlocksRegistry.DIRT_SLAB.get().defaultBlockState()
                        .setValue(SlabBlock.WATERLOGGED, true).setValue(CustomSlab.GENERATED, true);
                level.setBlock(target.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(target, wet, 2);
                grass.randomTick(level, source, eastNeighborRandom());
                helper.assertTrue(level.getBlockState(target).equals(wet), "Grass spread onto a waterlogged slab");
                BlockState dry = wet.setValue(SlabBlock.WATERLOGGED, false);
                level.setBlock(target, dry, 2);
                level.setBlock(target.above(), Blocks.STONE.defaultBlockState(), 2);
                grass.randomTick(level, source, eastNeighborRandom());
                helper.assertTrue(level.getBlockState(target).equals(dry), "Grass spread under a solid cover");
            }
            helper.succeed();
        });
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void grassDecayPreservesSlabProperties(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
        for (SlabType type : SlabType.values()) {
            for (boolean waterlogged : List.of(false, true)) {
                for (boolean generated : List.of(false, true)) {
                    BlockState grass = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState()
                            .setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, waterlogged)
                            .setValue(CustomSlab.GENERATED, generated);
                    level.setBlock(pos, grass, 2);
                    grass.randomTick(level, pos, eastNeighborRandom());
                    BlockState expected = ModBlocksRegistry.DIRT_SLAB.get().defaultBlockState()
                            .setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, waterlogged)
                            .setValue(CustomSlab.GENERATED, generated);
                    helper.assertTrue(level.getBlockState(pos).equals(expected), "Grass decay changed slab properties: " + grass);
                }
            }
        }
        helper.succeed();
    }
    private static void placeSurfaceSteps(ServerLevel level, Set<BlockPos> positions) throws Exception {
        SlabFeature feature = new SlabFeature(NoneFeatureConfiguration.CODEC);
        Method place = SlabFeature.class.getDeclaredMethod("placeBottomSlabs", WorldGenLevel.class, Set.class);
        place.setAccessible(true);
        place.invoke(feature, level, positions);
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void grassEdgesGenerateAsGrassImmediatelyInEitherOrder(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos edge = helper.absolutePos(new BlockPos(2, 3, 2));
        BlockPos upper = edge.east().above();
        for (boolean upperFirst : List.of(false, true)) {
            level.setBlock(edge.below(), Blocks.DIRT.defaultBlockState(), 2);
            level.setBlock(edge, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(edge.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(edge.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            level.setBlock(upper, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(upper.above(), Blocks.AIR.defaultBlockState(), 2);
            Set<BlockPos> positions = new LinkedHashSet<>(upperFirst ? List.of(upper, edge) : List.of(edge, upper));
            placeSurfaceSteps(level, positions);
            for (BlockPos pos : positions) {
                BlockState expected = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState()
                        .setValue(CustomSlab.GENERATED, true);
                helper.assertTrue(level.getBlockState(pos).equals(expected),
                        "Grass edge generated dirt before any random tick; upperFirst=" + upperFirst);
                helper.assertTrue(level.getBlockState(pos.below()).is(Blocks.DIRT), "Grass slab must retain dirt support");
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void extendedGrassStepsGenerateAsGrassImmediately(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos edge = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockState generatedGrass = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState()
                .setValue(CustomSlab.GENERATED, true);
        for (boolean raisedNeighbor : List.of(false, true)) {
            level.setBlock(edge.below(), Blocks.DIRT.defaultBlockState(), 2);
            level.setBlock(edge, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(edge.above(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(edge.east(), raisedNeighbor ? Blocks.DIRT.defaultBlockState() : generatedGrass, 2);
            level.setBlock(edge.east().above(), raisedNeighbor ? generatedGrass : Blocks.AIR.defaultBlockState(), 2);
            placeSurfaceSteps(level, Set.of(edge));
            helper.assertTrue(level.getBlockState(edge).equals(generatedGrass),
                    "Extended/corner step lost grass from an already smoothed surface");
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
    public void grassEdgeGenerationKeepsOtherMaterialsAndAuthoredSlabs(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos edge = helper.absolutePos(new BlockPos(3, 3, 3));
        level.setBlock(edge.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(edge.east().above(), Blocks.AIR.defaultBlockState(), 2);
        for (Block substrate : List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.STONE)) {
            for (String condition : List.of("dry", "water", "covered", "no_grass")) {
                level.setBlock(edge.below(), substrate.defaultBlockState(), 2);
                level.setBlock(edge, condition.equals("water") ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(edge.above(), condition.equals("covered") ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(edge.east(), condition.equals("no_grass") ? Blocks.DIRT.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                placeSurfaceSteps(level, Set.of(edge));
                Block expected = substrate == Blocks.DIRT && condition.equals("dry")
                        ? ModBlocksRegistry.GRASS_SLAB.get()
                        : net.countered.terrainslabs.block.ModSlabsMap.getSlabForBlock(substrate);
                BlockState state = level.getBlockState(edge);
                helper.assertTrue(state.is(expected), "Wrong surface material for " + substrate + " " + condition);
                helper.assertTrue(state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM && state.getValue(CustomSlab.GENERATED),
                        "Surface step must retain bottom shape and generation marker");
                helper.assertTrue(state.getValue(SlabBlock.WATERLOGGED) == condition.equals("water"), "Surface step lost waterlogging");
            }
        }
        BlockState authored = Blocks.POLISHED_TUFF_SLAB.defaultBlockState();
        level.setBlock(edge.below(), Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(edge.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(edge.above(), Blocks.AIR.defaultBlockState(), 2);
        level.setBlock(edge, authored, 2);
        placeSurfaceSteps(level, Set.of(edge));
        helper.assertTrue(level.getBlockState(edge).equals(authored), "Grass edge conversion replaced an authored WWOO slab");
        helper.succeed();
    }

    private static void clearSurfaceFixture(ServerLevel level, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -2; y <= 2; y++) level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_slabs_late_grass")
    public void lateGrassDecorationGreensGeneratedStepsBeforeRandomTicks(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 4, 3));
        for (int height : List.of(-1, 0, 1)) {
            for (boolean diagonal : List.of(false, true)) {
                clearSurfaceFixture(level, pos);
                level.setBlock(pos.below(), Blocks.DIRT.defaultBlockState(), 2);
                placeSurfaceSteps(level, Set.of(pos));
                helper.assertTrue(level.getBlockState(pos).is(ModBlocksRegistry.DIRT_SLAB.get()),
                        "Fixture must generate its dirt slab before the late grass exists");
                level.setBlock(pos.offset(1, height, diagonal ? 1 : 0), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                level.getChunkAt(pos).postProcessGeneration();
                BlockState expected = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState().setValue(CustomSlab.GENERATED, true);
                helper.assertTrue(level.getBlockState(pos).equals(expected),
                        "Late grass did not immediately green the generated step: height=" + height + ", diagonal=" + diagonal);
            }
        }
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_slabs_late_grass")
    public void finishingGrassPassPreservesCoveredWetAndUnmarkedSlabs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 4, 3));
        for (SlabType type : SlabType.values()) {
            for (String condition : List.of("exposed", "covered", "wet", "unmarked", "no_grass")) {
                clearSurfaceFixture(level, pos);
                BlockState dirt = ModBlocksRegistry.DIRT_SLAB.get().defaultBlockState()
                        .setValue(SlabBlock.TYPE, type).setValue(SlabBlock.WATERLOGGED, condition.equals("wet"))
                        .setValue(CustomSlab.GENERATED, !condition.equals("unmarked"));
                level.setBlock(pos, dirt, 2);
                if (!condition.equals("no_grass")) level.setBlock(pos.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                if (condition.equals("covered")) level.setBlock(pos.above(), Blocks.STONE.defaultBlockState(), 2);
                level.getChunkAt(pos).postProcessGeneration();
                BlockState expected = condition.equals("exposed")
                        ? ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState()
                                .setValue(SlabBlock.TYPE, type).setValue(CustomSlab.GENERATED, true)
                        : dirt;
                helper.assertTrue(level.getBlockState(pos).equals(expected), "Finishing pass changed wrong state: " + condition + " " + type);
            }
        }
        clearSurfaceFixture(level, pos);
        BlockState authored = Blocks.POLISHED_TUFF_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
        level.setBlock(pos, authored, 2);
        level.setBlock(pos.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.getChunkAt(pos).postProcessGeneration();
        helper.assertTrue(level.getBlockState(pos).equals(authored), "Finishing pass replaced authored WWOO landscaping");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_slabs_late_grass")
    public void lateGrassAcrossChunkEdgeGreensExistingGeneratedStep(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixture = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos pos = new BlockPos((fixture.getX() & ~15) + 15, fixture.getY(), fixture.getZ());
        clearSurfaceFixture(level, pos);
        BlockState dirt = ModBlocksRegistry.DIRT_SLAB.get().defaultBlockState().setValue(CustomSlab.GENERATED, true);
        level.setBlock(pos, dirt, 2);
        level.setBlock(pos.west(), dirt, 2);
        level.setBlock(pos.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.getChunkAt(pos.east()).postProcessGeneration();
        helper.assertTrue(level.getBlockState(pos).is(ModBlocksRegistry.GRASS_SLAB.get()), "Late neighboring chunk grass left a dirt seam");
        helper.assertTrue(level.getBlockState(pos.west()).is(ModBlocksRegistry.GRASS_SLAB.get()), "Connected exposed step must finish as grass too");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_slabs_late_grass")
    public void lateNeighborFeatureInsideChunkIsCorrectedBeforeTicking(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixture = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos pos = new BlockPos((fixture.getX() & ~15) + 8, fixture.getY(), (fixture.getZ() & ~15) + 8);
        clearSurfaceFixture(level, pos);
        BlockState grass = ModBlocksRegistry.GRASS_SLAB.get().defaultBlockState().setValue(CustomSlab.GENERATED, true);
        level.setBlock(pos, grass, 2);
        level.setBlock(pos.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        // A disk feature from a neighboring chunk can reach beyond the one-block
        // border visited by the former end-of-decoration cleanup.
        DiskConfiguration config = new DiskConfiguration(
                new RuleBasedBlockStateProvider(BlockStateProvider.simple(Blocks.DIRT), List.of()),
                BlockPredicate.matchesBlocks(Blocks.STONE), ConstantInt.of(0), 0);
        new DiskFeature(DiskConfiguration.CODEC).place(config, level,
                level.getChunkSource().getGenerator(), RandomSource.create(7L), pos.below());
        helper.assertTrue(level.getBlockState(pos).is(ModBlocksRegistry.DIRT_SLAB.get()), "Late feature must reproduce the dirt slab");
        SurfaceGrassSlabs.finishDecoration(level, level.getChunkAt(pos.offset(16, 0, 0)));
        helper.assertTrue(level.getBlockState(pos).is(ModBlocksRegistry.DIRT_SLAB.get()), "Old neighboring border pass must miss this interior slab");
        level.getChunkAt(pos).postProcessGeneration();
        helper.assertTrue(level.getBlockState(pos).equals(grass), "Chunk must become tickable with its exposed grass surface restored");
        helper.succeed();
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "terrain_slabs_late_grass")
    public void finishingGrassPassCompletesConnectedStepsWithoutCrossingCoveredOrUnmarkedGaps(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fixture = helper.absolutePos(new BlockPos(3, 4, 3));
        BlockPos pos = new BlockPos((fixture.getX() & ~15) + 8, fixture.getY(), (fixture.getZ() & ~15) + 8);
        for (String barrier : List.of("covered", "wet", "unmarked", "air")) {
            for (int x = -1; x <= 6; x++) {
                for (int z = -2; z <= 2; z++) {
                    for (int y = -2; y <= 2; y++) level.setBlock(pos.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            BlockState dirt = ModBlocksRegistry.DIRT_SLAB.get().defaultBlockState().setValue(CustomSlab.GENERATED, true);
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int x = 1; x <= 6; x++) level.setBlock(pos.offset(x, 0, 0), dirt, 2);
            BlockPos gap = pos.offset(4, 0, 0);
            if (barrier.equals("covered")) level.setBlock(gap.above(), Blocks.STONE.defaultBlockState(), 2);
            if (barrier.equals("wet")) level.setBlock(gap, dirt.setValue(SlabBlock.WATERLOGGED, true), 2);
            if (barrier.equals("unmarked")) level.setBlock(gap, dirt.setValue(CustomSlab.GENERATED, false), 2);
            if (barrier.equals("air")) level.setBlock(gap, Blocks.AIR.defaultBlockState(), 2);
            BlockState unchangedGap = level.getBlockState(gap);
            level.getChunkAt(pos).postProcessGeneration();
            for (int x = 1; x <= 3; x++) {
                helper.assertTrue(level.getBlockState(pos.offset(x, 0, 0)).is(ModBlocksRegistry.GRASS_SLAB.get()),
                        "Connected surface still contains dirt before random ticks: " + barrier + " x=" + x);
            }
            helper.assertTrue(level.getBlockState(gap).equals(unchangedGap), "Changed barrier " + barrier);
            for (int x = 5; x <= 6; x++) {
                helper.assertTrue(level.getBlockState(pos.offset(x, 0, 0)).equals(dirt), "Crossed the " + barrier + " gap");
            }
        }
        helper.succeed();
    }
}
