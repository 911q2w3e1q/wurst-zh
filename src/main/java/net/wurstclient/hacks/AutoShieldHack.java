/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * AutoShield Hack ("自动举盾") for Wurst Client.
 *
 * Watches incoming damage packets and nearby fast-moving players (e.g. a
 * spear charge or a falling mace user). When threatened, it swaps a shield
 * into the off-hand and holds right-click to block, then puts it away once
 * the danger is over.
 */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({ "auto shield", "shield", "autoshield", "block",
	"自动举盾", "举盾", "格挡", "盾牌" })
public final class AutoShieldHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final SliderSetting threatRange = new SliderSetting(
		"Threat range", "Distance to watch for incoming attackers.",
		8, 2, 30, 1, ValueDisplay.INTEGER);

	private final SliderSetting threatSpeed = new SliderSetting(
		"Threat speed", "Minimum speed (blocks/sec) that counts as a charge.",
		10, 2, 40, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting reactToDamage = new CheckboxSetting(
		"React to damage", "Raise shield when a damage packet is received.",
		true);

	private int hurtTimer;
	private int shieldSlot = -1;
	private boolean blocking = false;

	public AutoShieldHack()
	{
		super("AutoShield");
		setCategory(Category.COMBAT);
		addSetting(threatRange);
		addSetting(threatSpeed);
		addSetting(reactToDamage);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		hurtTimer = 0;
		blocking = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		stopBlocking();
	}

	@Override
	public String getRenderName()
	{
		return "自动举盾";
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		Packet<?> packet = event.getPacket();
		if(!reactToDamage.isChecked())
			return;

		if(packet instanceof ClientboundDamageEventPacket
			|| packet instanceof ClientboundHurtAnimationPacket)
			hurtTimer = 30;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.getConnection() == null)
			return;

		if(hurtTimer > 0)
			hurtTimer--;

		boolean threatened = hurtTimer > 0 || isFastAttackerNear();

		if(threatened)
			startBlocking();
		else
			stopBlocking();
	}

	private boolean isFastAttackerNear()
	{
		if(MC.level == null)
			return false;

		for(Entity e : MC.level.entitiesForRendering())
		{
			if(e == MC.player || !(e instanceof LivingEntity))
				continue;
			if(MC.player.distanceTo(e) > threatRange.getValue())
				continue;

			net.minecraft.world.phys.Vec3 v = e.getDeltaMovement();
			double speed = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20;
			if(speed >= threatSpeed.getValue())
				return true;
		}
		return false;
	}

	private void startBlocking()
	{
		ItemStack offhand = MC.player.getOffhandItem();
		if(!offhand.is(Items.SHIELD))
		{
			int slot = findShield();
			if(slot == -1)
				return;

			// Swap the shield into the off-hand (container slot 40).
			IMC.getInteractionManager().windowClick_SWAP(slot, 40);
			shieldSlot = slot;
			return;
		}

		if(!blocking)
		{
			MC.getConnection().send(new ServerboundUseItemPacket(
				InteractionHand.OFF_HAND, MC.player.getId(),
				MC.player.getXRot(), MC.player.getYRot()));
			blocking = true;
		}
	}

	private void stopBlocking()
	{
		if(!blocking)
			return;
		MC.player.connection.send(new ServerboundPlayerActionPacket(
			ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
			net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
		blocking = false;
	}

	private int findShield()
	{
		net.minecraft.world.entity.player.Inventory inv =
			MC.player.getInventory();
		for(int i = 0; i < 36; i++)
			if(inv.getItem(i).is(Items.SHIELD))
				return i;
		return -1;
	}
}
