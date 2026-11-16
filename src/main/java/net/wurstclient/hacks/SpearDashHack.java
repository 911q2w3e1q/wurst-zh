/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * SpearDash Hack ("长矛突进赶路") for Wurst Client.
 *
 * Reproduces the spear Lunge charge as a fast travel tool: while holding use
 * (right click) it re-sends the use-item packet every tick (no cooldown) and
 * overrides the client-side velocity to reach a configurable speed. Because
 * the rapid movement is driven client-side, it does not consume hunger the
 * way the server-side spear Lunge does, and speed is not capped at 129 m/s.
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

	public SpearDashHack()
	{
		super("SpearDash");
		setCategory(Category.MOVEMENT);
		addSetting(dashSpeed);
		addSetting(triggerMode);
		addSetting(autoEquip);
		addSetting(horizontalOnly);
		addSetting(sendLungePackets);
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

		boolean activated;
		if(triggerMode.getSelected() == TriggerMode.ALWAYS)
		{
			activated = true;
		}
		else
		{
			// Read the real physical right-click, then take over the packet
			// flow ourselves so there is no server-side lunge cooldown.
			activated = IKeyMapping.get(MC.options.keyUse).isActuallyDown();
			MC.options.keyUse.setDown(false);
		}

		if(!activated)
			return;

		// Direction from where the player is looking.
		Vec3 look = MC.player.getLookAngle();
		Vec3 dir = horizontalOnly.isChecked()
			? new Vec3(look.x, 0, look.z).normalize()
			: look.normalize();

		// m/s -> blocks per tick (20 tps, 1 block = 1 metre).
		double blocksPerTick = dashSpeed.getValue() / 20.0;
		MC.player.setDeltaMovement(dir.scale(blocksPerTick));
		MC.player.fallDistance = 0;

		// Re-send the use-item packet every tick to mimic the Lunge charge.
		if(sendLungePackets.isChecked())
			MC.getConnection().send(new ServerboundUseItemPacket(
				InteractionHand.MAIN_HAND, MC.player.getId(),
				MC.player.getXRot(), MC.player.getYRot()));
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
