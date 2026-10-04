package com.highlightfeed;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(HighlightFeedConfig.GROUP)
public interface HighlightFeedConfig extends Config
{
	String GROUP = "highlightfeed";

	@ConfigSection(
		name = "Feed",
		description = "Where the highlight list comes from",
		position = 0
	)
	String feedSection = "feed";

	@ConfigSection(
		name = "Colours",
		description = "How highlighted things are drawn",
		position = 1
	)
	String colourSection = "colours";

	@ConfigItem(
		keyName = "feedUrl",
		name = "Feed URL",
		description = "Address of the highlight list on this computer. Only localhost / 127.0.0.1 addresses are accepted.",
		section = feedSection,
		position = 0
	)
	default String feedUrl()
	{
		return "http://127.0.0.1:8765/highlights";
	}

	@Range(min = 1, max = 60)
	@ConfigItem(
		keyName = "pollSeconds",
		name = "Refresh every (seconds)",
		description = "How often to re-read the list",
		section = feedSection,
		position = 1
	)
	default int pollSeconds()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "clearWhenOffline",
		name = "Clear when the feed is offline",
		description = "Remove highlights if the list can't be read for a minute",
		section = feedSection,
		position = 2
	)
	default boolean clearWhenOffline()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "npcColour",
		name = "NPC outline",
		description = "Outline colour for highlighted NPCs (used when the list gives no colour)",
		section = colourSection,
		position = 0
	)
	default Color npcColour()
	{
		return Color.RED;
	}

	@Alpha
	@ConfigItem(
		keyName = "objectColour",
		name = "Object outline",
		description = "Outline colour for highlighted objects (used when the list gives no colour)",
		section = colourSection,
		position = 1
	)
	default Color objectColour()
	{
		return Color.RED;
	}

	@Alpha
	@ConfigItem(
		keyName = "itemColour",
		name = "Ground item",
		description = "Tile and label colour for highlighted ground items (used when the list gives no colour)",
		section = colourSection,
		position = 2
	)
	default Color itemColour()
	{
		return Color.RED;
	}

	@Range(min = 1, max = 8)
	@ConfigItem(
		keyName = "outlineWidth",
		name = "Outline width",
		description = "Thickness of NPC and object outlines",
		section = colourSection,
		position = 3
	)
	default int outlineWidth()
	{
		return 3;
	}

	@ConfigItem(
		keyName = "showNames",
		name = "Show names",
		description = "Draw the name above highlighted NPCs and objects",
		section = colourSection,
		position = 4
	)
	default boolean showNames()
	{
		return true;
	}
}
