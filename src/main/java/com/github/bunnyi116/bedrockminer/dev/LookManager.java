package com.github.bunnyi116.bedrockminer.dev;

import com.github.bunnyi116.bedrockminer.util.DirectionUtils;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;


public class LookManager {
    private final Context context;
    private float yaw;
    private float pitch;
    private boolean modifyYaw;
    private boolean modifyPitch;

    public LookManager(Context context) {
        this.context = context;
    }

    public float getYaw(float yaw) {
        return this.modifyYaw ? this.yaw : yaw;
    }

    public float getPitch(float pitch) {
        return this.modifyPitch ? this.pitch : pitch;
    }

    public boolean isLookDirection(Direction direction) {
        LocalPlayer player = context.getPlayerOrThrow();
        float yaw = getYaw(player.getYRot());
        float pitch = getPitch(player.getXRot());
        return DirectionUtils.orderedByNearest(yaw, pitch)[0] == direction;
    }
}
