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
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.InventoryUtils;

/**
 * Upgraded "autism hut": wraps the player in a 1-block thick shell of
 * obsidian and automatically refills any block that gets broken, so the
 * player can never be dug out.
 */
@SearchTags({"surround", "auto surround", "obsidian cage", "self trap", "自闭小屋"})
public final class SurroundHack extends Hack implements UpdateListener
{
	private final BlockSetting block =
		new BlockSetting("Block", "description.wurst.setting.surround.block",
			"minecraft:obsidian", false);
	
	private final CheckboxSetting headLevel =
		new CheckboxSetting("Build at head level",
			"description.wurst.setting.surround.head_level", true);
	
	private final CheckboxSetting roof = new CheckboxSetting("Build roof",
		"description.wurst.setting.surround.roof", false);
	
	private final int[][] ring =
		{{-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}};
	
	public SurroundHack()
	{
		super("Surround");
		setCategory(Category.BLOCKS);
		addSetting(block);
		addSetting(headLevel);
		addSetting(roof);
	}
	
	@Override
	protected void onEnable()
	{
		WURST.getHax().autoBuildHack.setEnabled(false);
		
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
		
		for(BlockPos pos : getPositions())
			if(placeBlock(pos))
				shouldSwing = true;
			
		if(shouldSwing)
			MC.player.swing(InteractionHand.MAIN_HAND);
	}
	
	private ArrayList<BlockPos> getPositions()
	{
		ArrayList<BlockPos> positions = new ArrayList<>();
		BlockPos center = MC.player.blockPosition();
		
		// ring at feet level
		for(int[] offset : ring)
			positions.add(center.offset(offset[0], 0, offset[1]));
		
		// ring at head level
		if(headLevel.isChecked())
			for(int[] offset : ring)
				positions.add(center.offset(offset[0], 1, offset[1]));
			
		// roof
		if(roof.isChecked())
			positions.add(center.above(2));
		
		return positions;
	}
	
	private boolean placeBlock(BlockPos pos)
	{
		// skip if the spot is already occupied (obsidian, bedrock, ...)
		if(!BlockUtils.getState(pos).canBeReplaced())
			return false;
		
		// never place into our own space
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
