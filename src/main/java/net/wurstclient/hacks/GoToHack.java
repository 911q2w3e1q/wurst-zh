/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * GoTo Hack ("自动前往") for Wurst Client.
 *
 * Navigates to a configurable set of coordinates. Unlike the .goto command
 * (walking only), this supports walking, elytra, flight and a hybrid mode,
 * works across dimensions, can recover from being pushed/pulled down, and
 * can perform an action on arrival (quit, build an obsidian hut, defend).
 */
package net.wurstclient.hacks;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.ai.PathFinder;
import net.wurstclient.ai.PathProcessor;
import net.wurstclient.events.RenderListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;

import com.mojang.blaze3d.vertex.PoseStack;

@SearchTags({ "goto", "go to", "travel", "navigate", "coordinates",
	"自动前往", "前往坐标", "导航", "寻路", "going" })
public final class GoToHack extends Hack
	implements UpdateListener, RenderListener
{
	private enum TravelMode
	{
		WALK("步行"), ELYTRA("翘翅"), FLIGHT("飞行"), SPEAR("长矛"),
		HYBRID("混合");

		private final String label;

		TravelMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private enum DimensionMode
	{
		CURRENT("当前"), OVERWORLD("主世界"), NETHER("下界"),
		END("末地");

		private final String label;

		DimensionMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private enum ArrivalAction
	{
		NONE("无"), QUIT("退出游戏"), HUT("搭建黑曜石小屋"),
		DEFEND("自动防御");

		private final String label;

		ArrivalAction(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final TextFieldSetting coordX =
		new TextFieldSetting("Target X",
			"description.wurst.setting.goto.coord_x", "0");
	private final TextFieldSetting coordY =
		new TextFieldSetting("Target Y",
			"description.wurst.setting.goto.coord_y", "64");
	private final TextFieldSetting coordZ =
		new TextFieldSetting("Target Z",
			"description.wurst.setting.goto.coord_z", "0");

	private final EnumSetting<DimensionMode> dimension = new EnumSetting<>(
		"Dimension", "description.wurst.setting.goto.dimension",
		DimensionMode.values(), DimensionMode.CURRENT);

	private final EnumSetting<TravelMode> travelMode = new EnumSetting<>(
		"Travel mode", "description.wurst.setting.goto.travel_mode",
		TravelMode.values(), TravelMode.FLIGHT);

	private final SliderSetting speed =
		new SliderSetting("Speed", "description.wurst.setting.goto.speed", 100,
			10, 300, 5, ValueDisplay.INTEGER.withSuffix(" m/s"));

	private final SliderSetting arrivalRadius = new SliderSetting(
		"Arrival radius", "description.wurst.setting.goto.arrival_radius", 3,
		1, 20, 1, ValueDisplay.INTEGER.withSuffix(" m"));

	// Elytra: link SimpleElytra (ExtraElytra) for stable flight. When linked,
	// speed is applied directly so "input speed = actual speed". Firework
	// boost is off by default because it stacks extra velocity on top.
	private final CheckboxSetting linkElytra = new CheckboxSetting(
		"Link SimpleElytra",
		"description.wurst.setting.goto.link_elytra", true);

	private final CheckboxSetting fireworkBoost = new CheckboxSetting(
		"Firework boost", "description.wurst.setting.goto.firework_boost",
		false);

	// Spear travel: equip a spear and spam the lunge packet each tick.
	private final CheckboxSetting autoEquipSpear = new CheckboxSetting(
		"Auto equip spear",
		"description.wurst.setting.goto.auto_equip_spear", true);

	private final CheckboxSetting spearLungePackets = new CheckboxSetting(
		"Spear lunge packets",
		"description.wurst.setting.goto.spear_lunge_packets", true);

	// Off by default: spear travel relies on the server-side Lunge only.
	// Enabling this injects client-side velocity for uncapped speed, which
	// the server does not authorize and may rubber-band back.
	private final CheckboxSetting spearSpeedOverride = new CheckboxSetting(
		"Spear speed override",
		"description.wurst.setting.goto.spear_speed_override", false);

	private final CheckboxSetting recoverFromPush = new CheckboxSetting(
		"Restart if pushed/pulled",
		"description.wurst.setting.goto.recover_from_push", true);

	private final SliderSetting recoverWait = new SliderSetting(
		"Restart delay", "description.wurst.setting.goto.recover_wait", 3, 1,
		30, 1, ValueDisplay.INTEGER.withSuffix(" s").withLabel(1, "1 s"));

	private final EnumSetting<ArrivalAction> arrivalAction =
		new EnumSetting<>("On arrival",
			"description.wurst.setting.goto.arrival_action",
			ArrivalAction.values(), ArrivalAction.NONE);

	private PathFinder pathFinder;
	private PathProcessor processor;
	private BlockPos goal;
	private int recoverTimer;
	private Vec3 lastPos;
	private int stuckTicks;
	private boolean elytraEnabledByUs;

	public GoToHack()
	{
		super("GoTo");
		setCategory(Category.MOVEMENT);
		addSetting(coordX);
		addSetting(coordY);
		addSetting(coordZ);
		addSetting(dimension);
		addSetting(travelMode);
		addSetting(speed);
		addSetting(linkElytra);
		addSetting(fireworkBoost);
		addSetting(autoEquipSpear);
		addSetting(spearLungePackets);
		addSetting(spearSpeedOverride);
		addSetting(arrivalRadius);
		addSetting(recoverFromPush);
		addSetting(recoverWait);
		addSetting(arrivalAction);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(RenderListener.class, this);
		goal = null;
		pathFinder = null;
		processor = null;
		recoverTimer = 0;
		stuckTicks = 0;
		elytraEnabledByUs = false;
		lastPos = MC.player != null ? MC.player.position() : null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(RenderListener.class, this);
		PathProcessor.releaseControls();
		pathFinder = null;
		processor = null;

		// Stop the SimpleElytra we turned on for linked flight.
		if(elytraEnabledByUs)
		{
			WURST.getHax().extraElytraHack.setEnabled(false);
			elytraEnabledByUs = false;
		}
	}

	@Override
	public String getRenderName()
	{
		return "自动前往";
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null)
			return;

		// Waiting to restart after being pushed/pulled down.
		if(recoverTimer > 0)
		{
			PathProcessor.lockControls();
			recoverTimer--;
			if(recoverTimer == 0)
			{
				lastPos = MC.player.position();
				resetNavigation();
				ChatUtils.message("自动前往 重新启动");
			}
			return;
		}

		// Parse the target coordinates.
		BlockPos parsed = parseGoal();
		if(parsed == null)
			return;
		if(goal == null || !goal.equals(parsed))
		{
			goal = parsed;
			resetNavigation();
		}

		// Dimension check: only arrive when in the requested dimension.
		if(!isInTargetDimension())
		{
			// Travel to the goal still works, but arrival won't trigger in the
			// wrong dimension. Keep navigating so the player can cross portals.
		}

		// Arrival check (coordinates, plus dimension if specifically set).
		Vec3 center = Vec3.atCenterOf(goal);
		double dx = MC.player.getX() - center.x;
		double dy = MC.player.getY() - center.y;
		double dz = MC.player.getZ() - center.z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if(dist <= arrivalRadius.getValue() && isInTargetDimension())
		{
			onArrive();
			return;
		}

		TravelMode mode = travelMode.getSelected();
		switch(mode)
		{
			case WALK -> walkStep();
			case SPEAR -> spearStep();
			default -> flyStep(mode); // Elytra / Flight / Hybrid
		}
	}

	private void walkStep()
	{
		if(goal == null)
			return;

		if(pathFinder == null)
		{
			pathFinder = new PathFinder(goal);
			processor = null;
		}

		if(!pathFinder.isDone() && !pathFinder.isFailed())
		{
			PathProcessor.lockControls();
			pathFinder.think();
			if(pathFinder.isDone())
			{
				pathFinder.formatPath();
				processor = pathFinder.getProcessor();
			}
			return;
		}

		if(pathFinder.isFailed())
		{
			ChatUtils.error("无法找到路径");
			setEnabled(false);
			return;
		}

		if(processor != null && !pathFinder.isPathStillValid(processor.getIndex()))
		{
			pathFinder = new PathFinder(goal);
			processor = null;
			return;
		}

		if(processor != null && !processor.isDone())
			processor.process();
	}

	private void flyStep(TravelMode mode)
	{
		Vec3 target = Vec3.atCenterOf(goal);
		Vec3 delta = target.subtract(MC.player.position());
		double flatDist = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

		// Hybrid: walk when close to the goal and on the ground.
		if(mode == TravelMode.HYBRID && flatDist < 8 && MC.player.onGround())
		{
			PathProcessor.releaseControls();
			walkStep();
			return;
		}

		// Link SimpleElytra (ExtraElytra) for reliable take-off and stable
		// gliding. We only enable it ourselves and remember that so we can
		// turn it back off when AutoGoTo stops, without touching a hack that
		// the user had already enabled.
		if(mode == TravelMode.ELYTRA && linkElytra.isChecked()
			&& !WURST.getHax().extraElytraHack.isEnabled())
		{
			WURST.getHax().extraElytraHack.setEnabled(true);
			elytraEnabledByUs = true;
		}

		// Take off. Sending the fall-flying packet ourselves also works when
		// SimpleElytra is not linked.
		if(mode == TravelMode.ELYTRA && !MC.player.isFallFlying())
		{
			if(MC.player.onGround())
				MC.player.jumpFromGround();
			MC.getConnection().send(new net.minecraft.network.protocol.game
				.ServerboundPlayerCommandPacket(MC.player,
					net.minecraft.network.protocol.game
						.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
		}

		// Face the target so movement looks natural.
		WURST.getRotationFaker().faceVectorPacket(target);

		// Exact velocity: input speed == actual speed. No firework stacking
		// unless the user explicitly enables the optional firework boost.
		double blocksPerTick = speed.getValue() / 20.0;
		Vec3 dir = delta.normalize();
		MC.player.setDeltaMovement(dir.scale(blocksPerTick));
		MC.player.fallDistance = 0;
		PathProcessor.releaseControls();

		// Optional firework boost (adds extra velocity, so speed is no longer
		// exact). Off by default.
		if(mode == TravelMode.ELYTRA && fireworkBoost.isChecked()
			&& MC.player.isFallFlying())
		{
			MC.getConnection().send(new ServerboundUseItemPacket(
				InteractionHand.OFF_HAND, MC.player.getId(), MC.player.getXRot(),
				MC.player.getYRot()));
		}

		// Detect being pushed/pulled down.
		if(recoverFromPush.isChecked())
			checkForPush(delta);
	}

	/**
	 * Spear travel mode: keep a spear in the main hand and submit the Lunge
	 * use-item packet so the server performs its own authoritative charge
	 * toward the goal. By default no client-side velocity is injected, so the
	 * server's cooldown, hunger and speed cap all apply and movement cannot be
	 * rubber-banded. Spear speed override (off by default) adds uncapped
	 * client-side velocity for servers that allow it.
	 */
	private void spearStep()
	{
		if(goal == null)
			return;

		if(autoEquipSpear.isChecked())
			equipSpear();

		// A spear must physically be in the main hand for the server to
		// perform a Lunge. Without one there is no movement at all.
		if(!MC.player.getMainHandItem().is(ItemTags.SPEARS))
			return;

		Vec3 target = Vec3.atCenterOf(goal);
		Vec3 delta = target.subtract(MC.player.position());

		// Face the target via the rotation faker to avoid server snap-back and
		// so the server-side Lunge is directed toward the goal.
		WURST.getRotationFaker().faceVectorPacket(target);

		// Submit the use-item (Lunge) input to the server. This is the only
		// thing done in the default mode; the server owns the resulting move.
		if(spearLungePackets.isChecked())
			MC.getConnection().send(new ServerboundUseItemPacket(
				InteractionHand.MAIN_HAND, MC.player.getId(), MC.player.getXRot(),
				MC.player.getYRot()));

		// Optional uncapped cheat mode (off by default): inject client-side
		// velocity toward the goal. The server may rubber-band this back.
		if(spearSpeedOverride.isChecked())
		{
			Vec3 dir = new Vec3(delta.x, 0, delta.z);
			if(dir.lengthSqr() > 0)
				dir = dir.normalize();
			else
				dir = MC.player.getLookAngle();

			double blocksPerTick = speed.getValue() / 20.0;
			MC.player.setDeltaMovement(dir.scale(blocksPerTick));
			MC.player.fallDistance = 0;
		}
		PathProcessor.releaseControls();

		if(recoverFromPush.isChecked())
			checkForPush(delta);
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

	private void checkForPush(Vec3 deltaToGoal)
	{
		Vec3 now = MC.player.position();
		if(lastPos == null)
		{
			lastPos = now;
			return;
		}

		// Did we actually move toward the goal this tick?
		Vec3 moved = now.subtract(lastPos);
		double towardGoal = moved.dot(deltaToGoal.normalize());
		double expected = speed.getValue() / 20.0;

		// Pulled back (moved away) or pushed down (big negative Y while trying
		// to gain/maintain height).
		boolean pulledBack = towardGoal < -0.1;
		boolean pushedDown = deltaToGoal.y > 0.5 && moved.y < -0.3;

		if(pulledBack || pushedDown)
		{
			if(++stuckTicks >= 3)
			{
				recoverTimer = recoverWait.getValueI() * 20;
				stuckTicks = 0;
				MC.player.setDeltaMovement(Vec3.ZERO);
				ChatUtils.warning("检测到被拉回/下压，关闭并在 "
					+ recoverWait.getValueI() + " 秒后重新启动");
			}
		}
		else
			stuckTicks = 0;

		lastPos = now;
	}

	private boolean isInTargetDimension()
	{
		DimensionMode dim = dimension.getSelected();
		if(dim == DimensionMode.CURRENT)
			return true;

		ResourceKey<Level> current = MC.level.dimension();
		return switch(dim)
		{
			case OVERWORLD -> current == Level.OVERWORLD;
			case NETHER -> current == Level.NETHER;
			case END -> current == Level.END;
			default -> true;
		};
	}

	private BlockPos parseGoal()
	{
		try
		{
			int x = Integer.parseInt(coordX.getValue().trim());
			int y = Integer.parseInt(coordY.getValue().trim());
			int z = Integer.parseInt(coordZ.getValue().trim());
			return new BlockPos(x, y, z);
		}
		catch(NumberFormatException e)
		{
			return null;
		}
	}

	private void resetNavigation()
	{
		pathFinder = null;
		processor = null;
		stuckTicks = 0;
	}

	private void onArrive()
	{
		ArrivalAction action = arrivalAction.getSelected();
		PathProcessor.releaseControls();

		switch(action)
		{
			case QUIT ->
			{
				ChatUtils.message("自动前往 已到达，退出游戏");
				MC.level.disconnect(ClientLevel.DEFAULT_QUIT_MESSAGE);
			}
			case HUT ->
			{
				ChatUtils.message("自动前往 已到达，搭建黑曜石自闭小屋");
				WURST.getHax().surroundHack.setEnabled(true);
			}
			case DEFEND ->
			{
				ChatUtils.message("自动前往 已到达，开启自动防御");
				WURST.getHax().surroundHack.setEnabled(true);
				WURST.getHax().autoShieldHack.setEnabled(true);
				WURST.getHax().antiChargeDodgeHack.setEnabled(true);
				WURST.getHax().pushAttackersHack.setEnabled(true);
			}
			default -> ChatUtils.message("自动前往 已到达目标坐标");
		}

		setEnabled(false);
	}

	@Override
	public void onRender(PoseStack matrixStack, float partialTicks)
	{
		if(pathFinder == null)
			return;
		PathFinder pathCmdRef = pathFinder;
		pathCmdRef.renderPath(matrixStack,
			WURST.getCmds().pathCmd.isDebugMode(),
			WURST.getCmds().pathCmd.isDepthTest());
	}
}
