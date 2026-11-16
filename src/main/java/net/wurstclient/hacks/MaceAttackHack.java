/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * MaceAttack Hack ("重锤攻击") for Wurst Client.
 *
 * Uses the Mace's smash attack (damage scales with fall distance). The hack
 * auto-equips a mace, launches itself up with a wind charge, faces the target
 * and swings on the way down. It also reuses the fake-Y trick from MaceDMG
 * to maximize the server-side fall distance. Only attacks, never drops items.
 *
 * Rush mode adds a rapid pulse cycle: smash down at high speed, strike, flip
 * to an upward recoil velocity, then instantly slam down again. It also
 * hard-locks onto a single target until it dies or goes out of range.
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.stream.Stream;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
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

@SearchTags({ "mace attack", "mace aura", "wind charge mace",
	"smash attack", "重锤攻击", "重锤", "风弹", "下砸", "连抽" })
public final class MaceAttackHack extends Hack implements UpdateListener
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
		new SliderSetting("Range", 12, 4, 40, 1, ValueDisplay.INTEGER);

	private final SliderSetting launchHeight = new SliderSetting("Launch height",
		"description.wurst.setting.maceattack.launch_height", 8, 2, 30, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting fakeFall = new CheckboxSetting("Fake fall distance",
		"description.wurst.setting.maceattack.fake_fall", true);

	private final CheckboxSetting autoEquip = new CheckboxSetting("Auto equip",
		"description.wurst.setting.maceattack.auto_equip", true);

	private final CheckboxSetting rushMode = new CheckboxSetting("Rush mode",
		"description.wurst.setting.maceattack.rush_mode", true);

	private final SliderSetting rushSpeed = new SliderSetting("Rush speed",
		"description.wurst.setting.maceattack.rush_speed", 25, 5, 60, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting recoilTicks = new SliderSetting("Recoil",
		"description.wurst.setting.maceattack.recoil", 3, 1, 10, 1,
		ValueDisplay.INTEGER);

	private int attackTimer;
	private int launchTimer;
	private boolean launched = false;

	// Rush-mode state: 0 = slamming down, 1 = recoil up.
	private int pulsePhase;
	private int phaseTimer;
	private Entity lockedTarget;

	public MaceAttackHack()
	{
		super("MaceAttack");
		setCategory(Category.COMBAT);
		addSetting(targetMode);
		addSetting(range);
		addSetting(launchHeight);
		addSetting(fakeFall);
		addSetting(autoEquip);
		addSetting(rushMode);
		addSetting(rushSpeed);
		addSetting(recoilTicks);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		attackTimer = 0;
		launchTimer = 0;
		launched = false;
		pulsePhase = 0;
		phaseTimer = 0;
		lockedTarget = null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		lockedTarget = null;
	}

	@Override
	public String getRenderName()
	{
		return "重锤攻击";
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.getConnection() == null)
			return;

		if(autoEquip.isChecked()
			&& !MC.player.getMainHandItem().is(Items.MACE))
		{
			int slot = findMace();
			if(slot != -1)
				IMC.getInteractionManager().windowClick_SWAP(slot,
					MC.player.getInventory().getSelectedSlot());
		}

		if(!MC.player.getMainHandItem().is(Items.MACE))
			return;

		Entity target = findTarget();
		if(target == null)
		{
			launched = false;
			lockedTarget = null;
			return;
		}

		// Face the target.
		Vec3 targetPos = EntityUtils.getLerpedPos(target, 1.0F);
		Rotation rotation = RotationUtils.getNeededRotations(targetPos);
		rotation.applyToClientPlayer();

		if(rushMode.isChecked())
		{
			rushAttack(target, targetPos);
			return;
		}

		// Normal mode: launch up with wind charge, then smash on the way down.
		Vec3 v = MC.player.getDeltaMovement();
		double dist = MC.player.distanceTo(target);

		boolean falling = v.y < -0.1;
		if(falling && dist <= 4.5 && ++attackTimer >= 3)
		{
			MC.gameMode.attack(MC.player, target);
			MC.player.swing(InteractionHand.MAIN_HAND);

			if(fakeFall.isChecked())
				sendFakeFallPackets();

			attackTimer = 0;
			launched = false;
			return;
		}

		if(!falling && dist <= range.getValue() && --launchTimer <= 0)
		{
			useWindCharge();
			launchTimer = 40;
			launched = true;
		}
	}

	/**
	 * Rapid pulse cycle: slam down toward the target at high speed and strike,
	 * then flip to an upward recoil velocity for a few ticks before slamming
	 * down again. Fake fall packets maximize the server-side damage bonus.
	 */
	private void rushAttack(Entity target, Vec3 targetPos)
	{
		Vec3 playerPos = RotationUtils.getEyesPos();
		Vec3 dir = targetPos.subtract(playerPos).normalize();
		double dist = MC.player.distanceTo(target);
		double speed = rushSpeed.getValueF() / 20F; // blocks per tick

		if(pulsePhase == 0)
		{
			// Slam down toward the target, biasing the velocity downward so
			// the mace accumulates fall distance for maximum damage.
			Vec3 slam = new Vec3(dir.x * speed, -speed, dir.z * speed);
			MC.player.setDeltaMovement(slam);

			if(dist <= 4.5 && ++attackTimer >= 2)
			{
				MC.gameMode.attack(MC.player, target);
				MC.player.swing(InteractionHand.MAIN_HAND);

				if(fakeFall.isChecked())
					sendFakeFallPackets();

				attackTimer = 0;
				// Flip to negative (upward) recoil velocity.
				pulsePhase = 1;
				phaseTimer = recoilTicks.getValueI();
			}
		}
		else
		{
			// Recoil: shoot upward for a few ticks, then slam down again.
			MC.player.setDeltaMovement(new Vec3(dir.x * speed * 0.3,
				speed * 1.2, dir.z * speed * 0.3));
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
				&& MC.hitResult.getType() == HitResult.Type.ENTITY)
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

	private void useWindCharge()
	{
		ItemStack held = MC.player.getInventory().getSelectedItem();
		InteractionHand hand = InteractionHand.MAIN_HAND;

		if(MC.player.getOffhandItem().is(Items.WIND_CHARGE))
			hand = InteractionHand.OFF_HAND;
		else if(!held.is(Items.WIND_CHARGE))
		{
			// Fallback: look straight down and right-click to boost off ground.
			MC.player.setXRot(90);
		}

		MC.getConnection().send(new ServerboundUseItemPacket(hand,
			MC.player.getId(), MC.player.getXRot(), MC.player.getYRot()));
	}

	private void sendFakeFallPackets()
	{
		double y = MC.player.getY();
		for(int i = 0; i < 4; i++)
			sendFakeY(y, 0);
		sendFakeY(y, Math.sqrt(500));
		sendFakeY(y, 0);
	}

	private void sendFakeY(double baseY, double offset)
	{
		MC.player.connection.send(new Pos(MC.player.getX(), baseY + offset,
			MC.player.getZ(), false, MC.player.horizontalCollision));
	}

	private int findMace()
	{
		net.minecraft.world.entity.player.Inventory inv =
			MC.player.getInventory();
		for(int i = 0; i < 36; i++)
			if(inv.getItem(i).is(Items.MACE))
				return i;
		return -1;
	}
}
