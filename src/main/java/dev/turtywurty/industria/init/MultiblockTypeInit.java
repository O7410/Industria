package dev.turtywurty.industria.init;

import dev.turtywurty.industria.Industria;
import dev.turtywurty.industria.block.ClarifierBlock;
import dev.turtywurty.industria.block.DigesterBlock;
import dev.turtywurty.industria.block.MixerBlock;
import dev.turtywurty.industria.blockentity.*;
import dev.turtywurty.industria.multiblock.MultiblockType;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registry;

public class MultiblockTypeInit {
    public static final MultiblockType<OilPumpJackBlockEntity> OIL_PUMP_JACK = register("oil_pump_jack",
            new MultiblockType.Builder<OilPumpJackBlockEntity>(123)
                    .setHasDirectionProperty(true));

    public static final MultiblockType<DrillBlockEntity> DRILL = register("drill",
            new MultiblockType.Builder<DrillBlockEntity>(26) // 3x3x3
                    .setHasDirectionProperty(true));

    public static final MultiblockType<UpgradeStationBlockEntity> UPGRADE_STATION = register("upgrade_station",
            new MultiblockType.Builder<UpgradeStationBlockEntity>(12)
                    .setHasDirectionProperty(true));

    public static final MultiblockType<MixerBlockEntity> MIXER = register("mixer",
            new MultiblockType.Builder<MixerBlockEntity>(26) // 3x3x3
                    .setHasDirectionProperty(true)
                    .shapes(MixerBlock.VOXEL_SHAPE)
                    .setOnMultiblockBreak((world, pos) -> {
                        if (world.getBlockEntity(pos) instanceof MixerBlockEntity blockEntity) {
                            blockEntity.breakMultiblock(world, pos);

                            blockEntity.getWrappedInventoryStorage().dropContents(world, pos);
                        }
                    }));

    public static final MultiblockType<DigesterBlockEntity> DIGESTER = register("digester",
            new MultiblockType.Builder<DigesterBlockEntity>(44) // 3x3x4
                    .setHasDirectionProperty(true)
                    .shapes(DigesterBlock.VOXEL_SHAPE)
                    .setOnMultiblockBreak((world, pos) -> {
                        if (world.getBlockEntity(pos) instanceof DigesterBlockEntity blockEntity) {
                            blockEntity.breakMultiblock(world, pos);

                            blockEntity.getWrappedInventoryStorage().dropContents(world, pos);
                        }
                    }));

    public static final MultiblockType<ClarifierBlockEntity> CLARIFIER = register("clarifier",
            new MultiblockType.Builder<ClarifierBlockEntity>(17) // 3x3x2
                    .setHasDirectionProperty(true)
                    .shapes(ClarifierBlock.VOXEL_SHAPE)
                    .setOnMultiblockBreak((world, pos) -> {
                        if (world.getBlockEntity(pos) instanceof ClarifierBlockEntity blockEntity) {
                            blockEntity.breakMultiblock(world, pos);

                            blockEntity.getWrappedInventoryStorage().dropContents(world, pos);
                        }
                    }));

    public static <T extends BlockEntity> MultiblockType<T> register(String name, MultiblockType.Builder<T> builder) {
        return Registry.register(IndustriaRegistries.MULTIBLOCK_TYPES, Industria.id(name), builder.build());
    }

    public static void init() {
    }
}
