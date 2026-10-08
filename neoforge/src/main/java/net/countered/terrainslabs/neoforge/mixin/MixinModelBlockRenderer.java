package net.countered.terrainslabs.neoforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.countered.terrainslabs.util.AOHelper;
import net.countered.terrainslabs.util.SnowRenderHelper;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public class MixinModelBlockRenderer {

    @Inject(method = "tesselateWithAO*", at = @At("HEAD"))
    private void terrain_slabs$setSnowFlag(
            BlockAndTintGetter level, BakedModel model, BlockState state,
            BlockPos pos, PoseStack poseStack, VertexConsumer vertexConsumer,
            boolean checkSides, RandomSource rand, long seed, int packedOverlay,
            ModelData modelData, RenderType renderType, CallbackInfo ci) {

        if (!SnowRenderHelper.isOffsetSnow(level, pos, state)) {
            AOHelper.SNOW_SLAB_POS.remove();
            return;
        }

        AOHelper.SNOW_SLAB_POS.set(pos.below());
    }

    @ModifyArg(
            method = "renderModelFaceAO",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer$AmbientOcclusionFace;calculate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;[FLjava/util/BitSet;Z)V"
            ),
            index = 2
    )
    private BlockPos terrain_slabs$fixAOPos(BlockPos pos) {
        BlockPos override = AOHelper.SNOW_SLAB_POS.get();
        return override != null ? override : pos;
    }

    @ModifyArg(
            method = "renderModelFaceAO",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer$AmbientOcclusionFace;calculate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;[FLjava/util/BitSet;Z)V"
            ),
            index = 4
    )
    private float[] terrain_slabs$fixAOShape(float[] shape) {
        if (AOHelper.SNOW_SLAB_POS.get() == null) return shape;

        int down = Direction.DOWN.get3DDataValue();
        int up = Direction.UP.get3DDataValue();
        int len = Direction.values().length;

        shape = shape.clone();
        shape[down] += 0.5f;
        shape[up] += 0.5f;
        shape[down + len] -= 0.5f;
        shape[up + len] -= 0.5f;

        return shape;
    }
    @Inject(method = "tesselateWithAO*", at = @At("RETURN"))
    private void terrain_slabs$clearSnowFlag(CallbackInfo ci) {
        AOHelper.SNOW_SLAB_POS.remove();
    }
}