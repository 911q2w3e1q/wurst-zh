/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

/**
 * Lets you hover in mid-air while flying with elytra.
 *
 * <p>
 * Hold the forward key (W by default) to move. Release it and you stop
 * instantly and stay floating in place instead of gliding down.
 */
@SearchTags({"elytra hover", "hover", "翘翅悬停", "悬停"})
public final class ElytraHoverHack extends Hack implements UpdateListener
{
	private final CheckboxSetting keepHeight = new CheckboxSetting(
		"Keep height", "description.wurst.setting.elytrahover.keep_height",
		true);

	public ElytraHoverHack()
	{
		super("ElytraHover");
		setCategory(Category.MOVEMENT);
		addSetting(keepHeight);
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
	}

	@Override
	public void onUpdate()
	{
		LocalPlayer player = MC.player;

		// only while actually gliding with elytra equipped
		if(player == null || !player.isFallFlying())
			return;

		// no fall damage while hovering
		player.fallDistance = 0;

		// moving: let normal flight physics run, optionally cancel gravity
		if(MC.options.keyUp.isDown())
		{
			if(keepHeight.isChecked())
			{
				Vec3 v = player.getDeltaMovement();
				if(v.y < 0)
					player.setDeltaMovement(v.x, 0, v.z);
			}

			return;
		}

		// not moving: freeze completely and hover in place
		player.setDeltaMovement(Vec3.ZERO);
	}
}
