/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * AntiChargeDodge Hack ("冲撞闪避") for Wurst Client.
 *
 * Detects two common 1.21+ PvP threats and dodges them automatically:
 *   - A spear user charging at high horizontal speed -> strafe sideways.
 *   - A mace user (or any entity) dropping fast from directly above ->
 *     jump/strafe out of the landing spot.
 *
 * Optional "escape when overwhelmed": if the player takes too many hits in a
 * short time window, automatically equip an elytra, take off and fly away
 * from the nearest attacker.
 */
package net.wurstclient.hacks;

import java.util.ArrayList;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({ "anti spear", "anti mace", "dodge", "anti charge",
	"反长矛", "反重锤", "闪避", "防冲撞", "防砸", "翘翅跑路", "逃跑" })
public final class AntiChargeDodgeHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final SliderSetting detectRange = new SliderSetting(
		"Detect range",
		"description.wurst.setting.antichargedodge.detect_range",
		10, 3, 40, 1, ValueDisplay.INTEGER);

	private final SliderSetting speedThreshold = new SliderSetting(
		"Speed threshold",
		"description.wurst.setting.antichargedodge.speed_threshold",
		14, 3, 50, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting dodgeSpear = new CheckboxSetting(
		"Dodge spear charges",
		"description.wurst.setting.antichargedodge.dodge_spear", true);

	private final CheckboxSetting dodgeMace = new CheckboxSetting(
		"Dodge mace slams",
		"description.wurst.setting.antichargedodge.dodge_mace", true);

	private final CheckboxSetting escapeWhenOverwhelmed = new CheckboxSetting(
		"Escape when overwhelmed",
		"description.wurst.setting.antichargedodge.escape_when_overwhelmed",
		true);

	private final SliderSetting hitsToEscape = new SliderSetting(
		"Hits to escape",
		"description.wurst.setting.antichargedodge.hits_to_escape",
		3, 2, 10, 1, ValueDisplay.INTEGER);

	private final SliderSetting timeWindow = new SliderSetting(
		"Time window",
		"description.wurst.setting.antichargedodge.time_window",
		2, 1, 10, 1, ValueDisplay.INTEGER);

	private int cooldown;
	private int dodgeDir = 1;
	private int dodgeTicks;

	// Escape state
	private final ArrayList<Long> hitTimes = new ArrayList<>();
	private boolean escaping;
	private int escapeTicks;
	private int fireworkTimer;
	private Vec3 escapeDir = Vec3.ZERO;

	public AntiChargeDodgeHack()
	{
		super("AntiChargeDodge");
		setCategory(Category.COMBAT);
		addSetting(detectRange);
		addSetting(speedThreshold);
		addSetting(dodgeSpear);
		addSetting(dodgeMace);
		addSetting(escapeWhenOverwhelmed);
		addSetting(hitsToEscape);
		addSetting(timeWindow);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
		cooldown = 0;
		dodgeTicks = 0;
		hitTimes.clear();
		escaping = false;
		escapeTicks = 0;
		fireworkTimer = 0;
		restoreMovement();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		escaping = false;
		hitTimes.clear();
		restoreMovement();
	}

	@Override
	public String getRenderName()
	{
		return "冲撞闪避";
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!escapeWhenOverwhelmed.isChecked())
			return;

		Packet<?> packet = event.getPacket();
		if(!(packet instanceof ClientboundDamageEventPacket)
			&& !(packet instanceof ClientboundHurtAnimationPacket))
			return;

		long now = System.currentTimeMillis();
		long windowMs = timeWindow.getValueI() * 1000L;

		// Drop hits outside the time window.
		hitTimes.removeIf(t -> now - t > windowMs);

		// De-dup: a single hit can produce both packet types in one instant.
		if(!hitTimes.isEmpty() && now - hitTimes.get(hitTimes.size() - 1) < 100)
			return;

		hitTimes.add(now);
		if(hitTimes.size() >= hitsToEscape.getValueI())
			startEscape();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.getConnection() == null)
			return;

		// Actively escaping: keep flying away from the threat.
		if(escaping)
		{
			updateEscape();
			return;
		}

		if(cooldown > 0)
			cooldown--;

		// Currently dodging: apply a sideways impulse for a few ticks.
		if(dodgeTicks > 0)
		{
			dodgeTicks--;
			Vec3 v = MC.player.getDeltaMovement();
			MC.player.setDeltaMovement(v.x + 0.35 * dodgeDir, v.y,
				v.z - 0.35 * dodgeDir);
			if(dodgeTicks == 0)
				restoreMovement();
			return;
		}

		if(cooldown > 0)
			return;

		for(Entity e : MC.level.entitiesForRendering())
		{
			if(e == MC.player || !(e instanceof LivingEntity))
				continue;

			double dist = MC.player.distanceTo(e);
			if(dist > detectRange.getValue())
				continue;

			Vec3 v = e.getDeltaMovement();
			double speed = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z) * 20;
			if(speed < speedThreshold.getValue())
				continue;

			boolean threat = false;

			// Fast horizontal charge (spear).
			double hSpeed = Math.sqrt(v.x * v.x + v.z * v.z) * 20;
			if(dodgeSpear.isChecked() && hSpeed >= speedThreshold.getValue())
				threat = true;

			// Fast vertical drop from above (mace).
			double dy = e.getY() - MC.player.getY();
			if(dodgeMace.isChecked() && v.y < -0.4 && dy > 0 && dy < 12
				&& Math.abs(e.getX() - MC.player.getX()) < 3
				&& Math.abs(e.getZ() - MC.player.getZ()) < 3)
				threat = true;

			if(threat)
			{
				startDodge(e);
				break;
			}
		}
	}

	private void startDodge(Entity threat)
	{
		// Dodge perpendicular to the incoming direction.
		double dx = MC.player.getX() - threat.getX();
		double dz = MC.player.getZ() - threat.getZ();
		dodgeDir = (dx * -dz >= 0) ? 1 : -1;

		// If it's a vertical slam, add a small hop too.
		if(dodgeMace.isChecked() && threat.getY() > MC.player.getY()
			&& MC.player.onGround())
			MC.player.jumpFromGround();

		dodgeTicks = 5;
		cooldown = 30;
	}

	/**
	 * Too many hits in a short window: equip an elytra, take off, and fly away
	 * from the closest attacker for a few seconds.
	 */
	private void startEscape()
	{
		hitTimes.clear();

		// Direction: away from the closest nearby living entity.
		Entity nearest = null;
		double nearestDist = Double.MAX_VALUE;
		if(MC.level != null)
		{
			for(Entity e : MC.level.entitiesForRendering())
			{
				if(e == MC.player || !(e instanceof LivingEntity))
					continue;
				double d = MC.player.distanceTo(e);
				if(d < nearestDist)
				{
					nearestDist = d;
					nearest = e;
				}
			}
		}

		if(nearest != null)
		{
			double dx = MC.player.getX() - nearest.getX();
			double dz = MC.player.getZ() - nearest.getZ();
			escapeDir = new Vec3(dx, 0, dz).normalize();
		}
		else
		{
			// No attacker found: fly opposite to where we are looking.
			Vec3 look = MC.player.getLookAngle();
			escapeDir = new Vec3(-look.x, 0, -look.z).normalize();
		}

		// Equip elytra into the chest slot (container slot 38).
		ItemStack chest = MC.player.getInventory().getItem(38);
		if(!chest.is(Items.ELYTRA))
		{
			int slot = findItem(Items.ELYTRA);
			if(slot != -1)
				IMC.getInteractionManager().windowClick_SWAP(slot, 38);
		}

		// Hop first, then send the take-off packet (requires being airborne).
		if(MC.player.onGround())
			MC.player.jumpFromGround();
		startFlying();

		// Initial impulse: fast and slightly upward.
		MC.player.setDeltaMovement(escapeDir.x, 0.25, escapeDir.z);

		escapeTicks = 120; // 6 seconds at 20 tps
		fireworkTimer = 0;
		escaping = true;
	}

	private void updateEscape()
	{
		escapeTicks--;
		if(escapeTicks <= 0)
		{
			escaping = false;
			return;
		}

		// Retry take-off in case the first packet was sent while grounded.
		if(!MC.player.isFallFlying())
			startFlying();

		// Maintain horizontal escape speed with a gentle climb.
		MC.player.setDeltaMovement(escapeDir.x * 1.15, 0.12,
			escapeDir.z * 1.15);

		// Boost with a firework from the off-hand if available.
		if(--fireworkTimer <= 0
			&& MC.player.getOffhandItem().is(Items.FIREWORK_ROCKET))
		{
			MC.getConnection().send(new ServerboundUseItemPacket(
				InteractionHand.OFF_HAND, MC.player.getId(),
				MC.player.getXRot(), MC.player.getYRot()));
			fireworkTimer = 20;
		}
	}

	private void startFlying()
	{
		ServerboundPlayerCommandPacket packet =
			new ServerboundPlayerCommandPacket(MC.player,
				ServerboundPlayerCommandPacket.Action.START_FALL_FLYING);
		MC.getConnection().send(packet);
	}

	private int findItem(net.minecraft.world.item.Item item)
	{
		net.minecraft.world.entity.player.Inventory inv =
			MC.player.getInventory();
		for(int i = 0; i < 36; i++)
			if(inv.getItem(i).is(item))
				return i;
		return -1;
	}

	private void restoreMovement()
	{
		dodgeTicks = 0;
	}
}
