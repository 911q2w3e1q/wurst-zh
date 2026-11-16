/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * PistonBreak Hack for Wurst Client
 *
 * Integrates the "piston breaking" exploit (originally from bunnyi116's
 * fabric-bedrock-miner, AGPL-3.0) into Wurst as a single toggleable feature.
 * The whitelist of the original Bedrock Miner is removed, so this version
 * can break ANY block (including bedrock).
 */
package net.wurstclient.hacks;

import com.github.bunnyi116.bedrockminer.BedrockMiner;
import com.github.bunnyi116.bedrockminer.task.TaskManager;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

@SearchTags({ "piston break", "bedrock breaker", "bedrockminer",
	"piston exploit", "break bedrock", "break anything", "destroy bedrock",
	"活塞破坏", "破基岩", "破坏基岩" })
public final class PistonBreakHack extends Hack
{
	public PistonBreakHack()
	{
		super("PistonBreak");
		setCategory(Category.BLOCKS);
	}

	@Override
	protected void onEnable()
	{
		// Initialize the integrated bedrock-miner state holders.
		BedrockMiner.init();

		// Turn on the piston-break task manager. The mixins will then feed
		// attacked blocks into the queue and process them every tick.
		TaskManager.getInstance().setRunning(true);
	}

	@Override
	protected void onDisable()
	{
		// Stop processing and discard any queued/active tasks.
		TaskManager manager = TaskManager.getInstance();
		manager.setRunning(false);
		manager.removeAll();
	}

	@Override
	public String getRenderName()
	{
		return "PistonBreak";
	}
}
