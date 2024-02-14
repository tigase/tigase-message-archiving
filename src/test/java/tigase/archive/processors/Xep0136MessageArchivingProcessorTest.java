/*
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
import tigase.archive.MessageArchiveVHostItemExtension;
import tigase.archive.Settings;
import tigase.archive.StoreMethod;
import tigase.eventbus.EventBusFactory;
import tigase.kernel.core.Kernel;
import tigase.server.Packet;
import tigase.vhosts.VHostItemExtension;
import tigase.vhosts.VHostItemImpl;
import tigase.xml.Element;
import tigase.xmpp.StanzaType;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.MessageDeliveryLogic;
import tigase.xmpp.impl.ProcessorTestCase;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Created by andrzej on 23.07.2016.
 */
public class Xep0136MessageArchivingProcessorTest
		extends ProcessorTestCase {

	private MessageArchivePlugin maPlugin;
	private Xep0136MessageArchivingProcessor xep0136Processor;

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		xep0136Processor = getInstance(Xep0136MessageArchivingProcessor.class);
		maPlugin = getInstance(MessageArchivePlugin.class);
		maPlugin.init(new HashMap<>());
	}

	@After
	@Override
	public void tearDown() throws Exception {
		xep0136Processor = null;
		maPlugin = null;
		super.tearDown();
	}

	@Override
	protected void registerBeans(Kernel kernel) {
		super.registerBeans(kernel);
		kernel.registerBean("eventBus").asInstance(EventBusFactory.getInstance()).setActive(true).exportable().exec();
		kernel.registerBean(MessageDeliveryLogic.class).exec();
		kernel.registerBean(MessageArchivePlugin.class).setActive(true).exec();
		kernel.registerBean(Xep0136MessageArchivingProcessor.class).setActive(true).exec();
	}

	@Test
	public void testChangingPreferencesEnablingArchiving() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Field f = VHostItemImpl.class.getDeclaredField("extensions");
		f.setAccessible(true);
		Map<Class<? extends VHostItemExtension>, VHostItemExtension> extensions = (Map<Class<? extends VHostItemExtension>, VHostItemExtension>) f.get(session1.getDomain());

		extensions.put(MessageArchiveVHostItemExtension.class,
					  MessageArchiveVHostItemExtension.class.newInstance());

		Settings settings = maPlugin.getSettings(session1.getBareJID(), session1);

		assertFalse(settings.isAutoArchivingEnabled());
		assertEquals(StoreMethod.Message, settings.getStoreMethod());

		settings.setArchiveOnlyForContactsInRoster(true);

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element pref = new Element("pref");
		pref.setAttribute("xmlns", "urn:xmpp:archive");
		packetEl.addChild(pref);
		pref.addChild(new Element("auto", new String[]{"save"}, new String[]{"true"}));

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		Packet result = null;
		xep0136Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());
		result = results.poll();
		assertEquals(StanzaType.result, result.getType());

		assertTrue(settings.isAutoArchivingEnabled());
		assertFalse(settings.archiveOnlyForContactsInRoster());
	}

	@Test
	public void testChangingPreferencesDisablingArchiving() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Settings settings = maPlugin.getSettings(session1.getBareJID(), session1);

		assertFalse(settings.isAutoArchivingEnabled());
		assertEquals(StoreMethod.Message, settings.getStoreMethod());

		settings.setAuto(true);
		settings.setArchiveOnlyForContactsInRoster(true);

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element pref = new Element("pref");
		pref.setAttribute("xmlns", "urn:xmpp:archive");
		packetEl.addChild(pref);
		pref.addChild(new Element("auto", new String[]{"save"}, new String[]{"false"}));

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		Packet result = null;
		xep0136Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());
		result = results.poll();
		assertEquals(StanzaType.result, result.getType());

		assertFalse(settings.isAutoArchivingEnabled());
		assertFalse(settings.archiveOnlyForContactsInRoster());
	}

	@Test
	public void testChangingPreferencesDisablingArchivingWithRequiredStoreMethodMessage() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Field f = MessageArchivePlugin.class.getDeclaredField("globalRequiredStoreMethod");
		f.setAccessible(true);
		f.set(maPlugin, StoreMethod.Message);

		Settings settings = maPlugin.getSettings(session1.getBareJID(), session1);

		assertTrue(settings.isAutoArchivingEnabled());
		assertEquals(StoreMethod.Message, settings.getStoreMethod());

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element pref = new Element("pref");
		pref.setAttribute("xmlns", "urn:xmpp:archive");
		packetEl.addChild(pref);
		pref.addChild(new Element("auto", new String[]{"save"}, new String[]{"false"}));

		Packet packet = Packet.packetInstance(packetEl);
		packet.setPacketFrom(JID.jidInstance("c2s@hostname/id"));

		Queue<Packet> results = new ArrayDeque<>();
		Packet result = null;
		xep0136Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());
		result = results.poll();
		assertEquals(StanzaType.error, result.getType());

		assertTrue(settings.isAutoArchivingEnabled());
		assertFalse(settings.archiveOnlyForContactsInRoster());
	}

	@Test
	public void testChangingPreferencesChangingStoreMethod() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Settings settings = maPlugin.getSettings(session1.getBareJID(), session1);

		assertFalse(settings.isAutoArchivingEnabled());
		assertEquals(StoreMethod.Message, settings.getStoreMethod());

		settings.setAuto(true);

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element pref = new Element("pref");
		pref.setAttribute("xmlns", "urn:xmpp:archive");
		packetEl.addChild(pref);
		pref.addChild(new Element("default", new String[]{"save"}, new String[]{"message"}));

		Packet packet = Packet.packetInstance(packetEl);

		Queue<Packet> results = new ArrayDeque<>();
		Packet result = null;
		xep0136Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());
		result = results.poll();
		assertEquals(StanzaType.result, result.getType());

		assertTrue(settings.isAutoArchivingEnabled());
		assertEquals(StoreMethod.Message, settings.getStoreMethod());

	}

	@Test
	public void testChangingPreferencesChangingStoreMethodWithRequiredStoreMethodMessage() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);

		Field f = MessageArchivePlugin.class.getDeclaredField("globalRequiredStoreMethod");
		f.setAccessible(true);
		f.set(maPlugin, StoreMethod.Message);

		Settings settings = maPlugin.getSettings(session1.getBareJID(), session1);

		assertTrue(settings.isAutoArchivingEnabled());
		assertEquals(StoreMethod.Message, settings.getStoreMethod());

		settings.setAuto(true);

		Element packetEl = new Element("iq");
		packetEl.setAttribute("type", "set");
		packetEl.setAttribute("id", UUID.randomUUID().toString());
		Element pref = new Element("pref");
		pref.setAttribute("xmlns", "urn:xmpp:archive");
		packetEl.addChild(pref);
		pref.addChild(new Element("default", new String[]{"save"}, new String[]{"body"}));

		Packet packet = Packet.packetInstance(packetEl);
		packet.setPacketFrom(JID.jidInstance("c2s@hostname/id"));

		Queue<Packet> results = new ArrayDeque<>();
		Packet result = null;
		xep0136Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());
		result = results.poll();
		assertEquals(StanzaType.error, result.getType());

		assertTrue(settings.isAutoArchivingEnabled());
		assertEquals(StoreMethod.Message, settings.getStoreMethod());

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
		prefs.setAttribute("xmlns", "urn:xmpp:archive");
		packetEl.addChild(prefs);

		Packet packet = Packet.packetInstance(packetEl);
		packet.setPacketFrom(session1.getConnectionId());

		Queue<Packet> results = new ArrayDeque<>();
		xep0136Processor.process(packet, session1, null, results, null);

		assertEquals(1, results.size());

		Packet result = results.poll();
		assertEquals(StanzaType.set, result.getType());

		assertEquals(maPlugin.getComponentJid(), result.getPacketTo());
	}

}
