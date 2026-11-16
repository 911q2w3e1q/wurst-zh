/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * SpearElytraAttack Hack ("长矛翘翅烟花") for Wurst Client.
 *
 * Building on the vanilla Spear's charge attack (damage scales with the
 * relative speed of attacker and target), this hack automatically:
 *   - equips a spear and elytra,
 *   - boosts with firework rockets,
 *   - faces the target,
 *   - holds right-click to charge the spear at high speed,
 *   - lands a jab when close enough.
 *
 * Rush mode adds a rapid "pulse" cycle: charge in at high speed, strike,
 * flip to a negative recoil velocity, then instantly surge forward again.
 * It also hard-locks onto a single target until it dies or goes out of range.
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.Stream;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.Rotation;
import net.wurstclient.util.RotationUtils;

@SearchTags({ "spear elytra", "spear attack", "elytra attack",
	"firework spear", "charge attack", "长矛翘翅", "长矛攻击", "翘翅攻击",
	"烟花攻击", "冲锋攻击", "连抽" })
public final class SpearElytraAttackHack extends Hack implements UpdateListener
{
	private enum TargetMode
	{
		AUTO("Auto"), CROSSHAIR("Crosshair");

		private final String label;

		TargetMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final EnumSetting<TargetMode> targetMode = new EnumSetting<>(
		"Target", TargetMode.values(), TargetMode.AUTO);

	private final SliderSetting range =
		new SliderSetting("Range", 30, 5, 80, 1, ValueDisplay.INTEGER);

