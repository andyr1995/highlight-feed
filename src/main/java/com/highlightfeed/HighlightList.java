package com.highlightfeed;

import java.awt.Color;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Value;

/**
 * The parsed highlight list: lower-cased name -> colour (null = use the configured colour).
 * <p>
 * Wire format (JSON): {"npcs": [{"name": "Imp", "colour": "#FF0000"}], "items": [...], "objects": [...]}.
 * Plain strings are also accepted: {"npcs": ["Imp"]}.
 */
@Value
public class HighlightList
{
	public static final HighlightList EMPTY = new HighlightList(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

	Map<String, Color> npcs;
	Map<String, Color> items;
	Map<String, Color> objects;

	public boolean isEmpty()
	{
		return npcs.isEmpty() && items.isEmpty() && objects.isEmpty();
	}

	/** Raw JSON shape, filled by Gson. */
	static class Wire
	{
		List<Object> npcs;
		List<Object> items;
		List<Object> objects;
	}

	static HighlightList fromWire(Wire w)
	{
		if (w == null)
		{
			return EMPTY;
		}
		return new HighlightList(toMap(w.npcs), toMap(w.items), toMap(w.objects));
	}

	private static Map<String, Color> toMap(List<Object> entries)
	{
		if (entries == null || entries.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<String, Color> out = new HashMap<>();
		for (Object e : entries)
		{
			String name = null;
			Color colour = null;
			if (e instanceof String)
			{
				name = (String) e;
			}
			else if (e instanceof Map)
			{
				Object n = ((Map<?, ?>) e).get("name");
				Object c = ((Map<?, ?>) e).get("colour");
				name = n instanceof String ? (String) n : null;
				colour = c instanceof String ? parseColour((String) c) : null;
			}
			String key = normalise(name);
			if (!key.isEmpty())
			{
				out.put(key, colour);
			}
		}
		return Collections.unmodifiableMap(out);
	}

	/** Lower-case, trimmed, with RuneLite colour tags (e.g. &lt;col=ff0000&gt;) removed. */
	static String normalise(String name)
	{
		if (name == null)
		{
			return "";
		}
		return name.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
	}

	/** "#RRGGBB" or "#AARRGGBB"; null if it can't be read. */
	static Color parseColour(String s)
	{
		if (s == null)
		{
			return null;
		}
		String hex = s.trim().replace("#", "");
		try
		{
			if (hex.length() == 6)
			{
				return new Color(Integer.parseInt(hex, 16));
			}
			if (hex.length() == 8)
			{
				return new Color((int) Long.parseLong(hex, 16), true);
			}
		}
		catch (NumberFormatException ignored)
		{
			// fall through
		}
		return null;
	}

	/** Only http(s) URLs on this computer are allowed, so the plugin can never talk to an outside server. */
	static boolean isLocalUrl(String url)
	{
		try
		{
			URI u = new URI(url.trim());
			String scheme = u.getScheme();
			String host = u.getHost();
			return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && host != null
				&& ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host) || "::1".equals(host));
		}
		catch (Exception e)
		{
			return false;
		}
	}
}
