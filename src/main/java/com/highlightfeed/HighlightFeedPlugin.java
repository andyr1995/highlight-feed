package com.highlightfeed;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@PluginDescriptor(
	name = "Highlight Feed",
	description = "Highlights NPCs, ground items and objects named in a list served on your own computer",
	tags = {"highlight", "npc", "object", "ground items", "planner", "local"}
)
public class HighlightFeedPlugin extends Plugin
{
	private static final long OFFLINE_CLEAR_MS = 60_000;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private HighlightFeedConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private HighlightFeedOverlay overlay;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	/** The current list; replaced as a whole when the feed changes (read by the overlay every frame). */
	@Getter
	private volatile HighlightList list = HighlightList.EMPTY;

	/** Every NPC in the scene; filtered by name when drawing (names can change, e.g. after a transformation). */
	@Getter
	private final Set<NPC> npcs = new HashSet<>();

	/** Objects in the scene whose name is on the list, with that name (looked up once, at spawn). */
	@Getter
	private final Map<TileObject, String> objects = new HashMap<>();

	/** Ground items in the scene whose name is on the list. */
	@Getter
	private final List<GroundEntry> items = new ArrayList<>();

	private ScheduledFuture<?> pollTask;
	private volatile long lastSuccess;
	private volatile boolean inFlight;

	@AllArgsConstructor
	@Getter
	static class GroundEntry
	{
		private final Tile tile;
		private final TileItem item;
		private final String name;
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		lastSuccess = System.currentTimeMillis();
		schedulePoll();
		clientThread.invoke(this::rescanScene);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		if (pollTask != null)
		{
			pollTask.cancel(true);
			pollTask = null;
		}
		list = HighlightList.EMPTY;
		npcs.clear();
		objects.clear();
		items.clear();
	}

	@Provides
	HighlightFeedConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HighlightFeedConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (HighlightFeedConfig.GROUP.equals(e.getGroup()) && ("pollSeconds".equals(e.getKey()) || "feedUrl".equals(e.getKey())))
		{
			if (pollTask != null)
			{
				pollTask.cancel(false);
			}
			schedulePoll();
		}
	}

	// ---------- Reading the list ----------

	private void schedulePoll()
	{
		pollTask = executor.scheduleWithFixedDelay(this::poll, 0, config.pollSeconds(), TimeUnit.SECONDS);
	}

	private void poll()
	{
		String url = config.feedUrl();
		if (!HighlightList.isLocalUrl(url))
		{
			log.debug("Highlight Feed: ignoring non-local URL {}", url);
			offline();
			return;
		}
		if (inFlight)
		{
			return;
		}
		inFlight = true;
		Request request = new Request.Builder().url(url.trim()).get().build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException ex)
			{
				inFlight = false;
				log.debug("Highlight Feed: feed unreachable", ex);
				offline();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						offline();
						return;
					}
					HighlightList next = HighlightList.fromWire(gson.fromJson(body.charStream(), HighlightList.Wire.class));
					lastSuccess = System.currentTimeMillis();
					apply(next);
				}
				catch (Exception ex)
				{
					log.debug("Highlight Feed: could not read the list", ex);
					offline();
				}
				finally
				{
					inFlight = false;
				}
			}
		});
	}

	private void offline()
	{
		if (config.clearWhenOffline() && System.currentTimeMillis() - lastSuccess > OFFLINE_CLEAR_MS)
		{
			apply(HighlightList.EMPTY);
		}
	}

	private void apply(HighlightList next)
	{
		if (next.equals(list))
		{
			return;
		}
		list = next;
		clientThread.invoke(this::rescanScene);   // objects and ground items are matched by name when they spawn
	}

	// ---------- Tracking what's in the scene (client thread) ----------

	private void rescanScene()
	{
		objects.clear();
		items.clear();
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		npcs.clear();
		for (NPC npc : client.getTopLevelWorldView().npcs())
		{
			npcs.add(npc);
		}
		HighlightList l = list;
		if (l.getObjects().isEmpty() && l.getItems().isEmpty())
		{
			return;
		}
		Scene scene = client.getTopLevelWorldView().getScene();
		for (Tile[][] plane : scene.getTiles())
		{
			for (Tile[] row : plane)
			{
				for (Tile tile : row)
				{
					if (tile == null)
					{
						continue;
					}
					if (!l.getObjects().isEmpty())
					{
						for (GameObject go : tile.getGameObjects())
						{
							addObject(go);
						}
						addObject(tile.getWallObject());
						addObject(tile.getDecorativeObject());
						addObject(tile.getGroundObject());
					}
					List<TileItem> ground = tile.getGroundItems();
					if (ground != null && !l.getItems().isEmpty())
					{
						for (TileItem item : ground)
						{
							addItem(tile, item);
						}
					}
				}
			}
		}
	}

	private void addObject(TileObject o)
	{
		if (o == null || list.getObjects().isEmpty())
		{
			return;
		}
		String name = objectName(o);
		if (list.getObjects().containsKey(HighlightList.normalise(name)))
		{
			objects.put(o, name);
		}
	}

	String objectName(TileObject o)
	{
		ObjectComposition comp = client.getObjectDefinition(o.getId());
		if (comp != null && comp.getImpostorIds() != null)
		{
			ObjectComposition imp = comp.getImpostor();
			if (imp != null)
			{
				comp = imp;
			}
		}
		return comp == null ? null : comp.getName();
	}

	private void addItem(Tile tile, TileItem item)
	{
		if (item == null || list.getItems().isEmpty())
		{
			return;
		}
		String name = itemManager.getItemComposition(item.getId()).getName();
		if (list.getItems().containsKey(HighlightList.normalise(name)))
		{
			items.add(new GroundEntry(tile, item, name));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOADING || e.getGameState() == GameState.LOGIN_SCREEN || e.getGameState() == GameState.HOPPING)
		{
			npcs.clear();
			objects.clear();
			items.clear();
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		npcs.add(e.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		npcs.remove(e.getNpc());
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		addObject(e.getGameObject());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned e)
	{
		objects.remove(e.getGameObject());
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned e)
	{
		addObject(e.getWallObject());
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned e)
	{
		objects.remove(e.getWallObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned e)
	{
		addObject(e.getDecorativeObject());
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned e)
	{
		objects.remove(e.getDecorativeObject());
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned e)
	{
		addObject(e.getGroundObject());
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned e)
	{
		objects.remove(e.getGroundObject());
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned e)
	{
		addItem(e.getTile(), e.getItem());
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned e)
	{
		items.removeIf(g -> g.getItem() == e.getItem());
	}

	/** For the overlay: a stable copy, since spawn events can change the map between frames. */
	List<Map.Entry<TileObject, String>> objectSnapshot()
	{
		return objects.isEmpty() ? Collections.emptyList() : new ArrayList<>(objects.entrySet());
	}
}
