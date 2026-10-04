/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
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
import net.wurstclient.util.InventoryUtils;

/**
 * Fills holes next to the player, so enemies can't hide in them or use them
 * to rush the player. Great for crystal PvP defense.
 */
@SearchTags({"hole fill", "fill holes", "fill pits", "填洞"})
public final class HoleFillHack extends Hack implements UpdateListener
{
	private final BlockSetting block =
		new BlockSetting("Block", "description.wurst.setting.holefill.block",
			"minecraft:obsidian", false);
	
	private final SliderSetting radius =
		new SliderSetting("Radius", "description.wurst.setting.holefill.radius",
			1, 1, 4, 1, ValueDisplay.INTEGER);
	
	public HoleFillHack()
	{
		super("HoleFill");
		setCategory(Category.BLOCKS);
		addSetting(block);
		addSetting(radius);
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
		// find and select the block in the inventory (once per tick)
		if(!InventoryUtils.selectItem(block.getBlock().asItem(), 36))
			return;
		
		// wait until the item has actually arrived in the selected slot
		if(!MC.player.getMainHandItem().is(block.getBlock().asItem()))
			return;
		
		boolean shouldSwing = false;
		
		for(BlockPos pos : getHolePositions())
			if(placeBlock(pos))
				shouldSwing = true;
			
		if(shouldSwing)
			MC.player.swing(InteractionHand.MAIN_HAND);
	}
	
	private ArrayList<BlockPos> getHolePositions()
	{
		ArrayList<BlockPos> positions = new ArrayList<>();
		BlockPos center = MC.player.blockPosition();
		int r = radius.getValueI();
		
		for(int dx = -r; dx <= r; dx++)
			for(int dz = -r; dz <= r; dz++)
			{
				if(dx == 0 && dz == 0)
					continue;
				
				BlockPos pos = center.offset(dx, 0, dz);
				
				// the spot itself must be empty (replaceable)
				if(!BlockUtils.getState(pos).canBeReplaced())
					continue;
					
				// only fill actual holes: the block below must also be
				// replaceable (air or water)
				if(!BlockUtils.getState(pos.below()).canBeReplaced())
					continue;
				
				// never place into our own space
				if(MC.player.getBoundingBox().intersects(new AABB(pos)))
					continue;
				
				positions.add(pos);
			}
		
		return positions;
	}
	
	private boolean placeBlock(BlockPos pos)
	{
		// place it
		if(!BlockPlacer.placeOneBlock(pos))
			return false;
		
		// reset the right click delay
		MC.rightClickDelay = 4;
		return true;
	}
}
