/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.Comparator;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.InventoryUtils;

/**
 * Automatically traps the nearest enemy in a shell of obsidian blocks,
 * sealing them in place so they can't escape.
 */
@SearchTags({"auto trap", "auto city", "trap enemy", "封人"})
public final class AutoTrapHack extends Hack implements UpdateListener
{
	private final BlockSetting block =
		new BlockSetting("Block", "description.wurst.setting.autotrap.block",
			"minecraft:obsidian", false);
	
	private final SliderSetting range =
		new SliderSetting("Range", "description.wurst.setting.autotrap.range",
			4, 1, 6, 1, ValueDisplay.INTEGER);
	
	private final int[][] ring =
		{{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
	
	public AutoTrapHack()
	{
		super("AutoTrap");
		setCategory(Category.COMBAT);
		addSetting(block);
		addSetting(range);
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
		Entity target = getNearestTarget();
		if(target == null)
			return;
		
		// find and select the block in the inventory (once per tick)
		if(!InventoryUtils.selectItem(block.getBlock().asItem(), 36))
			return;
		
		// wait until the item has actually arrived in the selected slot
		if(!MC.player.getMainHandItem().is(block.getBlock().asItem()))
			return;
		
		boolean shouldSwing = false;
		BlockPos center = target.blockPosition();
		
		// ring around the target's feet
		for(int[] offset : ring)
		{
			BlockPos pos = center.offset(offset[0], 0, offset[1]);
			if(placeBlock(pos, target))
				shouldSwing = true;
		}
		
		// block above the target, so they can't jump out
		BlockPos above = center.above(2);
		if(placeBlock(above, target))
			shouldSwing = true;
		
		if(shouldSwing)
			MC.player.swing(InteractionHand.MAIN_HAND);
	}
	
	private Entity getNearestTarget()
	{
		double rangeSq = Math.pow(range.getValueI(), 2);
		
		return EntityUtils.getAttackableEntities()
			.filter(e -> EntityUtils.distanceToHitboxSq(e) <= rangeSq)
			.min(Comparator.comparingDouble(EntityUtils::distanceToHitboxSq))
			.orElse(null);
	}
	
	private boolean placeBlock(BlockPos pos, Entity target)
	{
		// skip if the spot is already occupied
		if(!BlockUtils.getState(pos).canBeReplaced())
			return false;
		
		// never place into the target's own space
		if(target.getBoundingBox().intersects(new AABB(pos)))
			return false;
		
		// never trap ourselves
		if(MC.player.getBoundingBox().intersects(new AABB(pos)))
			return false;
		
		// place it
		if(!BlockPlacer.placeOneBlock(pos))
			return false;
		
		// reset the right click delay
		MC.rightClickDelay = 4;
		return true;
	}
}
