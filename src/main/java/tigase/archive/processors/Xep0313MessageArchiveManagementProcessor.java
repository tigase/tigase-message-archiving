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

import tigase.archive.Settings;
import tigase.archive.StoreMethod;
import tigase.archive.StoreMuc;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.annotation.*;
import tigase.xmpp.jid.JID;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tigase.archive.processors.MessageArchivePlugin.ARCHIVE;
import static tigase.archive.processors.Xep0313MessageArchiveManagementProcessor.ID;

/**
 * Created by andrzej on 22.07.2016.
 */
@Id(ID)
@Handles({@Handle(path = {"iq", "query"}, xmlns = ID), @Handle(path = {"iq", "prefs"}, xmlns = ID)})
@DiscoFeatures({ID})
@Bean(name = ID, parent = SessionManager.class, active = true)
public class Xep0313MessageArchiveManagementProcessor
		extends AnnotatedXMPPProcessor
		implements XMPPProcessorIfc {

	public static final String ID = "urn:xmpp:mam:1";
	private static final Logger log = Logger.getLogger(
			Xep0313MessageArchiveManagementProcessor.class.getCanonicalName());
	@Inject
	private MessageArchivePlugin messageArchivePlugin;

	@Override
	public Authorization canHandle(Packet packet, XMPPResourceConnection conn) {
		if (packet.getStanzaTo() == null) {
			return super.canHandle(packet, conn);
		}
		return null;
	}

	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
						Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
		Element prefs = packet.getElement().getChild("prefs", ID);
		if (prefs == null) {
			// for quering archive
			if (!session.getConnectionId().equals(packet.getPacketFrom())) {
				// is this needed for responses? modified version of can handle should take care of this
				JID connId = session.getConnectionId(packet.getStanzaTo());
				Packet result = packet.copyElementOnly();
				result.setPacketTo(connId);
				results.offer(result);

				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "forwarding packet to client connection {0]", packet);
				}

				return;
			} else {
				Packet result = packet.copyElementOnly();
				if (result.getStanzaFrom() == null) {
					result.initVars(session.getJID(), messageArchivePlugin.getComponentJid());
				} else {
					result.setPacketTo(messageArchivePlugin.getComponentJid());
				}
				results.offer(result);
			}
		} else {
			// handling preferences
			switch (packet.getType()) {
				case get:
					retrievePreferences(session, packet, results);
					break;
				case set:
					updatePrefrerences(session, packet, prefs, results);
					break;
				default:
					results.offer(
							Authorization.BAD_REQUEST.getResponseMessage(packet, "Invalid request for XEP-0313", true));
					break;
			}
		}
	}

	private void retrievePreferences(XMPPResourceConnection session, Packet packet, Queue<Packet> results)
			throws NotAuthorizedException {
		Settings settings = messageArchivePlugin.getSettings(session);
		Element prefs = preferencesAsElement(settings);

		results.offer(packet.okResult(prefs, 0));
	}

	private void updatePrefrerences(XMPPResourceConnection session, Packet packet, Element prefs, Queue<Packet> results)
			throws PacketErrorTypeException, NotAuthorizedException {
		Element always = prefs.getChild("always");
		Element never = prefs.getChild("never");
		if ((never != null && !never.getChildren().isEmpty()) || (always != null && !always.getChildren().isEmpty())) {
			results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
																	   "Could not set list of always or never - feature not implemented!",
																	   true));
			return;
		}

		String def = prefs.getAttributeStaticStr("default");
		if (def == null) {
			results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Missing 'default' attribute", true));
			return;
		}

		Settings settings = messageArchivePlugin.getSettings(session);

		StoreMethod requiredStoreMethod = messageArchivePlugin.getRequiredStoreMethod(session);
		StoreMuc storeMuc = messageArchivePlugin.getRequiredStoreMucMessages(session);

		if (storeMuc == StoreMuc.True) {
			results.offer(Authorization.NOT_ALLOWED.getResponseMessage(packet,
																	   "Due to system configuration it is not allowed to" +
																			   " disable automatic archivization of MUC messages which should be done for MAM",
																	   true));
			return;
		}

		switch (def) {
			case "always":
				settings.setAuto(true);
				break;
			case "never":
				if (requiredStoreMethod != StoreMethod.False) {
					results.offer(Authorization.NOT_ALLOWED.getResponseMessage(packet,
																			   "Due to system configuration it is not possible" +
																					   " to disable message archivization",
																			   true));
					return;
				}
				settings.setAuto(false);
				break;
			case "roster":
				if (requiredStoreMethod != StoreMethod.False) {
					results.offer(Authorization.NOT_ALLOWED.getResponseMessage(packet,
																			   "Due to system configuration it is not possible" +
																					   " to disable message archivization",
																			   true));
					return;
				}
				settings.setAuto(true);
				settings.setArchiveOnlyForContactsInRoster(true);
				break;
			default:
				results.offer(
						Authorization.BAD_REQUEST.getResponseMessage(packet, "Invalid value for 'default' attribute",
																	 true));
				return;
		}
		settings.setStoreMethod(StoreMethod.Message);
		settings.setArchiveMucMessages(false);

		try {
			session.setData(ARCHIVE, "settings", settings.serialize());
		} catch (TigaseDBException ex) {
			results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, null, false));
		}

		results.offer(packet.okResult(preferencesAsElement(settings), 0));
	}

	private Element preferencesAsElement(Settings settings) {
		Element prefs = new Element("prefs");
		prefs.setXMLNS(ID);

		prefs.addChild(new Element("always"));
		prefs.addChild(new Element("never"));

		if (settings.isAutoArchivingEnabled()) {
			prefs.setAttribute("default", settings.archiveOnlyForContactsInRoster() ? "roster" : "always");
		} else {
			prefs.setAttribute("default", "never");
		}

		return prefs;
	}
}
