/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockPlacer;

/**
 * Allows attacking, placing and mining through walls.
 *
 * <p>
 * <b>Attack:</b> forces Killaura's line-of-sight check off.
 * <b>Place:</b> makes BlockPlacer-based hacks ignore line of sight.
 * <b>Mine:</b> makes BlockBreaker-based hacks ignore line of sight.
 */
@SearchTags({"wall hack", "through walls", "through-wall", "penetrate", "穿透",
	"隔墙"})
public final class WallHackHack extends Hack implements UpdateListener
{
	private final CheckboxSetting attack = new CheckboxSetting("Attack",
		"description.wurst.setting.wallhack.attack", true);
	
	private final CheckboxSetting place = new CheckboxSetting("Place",
		"description.wurst.setting.wallhack.place", true);
	
	private final CheckboxSetting mine = new CheckboxSetting("Mine",
		"description.wurst.setting.wallhack.mine", true);
	
	private boolean prevKillauraLOS;
	
	public WallHackHack()
	{
		super("WallHack");
		setCategory(Category.OTHER);
		addSetting(attack);
		addSetting(place);
		addSetting(mine);
	}
	
	@Override
	protected void onEnable()
	{
		prevKillauraLOS = WURST.getHax().killauraHack.getCheckLOS();
		
		EVENTS.add(UpdateListener.class, this);
		applySettings();
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		
		// restore Killaura's line-of-sight setting
		WURST.getHax().killauraHack.setCheckLOS(prevKillauraLOS);
		
		// restore block placer / breaker behavior
		BlockPlacer.ignoreLineOfSight = false;
		BlockBreaker.ignoreLineOfSight = false;
	}
	
	@Override
	public void onUpdate()
	{
		applySettings();
	}
	
	private void applySettings()
	{
		// attack through walls
		if(attack.isChecked())
			WURST.getHax().killauraHack.setCheckLOS(false);
		else
			WURST.getHax().killauraHack.setCheckLOS(prevKillauraLOS);
		
		// place through walls
		BlockPlacer.ignoreLineOfSight = place.isChecked();
		
		// mine through walls
		BlockBreaker.ignoreLineOfSight = mine.isChecked();
	}
}
