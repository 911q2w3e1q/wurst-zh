package com.github.bunnyi116.bedrockminer.dev;

import lombok.Getter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

@Getter
public class Task {
    private final ClientLevel world;
    private final Block block;
    private final BlockPos pos;

    public Task(ClientLevel world, Block block, BlockPos pos) {
        this.world = world;
        this.block = block;
        this.pos = pos;
    }

    public void preprocessTick() {
    }

    public void onTick() {
    }
}
