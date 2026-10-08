package net.countered.terrainslabs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.countered.terrainslabs.platform.PlatformConfigHooks;
import net.countered.terrainslabs.util.SnowRenderHelper;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.QuadViewImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AoCalculator.class)
public abstract class MixinAoCalculator {
    @Final @Shadow protected net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo blockInfo;
    @Final @Shadow public float[] ao;
    @Unique private final float[] terrain_slabs$originalY = new float[4];

    @WrapMethod(method = "compute")
    private void terrain_slabs$lightAtSlabHeight(QuadViewImpl quad, boolean vanillaShade, Operation<Void> original) {
        BlockPos originalPos = blockInfo.blockPos;
        boolean offsetSnow = SnowRenderHelper.isOffsetSnow(blockInfo.blockView, originalPos, blockInfo.blockState);
        if (offsetSnow) {
            blockInfo.blockPos = originalPos.below();
            for (int i = 0; i < 4; i++) {
                terrain_slabs$originalY[i] = quad.y(i);
                ((QuadEmitter) quad).pos(i, quad.x(i), quad.y(i) + 0.5f, quad.z(i));
            }
        }
        try {
            original.call(quad, vanillaShade);
            if ((blockInfo.blockState.is(BlockTags.SLABS) || offsetSnow) && quad.lightFace() == Direction.UP) {
                float multiplier = PlatformConfigHooks.getAoStrength();
                for (int i = 0; i < 4; i++) {
                    ao[i] = 1.0f - (1.0f - ao[i]) * multiplier;
                }
            }
        } finally {
            if (offsetSnow) {
                blockInfo.blockPos = originalPos;
                for (int i = 0; i < 4; i++) {
                    ((QuadEmitter) quad).pos(i, quad.x(i), terrain_slabs$originalY[i], quad.z(i));
                }
            }
        }
    }
}
