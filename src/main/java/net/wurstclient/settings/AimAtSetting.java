/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.settings;

import java.util.function.Function;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.util.RotationUtils;

public final class AimAtSetting extends EnumSetting<AimAtSetting.AimAt>
{
	private static final String FULL_DESCRIPTION_SUFFIX =
		buildDescriptionSuffix();
	
	private AimAtSetting(String name, String description, AimAt[] values,
		AimAt selected)
	{
		super(name, description, values, selected);
	}
	
	public AimAtSetting(String name, String description, AimAt selected)
	{
		this(name, description + FULL_DESCRIPTION_SUFFIX, AimAt.values(),
			selected);
	}
	
	public AimAtSetting(String description, AimAt selected)
	{
		this("Aim at", description, selected);
	}
	
	public AimAtSetting(String description)
	{
		this(description, AimAt.AUTO);
	}
	
	public Vec3 getAimPoint(Entity e)
	{
		return getSelected().aimFunction.apply(e);
	}
	
	private static String buildDescriptionSuffix()
	{
		StringBuilder builder = new StringBuilder("\n\n");
		AimAt[] values = AimAt.values();
		
		for(AimAt value : values)
			builder.append("\u00a7l").append(value.name).append("\u00a7r - ")
				.append(value.description).append("\n\n");
		
		return builder.toString();
	}
	
	private static Vec3 aimAtClosestPoint(Entity e)
	{
		AABB box = e.getBoundingBox();
		Vec3 eyes = RotationUtils.getEyesPos();
		
		if(box.contains(eyes))
			return eyes;
		
		double clampedX = Mth.clamp(eyes.x, box.minX, box.maxX);
		double clampedY = Mth.clamp(eyes.y, box.minY, box.maxY);
		double clampedZ = Mth.clamp(eyes.z, box.minZ, box.maxZ);
		
		return new Vec3(clampedX, clampedY, clampedZ);
	}
	
	private static Vec3 aimAtHead(Entity e)
	{
		float eyeHeight = e.getEyeHeight(e.getPose());
		return e.position().add(0, eyeHeight, 0);
	}
	
	private static Vec3 aimAtCenter(Entity e)
	{
		return e.getBoundingBox().getCenter();
	}
	
	private static Vec3 aimAtFeet(Entity e)
	{
		return e.position().add(0, 0.001, 0);
	}
	
	public enum AimAt
	{
		AUTO("Auto", "瞄准目标碰撞箱距离准星最近的位置。",
			AimAtSetting::aimAtClosestPoint),
		
		HEAD("Head", "瞄准目标眼睛的位置。",
			AimAtSetting::aimAtHead),
		
		CENTER("Center", "瞄准目标碰撞箱的中心。",
			AimAtSetting::aimAtCenter),
		
		FEET("Feet", "瞄准目标碰撞箱的底部。",
			AimAtSetting::aimAtFeet);
		
		private final String name;
		private final String description;
		private final Function<Entity, Vec3> aimFunction;
		
		private AimAt(String name, String description,
			Function<Entity, Vec3> aimFunction)
		{
			this.name = name;
			this.description = description;
			this.aimFunction = aimFunction;
		}
		
		public Vec3 getAimPoint(Entity e)
		{
			return aimFunction.apply(e);
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
}
