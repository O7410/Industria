package dev.turtywurty.industria.pipe.impl.network;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.turtywurty.heatapi.api.HeatStorage;
import dev.turtywurty.heatapi.api.base.NoLimitHeatStorage;
import dev.turtywurty.heatapi.api.base.SimpleHeatStorage;
import dev.turtywurty.industria.init.PipeNetworkTypeInit;
import dev.turtywurty.industria.multiblock.TransferType;
import dev.turtywurty.industria.pipe.PipeNetwork;
import dev.turtywurty.industria.pipe.PipeNetworkType;
import dev.turtywurty.industria.util.ExtraCodecs;
import dev.turtywurty.industria.util.ExtraPacketCodecs;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

public class HeatPipeNetwork extends PipeNetwork<HeatStorage> {
    public static final MapCodec<HeatPipeNetwork> CODEC = PipeNetwork.createCodec(
            Codec.DOUBLE.fieldOf("storageAmount").forGetter(network -> network.storage.getAmount()),
            (storage, storageAmount) -> ((SimpleHeatStorage) storage).setAmount(storageAmount),
            HeatPipeNetwork::new);

    public static final PacketCodec<RegistryByteBuf, HeatPipeNetwork> PACKET_CODEC =
            PipeNetwork.createPacketCodec(
                    PacketCodecs.DOUBLE,
                    network -> network.storage.getAmount(),
                    (storage, storageAmount) -> ((SimpleHeatStorage) storage).setAmount(storageAmount),
                    HeatPipeNetwork::new);

    public static final Codec<Set<HeatPipeNetwork>> SET_CODEC = ExtraCodecs.setOf(CODEC);
    public static final PacketCodec<RegistryByteBuf, Set<HeatPipeNetwork>> SET_PACKET_CODEC =
            ExtraPacketCodecs.setOf(PACKET_CODEC);

    private final Map<BlockPos, Map<BlockPos, Integer>> pipeToSourceDistance = new HashMap<>();
    private final Map<BlockPos, HeatStorage> pipeStorages = new HashMap<>();

    public HeatPipeNetwork(UUID id) {
        super(id, TransferType.HEAT);
    }

    @Override
    protected SimpleHeatStorage createStorage() {
        return new NoLimitHeatStorage(true, true);
    }

    @Override
    protected PipeNetworkType<HeatStorage, ? extends PipeNetwork<HeatStorage>> getType() {
        return PipeNetworkTypeInit.HEAT;
    }

    @Override
    protected void onConnectedBlocksChanged(World world) {
        super.onConnectedBlocksChanged(world);
        this.pipeToSourceDistance.clear();

        // Go through connected blocks, get the heat storage, check if it supports extraction
        Set<BlockPos> sources = new HashSet<>();
        for (BlockPos connectedBlockPos : this.connectedBlocks) {
            for (Direction direction : Direction.values()) {
                BlockPos offset = connectedBlockPos.offset(direction);
                if (!this.pipes.contains(offset))
                    continue;

                HeatStorage storage = this.transferType.lookup(world, connectedBlockPos, direction);
                if (storage != null && storage.supportsExtraction()) {
                    sources.add(connectedBlockPos);
                    break;
                }
            }
        }

        // Go through sources, calculate distance to each pipe
        for (BlockPos sourcePos : sources) {
            Map<BlockPos, Integer> pipeDistances = calculateDistancesFromSource(sourcePos);
            this.pipeToSourceDistance.put(sourcePos, pipeDistances);
        }
    }

    private Map<BlockPos, Integer> calculateDistancesFromSource(BlockPos sourcePos) {
        // Find adjacent pipes
        Set<BlockPos> adjacentPipes = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos adjacentPos = sourcePos.offset(dir);
            if (this.pipes.contains(adjacentPos)) {
                adjacentPipes.add(adjacentPos);
            }
        }

        if (adjacentPipes.isEmpty()) {
            return new HashMap<>();
        }

