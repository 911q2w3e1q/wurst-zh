/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * PushAttackers Hack ("击退来袭者") for Wurst Client.
 *
 * Automatically knocks nearby enemies away so they can't stay in melee
 * range to combo you. Complements the existing AntiKnockback hack: that one
 * stops you from being knocked around, this one pushes the attacker back.
 */
package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({ "push attackers", "knockback aura", "push aura",
	"击退来袭者", "击退", "弹开", "防近身" })
public final class PushAttackersHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How close an enemy must be before being pushed.", 3, 1, 8, 0.5,
		ValueDisplay.DECIMAL);

	private final SliderSetting strength = new SliderSetting("Strength",
		"Horizontal push impulse.", 0.8, 0.1, 3, 0.1, ValueDisplay.DECIMAL);

	private final SliderSetting vertical = new SliderSetting("Vertical",
		"Upward push impulse.", 0.2, 0, 1, 0.05, ValueDisplay.DECIMAL);

	private int cooldown;

	public PushAttackersHack()
	{
		super("PushAttackers");
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(strength);
		addSetting(vertical);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		cooldown = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public String getRenderName()
	{
		return "击退来袭者";
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(cooldown > 0)
		{
			cooldown--;
			return;
		}

		boolean pushed = false;
		for(Entity e : MC.level.entitiesForRendering())
		{
			if(e == MC.player || !(e instanceof LivingEntity))
				continue;
			if(MC.player.distanceTo(e) > range.getValue())
				continue;

			Vec3 dir = new Vec3(e.getX() - MC.player.getX(), 0,
				e.getZ() - MC.player.getZ());
			if(dir.lengthSqr() < 1.0E-4)
				continue;

			dir = dir.normalize().scale(strength.getValue());
			Vec3 ev = e.getDeltaMovement();
			e.setDeltaMovement(ev.x + dir.x, ev.y + vertical.getValue(),
				ev.z + dir.z);
			pushed = true;
		}

		if(pushed)
			cooldown = 4;
	}
}
