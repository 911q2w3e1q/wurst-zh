/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * SpearDash Hack ("长矛突进赶路") for Wurst Client.
 *
 * Default mode is server-authoritative: a spear must actually be held, and
 * the movement is produced only by the server-side spear Lunge triggered by
 * the real right-click input. No client-side velocity is injected, so the
 * server's cooldown, hunger cost and speed limit all apply and there is no
 * rubber-banding. An optional client-side speed override can be enabled for
 * uncapped "cheat" dashes; even then a spear must still be held.
 */
package net.wurstclient.hacks;

import net.minecraft.world.item.ItemStack;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IKeyMapping;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({ "spear dash", "spear lunge", "lunge spam", "spear travel",
	"长矛突进", "长矛赶路", "突进", "无冷却长矛", "lunge" })
public final class SpearDashHack extends Hack implements UpdateListener
{
	private enum TriggerMode
	{
		RIGHT_CLICK("Right click"), ALWAYS("Always");

		private final String label;

		TriggerMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final SliderSetting dashSpeed = new SliderSetting(
		"Dash speed", "description.wurst.setting.speardash.dash_speed",
		120, 10, 300, 5, ValueDisplay.INTEGER.withSuffix(" m/s"));

	private final EnumSetting<TriggerMode> triggerMode = new EnumSetting<>(
		"Trigger", "description.wurst.setting.speardash.trigger",
		TriggerMode.values(), TriggerMode.RIGHT_CLICK);

	private final CheckboxSetting autoEquip = new CheckboxSetting(
		"Auto equip spear",
		"description.wurst.setting.speardash.auto_equip", true);

	private final CheckboxSetting horizontalOnly = new CheckboxSetting(
		"Horizontal only",
		"description.wurst.setting.speardash.horizontal_only", true);

	private final CheckboxSetting sendLungePackets = new CheckboxSetting(
		"Send lunge packets",
		"description.wurst.setting.speardash.send_lunge_packets", true);

	// Off by default: movement comes from the server-side Lunge only. Turning
	// this on injects client-side velocity for uncapped "cheat" dashes, which
	// the server does not authorize and may rubber-band back.
	private final CheckboxSetting clientSpeedOverride = new CheckboxSetting(
		"Client speed override",
		"description.wurst.setting.speardash.client_speed_override", false);

	public SpearDashHack()
	{
		super("SpearDash");
		setCategory(Category.MOVEMENT);
		addSetting(triggerMode);
		addSetting(autoEquip);
		addSetting(sendLungePackets);
		addSetting(clientSpeedOverride);
		addSetting(dashSpeed);
		addSetting(horizontalOnly);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		// Restore the use key so normal right-clicking works again.
		IKeyMapping.get(MC.options.keyUse).resetPressedState();
	}

	@Override
	public String getRenderName()
	{
		return "长矛突进";
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.getConnection() == null)
			return;

		if(autoEquip.isChecked())
			equipSpear();

		// A spear must physically be in the main hand. The server only
		// performs a spear Lunge when it sees a held spear, so without one
		// there is no movement at all - no more flying empty-handed.
		if(!MC.player.getMainHandItem().is(ItemTags.SPEARS))
			return;

		boolean activated;
		if(triggerMode.getSelected() == TriggerMode.ALWAYS)
		{
			activated = true;
		}
		else
		{
			// Read the real physical right-click, then take over the packet
			// flow ourselves so lunge can be driven every tick.
			activated = IKeyMapping.get(MC.options.keyUse).isActuallyDown();
			MC.options.keyUse.setDown(false);
		}

		if(!activated)
			return;

		// Submit the use-item (Lunge) input to the server. In the default mode
		// this is the ONLY thing we do: the server runs its own authoritative
		// Lunge, so cooldown, hunger and speed all apply and the movement is
		// never rubber-banded by the server.
		if(sendLungePackets.isChecked())
			MC.getConnection().send(new ServerboundUseItemPacket(
				InteractionHand.MAIN_HAND, MC.player.getId(),
				MC.player.getXRot(), MC.player.getYRot()));

		// Optional uncapped cheat mode (off by default): inject client-side
		// velocity on top of the server Lunge. The server does not authorize
		// this, so it may rubber-band back. Still requires a held spear.
		if(clientSpeedOverride.isChecked())
		{
			Vec3 look = MC.player.getLookAngle();
			Vec3 dir = horizontalOnly.isChecked()
				? new Vec3(look.x, 0, look.z).normalize()
				: look.normalize();

			double blocksPerTick = dashSpeed.getValue() / 20.0;
			MC.player.setDeltaMovement(dir.scale(blocksPerTick));
			MC.player.fallDistance = 0;
		}
	}

	private void equipSpear()
	{
		ItemStack held = MC.player.getMainHandItem();
		if(held.is(ItemTags.SPEARS))
			return;

		net.minecraft.world.entity.player.Inventory inv =
			MC.player.getInventory();
		for(int i = 0; i < 36; i++)
		{
			if(inv.getItem(i).is(ItemTags.SPEARS))
			{
				int selected = inv.getSelectedSlot();
				IMC.getInteractionManager().windowClick_SWAP(i, selected);
				return;
			}
		}
	}
}
