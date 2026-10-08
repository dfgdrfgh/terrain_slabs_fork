package net.countered.terrainslabs.neoforge.mixin.compat;

import net.countered.terrainslabs.util.MixinHelper;
import net.countered.terrainslabs.util.SnowRenderHelper;
import net.countered.terrainslabs.neoforge.model.EmbeddiumSnowLightQuad;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.embeddedt.embeddium.api.render.chunk.BlockRenderContext;
import org.embeddedt.embeddium.impl.model.light.LightPipeline;
import org.embeddedt.embeddium.impl.model.light.data.QuadLightData;
import org.embeddedt.embeddium.impl.model.quad.ModelQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.world.level.block.state.BlockState;
import org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.BlockRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockRenderer.class)
public class MixinEmbeddiumBlockRenderer {

    @Redirect(
            method = "renderModel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;hasOffsetFunction()Z")
    )
    private boolean terrain_slabs$forceHasOffset(BlockState instance) {
        if (MixinHelper.terrain_slabs$isStateValidOnTop(instance)) {
            return true;
        }
        return instance.hasOffsetFunction();
    }
    @Unique private final EmbeddiumSnowLightQuad terrain_slabs$snowQuad = new EmbeddiumSnowLightQuad();

    @WrapOperation(method = "getVertexLight", at = @At(value = "INVOKE",
            target = "Lorg/embeddedt/embeddium/impl/model/light/LightPipeline;calculate(Lorg/embeddedt/embeddium/impl/model/quad/ModelQuadView;Lnet/minecraft/core/BlockPos;Lorg/embeddedt/embeddium/impl/model/light/data/QuadLightData;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;Z)V"))
    private void terrain_slabs$lightAtSlabHeight(LightPipeline pipeline, ModelQuadView quad,
            BlockPos pos, QuadLightData data, Direction cullFace, Direction lightFace,
            boolean shade, Operation<Void> original, @Local(argsOnly = true) BlockRenderContext ctx) {
        if (!SnowRenderHelper.isOffsetSnow(ctx.localSlice(), pos, ctx.state())) {
            original.call(pipeline, quad, pos, data, cullFace, lightFace, shade);
            return;
        }
        terrain_slabs$snowQuad.set(quad);
        try {
            original.call(pipeline, terrain_slabs$snowQuad, pos.below(), data,
                    cullFace, lightFace, shade);
        } finally {
            terrain_slabs$snowQuad.clear();
        }
    }
}