        // BFS to compute the distances to all pipes
        Map<BlockPos, Integer> distances = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        for (BlockPos startPos : adjacentPipes) {
            queue.add(startPos);
            distances.put(startPos, 0);
            visited.add(startPos);
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int currentDistance = distances.get(current);

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.offset(dir);
                if (this.pipes.contains(neighbor) && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    distances.put(neighbor, currentDistance + 1);
                    queue.add(neighbor);
                }
            }
        }

        return distances;
    }

    @Override
    public HeatStorage getStorage(BlockPos pos) {
        return this.pipeStorages.computeIfAbsent(pos, p -> createStorage());
    }

    @Override
    public void addPipe(BlockPos pos) {
        super.addPipe(pos);
        if (!this.pipeStorages.containsKey(pos)) {
            this.pipeStorages.put(pos, createStorage());
        }
    }

    @Override
    public void movePipesFrom(PipeNetwork<HeatStorage> oldNetwork, Set<BlockPos> pipesToInherit) {
        if (oldNetwork instanceof HeatPipeNetwork heatOldNetwork) {
            Map<BlockPos, HeatStorage> storagesToInherit = new HashMap<>();
            for (BlockPos pipe : pipesToInherit) {
                HeatStorage storage = heatOldNetwork.pipeStorages.get(pipe);
                if (storage != null) {
                    storagesToInherit.put(pipe, storage);
                }
            }

            super.movePipesFrom(oldNetwork, pipesToInherit);
            this.pipeStorages.putAll(storagesToInherit);
        } else {
            super.movePipesFrom(oldNetwork, pipesToInherit);
        }
    }

    @Override
    public boolean hasCentralStorage() {
        return false;
    }

    @Override
    public void tick(World world) {
        super.tick(world);

        Map<HeatStorage, Double> heatChanges = new HashMap<>();

        // Step 1: Transfer heat between adjacent pipes
        for (BlockPos pipePos : this.pipes) {
            HeatStorage pipeStorage = getStorage(pipePos);
            for (Direction dir : Direction.values()) {
                BlockPos neighborPos = pipePos.offset(dir);
                if (this.pipes.contains(neighborPos)) {
                    HeatStorage neighborStorage = getStorage(neighborPos);
                    double transfer = calculateTransfer(pipeStorage, neighborStorage);
                    heatChanges.merge(pipeStorage, -transfer, Double::sum);
                    heatChanges.merge(neighborStorage, transfer, Double::sum);
                }
            }
        }

        // Step 2: Transfer heat between pipes and connected blocks (sources or sinks)
        for (BlockPos connectedPos : this.connectedBlocks) {
            for (Direction dir : Direction.values()) {
                BlockPos pipePos = connectedPos.offset(dir);
                if (this.pipes.contains(pipePos)) {
                    HeatStorage pipeStorage = getStorage(pipePos);
                    HeatStorage connectedStorage = this.transferType.lookup(world, connectedPos, dir.getOpposite());
                    if (connectedStorage != null && connectedStorage.supportsInsertion()) {
                        double transfer = calculateTransfer(pipeStorage, connectedStorage);
                        heatChanges.merge(pipeStorage, -transfer, Double::sum);
                        heatChanges.merge(connectedStorage, transfer, Double::sum);
                    }
                }
            }
        }

        // Step 3: Apply all heat changes
        for (Map.Entry<HeatStorage, Double> entry : heatChanges.entrySet()) {
            HeatStorage storage = entry.getKey();
            double change = entry.getValue();
            ((SimpleHeatStorage) storage).setAmount(Math.max(0, storage.getAmount() + change));
        }

        // Step 4: Apply dissipation to pipes
        for (HeatStorage pipeStorage : this.pipeStorages.values()) {
            double dissipation = pipeStorage.getAmount() / 100D; // Dissipate 1% of the heat every tick
            ((SimpleHeatStorage) pipeStorage).setAmount(Math.max(0, pipeStorage.getAmount() - dissipation));
        }
    }

    private static double calculateTransfer(HeatStorage a, HeatStorage b) {
        return (a.getAmount() - b.getAmount()) / 10D; // Transfer 10% of the heat difference
    }
}