	private final SliderSetting speedThreshold = new SliderSetting("Charge speed",
		"description.wurst.setting.spearelytra.charge_speed", 12, 2, 40, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting autoFirework = new CheckboxSetting(
		"Auto firework",
		"description.wurst.setting.spearelytra.auto_firework", true);

	private final CheckboxSetting autoEquip = new CheckboxSetting("Auto equip",
		"description.wurst.setting.spearelytra.auto_equip", true);

	private final CheckboxSetting rushMode = new CheckboxSetting("Rush mode",
		"description.wurst.setting.spearelytra.rush_mode", true);

	private final SliderSetting rushSpeed = new SliderSetting("Rush speed",
		"description.wurst.setting.spearelytra.rush_speed", 30, 5, 80, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting recoilTicks = new SliderSetting("Recoil",
		"description.wurst.setting.spearelytra.recoil", 3, 1, 10, 1,
		ValueDisplay.INTEGER);

	private int fireworkTimer;
	private int attackTimer;
	private int flyingSlot = -1;
	private int spearSlot = -1;
	private boolean charging = false;

	// Rush-mode state: 0 = charging in, 1 = recoiling backwards.
	private int pulsePhase;
	private int phaseTimer;
	private Entity lockedTarget;

	public SpearElytraAttackHack()
	{
		super("SpearElytraAttack");
		setCategory(Category.COMBAT);
		addSetting(targetMode);
		addSetting(range);
		addSetting(speedThreshold);
		addSetting(autoFirework);
		addSetting(autoEquip);
		addSetting(rushMode);
		addSetting(rushSpeed);
		addSetting(recoilTicks);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		fireworkTimer = 0;
		attackTimer = 0;
		charging = false;
		pulsePhase = 0;
		phaseTimer = 0;
		lockedTarget = null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		stopCharging();
		lockedTarget = null;
	}

	@Override
	public String getRenderName()
	{
		return "长矛翘翅烟花";
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.getConnection() == null)
			return;

		// Equip elytra and spear when needed.
		if(autoEquip.isChecked())
			equipGear();

		Entity target = findTarget();
		if(target == null)
		{
			stopCharging();
			lockedTarget = null;
			return;
		}

		// Face the target.
		Vec3 targetPos = EntityUtils.getLerpedPos(target, 1.0F);
		Rotation rotation = RotationUtils.getNeededRotations(targetPos);
		rotation.applyToClientPlayer();

		// Start gliding if not already.
		if(!MC.player.isFallFlying())
			startFlying();

		// Boost with fireworks.
		if(autoFirework.isChecked() && MC.player.isFallFlying()
			&& --fireworkTimer <= 0)
		{
			useFirework();
			fireworkTimer = 20;
		}

		if(rushMode.isChecked())
		{
			rushAttack(target, targetPos);
			return;
		}

		// Normal mode: hold right-click at high speed, jab when close.
		Vec3 v = MC.player.getDeltaMovement();
		double hSpeed = Math.sqrt(v.x * v.x + v.z * v.z) * 20;
		double dist = MC.player.distanceTo(target);

		if(hSpeed >= speedThreshold.getValue())
			startCharging();
		else
			stopCharging();

		if(++attackTimer >= 5 && dist <= 4.5)
		{
			MC.gameMode.attack(MC.player, target);
			MC.player.swing(InteractionHand.MAIN_HAND);
			attackTimer = 0;
		}
	}

	/**
	 * Rapid pulse cycle: surge toward the target at high speed and strike,
	 * then flip to a negative recoil velocity for a few ticks before surging
	 * forward again. Damage comes from the high relative speed at each hit.
	 */
	private void rushAttack(Entity target, Vec3 targetPos)
	{
		Vec3 playerPos = RotationUtils.getEyesPos();
		Vec3 dir = targetPos.subtract(playerPos).normalize();
		double dist = MC.player.distanceTo(target);
		double speed = rushSpeed.getValueF() / 20F; // blocks per tick

		if(pulsePhase == 0)
		{
			// Charge in at full speed toward the target.
			MC.player.setDeltaMovement(dir.scale(speed));
			startCharging();

			if(dist <= 4.5 && ++attackTimer >= 2)
			{
				MC.gameMode.attack(MC.player, target);
				MC.player.swing(InteractionHand.MAIN_HAND);
				attackTimer = 0;
				stopCharging();
				// Flip to negative recoil velocity.
				pulsePhase = 1;
				phaseTimer = recoilTicks.getValueI();
			}
		}
		else
		{
			// Recoil: reverse direction for a few ticks, then surge again.
			MC.player.setDeltaMovement(dir.scale(-speed * 0.6));
			if(--phaseTimer <= 0)
				pulsePhase = 0;
		}
	}

	private Entity findTarget()
	{
		// Hard-lock: keep attacking the same target while it's alive & close.
		if(rushMode.isChecked() && isTargetValid(lockedTarget))
			return lockedTarget;

		Entity found = findNewTarget();
		if(rushMode.isChecked())
			lockedTarget = found;
		return found;
	}

	private boolean isTargetValid(Entity e)
	{
		return e != null && e.isAlive() && e != MC.player
			&& MC.player.distanceTo(e) <= range.getValue() * 1.5;
	}

	private Entity findNewTarget()
	{
		if(targetMode.getSelected() == TargetMode.CROSSHAIR)
		{
			if(MC.hitResult != null
				&& MC.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY)
			{
				Entity e = ((net.minecraft.world.phys.EntityHitResult)MC.hitResult)
					.getEntity();
				if(e instanceof LivingEntity && e != MC.player)
					return e;
			}
			return null;
		}

		Stream<Entity> stream = EntityUtils.getAttackableEntities();
		return stream.filter(e -> e != MC.player && e instanceof LivingEntity)
			.min(Comparator.comparingDouble(e -> MC.player.distanceTo(e)))
			.orElse(null);
	}

	private void equipGear()
	{
		// Equip elytra in chest slot if a free/empty chest is available.
		ItemStack chest = MC.player.getInventory().getItem(38);
		if(!chest.is(Items.ELYTRA))
		{
			int slot = findItem(Items.ELYTRA);
			if(slot != -1)
				flyingSlot = slot;
		}

		// Equip a spear in main hand if not holding one.
		if(!MC.player.getMainHandItem().is(ItemTags.SPEARS))
		{
			int slot = findSpear();
			if(slot != -1)
			{
				net.wurstclient.mixinterface.IClientPlayerInteractionManager im =
					IMC.getInteractionManager();
				int selected = MC.player.getInventory().getSelectedSlot();
				im.windowClick_SWAP(slot, selected);
				spearSlot = slot;
			}
		}
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

	private int findSpear()
	{
		net.minecraft.world.entity.player.Inventory inv =
			MC.player.getInventory();
		for(int i = 0; i < 36; i++)
			if(inv.getItem(i).is(ItemTags.SPEARS))
				return i;
		return -1;
	}

	private void startFlying()
	{
		ServerboundPlayerCommandPacket packet =
			new ServerboundPlayerCommandPacket(MC.player,
				ServerboundPlayerCommandPacket.Action.START_FALL_FLYING);
		MC.getConnection().send(packet);
	}

	private void useFirework()
	{
		ItemStack held = MC.player.getInventory().getSelectedItem();
		InteractionHand hand = InteractionHand.MAIN_HAND;

		// Use a firework from the off-hand if present, else main hand.
		if(!MC.player.getOffhandItem().is(Items.FIREWORK_ROCKET)
			&& held.is(Items.FIREWORK_ROCKET))
			hand = InteractionHand.MAIN_HAND;
		else if(MC.player.getOffhandItem().is(Items.FIREWORK_ROCKET))
			hand = InteractionHand.OFF_HAND;

		MC.getConnection().send(new ServerboundUseItemPacket(hand,
			MC.player.getId(), MC.player.getXRot(), MC.player.getYRot()));
	}

	private void startCharging()
	{
		if(charging)
			return;
		MC.player.startUsingItem(InteractionHand.MAIN_HAND);
		MC.getConnection().send(new ServerboundUseItemPacket(
			InteractionHand.MAIN_HAND, MC.player.getId(), MC.player.getXRot(),
			MC.player.getYRot()));
		charging = true;
	}

	private void stopCharging()
	{
		if(!charging)
			return;
		MC.player.connection.send(
			new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
				net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM,
				net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
		charging = false;
	}
}
