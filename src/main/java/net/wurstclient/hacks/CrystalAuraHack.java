/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.FaceTargetSetting;
import net.wurstclient.settings.FaceTargetSetting.FaceTarget;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.SwingHandSetting;
import net.wurstclient.settings.SwingHandSetting.SwingHand;
import net.wurstclient.settings.TakeItemsFromSetting;
import net.wurstclient.settings.TakeItemsFromSetting.TakeItemsFrom;
import net.wurstclient.settings.filterlists.CrystalAuraFilterList;
import net.wurstclient.settings.filterlists.EntityFilterList;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.EntityUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.RotationUtils;

@SearchTags({"crystal aura"})
public final class CrystalAuraHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"Determines how far CrystalAura will reach to place and detonate crystals.",
		6, 1, 6, 0.05, ValueDisplay.DECIMAL);
	
	private final CheckboxSetting autoPlace = new CheckboxSetting(
		"Auto-place crystals",
		"When enabled, CrystalAura will automatically place crystals near valid entities.\n"
			+ "When disabled, CrystalAura will only detonate manually placed crystals.",
		true);
	
	private final CheckboxSetting checkLOS = new CheckboxSetting(
		"Check line of sight",
		"Ensures that you don't reach through blocks when placing or left-clicking end crystals.\n\n"
			+ "Slower but can help with anti-cheat plugins.",
		false);
	
	private final FaceTargetSetting faceTarget =
		FaceTargetSetting.withPacketSpam(this, FaceTarget.OFF);
	
	private final SwingHandSetting swingHand =
		new SwingHandSetting(this, SwingHand.CLIENT);
	
	private final TakeItemsFromSetting takeItemsFrom =
		TakeItemsFromSetting.withoutHands(this, TakeItemsFrom.INVENTORY);
	
	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"description.wurst.setting.crystalaura.place_delay", 0, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks").withLabel(0, "off"));
	
	private final SliderSetting detonateDelay =
		new SliderSetting("Detonate delay",
			"description.wurst.setting.crystalaura.detonate_delay", 0, 0, 20, 1,
			ValueDisplay.INTEGER.withSuffix(" ticks").withLabel(0, "off"));
	
	private final CheckboxSetting avoidSelfDamage =
		new CheckboxSetting("Avoid self-damage",
			"description.wurst.setting.crystalaura.avoid_self_damage", true);
	
	private final SliderSetting minSafeDistance =
		new SliderSetting("Minimum safe distance",
			"description.wurst.setting.crystalaura.min_safe_distance", 6, 1, 8,
			0.5, ValueDisplay.DECIMAL.withSuffix("m"));
	
	private final EntityFilterList entityFilters =
		CrystalAuraFilterList.create();
	
	private int placeTimer;
	private int detonateTimer;
	
	public CrystalAuraHack()
	{
		super("CrystalAura");
		
		setCategory(Category.COMBAT);
		addSetting(range);
		addSetting(autoPlace);
		addSetting(checkLOS);
		addSetting(faceTarget);
		addSetting(swingHand);
		addSetting(takeItemsFrom);
		addSetting(placeDelay);
		addSetting(detonateDelay);
		addSetting(avoidSelfDamage);
		addSetting(minSafeDistance);
		
		entityFilters.forEach(this::addSetting);
	}
	
	@Override
	protected void onEnable()
	{
		// reset timers
		placeTimer = 0;
		detonateTimer = 0;
		
		// disable other killauras
		WURST.getHax().aimAssistHack.setEnabled(false);
		WURST.getHax().clickAuraHack.setEnabled(false);
		WURST.getHax().fightBotHack.setEnabled(false);
		WURST.getHax().killauraHack.setEnabled(false);
		WURST.getHax().killauraLegitHack.setEnabled(false);
		WURST.getHax().multiAuraHack.setEnabled(false);
		WURST.getHax().protectHack.setEnabled(false);
		WURST.getHax().triggerBotHack.setEnabled(false);
		WURST.getHax().tpAuraHack.setEnabled(false);
		
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
		ArrayList<Entity> crystals = getNearbyCrystals();
		
		if(!crystals.isEmpty())
		{
			if(detonateTimer > 0)
			{
				detonateTimer--;
				return;
			}
			
			detonate(crystals);
			detonateTimer = detonateDelay.getValueI();
			return;
		}
		
		// reset the detonate timer when there are no crystals
		detonateTimer = 0;
		
		if(!autoPlace.isChecked())
			return;
		
		if(placeTimer > 0)
		{
			placeTimer--;
			return;
		}
		
		if(InventoryUtils.indexOf(Items.END_CRYSTAL,
			takeItemsFrom.getMaxInvSlot()) == -1)
			return;
		
		ArrayList<Entity> targets = getNearbyTargets();
		if(!placeCrystalsNear(targets).isEmpty())
			placeTimer = placeDelay.getValueI();
	}
	
	private ArrayList<BlockPos> placeCrystalsNear(ArrayList<Entity> targets)
	{
		ArrayList<BlockPos> newCrystals = new ArrayList<>();
		
		boolean shouldSwing = false;
		for(Entity target : targets)
		{
			ArrayList<BlockPos> freeBlocks = getFreeBlocksNear(target);
			
			for(BlockPos pos : freeBlocks)
				if(placeCrystal(pos))
				{
					shouldSwing = true;
					newCrystals.add(pos);
					
					// TODO optional speed limit(?)
					break;
				}
		}
		
		if(shouldSwing)
			swingHand.swing(InteractionHand.MAIN_HAND);
		
		return newCrystals;
	}
	
	private void detonate(ArrayList<Entity> crystals)
	{
		if(avoidSelfDamage.isChecked())
			crystals = filterSafeCrystals(crystals);
		
		for(Entity e : crystals)
		{
			faceTarget.face(e.getBoundingBox().getCenter());
			MC.gameMode.attack(MC.player, e);
		}
		
		if(!crystals.isEmpty())
			swingHand.swing(InteractionHand.MAIN_HAND);
	}
	
	/**
	 * Removes crystals that are too close to the player, so their explosions
	 * can't hurt the player. If no safe crystals are left, it returns the
	 * original list so the hack still works.
	 */
	private ArrayList<Entity> filterSafeCrystals(ArrayList<Entity> crystals)
	{
		double minDistanceSq = Math.pow(minSafeDistance.getValue(), 2);
		
		ArrayList<Entity> safe = crystals.stream()
			.filter(e -> EntityUtils.distanceToHitboxSq(e) >= minDistanceSq)
			.collect(Collectors.toCollection(ArrayList::new));
		
		return safe.isEmpty() ? crystals : safe;
	}
	
	private boolean placeCrystal(BlockPos pos)
	{
		Vec3 eyesPos = RotationUtils.getEyesPos();
		double rangeSq = Math.pow(range.getValue(), 2);
		Vec3 posVec = Vec3.atCenterOf(pos);
		double distanceSqPosVec = eyesPos.distanceToSqr(posVec);
		
		for(Direction side : Direction.values())
		{
			BlockPos neighbor = pos.relative(side);
			
			// check if neighbor can be right clicked
			if(!isClickableNeighbor(neighbor))
				continue;
			
			Vec3 dirVec = Vec3.atLowerCornerOf(side.getUnitVec3i());
			Vec3 hitVec = posVec.add(dirVec.scale(0.5));
			
			// check if hitVec is within range
			if(eyesPos.distanceToSqr(hitVec) > rangeSq)
				continue;
			
			// check if side is visible (facing away from player)
			if(distanceSqPosVec > eyesPos.distanceToSqr(posVec.add(dirVec)))
				continue;
			
			if(checkLOS.isChecked()
				&& !BlockUtils.hasLineOfSight(eyesPos, hitVec))
				continue;
			
			InventoryUtils.selectItem(Items.END_CRYSTAL,
				takeItemsFrom.getMaxInvSlot());
			if(!MC.player.isHolding(Items.END_CRYSTAL))
				return false;
			
			faceTarget.face(hitVec);
			
			// place block
			IMC.getInteractionManager().rightClickBlock(neighbor,
				side.getOpposite(), hitVec);
			
			return true;
		}
		
		return false;
	}
	
	private ArrayList<Entity> getNearbyCrystals()
	{
		double rangeSq = Math.pow(range.getValue(), 2);
		
		Comparator<Entity> furthestFromPlayer =
			Comparator.<Entity> comparingDouble(EntityUtils::distanceToHitboxSq)
				.reversed();
		
		return EntityUtils.getAliveEntities(EndCrystal.class)
			.filter(e -> EntityUtils.distanceToHitboxSq(e) <= rangeSq)
			.sorted(furthestFromPlayer)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private ArrayList<Entity> getNearbyTargets()
	{
		double rangeSq = Math.pow(range.getValue(), 2);
		
		Comparator<Entity> furthestFromPlayer =
			Comparator.<Entity> comparingDouble(EntityUtils::distanceToHitboxSq)
				.reversed();
		
		Stream<LivingEntity> stream =
			EntityUtils.getExplosionWorthyAttackableEntities()
				.filter(e -> EntityUtils.distanceToHitboxSq(e) <= rangeSq);
		
		stream = entityFilters.applyTo(stream);
		
		return stream.sorted(furthestFromPlayer)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private ArrayList<BlockPos> getFreeBlocksNear(Entity target)
	{
		Vec3 eyesVec = RotationUtils.getEyesPos().subtract(0.5, 0.5, 0.5);
		double rangeD = range.getValue();
		double rangeSq = Math.pow(rangeD + 0.5, 2);
		int rangeI = 2;
		
		BlockPos center = target.blockPosition();
		BlockPos min = center.offset(-rangeI, -rangeI, -rangeI);
		BlockPos max = center.offset(rangeI, rangeI, rangeI);
		AABB targetBB = target.getBoundingBox();
		
		Vec3 targetEyesVec =
			target.position().add(0, target.getEyeHeight(target.getPose()), 0);
		
		Comparator<BlockPos> closestToTarget =
			Comparator.<BlockPos> comparingDouble(
				pos -> targetEyesVec.distanceToSqr(Vec3.atCenterOf(pos)));
		
		return BlockUtils.getAllInBoxStream(min, max).filter(
			pos -> eyesVec.distanceToSqr(Vec3.atLowerCornerOf(pos)) <= rangeSq)
			.filter(this::isReplaceable).filter(this::hasCrystalBase)
			.filter(pos -> !targetBB.intersects(new AABB(pos)))
			.sorted(closestToTarget)
			.collect(Collectors.toCollection(ArrayList::new));
	}
	
	private boolean isReplaceable(BlockPos pos)
	{
		return BlockUtils.getState(pos).canBeReplaced();
	}
	
	private boolean hasCrystalBase(BlockPos pos)
	{
		Block block = BlockUtils.getBlock(pos.below());
		return block == Blocks.BEDROCK || block == Blocks.OBSIDIAN;
	}
	
	private boolean isClickableNeighbor(BlockPos pos)
	{
		return BlockUtils.canBeClicked(pos)
			&& !BlockUtils.getState(pos).canBeReplaced();
	}
}
