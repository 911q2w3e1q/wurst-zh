/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * SpeedDisplay Hack ("速度显示") for Wurst Client.
 *
 * Draws the player's current movement speed in the bottom-right corner of
 * the HUD, in red, showing both m/s and km/h. Minecraft moves in blocks per
 * tick (20 tps) and one block equals one metre, so speed * 20 = m/s.
 */
package net.wurstclient.hacks;

import java.text.DecimalFormat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.GUIRenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

@SearchTags({ "speed display", "speedometer", "speed hud", "velocity",
	"速度显示", "测速", "速度计", "m/s", "km/h" })
public final class SpeedDisplayHack extends Hack
	implements GUIRenderListener, UpdateListener
{
	private static final DecimalFormat DF = new DecimalFormat("0.00");
	private static final int RED = 0xFFFF0000;
	private static final int BLACK = 0xFF000000;

	// Smoothed speed in blocks/tick, to keep the numbers from flickering.
	private float smoothSpeed;

	public SpeedDisplayHack()
	{
		super("SpeedDisplay");
		setCategory(Category.RENDER);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(GUIRenderListener.class, this);
		EVENTS.add(UpdateListener.class, this);
		smoothSpeed = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(GUIRenderListener.class, this);
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public String getRenderName()
	{
		return "速度显示";
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		Vec3 v = MC.player.getDeltaMovement();
		float speed = (float)Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);

		// Exponential smoothing: respond quickly, but don't jitter.
		smoothSpeed += (speed - smoothSpeed) * 0.25F;
		if(smoothSpeed < 0.001F)
			smoothSpeed = 0;
	}

	@Override
	public void onRenderGUI(GuiGraphics context, float partialTicks)
	{
		if(MC.player == null)
			return;

		float mps = smoothSpeed * 20F; // blocks/tick -> m/s
		float kmh = mps * 3.6F; // m/s -> km/h

		String line1 = DF.format(mps) + " m/s";
		String line2 = DF.format(kmh) + " km/h";

		Font font = MC.font;
		int width = Math.max(font.width(line1), font.width(line2));
		int x = context.guiWidth() - width - 4;
		int y = context.guiHeight() - 26;

		// Black outline for readability, then red text on top.
		context.drawString(font, line1, x + 1, y + 1, BLACK, false);
		context.drawString(font, line1, x, y, RED, false);
		context.drawString(font, line2, x + 1, y + 10, BLACK, false);
		context.drawString(font, line2, x, y + 9, RED, false);
	}
}
