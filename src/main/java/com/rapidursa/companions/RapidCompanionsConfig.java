package com.rapidursa.companions;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;

@ConfigGroup("rapidCompanions")
public interface RapidCompanionsConfig extends Config
{
	@ConfigItem(
		keyName = "pet",
		name = "Pick Pet",
		description = "Pick pet",
		hidden = true
	)
	default PetData pet()
	{
		return PetData.ABYSSAL_ORPHAN;
	}


	@ConfigItem(
			keyName = "filter",
			name = "Filter",
			description = "Filter",
			hidden = true
	)
	default boolean filter()
	{
		return true;
	}


	@ConfigItem(
			keyName = "showPets",
			name = "Show Pets",
			description = "Show Pets",
			hidden = true
	)
	default boolean showPetList()
	{
		return true;
	}


	@ConfigItem(
			keyName = "favorites",
			name = "Favorites",
			description = "favorites",
			hidden = true
	)
	default String favorites()
	{
		return "ABYSSAL_ORPHAN,HELLPUPPY,TANGLEROOT,OLMLET";
	}

	@ConfigItem(
			keyName = "customNpcIds",
			name = "Custom NPC IDs",
			description = "Saved custom companion NPC IDs",
			hidden = true
	)
	default String customNpcIds()
	{
		return "";
	}

	@ConfigItem(
			keyName = "selectedCustomNpcId",
			name = "Selected custom NPC ID",
			description = "Currently selected custom companion, or -1 for a built-in pet",
			hidden = true
	)
	default int selectedCustomNpcId()
	{
		return -1;
	}


	@ConfigItem(
			keyName = "meleeThrall",
			name = "meleeThrall",
			description = "meleeThrall",
			hidden = true
	)
	default PetData meleeThrall()
	{
		return PetData.SNAKELING_RED;
	}


	@ConfigItem(
			keyName = "rangeThrall",
			name = "rangeThrall",
			description = "rangeThrall",
			hidden = true
	)
	default PetData rangeThrall()
	{
		return PetData.SNAKELING_GREEN;
	}


	@ConfigItem(
			keyName = "mageThrall",
			name = "mageThrall",
			description = "mageThrall",
			hidden = true
	)
	default PetData mageThrall()
	{
		return PetData.SNAKELING_BLUE;
	}


	@ConfigItem(
			keyName = "showThralls",
			name = "showThralls",
			description = "showThralls",
			hidden = true
	)
	default boolean showThralls()
	{
		return true;
	}


	@ConfigItem(
			keyName = "companionThralls",
			name = "companionThralls",
			description = "companionThralls",
			hidden = true
	)
	default boolean companionThralls()
	{
		return true;
	}


	@Range(min = 1, max = 5)
	@ConfigItem(
			keyName = "followDistance",
			name = "Follow distance",
			description = "How many tiles behind you the companion should follow",
			position = 0
	)
	default int followDistance()
	{
		return 2;
	}

	@Range(min = 6, max = 20)
	@ConfigItem(
			keyName = "catchUpDistance",
			name = "Catch-up distance",
			description = "Teleport the companion back when it falls this many tiles behind",
			position = 1
	)
	default int catchUpDistance()
	{
		return 12;
	}

	@Range(min = 100, max = 200)
	@ConfigItem(
			keyName = "followSpeedPercent",
			name = "Follow speed",
			description = "Companion follow speed as a percentage (changes in 1% steps)",
			position = 2
	)
	default int followSpeedPercent()
	{
		return 125;
	}

	@ConfigItem(
			keyName = "fleeDuringCombat",
			name = "Run away in combat",
			description = "Makes the companion run to a safe distance once combat damage begins",
			position = 3
	)
	default boolean fleeDuringCombat()
	{
		return true;
	}

	@Range(min = 3, max = 8)
	@ConfigItem(
			keyName = "combatFleeDistance",
			name = "Combat flee distance",
			description = "How many tiles away the companion tries to stay during combat",
			position = 4
	)
	default int combatFleeDistance()
	{
		return 5;
	}

	@Range(min = 24, max = 96)
	@ConfigItem(
			keyName = "buttonSize",
			name = "Summon button size",
			description = "Size of the movable summon and dismiss button in pixels",
			position = 5
	)
	default int buttonSize()
	{
		return 44;
	}

	@ConfigItem(
			keyName = "disableWhistle",
			name = "Disable Fake Follower",
			description = "Disables the ability to spawn a fake follower by clicking the call pet whistle.",
			hidden = false,
			position = 6
	)
	default boolean disableWhistle()
	{
		return false;
	}


	@ConfigItem(
			keyName = "debug",
			name = "Enable Debug",
			description = "Enables Debug.",
			hidden = false
	)
	default boolean debug()
	{
		return false;
	}

	@ConfigItem(
			keyName = "allowBrokenPets",
			name = "Enable Broken Pets",
			description = "These pets do not scale correctly and are awaiting a RL update to fix.",
			hidden = true
	)
	default boolean allowBrokenPets()
	{
		return false;
	}

	@Range(min = 50, max = 200)
	@ConfigItem(
			keyName = "sizePercent",
			name = "Pet size",
			description = "Pet size as a percentage (changes in 1% steps)",
			hidden = false,
			position = 7
	)
	default int sizePercent()
	{
		return 100;
	}

}
