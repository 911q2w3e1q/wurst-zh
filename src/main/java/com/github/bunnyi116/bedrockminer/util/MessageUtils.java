package com.github.bunnyi116.bedrockminer.util;

import net.minecraft.network.chat.Component;

import static com.github.bunnyi116.bedrockminer.BedrockMiner.minecraft;

public class MessageUtils {
    private static final Component PREFIX = Component.literal("§8[§bBedrockMiner§8] §r");

    public static void setOverlayMessage(Component message) {
        // Adapted for MC 1.21.11 official mappings
        minecraft.gui.setOverlayMessage(message, false);
    }

    public static void addMessage(Component message) {
        Component finalMessage = Component.empty().append(PREFIX).append(message);
        // Adapted for MC 1.21.11 official mappings
        minecraft.gui.getChat().addMessage(finalMessage);
    }
}

