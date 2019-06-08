/**
 * Tigase Message Archiving Component - Implementation of Message Archiving component for Tigase XMPP Server.
 * Copyright (C) 2012 Tigase, Inc. (office@tigase.com)
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
package tigase.archive.processors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import tigase.archive.Settings;
import tigase.archive.StoreMethod;
import tigase.kernel.core.Kernel;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.ProcessorTestCase;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Created by andrzej on 23.07.2016.
 */
public class Xep0313MessageArchiveManagementProcessorTest
		extends ProcessorTestCase {

	private Kernel kernel;
	private MessageArchivePlugin maPlugin;
	private Xep0313MessageArchiveManagementProcessor xep0313Processor;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		kernel = new Kernel();
		kernel.registerBean(Xep0313MessageArchiveManagementProcessor.class).setActive(true).exec();

		xep0313Processor = kernel.getInstance(Xep0313MessageArchiveManagementProcessor.class);
		maPlugin = kernel.getInstance(MessageArchivePlugin.class);
		assertNotNull(maPlugin);
		maPlugin.init(new HashMap<>());
	}

	@After
	@Override
	public void tearDown() throws Exception {
		xep0313Processor = null;
		maPlugin = null;
		kernel = null;
		super.tearDown();
	}

	@Test
	public void testChangingPreferencesToAlways() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Settings settings = maPlugin.getSettings(session1);

		assertFalse("Archiving should be disabled by default", settings.isAutoArchivingEnabled());

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element prefs = new Element("prefs");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		prefs.setAttribute("default", "always");
		packetEl.addChild(prefs);

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		Packet result = results.poll();
		assertEquals("always", result.getAttributeStaticStr(new String[]{"iq", "prefs"}, "default"));

		assertTrue("Message archiving should be enabled", settings.isAutoArchivingEnabled());
	}

	@Test
	public void testChangingPreferencesToRoster() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Settings settings = maPlugin.getSettings(session1);

		assertFalse("Archiving should be disabled by default", settings.isAutoArchivingEnabled());

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element prefs = new Element("prefs");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		prefs.setAttribute("default", "roster");
		packetEl.addChild(prefs);

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		Packet result = results.poll();
		assertEquals("roster", result.getAttributeStaticStr(new String[]{"iq", "prefs"}, "default"));

		assertTrue("Message archiving should be enabled", settings.isAutoArchivingEnabled());
		assertTrue("Filtering messages by roster contact should be enabled", settings.archiveOnlyForContactsInRoster());
	}

	@Test
	public void testChangingPreferencesToNever() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Settings settings = maPlugin.getSettings(session1);

		assertFalse("Archiving should be disabled by default", settings.isAutoArchivingEnabled());

		settings.setAuto(true);

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element prefs = new Element("prefs");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		prefs.setAttribute("default", "never");
		packetEl.addChild(prefs);

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		Packet result = results.poll();
		assertEquals("never", result.getAttributeStaticStr(new String[]{"iq", "prefs"}, "default"));

		assertFalse("Message archiving should be disabled", settings.isAutoArchivingEnabled());
	}

	@Test
	public void testChangingPreferencesToRosterWithRequiredStoreMethodMessage() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Field f = MessageArchivePlugin.class.getDeclaredField("globalRequiredStoreMethod");
		f.setAccessible(true);
		f.set(maPlugin, StoreMethod.Message);

		Settings settings = maPlugin.getSettings(session1);

		assertTrue("Archiving should be enabled due to store method", settings.isAutoArchivingEnabled());

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element prefs = new Element("prefs");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		prefs.setAttribute("default", "roster");
		packetEl.addChild(prefs);

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		Packet result = results.poll();
		assertEquals(StanzaType.error, result.getType());

		assertTrue("Message archiving should be enabled", settings.isAutoArchivingEnabled());
		assertFalse(settings.archiveOnlyForContactsInRoster());
	}

	@Test
	public void testChangingPreferencesToNeverWithRequiredStoreMethodMessage() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Field f = MessageArchivePlugin.class.getDeclaredField("globalRequiredStoreMethod");
		f.setAccessible(true);
		f.set(maPlugin, StoreMethod.Message);

		Settings settings = maPlugin.getSettings(session1);

		assertTrue("Archiving should be enabled due to store method", settings.isAutoArchivingEnabled());

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element prefs = new Element("prefs");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		prefs.setAttribute("default", "never");
		packetEl.addChild(prefs);

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		Packet result = results.poll();
		assertEquals(StanzaType.error, result.getType());

		assertTrue("Message archiving should be enabled", settings.isAutoArchivingEnabled());
		assertFalse(settings.archiveOnlyForContactsInRoster());
	}

	@Test
	public void testForwardingQueriesToComponent() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element prefs = new Element("list");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		packetEl.addChild(prefs);

		Packet packet = Packet.packetInstance(packetEl);
		packet.setPacketFrom(session1.getConnectionId());

		Queue<Packet> results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		Packet result = results.poll();
		assertEquals(StanzaType.set, result.getType());

		assertEquals(maPlugin.getComponentJid(), result.getPacketTo());

		packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		prefs = new Element("retrieve");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		packetEl.addChild(prefs);

		packet = Packet.packetInstance(packetEl);
		packet.setPacketFrom(session1.getConnectionId());

		results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		result = results.poll();
		assertEquals(StanzaType.set, result.getType());

		assertEquals(maPlugin.getComponentJid(), result.getPacketTo());

		packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		prefs = new Element("remove");
		prefs.setAttribute("xmlns", "urn:xmpp:mam:1");
		packetEl.addChild(prefs);

		packet = Packet.packetInstance(packetEl);
		packet.setPacketFrom(session1.getConnectionId());

		results = new ArrayDeque<>();
		xep0313Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		result = results.poll();
		assertEquals(StanzaType.set, result.getType());

		assertEquals(maPlugin.getComponentJid(), result.getPacketTo());

	}

}
