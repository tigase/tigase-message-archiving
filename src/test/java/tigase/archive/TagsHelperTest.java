/*
 * TagsHelperTest.java
 *
 * Tigase Message Archiving Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.archive;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;
import tigase.xml.Element;

/**
 *
 * @author andrzej
 */
public class TagsHelperTest {
	
	public TagsHelperTest() {
	}

	/**
	 * Test of extractTags method, of class TagsHelper.
	 */
	@Test
	public void testExtractTags_Element() {
		Set<String> result = TagsHelper.extractTags(null);
		assertTrue("Found tags in empty message 1", result.isEmpty());
		Element msg = new Element("message");
		result = TagsHelper.extractTags(null);
		assertTrue("Found tags in empty message 2", result.isEmpty());
		msg.addChild(new Element("body", "some data here"));
		assertTrue("Found tags in empty message 3", result.isEmpty());
		msg = new Element("message");
		String body = "Example message about #Tigase with @User1";
		Set<String> expResult = new HashSet<String>();
		expResult.add("#Tigase");
		expResult.add("@User1");		
		msg.addChild(new Element("body", body));
		result = TagsHelper.extractTags(msg);
		assertEquals(expResult, result);
	}

	/**
	 * Test of extractTags method, of class TagsHelper.
	 */
	@Test
	public void testExtractTags_Set_String() {
		Set<String> tags = new HashSet<String>();
		Set<String> result = TagsHelper.extractTags(tags, "no tags in string");
		assertTrue("Found tags in body with no tags", result.isEmpty());
		String body = "Example message about #Tigase with @User1";
		Set<String> expResult = new HashSet<String>();
		expResult.add("#Tigase");
		expResult.add("@User1");
		result = TagsHelper.extractTags(tags, body);
		assertEquals("found other tags than expected", expResult, result);
	}

	/**
	 * Test of matches method, of class TagsHelper.
	 */
	@Test
	public void testMatches() {
		String part = "";
		boolean expResult = false;
		boolean result = TagsHelper.matches(part);
		assertEquals(expResult, result);

		part = "test";
		expResult = false;
		result = TagsHelper.matches(part);
		assertEquals(expResult, result);

		part = "@test";
		expResult = true;
		result = TagsHelper.matches(part);
		assertEquals(expResult, result);
		
		part = "#test";
		expResult = true;
		result = TagsHelper.matches(part);
		assertEquals(expResult, result);		
	}

	/**
	 * Test of process method, of class TagsHelper.
	 */
	@Test
	public void testProcess() {
		String tag = "@test";
		String expResult = "@test";
		String result = TagsHelper.process(tag);
		assertEquals(expResult, result);

		tag = "@test..";
		expResult = "@test";
		result = TagsHelper.process(tag);
		assertEquals(expResult, result);
	
	}
	
}
