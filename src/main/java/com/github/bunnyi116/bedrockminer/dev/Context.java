package com.github.bunnyi116.bedrockminer.dev;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.config.Config;
import com.github.bunnyi116.bedrockminer.config.ConfigManager;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

@Getter
@Setter
public class Context {
    private final ConfigManager configManager = ConfigManager.getInstance();
    private final InventoryManager inventoryManager = new InventoryManager(this);
    private final BreakManager breakManager = new BreakManager(this);
    private final PlacementManager placementManager = new PlacementManager(this);
    private final LookManager lookManager = new LookManager(this);
    private final TaskManager taskManager = new TaskManager(this);
    private int ticks;

    private Context() {
    }

    public Config getConfig() {
        return this.configManager.getConfig();
    }

    public LocalPlayer getPlayerOrThrow() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) throw new IllegalStateException("LocalPlayer is null, not in game");
        return p;
    }

    public ClientLevel getLevelOrThrow() {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null) throw new IllegalStateException("ClientLevel is null");
        return l;
    }

    public MultiPlayerGameMode getGameModeOrThrow() {
        MultiPlayerGameMode gm = Minecraft.getInstance().gameMode;
        if (gm == null) throw new IllegalStateException("MultiPlayerGameMode is null");
        return gm;
    }

    public ClientPacketListener getConnectionOrThrow() {
        ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (c == null) throw new IllegalStateException("ClientPacketListener is null");
        return c;
    }

    public void info(String format, Object... arguments) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StackTraceElement caller = stack.length >= 4 ? stack[3] : null;
        String source;
        if (caller != null) {
            String fullCls = caller.getClassName();
            String simpleCls = fullCls.substring(fullCls.lastIndexOf('.') + 1);
            source = simpleCls + "#" + caller.getMethodName();
        } else {
            source = "unknown";
        }
        format = "[" + ticks + "] [" + source + "] " + format;
        BedrockMiner.LOGGER.info(format, arguments);
    }

    public void debug(String format, Object... arguments) {
        if (getConfig().debug) {
            this.info(format, arguments);
        }
    }

    private static class Holder {
        private static final Context INSTANCE = new Context();
    }

    public static Context getInstance() {
        return Holder.INSTANCE;
    }
}
