package com.github.bunnyi116.bedrockminer.dev;

import com.github.bunnyi116.bedrockminer.config.Config;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.HashSet;

public class TaskManager {
    private final Context context;
    private final Config config;

    private boolean enabled;
    private final HashSet<Task> tasks = new HashSet<>();

    public TaskManager(Context context) {
        this.context = context;
        this.config = context.getConfig();
    }

    public void onTick() {
        if (!this.enabled || this.config.disable) {
            return;
        }
    }

    public void addTask(ClientLevel world, Block block, BlockPos pos) {
        if (!this.enabled || this.config.disable) {
            return;
        }
        this.tasks.add(new Task(world, block, pos));
    }

    public boolean isAllowExecutionEnvironment() {
        return this.enabled;
    }
}
