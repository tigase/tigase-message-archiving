/*
 * MessageArchivePlugin.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
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

//~--- non-JDK imports --------------------------------------------------------

import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;

import tigase.server.Message;
import tigase.server.Packet;

import tigase.util.DNSResolver;

import tigase.xml.Element;

import tigase.xmpp.*;

//~--- JDK imports ------------------------------------------------------------

import java.text.SimpleDateFormat;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Queue;

/**
 * MessageArchingPlugin is implementation of plugin which forwards messages
 * with type set to "chat" to MessageArchivingComponent to store this messages
 * in message archive.
 */
public class MessageArchivePlugin
				extends XMPPProcessor
				implements XMPPProcessorIfc {
	/** Field description */
	public static final String LIST = "list";

	/** Field description */
	public static final String OWNER_JID = "owner";

	/** Field description */
	public static final String REMOVE = "remove";

	/** Field description */
	public static final String RETRIEVE = "retrieve";

	/** Field description */
	public static final String XEP0136NS = "urn:xmpp:archive";
	private static final String ARCHIVE  = "message-archive";
	private static final String AUTO     = "auto";
	private static final String ID       = "message-archive-xep-0136";
	private static final Logger log      =
		Logger.getLogger(MessageArchivePlugin.class.getCanonicalName());
	private static final String MESSAGE           = "message";
	private static final String SETTINGS          = ARCHIVE + "/settings";
	private static final String XMLNS             = "jabber:client";
	private static final String[] ELEMENTS        = { MESSAGE, ALL };
	private static final String[] XMLNSS          = { XMLNS, XEP0136NS };
	private static final Element[] DISCO_FEATURES = { new Element("feature",
																										new String[] { "var" },
																										new String[] {
																											XEP0136NS + ":" + AUTO }),
					new Element("feature", new String[] { "var" },
											new String[] { XEP0136NS + ":manage" }) };

	//~--- fields ---------------------------------------------------------------

	private SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private JID ma_jid                 = null;

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param settings
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public void init(Map<String, Object> settings) throws TigaseDBException {
		super.init(settings);

		String componentJidStr = (String) settings.get("component-jid");

		if (componentJidStr != null) {
			ma_jid = JID.jidInstanceNS(componentJidStr);
		} else {
			String defHost = DNSResolver.getDefaultHostname();

			ma_jid = JID.jidInstanceNS("message-archive", defHost, null);
		}
		log.log(Level.CONFIG, "Loaded message archiving component jid option: {0} = {1}",
						new Object[] { "component-jid",
													 ma_jid });
		System.out.println("MA LOADED = " + ma_jid.toString());
	}

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 * @param session
	 * @param repo
	 * @param results
	 * @param settings
	 *
	 * @throws XMPPException
	 */
	@Override
	public void process(Packet packet, XMPPResourceConnection session,
											NonAuthUserRepository repo, Queue<Packet> results,
											Map<String, Object> settings)
					throws XMPPException {
		if (session == null) {
			return;
		}
		try {
			if (MESSAGE.equals(packet.getElemName())) {
				StanzaType type = packet.getType();

				if ((packet.getElement().findChildStaticStr(Message.MESSAGE_BODY_PATH) ==
						 null) || ((type != null) && (type != StanzaType.chat) &&
											 (type != StanzaType.normal))) {
					return;
				}

				boolean auto = getAutoSave(session);

				if (auto && (packet.getElemCDataStaticStr(Message.MESSAGE_BODY_PATH) != null)) {

					// redirecting to message archiving component
					Packet result = packet.copyElementOnly();

					result.setPacketTo(ma_jid);
					result.getElement().addAttribute(OWNER_JID, session.getBareJID().toString());
					results.offer(result);
				}
			} else if ("iq".equals(packet.getElemName())) {
				if (ma_jid.equals(packet.getPacketFrom())) {
					JID connId    = session.getConnectionId(packet.getStanzaTo());
					Packet result = packet.copyElementOnly();

					result.setPacketTo(connId);
					results.offer(result);

					return;
				}
				if ((packet.getType() != StanzaType.get) &&
						(packet.getType() != StanzaType.set)) {
					return;
				}

				Element auto = packet.getElement().getChild("auto");
				Element pref = packet.getElement().getChild("pref");

				if ((auto == null) && (pref == null)) {

					// redirecting to message archiving component
					Packet result = packet.copyElementOnly();

					result.setPacketTo(ma_jid);
					results.offer(result);
				} else if (pref != null) {
					if (packet.getType() == StanzaType.get) {
						Element prefEl = new Element("pref");

						prefEl.setXMLNS(XEP0136NS);

						// auto
						Element autoEl = new Element("auto");

						autoEl.setAttribute("save", String.valueOf(getAutoSave(session)));
						prefEl.addChild(autoEl);

						// default
						Element defaultEl = new Element("default");

						defaultEl.setAttribute("otr", "forbid");
						defaultEl.setAttribute("save", "message");
						prefEl.addChild(defaultEl);

						Element methodEl = new Element("method");

						methodEl.setAttribute("type", "auto");
						methodEl.setAttribute("use", "prefer");
						prefEl.addChild(methodEl);
						methodEl = new Element("method");
						methodEl.setAttribute("type", "local");
						methodEl.setAttribute("use", "prefer");
						prefEl.addChild(methodEl);
						methodEl = new Element("method");
						methodEl.setAttribute("type", "manual");
						methodEl.setAttribute("use", "prefer");
						prefEl.addChild(methodEl);
						results.offer(packet.okResult(prefEl, 0));
					} else if (packet.getType() == StanzaType.set) {
						results.offer(
								Authorization.FEATURE_NOT_IMPLEMENTED.getResponseMessage(
									packet, null, true));
					} else {
						results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, null,
										true));
					}
				} else {
					String val   = auto.getAttributeStaticStr("save");
					boolean save = false;

					if ("1".equals(val) || "true".equals(val)) {
						save = true;
					} else if ("0".equals(val) || "false".equals(val)) {
						save = false;
					} else {
						results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
										"Save value is incorrect or missing", false));
					}
					try {
						setAutoSave(session, save);
						session.putCommonSessionData(ID + "/" + AUTO, save);

						Element res = new Element("auto");

						res.setXMLNS(XEP0136NS);
						res.setAttribute("save", save
																		 ? "true"
																		 : "false");
						results.offer(packet.okResult(res, 0));

						return;
					} catch (TigaseDBException ex) {
						log.log(Level.WARNING, "Error setting Message Archive state: {0}",
										ex.getMessage());
						results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
										"Database error occured", true));
					}
				}
			}
		} catch (NotAuthorizedException ex) {
			log.log(Level.WARNING, "NotAuthorizedException for packet: {0}", packet);
			results.offer(Authorization.NOT_AUTHORIZED.getResponseMessage(packet,
							"You must authorize session first.", true));
		}
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String id() {
		return ID;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] supElements() {
		return ELEMENTS;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String[] supNamespaces() {
		return XMLNSS;
	}

	/**
	 * Method description
	 *
	 *
	 * @param session
	 *
	 * @return
	 */
	@Override
	public Element[] supDiscoFeatures(final XMPPResourceConnection session) {
		return DISCO_FEATURES;
	}

	//~--- get methods ----------------------------------------------------------

	private boolean getAutoSave(final XMPPResourceConnection session)
					throws NotAuthorizedException {
		Boolean auto = (Boolean) session.getCommonSessionData(ID + "/" + AUTO);

		if (auto == null) {
			try {
				String data = session.getData(SETTINGS, AUTO, "false");

				auto = Boolean.parseBoolean(data);
				session.putCommonSessionData(ID + "/" + AUTO, auto);
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Error getting Message Archive state: {0}",
								ex.getMessage());
				auto = false;
			}
		}

		return auto;
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param session
	 * @param auto
	 *
	 * @throws NotAuthorizedException
	 * @throws TigaseDBException
	 */
	public void setAutoSave(XMPPResourceConnection session, Boolean auto)
					throws NotAuthorizedException, TigaseDBException {
		session.setData(SETTINGS, AUTO, String.valueOf(auto));
	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
