package net.countered.terrainslabs.neoforge.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.caffeinemc.mods.sodium.client.model.light.LightPipeline;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;
import net.countered.terrainslabs.neoforge.model.SodiumSnowLightQuad;
import net.countered.terrainslabs.util.SnowRenderHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractBlockRenderContext.class)
public abstract class MixinSodiumSnowLighting {
    @Shadow protected BlockAndTintGetter level;
    @Shadow protected BlockState state;
    @Unique private final SodiumSnowLightQuad terrain_slabs$snowQuad = new SodiumSnowLightQuad();

    @WrapOperation(method = "shadeQuad", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/model/light/LightPipeline;calculate(Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;Lnet/minecraft/core/BlockPos;Lnet/caffeinemc/mods/sodium/client/model/light/data/QuadLightData;Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;ZZ)V"))
    private void terrain_slabs$lightAtSlabHeight(LightPipeline pipeline, ModelQuadView quad,
            BlockPos pos, QuadLightData data, Direction cullFace, Direction lightFace,
            boolean shade, boolean enhanced, Operation<Void> original) {
        if (!SnowRenderHelper.isOffsetSnow(level, pos, state)) {
            original.call(pipeline, quad, pos, data, cullFace, lightFace, shade, enhanced);
            return;
        }
        terrain_slabs$snowQuad.set(quad);
        try {
            original.call(pipeline, terrain_slabs$snowQuad, pos.below(), data,
                    cullFace, lightFace, shade, enhanced);
        } finally {
            terrain_slabs$snowQuad.clear();
        }
    }
}
