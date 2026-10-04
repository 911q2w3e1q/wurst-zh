/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.AirStrafingSpeedListener;
import net.wurstclient.events.AirStrafingSpeedListener.AirStrafingSpeedEvent;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyMapping;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * "Fake flight": lets you fly freely on the client, while intercepting
 * outgoing movement packets and forcing onGround=true, so the server
 * believes you are simply walking on the ground instead of flying.
 */
@SearchTags({"fake flight", "packet flight", "fake fly", "假飞"})
public final class FakeFlightHack extends Hack
	implements UpdateListener, AirStrafingSpeedListener, PacketOutputListener
{
	private final SliderSetting horizontalSpeed =
		new SliderSetting("Horizontal speed",
			"description.wurst.setting.fakeflight.horizontal_speed", 1, 0.05,
			10, 0.05, ValueDisplay.DECIMAL);
	
	private final SliderSetting verticalSpeed = new SliderSetting(
		"Vertical speed", "description.wurst.setting.fakeflight.vertical_speed",
		1, 0.05, 5, 0.05, ValueDisplay.DECIMAL);
	
	public FakeFlightHack()
	{
		super("FakeFlight");
		setCategory(Category.MOVEMENT);
		addSetting(horizontalSpeed);
		addSetting(verticalSpeed);
	}
	
	@Override
	protected void onEnable()
	{
		// disable other flight hacks
		WURST.getHax().flightHack.setEnabled(false);
		WURST.getHax().creativeFlightHack.setEnabled(false);
		WURST.getHax().jetpackHack.setEnabled(false);
		WURST.getHax().glideHack.setEnabled(false);
		
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(AirStrafingSpeedListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(AirStrafingSpeedListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		
		MC.player.getAbilities().flying = false;
	}
	
	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;
		
		// hover in place: freeze gravity and cancel all movement first
		player.setDeltaMovement(Vec3.ZERO);
		player.getAbilities().flying = false;
		
		double vSpeed = getActualVerticalSpeed();
		
		// jump to fly up
		if(MC.options.keyJump.isDown())
			player.addDeltaMovement(new Vec3(0, vSpeed, 0));
		
		// sneak to fly down
		if(IKeyMapping.get(MC.options.keyShift).isActuallyDown())
		{
			MC.options.keyShift.setDown(false);
			player.addDeltaMovement(new Vec3(0, -vSpeed, 0));
		}
		
		// no fall damage
		player.fallDistance = 0;
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		// only rewrite packets that contain a position
		if(!(event.getPacket() instanceof ServerboundMovePlayerPacket.Pos
			|| event.getPacket() instanceof ServerboundMovePlayerPacket.PosRot))
			return;
		
		ServerboundMovePlayerPacket packet =
			(ServerboundMovePlayerPacket)event.getPacket();
		
		// get position & rotation from the original packet
		double x = packet.getX(0);
		double y = packet.getY(0);
		double z = packet.getZ(0);
		
		// rebuild the packet with onGround = true, so the server
		// believes we are walking on the ground
		Packet<?> newPacket;
		if(packet instanceof ServerboundMovePlayerPacket.Pos)
			newPacket = new ServerboundMovePlayerPacket.Pos(x, y, z, true,
				MC.player.horizontalCollision);
		else
			newPacket = new ServerboundMovePlayerPacket.PosRot(x, y, z,
				packet.getYRot(0), packet.getXRot(0), true,
				MC.player.horizontalCollision);
		
		// cancel the original packet and send the modified one instead
		event.cancel();
		MC.player.connection.getConnection().send(newPacket);
	}
	
	@Override
	public void onGetAirStrafingSpeed(AirStrafingSpeedEvent event)
	{
		event.setSpeed(horizontalSpeed.getValueF());
	}
	
	private double getActualVerticalSpeed()
	{
		return Mth.clamp(horizontalSpeed.getValue() * verticalSpeed.getValue(),
			0.05, 5);
	}
}
