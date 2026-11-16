/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * ChickenDupe Hack ("鸡刷复制") for Wurst Client.
 *
 * Keeps sending /dupe (which costs XP on some servers) to duplicate the
 * item in your main hand. When XP runs out, it refills XP by using the
 * classic "XP bottle chicken" cycle: it switches to a stack of XP bottles,
 * throws bottles down to 32 (they shatter into XP orbs), then /dupe-s the
 * remaining 32 bottles back up to 64, and finally switches back to the
 * original item. Only XP bottles are ever thrown - the item being duped is
 * never dropped.
 */
package net.wurstclient.hacks;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

@SearchTags({ "chicken dupe", "xp dupe", "exp dupe", "dupe", "鸡刷复制",
	"鸡刷", "经验复制", "刷物品", "复制物品", "经验瓶" })
public final class ChickenDupeHack extends Hack implements UpdateListener
{
	/** Ticks between each /dupe in the main loop. 2 ticks = 100ms. */
	private static final int DUPE_INTERVAL = 2;

	/** How many XP bottles to keep in hand after throwing. */
	private static final int KEEP_BOTTLES = 32;

	/** A full stack of XP bottles after duping. */
	private static final int FULL_STACK = 64;

	/** Ticks to wait for thrown bottles to shatter and XP to be absorbed. */
	private static final int XP_WAIT_TICKS = 60;

	/** Ticks between each individual bottle throw. */
	private static final int THROW_INTERVAL = 3;

	private enum State
	{
		DUPING, SWAP_TO_BOTTLE, THROWING, WAIT_XP, DUPING_BOTTLE
	}

	private State state = State.DUPING;
	private int tickCounter = 0;
	private int originalSlot = -1;
	private int bottleSlot = -1;
	private boolean usedSwap = false;

	public ChickenDupeHack()
	{
		super("ChickenDupe");
		setCategory(Category.ITEMS);
	}

	@Override
	protected void onEnable()
	{
		reset();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		restoreHand();
		reset();
	}

	@Override
	public String getRenderName()
	{
		return "鸡刷复制";
	}

	private void reset()
	{
		state = State.DUPING;
		tickCounter = 0;
		originalSlot = -1;
		bottleSlot = -1;
		usedSwap = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.getConnection() == null)
			return;

		switch(state)
		{
			case DUPING:
				onDuping();
				break;

			case SWAP_TO_BOTTLE:
				// Give the server a moment to process the slot swap.
				if(++tickCounter >= 3)
				{
					state = State.THROWING;
					tickCounter = 0;
				}
				break;

			case THROWING:
				onThrowing();
				break;

			case WAIT_XP:
				onWaitXp();
				break;

			case DUPING_BOTTLE:
				onDupingBottle();
				break;
		}
	}

	/** Main loop: spam /dupe to duplicate the held item, watch XP level. */
	private void onDuping()
	{
		if(++tickCounter >= DUPE_INTERVAL)
		{
			sendDupe();
			tickCounter = 0;
		}

		if(MC.player.experienceLevel < 1)
		{
			int slot = findExpBottleSlot();
			if(slot != -1)
			{
				bottleSlot = slot;
				originalSlot = MC.player.getInventory().getSelectedSlot();

				if(slot < 9)
				{
					// Bottle is in the hotbar - just select it.
					MC.player.getInventory().setSelectedSlot(slot);
					usedSwap = false;
				}
				else
				{
					// Bottle is in the main inventory - swap it to the hand.
					IMC.getInteractionManager().windowClick_SWAP(slot,
						originalSlot);
					usedSwap = true;
				}

				state = State.SWAP_TO_BOTTLE;
				tickCounter = 0;
			}
		}
	}

	/** Throw XP bottles one by one until only 32 are left in hand. */
	private void onThrowing()
	{
		ItemStack held = MC.player.getInventory().getSelectedItem();
		if(held.isEmpty() || !held.is(Items.EXPERIENCE_BOTTLE))
		{
			// Something went wrong (e.g. ran out of bottles). Bail out.
			restoreHand();
			reset();
			return;
		}

		if(held.getCount() <= KEEP_BOTTLES)
		{
			state = State.WAIT_XP;
			tickCounter = 0;
			return;
		}

		if(++tickCounter >= THROW_INTERVAL)
		{
			// ClickType.THROW with button 0 drops a single item.
			MC.gameMode.handleInventoryMouseClick(0,
				MC.player.getInventory().getSelectedSlot(), 0,
				ClickType.THROW, MC.player);
			tickCounter = 0;
		}
	}

	/** Wait for the thrown bottles to shatter and the XP orbs to be picked up. */
	private void onWaitXp()
	{
		tickCounter++;

		if(MC.player.experienceLevel >= 1 && tickCounter >= XP_WAIT_TICKS)
		{
			state = State.DUPING_BOTTLE;
			tickCounter = 0;
		}
		else if(tickCounter >= XP_WAIT_TICKS * 4)
		{
			// No XP arrived (bottles didn't shatter / player moved away).
			// Give up and restore the original item.
			restoreHand();
			reset();
		}
	}

	/** /dupe the 32 remaining bottles back up to a full stack of 64. */
	private void onDupingBottle()
	{
		tickCounter++;

		if(tickCounter % DUPE_INTERVAL == 0)
			sendDupe();

		ItemStack held = MC.player.getInventory().getSelectedItem();
		if(held.getCount() >= FULL_STACK || tickCounter >= 40)
		{
			restoreHand();
			reset();
		}
	}

	/** Switch the original item back into the main hand. */
	private void restoreHand()
	{
		if(originalSlot == -1 || MC.player == null)
			return;

		if(usedSwap)
			IMC.getInteractionManager().windowClick_SWAP(bottleSlot,
				originalSlot);
		else
			MC.player.getInventory().setSelectedSlot(originalSlot);
	}

	private void sendDupe()
	{
		MC.getConnection().sendCommand("dupe");
	}

	/** Finds the XP-bottle stack with the most bottles (more than 32). */
	private int findExpBottleSlot()
	{
		Inventory inv = MC.player.getInventory();
		int bestSlot = -1;
		int bestCount = KEEP_BOTTLES;

		for(int i = 0; i < 36; i++)
		{
			ItemStack stack = inv.getItem(i);
			if(!stack.isEmpty() && stack.is(Items.EXPERIENCE_BOTTLE)
				&& stack.getCount() > bestCount)
			{
				bestCount = stack.getCount();
				bestSlot = i;
			}
		}

		return bestSlot;
	}
}
