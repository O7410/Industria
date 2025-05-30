package dev.turtywurty.industria.renderer.block;

import dev.turtywurty.industria.blockentity.WindTurbineBlockEntity;
import dev.turtywurty.industria.model.WindTurbineModel;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class WindTurbineBlockEntityRenderer implements BlockEntityRenderer<WindTurbineBlockEntity> {
    private final BlockEntityRendererFactory.Context context;
    private final WindTurbineModel model;

    public WindTurbineBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.context = context;

        this.model = new WindTurbineModel(context.getLayerModelPart(WindTurbineModel.LAYER_LOCATION));
    }

    @Override
    public void render(WindTurbineBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
        matrices.push();
        matrices.translate(0.5f, 1.5f, 0.5f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180));

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180 + switch (entity.getCachedState().get(Properties.HORIZONTAL_FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        }));

        float outputPercentage = getEnergyPerTickPercent(entity);
        entity.setPropellerRotation(entity.getPropellerRotation() + (outputPercentage * 0.25f));
        model.getWindTurbineParts().propellers().roll = entity.getPropellerRotation();

        VertexConsumer consumer = vertexConsumers.getBuffer(this.model.getLayer(WindTurbineModel.TEXTURE_LOCATION));
        this.model.render(matrices, consumer, light, overlay);
        this.model.getWindTurbineParts().propellers().roll = 0.0F;
        matrices.pop();
    }

    public int getEnergyPerTick(WindTurbineBlockEntity blockEntity) {
        return blockEntity.getEnergyOutput();
    }

    public float getEnergyPerTickPercent(WindTurbineBlockEntity blockEntity) {
        int output = getEnergyPerTick(blockEntity);
        if (output == 0)
            return 0.0F;

        return MathHelper.clamp((float) output / 500.0F, 0.0F, 1.0F);
    }
}
