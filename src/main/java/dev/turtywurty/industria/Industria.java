package dev.turtywurty.industria;

import dev.turtywurty.industria.blockentity.*;
import dev.turtywurty.industria.fluid.FluidData;
import dev.turtywurty.industria.init.*;
import dev.turtywurty.industria.init.list.TagList;
import dev.turtywurty.industria.init.worldgen.BiomeModificationInit;
import dev.turtywurty.industria.init.worldgen.FeatureInit;
import dev.turtywurty.industria.network.BatteryChargeModePayload;
import dev.turtywurty.industria.network.OpenSeismicScannerPayload;
import dev.turtywurty.industria.network.SyncFluidPocketsPayload;
import dev.turtywurty.industria.persistent.WorldFluidPocketsState;
import dev.turtywurty.industria.screenhandler.BatteryScreenHandler;
import dev.turtywurty.industria.util.ExtraPacketCodecs;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import team.reborn.energy.api.EnergyStorage;

public class Industria implements ModInitializer {
    public static final String MOD_ID = "industria";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    public static Text containerTitle(String name) {
        return Text.translatable("container." + MOD_ID + "." + name);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Loading Industria...");

        // Registries
        ItemInit.init();
        BlockInit.init();
        BlockEntityTypeInit.init();
        ScreenHandlerTypeInit.init();
        RecipeTypeInit.init();
        RecipeSerializerInit.init();
        ItemGroupInit.init();
        BiomeModificationInit.init();
        FeatureInit.init();
        FluidInit.init();
        AttachmentTypeInit.init();
        PositionSourceTypeInit.init();
        ComponentTypeInit.init();

        ExtraPacketCodecs.registerDefaults();

        // Item Lookup
        ItemStorage.SIDED.registerForBlockEntity(AlloyFurnaceBlockEntity::getInventoryProvider, BlockEntityTypeInit.ALLOY_FURNACE);

        EnergyStorage.SIDED.registerForBlockEntity(ThermalGeneratorBlockEntity::getEnergyProvider, BlockEntityTypeInit.THERMAL_GENERATOR);
        FluidStorage.SIDED.registerForBlockEntity(ThermalGeneratorBlockEntity::getFluidProvider, BlockEntityTypeInit.THERMAL_GENERATOR);
        ItemStorage.SIDED.registerForBlockEntity(ThermalGeneratorBlockEntity::getInventoryProvider, BlockEntityTypeInit.THERMAL_GENERATOR);

        EnergyStorage.SIDED.registerForBlockEntity(BatteryBlockEntity::getEnergyProvider, BlockEntityTypeInit.BATTERY);
        ItemStorage.SIDED.registerForBlockEntity(BatteryBlockEntity::getInventoryProvider, BlockEntityTypeInit.BATTERY);

        EnergyStorage.SIDED.registerForBlockEntity(CombustionGeneratorBlockEntity::getEnergyProvider, BlockEntityTypeInit.COMBUSTION_GENERATOR);
        ItemStorage.SIDED.registerForBlockEntity(CombustionGeneratorBlockEntity::getInventoryProvider, BlockEntityTypeInit.COMBUSTION_GENERATOR);

        EnergyStorage.SIDED.registerForBlockEntity(SolarPanelBlockEntity::getEnergyProvider, BlockEntityTypeInit.SOLAR_PANEL);

        EnergyStorage.SIDED.registerForBlockEntity(CrusherBlockEntity::getEnergyProvider, BlockEntityTypeInit.CRUSHER);
        ItemStorage.SIDED.registerForBlockEntity(CrusherBlockEntity::getInventoryProvider, BlockEntityTypeInit.CRUSHER);

        EnergyStorage.SIDED.registerForBlockEntity(CableBlockEntity::getEnergyProvider, BlockEntityTypeInit.CABLE);

        EnergyStorage.SIDED.registerForBlockEntity(WindTurbineBlockEntity::getEnergyProvider, BlockEntityTypeInit.WIND_TURBINE);

        // Payloads
        PayloadTypeRegistry.playC2S().register(BatteryChargeModePayload.ID, BatteryChargeModePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSeismicScannerPayload.ID, OpenSeismicScannerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncFluidPocketsPayload.ID, SyncFluidPocketsPayload.CODEC);

        // Packets
        ServerPlayNetworking.registerGlobalReceiver(BatteryChargeModePayload.ID, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayerEntity player = context.player();
                    ScreenHandler handler = player.currentScreenHandler;
                    if (handler instanceof BatteryScreenHandler batteryScreenHandler) {
                        batteryScreenHandler.getBlockEntity().setChargeMode(payload.chargeMode());
                    }
                }));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            sender.sendPacket(WorldFluidPocketsState.createSyncPacket(handler.player.getServerWorld()));
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(world instanceof ServerWorld serverWorld))
                return;

            WorldFluidPocketsState serverState = WorldFluidPocketsState.getServerState(serverWorld);
            if (serverState.removePosition(pos)) {
                WorldFluidPocketsState.sync(serverWorld);
            }
        });

        // Fluid Properties
        var crudeOilAttributes = new FluidVariantAttributeHandler() {
            @Override
            public int getViscosity(FluidVariant variant, @Nullable World world) {
                return 7500;
            }
        };

        FluidVariantAttributes.register(FluidInit.CRUDE_OIL, crudeOilAttributes);
        FluidVariantAttributes.register(FluidInit.CRUDE_OIL_FLOWING, crudeOilAttributes);

        // Fluid Data
        var crudeOilData = new FluidData.Builder(TagList.Fluids.CRUDE_OIL)
                .preventsBlockSpreading()
                .canSwim()
                .fluidMovementSpeed((entity, speed) -> 0.01F)
                .applyWaterMovement()
                .applyBuoyancy(itemEntity -> itemEntity.setVelocity(itemEntity.getVelocity().add(0.0D, 0.01D, 0.0D)))
                .canCauseDrowning()
                .shouldWitchDrinkWaterBreathing()
                .affectsBlockBreakSpeed()
                .bubbleParticle(ParticleTypes.ASH)
                .splashParticle(ParticleTypes.HEART)
                .build();

        FluidData.registerFluidData(FluidInit.CRUDE_OIL, crudeOilData);
        FluidData.registerFluidData(FluidInit.CRUDE_OIL_FLOWING, crudeOilData);

        LOGGER.info("Industria has finished loading!");
    }
}