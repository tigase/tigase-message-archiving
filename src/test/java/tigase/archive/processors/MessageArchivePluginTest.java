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
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.archive.Settings;
import tigase.kernel.core.Kernel;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.impl.ProcessorTestCase;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * @author andrzej
 */
public class MessageArchivePluginTest
		extends ProcessorTestCase {

	private static final Logger log = Logger.getLogger(MessageArchivePluginTest.class.getCanonicalName());
	private Kernel kernel;
	private MessageArchivePlugin messageArchivePlugin;

	@Before
	public void setUp() throws Exception {
		super.setUp();

		kernel = new Kernel();
		kernel.registerBean(MessageArchivePlugin.class).setActive(true).exec();

		messageArchivePlugin = kernel.getInstance(MessageArchivePlugin.class);
		messageArchivePlugin.init(new HashMap<String, Object>());
	}

	@After
	public void tearDown() throws Exception {
		messageArchivePlugin = null;
		super.tearDown();
	}

	@Test
	public void testXEP0334_MessageHints() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);
		Settings settings = new Settings();
		session1.putCommonSessionData("message-archive/settings", settings);
		Queue<Packet> results = new ArrayDeque<Packet>();

		settings.setAuto(true);

		Packet packet = Packet.packetInstance(
				new Element("message", new Element[]{new Element("body", "Test message 123")},
							new String[]{"from", "to"}, new String[]{"from@example.com/res1", "to@example.com/res2"}));

		messageArchivePlugin.process(packet, session1, null, results, null);
		Assert.assertFalse("should sent packet " + packet + " for storage", results.isEmpty());

		results.clear();
		packet = Packet.packetInstance(new Element("message", new Element[]{new Element("body", "Test message 123"),
																			new Element("no-store",
																						new String[]{"xmlns"},
																						new String[]{
																								"urn:xmpp:hints"})},
												   new String[]{"from", "to"},
												   new String[]{"from@example.com/res1", "to@example.com/res2"}));

		messageArchivePlugin.process(packet, session1, null, results, null);
		Assert.assertTrue("should not sent packet " + packet + " for storage", results.isEmpty());

		results.clear();
		packet = Packet.packetInstance(new Element("message", new Element[]{new Element("body", "Test message 123"),
																			new Element("no-permanent-store",
																						new String[]{"xmlns"},
																						new String[]{
																								"urn:xmpp:hints"})},
												   new String[]{"from", "to"},
												   new String[]{"from@example.com/res1", "to@example.com/res2"}));

		messageArchivePlugin.process(packet, session1, null, results, null);
		Assert.assertTrue("should not sent packet " + packet + " for storage", results.isEmpty());

	}

	@Test
	public void testXEP0313_IngoringArchivizationMessagesRetrievedFromRepository() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()),
													 res1);
		Settings settings = new Settings();
		session1.putCommonSessionData("message-archive/settings", settings);
		Queue<Packet> results = new ArrayDeque<Packet>();

		settings.setAuto(true);

		Packet packet = Packet.packetInstance(new Element("message", new Element[]{new Element("result", new Element[]{
				new Element("forwarded", new Element[]{
						new Element("message", new Element[]{new Element("body", "Test body messages")},
									new String[]{"from", "to"},
									new String[]{"user2@example.com", "user1@example.com"})}, new String[]{"xmlns"},
							new String[]{"urn:xmpp:forward:0"})}, new String[]{"xmlns", "queryid", "id"}, new String[]{
				"urn:xmpp:mam:1", "g28", "28428-20978-43925"})}, new String[]{"from", "to"},
														  new String[]{"from@example.com/res1",
																	   "to@example.com/res2"}));

		messageArchivePlugin.process(packet, session1, null, results, null);
		Assert.assertTrue("should not sent packet " + packet + " for storage", results.isEmpty());
	}

}
