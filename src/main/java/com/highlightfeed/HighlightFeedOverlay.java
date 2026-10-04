package com.highlightfeed;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

class HighlightFeedOverlay extends Overlay
{
	private final Client client;
	private final HighlightFeedPlugin plugin;
	private final HighlightFeedConfig config;
	private final ModelOutlineRenderer outlines;

	@Inject
	HighlightFeedOverlay(Client client, HighlightFeedPlugin plugin, HighlightFeedConfig config, ModelOutlineRenderer outlines)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		this.outlines = outlines;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		HighlightList list = plugin.getList();
		if (list.isEmpty())
		{
			return null;
		}
		int width = config.outlineWidth();
		int plane = client.getTopLevelWorldView().getPlane();

		if (!list.getNpcs().isEmpty())
		{
			for (NPC npc : plugin.getNpcs())
			{
				String name = npc.getName();
				String key = HighlightList.normalise(name);
				if (!list.getNpcs().containsKey(key))
				{
					continue;
				}
				Color c = colour(list.getNpcs().get(key), config.npcColour());
				outlines.drawOutline(npc, width, c, 0);
				if (config.showNames())
				{
					Point at = npc.getCanvasTextLocation(g, name, npc.getLogicalHeight() + 40);
					if (at != null)
					{
						OverlayUtil.renderTextLocation(g, at, name, c);
					}
				}
			}
		}

		for (java.util.Map.Entry<TileObject, String> entry : plugin.objectSnapshot())
		{
			TileObject o = entry.getKey();
			if (o.getPlane() != plane)
			{
				continue;
			}
			String name = entry.getValue();
			Color c = colour(list.getObjects().get(HighlightList.normalise(name)), config.objectColour());
			if (o instanceof GameObject)
			{
				outlines.drawOutline((GameObject) o, width, c, 0);
			}
			else if (o instanceof WallObject)
			{
				outlines.drawOutline((WallObject) o, width, c, 0);
			}
			else if (o instanceof DecorativeObject)
			{
				outlines.drawOutline((DecorativeObject) o, width, c, 0);
			}
			else if (o instanceof GroundObject)
			{
				outlines.drawOutline((GroundObject) o, width, c, 0);
			}
			if (config.showNames() && name != null)
			{
				Point at = o.getCanvasTextLocation(g, name, 60);
				if (at != null)
				{
					OverlayUtil.renderTextLocation(g, at, name, c);
				}
			}
		}

		for (HighlightFeedPlugin.GroundEntry e : plugin.getItems())
		{
			if (e.getTile().getPlane() != plane)
			{
				continue;
			}
			LocalPoint lp = e.getTile().getLocalLocation();
			if (lp == null)
			{
				continue;
			}
			Color c = colour(list.getItems().get(HighlightList.normalise(e.getName())), config.itemColour());
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly != null)
			{
				OverlayUtil.renderPolygon(g, poly, c);
			}
			Point at = Perspective.getCanvasTextLocation(client, g, lp, e.getName(), 16);
			if (at != null)
			{
				OverlayUtil.renderTextLocation(g, at, e.getName(), c);
			}
		}
		return null;
	}

	private static Color colour(Color fromList, Color fallback)
	{
		return fromList != null ? fromList : fallback;
	}
}
