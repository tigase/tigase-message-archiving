/*
 * MessageArchivePluginTest.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2015 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */
package tigase.archive;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import tigase.db.AuthRepository;
import tigase.db.MsgRepositoryIfc;
import tigase.db.NonAuthUserRepository;
import tigase.db.UserRepository;
import tigase.db.xml.XMLRepository;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManagerHandler;
import tigase.util.TigaseStringprepException;
import tigase.vhosts.VHostItem;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.NotAuthorizedException;
import tigase.xmpp.XMPPResourceConnection;
import tigase.xmpp.XMPPSession;

/**
 *
 * @author andrzej
 */
public class MessageArchivePluginTest {
	
	private static final Logger log = Logger.getLogger(MessageArchivePluginTest.class.getCanonicalName());
	
	private MessageArchivePlugin messageArchivePlugin;
	private SessionManagerHandlerImpl loginHandler;
	
	@Before
	public void setUp() throws Exception {
		loginHandler = new SessionManagerHandlerImpl();
		messageArchivePlugin = new MessageArchivePlugin();
		messageArchivePlugin.init(new HashMap<String,Object>());
	}
	
	@After
	public void tearDown() throws Exception {
		messageArchivePlugin = null;
		loginHandler = null;
	}		
	
	@Test
	public void testXEP0334_MessageHints() throws Exception {
		BareJID userJid = BareJID.bareJIDInstance("user1@example.com");
		JID res1 = JID.jidInstance(userJid, "res1");
		XMPPResourceConnection session1 = getSession(JID.jidInstance("c2s@example.com/" + UUID.randomUUID().toString()), res1);
		Queue<Packet> results = new ArrayDeque<Packet>();

		messageArchivePlugin.setAutoSave(session1, Boolean.TRUE);
		
		Packet packet = Packet.packetInstance(new Element("message", new Element[]{
			new Element("body", "Test message 123")
		}, new String[] { "from", "to" }, new String[] { "from@example.com/res1", "to@example.com/res2" }));
		
		messageArchivePlugin.process(packet, session1, null, results, null);
		Assert.assertFalse("should sent packet " + packet + " for storage", results.isEmpty());
		
		results.clear();
		packet = Packet.packetInstance(new Element("message", new Element[]{
			new Element("body", "Test message 123"),
			new Element("no-store", new String[] { "xmlns" }, new String[] { "urn:xmpp:hints" })
		}, new String[] { "from", "to" }, new String[] { "from@example.com/res1", "to@example.com/res2" }));
		
		messageArchivePlugin.process(packet, session1, null, results, null);
		Assert.assertTrue("should not sent packet " + packet + " for storage", results.isEmpty());
		
		results.clear();
		packet = Packet.packetInstance(new Element("message", new Element[]{
			new Element("body", "Test message 123"),
			new Element("no-permanent-store", new String[] { "xmlns" }, new String[] { "urn:xmpp:hints" })
		}, new String[] { "from", "to" }, new String[] { "from@example.com/res1", "to@example.com/res2" }));
		
		messageArchivePlugin.process(packet, session1, null, results, null);
		Assert.assertTrue("should not sent packet " + packet + " for storage", results.isEmpty());
		
	}
	
	protected XMPPResourceConnection getSession( JID connId, JID userJid) throws NotAuthorizedException, TigaseStringprepException {

		String xmlRepositoryURI = "memory://xmlRepo?autoCreateUser=true";
		XMLRepository xmlRepository = new XMLRepository();
		xmlRepository.initRepository( xmlRepositoryURI, null );

		XMPPResourceConnection conn = new XMPPResourceConnection( connId, (UserRepository) xmlRepository, (AuthRepository) xmlRepository, loginHandler );
		VHostItem vhost = new VHostItem();
		vhost.setVHost( userJid.getDomain() );
		conn.setDomain( vhost );
		conn.authorizeJID( userJid.getBareJID(), false );
		conn.setResource( userJid.getResource() );

		return conn;
	}
	
	
	private class SessionManagerHandlerImpl implements SessionManagerHandler {

		public SessionManagerHandlerImpl() {
		}
		Map<BareJID, XMPPSession> sessions = new HashMap<BareJID, XMPPSession>();

		@Override
		public JID getComponentId() {
			return JID.jidInstanceNS( "sess-man@localhost" );
		}

		@Override
		public void handleLogin( BareJID userId, XMPPResourceConnection conn ) {
			XMPPSession session = sessions.get( userId );
			if ( session == null ){
				session = new XMPPSession( userId.getLocalpart() );
				sessions.put( userId, session );
			}
			try {
				session.addResourceConnection( conn );
			} catch ( TigaseStringprepException ex ) {
				log.log( Level.SEVERE, null, ex );
			}
		}

		@Override
		public void handleLogout( BareJID userId, XMPPResourceConnection conn ) {
			XMPPSession session = sessions.get( conn );
			if ( session != null ){
				session.removeResourceConnection( conn );
				if ( session.getActiveResourcesSize() == 0 ){
					sessions.remove( userId );
				}
			}
		}

		@Override
		public void handlePresenceSet( XMPPResourceConnection conn ) {
		}

		@Override
		public void handleResourceBind( XMPPResourceConnection conn ) {
		}

		@Override
		public boolean isLocalDomain( String domain, boolean includeComponents ) {
			return !domain.contains( "-ext" );
		}

		@Override
		public void handleDomainChange(String domain, XMPPResourceConnection conn) {
		}
	}
	
}
