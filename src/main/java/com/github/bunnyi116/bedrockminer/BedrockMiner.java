package com.github.bunnyi116.bedrockminer;

import com.github.bunnyi116.bedrockminer.dev.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core state holder for the piston-break feature, integrated into Wurst Client.
 * Original Bedrock Miner by bunnyi116 (AGPL-3.0), adapted for Wurst (GPL-3.0).
 *
 * This is no longer a standalone Fabric mod entrypoint. Initialization is
 * driven by the Wurst {@code PistonBreakHack} instead.
 */
public final class BedrockMiner {
    public static final String MOD_NAME = "PistonBreak";
    public static final String MOD_ID = "bedrockminer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
    public static final Context CONTEXT = Context.getInstance();
    public static final boolean TEST = false;

    // 常用游戏变量(通过 mixin 从 LocalPlayer.tick 更新)
    public static Minecraft minecraft;
    public static ClientLevel level;
    public static LocalPlayer player;
    public static Inventory playerInventory;
    public static @Nullable HitResult hitResult;
    public static ClientPacketListener connection;
    public static MultiPlayerGameMode gameMode;
    public static GameType gameType;

    private BedrockMiner()
    {
    }

    public static void init()
    {
        initGameVariable();
        Debug.alwaysWrite("PistonBreak initialized");
    }

    public static void initGameVariable() {
        Minecraft mc = Minecraft.getInstance();
        BedrockMiner.minecraft = mc;
        BedrockMiner.level = mc.level;
        BedrockMiner.player = mc.player;
        if (mc.player != null) {
            BedrockMiner.playerInventory = mc.player.getInventory();
        }
        BedrockMiner.hitResult = mc.hitResult;
        BedrockMiner.connection = mc.getConnection();
        BedrockMiner.gameMode = mc.gameMode;
        if (mc.gameMode != null) {
            BedrockMiner.gameType = mc.gameMode.getPlayerMode();
        }
    }

    public static boolean gameVariableIsValid() {
        return minecraft != null
                && level != null
                && player != null
                && connection != null
                && gameMode != null;
    }
}
