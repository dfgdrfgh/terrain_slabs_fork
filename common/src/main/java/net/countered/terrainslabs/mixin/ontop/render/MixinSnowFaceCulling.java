package net.countered.terrainslabs.mixin.ontop.render;

import net.countered.terrainslabs.util.SnowRenderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// MoreCulling injects at HEAD with priority 2500. Decide offset snow first,
// before its position-independent shape cache can discard an exposed face.
@Mixin(value = Block.class, priority = 3000)
public abstract class MixinSnowFaceCulling {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void terrain_slabs$keepSnowSide(BlockState state, BlockGetter level,
            BlockPos pos, Direction face, BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        if (SnowRenderHelper.shouldKeepSnowSide(level, pos, state, face)) {
            cir.setReturnValue(true);
        }
    }
}
