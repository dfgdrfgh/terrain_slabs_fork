package net.countered.terrainslabs.mixin.feature;

import net.countered.terrainslabs.util.IcebergSlabCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.IcebergFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IcebergFeature.class)
public class MixinIcebergFeature {
    @Inject(method = "isIcebergState", at = @At("RETURN"), cancellable = true)
    private static void terrain_slabs$includeGeneratedSlabs(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && IcebergSlabCompatibility.isGeneratedIcebergSlab(state)) {
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "setIcebergBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState terrain_slabs$allowOverlappingIceberg(LevelAccessor level, BlockPos pos) {
        return IcebergSlabCompatibility.placementState(level.getBlockState(pos));
    }

    @Inject(method = "belowIsAir", at = @At("RETURN"), cancellable = true)
    private void terrain_slabs$checkTopSlabSupport(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        BlockState state = level.getBlockState(pos);
        if (IcebergSlabCompatibility.isGeneratedIcebergSlab(state) && IcebergSlabCompatibility.isTopSlab(state)) {
            cir.setReturnValue(!IcebergSlabCompatibility.topSlabHasSupport(level, pos));
        }
    }
}
