package net.countered.terrainslabs.mixin.blocks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.countered.terrainslabs.registries.ModBlocksRegistry;
import net.countered.terrainslabs.util.MixinHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpreadingSnowyDirtBlock.class)
public abstract class SpreadingSnowyDirtBlockMixin {
    // Reuse vanilla's four target positions, light checks and propagation checks.
    // Mycelium keeps its original behavior.
    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/world/level/block/Block;)Z"))
    private boolean terrain_slabs$includeDirtSlabs(BlockState candidate, Block expected,
                                                  Operation<Boolean> original) {
        return original.call(candidate, expected)
                || ((Object) this == Blocks.GRASS_BLOCK && expected == Blocks.DIRT
                    && candidate.is(ModBlocksRegistry.DIRT_SLAB.get())
                    && !candidate.getValue(SlabBlock.WATERLOGGED));
    }

    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean terrain_slabs$spreadGrassOntoSlab(ServerLevel level, BlockPos pos,
                                                    BlockState replacement, Operation<Boolean> original) {
        BlockState existing = level.getBlockState(pos);
        if ((Object) this == Blocks.GRASS_BLOCK && replacement.is(Blocks.GRASS_BLOCK)
                && existing.is(ModBlocksRegistry.DIRT_SLAB.get())) {
            replacement = MixinHelper.withCopiedSlabProperties(existing, ModBlocksRegistry.GRASS_SLAB.get())
                    .setValue(BlockStateProperties.SNOWY, replacement.getValue(BlockStateProperties.SNOWY));
        }
        return original.call(level, pos, replacement);
    }
}
