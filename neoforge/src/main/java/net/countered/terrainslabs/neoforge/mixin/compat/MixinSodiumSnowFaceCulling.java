package net.countered.terrainslabs.neoforge.mixin.compat;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache;
import net.countered.terrainslabs.util.SnowRenderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockOcclusionCache.class, priority = 3000)
public abstract class MixinSodiumSnowFaceCulling {
    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private void terrain_slabs$keepSnowSide(BlockState state, BlockGetter level,
            BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (SnowRenderHelper.shouldKeepSnowSide(level, pos, state, face)) {
            cir.setReturnValue(true);
        }
    }
}
