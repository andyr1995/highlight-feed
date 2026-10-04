package com.highlightfeed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.awt.Color;
import org.junit.Test;

public class HighlightListTest
{
	private final Gson gson = new Gson();

	private HighlightList parse(String json)
	{
		return HighlightList.fromWire(gson.fromJson(json, HighlightList.Wire.class));
	}

	@Test
	public void parsesObjectsAndPlainStrings()
	{
		HighlightList l = parse("{\"npcs\":[{\"name\":\"Imp\",\"colour\":\"#00FF00\"},\"Rat\"],"
			+ "\"items\":[\"Black bead\"],\"objects\":[{\"name\":\"Oak tree\"}]}");
		assertEquals(new Color(0x00FF00), l.getNpcs().get("imp"));
		assertTrue(l.getNpcs().containsKey("rat"));
		assertNull(l.getNpcs().get("rat"));
		assertTrue(l.getItems().containsKey("black bead"));
		assertTrue(l.getObjects().containsKey("oak tree"));
		assertFalse(l.isEmpty());
	}

	@Test
	public void emptyAndMissingListsAreEmpty()
	{
		assertTrue(parse("{}").isEmpty());
		assertTrue(parse("{\"npcs\":[],\"items\":null}").isEmpty());
		assertTrue(HighlightList.fromWire(null).isEmpty());
	}

	@Test
	public void namesAreNormalised()
	{
		assertEquals("imp", HighlightList.normalise("  <col=ff0000>Imp</col> "));
		assertEquals("", HighlightList.normalise(null));
	}

	@Test
	public void coloursWithAndWithoutAlpha()
	{
		assertEquals(new Color(0xFF0000), HighlightList.parseColour("#FF0000"));
		assertEquals(new Color(0x80FF0000, true), HighlightList.parseColour("80FF0000"));
		assertNull(HighlightList.parseColour("red"));
	}

	@Test
	public void onlyLocalUrlsAreAllowed()
	{
		assertTrue(HighlightList.isLocalUrl("http://127.0.0.1:8765/highlights"));
		assertTrue(HighlightList.isLocalUrl("http://localhost:8080/list"));
		assertFalse(HighlightList.isLocalUrl("https://example.com/highlights"));
		assertFalse(HighlightList.isLocalUrl("http://127.0.0.1.example.com/x"));
		assertFalse(HighlightList.isLocalUrl("file:///C:/list.json"));
		assertFalse(HighlightList.isLocalUrl("not a url"));
	}

	@Test
	public void sameListsAreEqualSoNothingRescans()
	{
		String json = "{\"npcs\":[\"Imp\"],\"items\":[\"Black bead\"]}";
		assertEquals(parse(json), parse(json));
	}
}
