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

//~--- non-JDK imports --------------------------------------------------------

import tigase.archive.*;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Iq;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.jid.JID;

import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tigase.archive.processors.MessageArchivePlugin.ARCHIVE;

/**
 * MessageArchingPlugin is implementation of plugin which forwards messages with type set to "chat" to
 * MessageArchivingComponent to store this messages in message archive.
 */
@Bean(name = Xep0136MessageArchivingProcessor.ID, parent = SessionManager.class, active = true)
public class Xep0136MessageArchivingProcessor
		extends XMPPProcessor
		implements XMPPProcessorIfc {

	public static final String MUC_SAVE = "muc-save";

	public static final String LIST = "list";

	public static final String REMOVE = "remove";

	public static final String RETRIEVE = "retrieve";

	public static final String XEP0136NS = "urn:xmpp:archive";
	protected static final String AUTO = "auto";
	protected static final String ID = "message-archive-xep-0136";
	protected static final String SETTINGS = ARCHIVE + "/settings";
	private static final String EXPIRE = "expire";
	private static final Logger log = Logger.getLogger(Xep0136MessageArchivingProcessor.class.getCanonicalName());
	private static final String SAVE = "save";
	private static final String[][] ELEMENT_PATHS = {{Iq.ELEM_NAME, AUTO}, {Iq.ELEM_NAME, RETRIEVE},
													 {Iq.ELEM_NAME, LIST}, {Iq.ELEM_NAME, REMOVE}, {Iq.ELEM_NAME, SAVE},
													 {Iq.ELEM_NAME, "pref"}, {Iq.ELEM_NAME, "tags"}};
	private static final String[] XMLNSS = {XEP0136NS, XEP0136NS, XEP0136NS, XEP0136NS, XEP0136NS, XEP0136NS,
											QueryCriteria.QUERTY_XMLNS};
	private static final Element[] DISCO_FEATURES = {
			new Element("feature", new String[]{"var"}, new String[]{XEP0136NS + ":" + AUTO}),
			new Element("feature", new String[]{"var"}, new String[]{XEP0136NS + ":manage"})};

	@Inject
	private MessageArchivePlugin messageArchivePlugin;

	//~--- methods --------------------------------------------------------------

	@Override
	public String id() {
		return ID;
	}

	@Override
	public String[][] supElementNamePaths() {
		return ELEMENT_PATHS;

	}

	@Override
	public String[] supNamespaces() {
		return XMLNSS;
	}

	@Override
	public Element[] supDiscoFeatures(final XMPPResourceConnection session) {
		return DISCO_FEATURES;
	}

	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
						Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
		if (session == null) {
			return;
		}
		try {
			if (messageArchivePlugin.getComponentJid().equals(packet.getPacketFrom())) {
				JID connId = session.getConnectionId(packet.getStanzaTo());
				Packet result = packet.copyElementOnly();

				result.setPacketTo(connId);
				results.offer(result);

				return;
			}
			if ((packet.getType() != StanzaType.get) && (packet.getType() != StanzaType.set)) {
				return;
			}

			Element auto = packet.getElement().getChild("auto");
			Element pref = packet.getElement().getChild("pref");

			if ((auto == null) && (pref == null)) {

				// redirecting to message archiving component
				Packet result = packet.copyElementOnly();

				result.setPacketTo(messageArchivePlugin.getComponentJid());
				results.offer(result);
			} else if (pref != null) {
				if (packet.getType() == StanzaType.get) {
					requestingPreferrences(session, packet, results);
				} else if (packet.getType() == StanzaType.set) {
					updatingPreferences(session, packet, pref, results);
				} else {
					results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, null, true));
				}
			} else {
				updateAutoSave(session, packet, auto, results);
			}
		} catch (TigaseDBException ex) {
			log.log(Level.WARNING, "Failed to access database during processing of packet: " + packet.toString(), ex);
			results.offer(
					Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Internal server error occurred",
																		   false));
		} catch (NotAuthorizedException ex) {
			log.log(Level.WARNING, "NotAuthorizedException for packet: {0}", packet);
			results.offer(
					Authorization.NOT_AUTHORIZED.getResponseMessage(packet, "You must authorize session first.", true));
		}
	}

	protected void requestingPreferrences(XMPPResourceConnection session, Packet packet, Queue<Packet> results)
			throws NotAuthorizedException, TigaseDBException {
		Settings settings = messageArchivePlugin.getSettings(session);

		Element prefEl = new Element("pref");

		prefEl.setXMLNS(XEP0136NS);

		// auto
		Element autoEl = new Element("auto");

		autoEl.setAttribute("save", String.valueOf(settings.isAutoArchivingEnabled()));
		prefEl.addChild(autoEl);

		// default
		Element defaultEl = new Element("default");

		defaultEl.setAttribute("otr", "forbid");
		try {
			RetentionType retentionType = VHostItemHelper.getRetentionType(session.getDomain());
			String expire = null;
			switch (retentionType) {
				case userDefined:
					expire = session.getData(SETTINGS, EXPIRE, null);
					break;
				case numberOfDays:
					Integer retention = VHostItemHelper.getRetentionDays(session.getDomain());
					if (retention != null) {
						expire = String.valueOf(retention.longValue() * 60 * 60 * 24);
					}
					break;
				case unlimited:
					break;
			}
			if (expire != null) {
				defaultEl.setAttribute(EXPIRE, expire);
			}
		} catch (TigaseDBException ex) {
			log.log(Level.WARNING, "could not retrieve expire setting for message archive for user {0}",
					session.getjid());
		}

		boolean isStoreMuc = StoreMuc.True == StoreMuc.valueof(session.getData(SETTINGS, MUC_SAVE, "false"));
		defaultEl.setAttribute(MUC_SAVE, Boolean.toString(isStoreMuc));

		StoreMethod storeMethod = settings.getStoreMethod();
		defaultEl.setAttribute("save", storeMethod.toString());
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
	}

	protected void updatingPreferences(XMPPResourceConnection session, Packet packet, Element pref,
									   Queue<Packet> results) throws PacketErrorTypeException, NotAuthorizedException {
		StoreMethod requiredStoreMethod = messageArchivePlugin.getRequiredStoreMethod(session);
		Settings settings = messageArchivePlugin.getSettings(session);

		Authorization error = null;
		StoreMethod storeMethod = null;
		Boolean autoSave = null;
		String errorMsg = null;
		String expire = null;
		String storeMuc = null;
		for (Element elem : pref.getChildren()) {
			switch (elem.getName()) {
				case "default":
					String storeMethodStr = elem.getAttributeStaticStr("save");
					if (storeMethodStr != null) {
						try {
							storeMethod = StoreMethod.valueof(storeMethodStr);
							if (storeMethod == StoreMethod.Stream) {
								error = Authorization.FEATURE_NOT_IMPLEMENTED;
								errorMsg = "Value stream of save attribute is not supported";
								break;
							}
							if (storeMethod.ordinal() < requiredStoreMethod.ordinal()) {
								error = Authorization.NOT_ACCEPTABLE;
								errorMsg =
										"Required minimal message archiving level is " + requiredStoreMethod.toString();
								break;
							}
						} catch (IllegalArgumentException ex) {
							error = Authorization.BAD_REQUEST;
							errorMsg = "Value " + storeMethodStr + " of save attribute is valid";
							break;
						}
					}
					String otr = elem.getAttributeStaticStr("otr");
					if (otr != null && !"forbid".equals(otr)) {
						error = Authorization.FEATURE_NOT_IMPLEMENTED;
						errorMsg = "Value " + otr + " of otr attribute is not supported";
					}
					expire = elem.getAttributeStaticStr(EXPIRE);
					if (expire != null) {
						if (RetentionType.userDefined != VHostItemHelper.getRetentionType(session.getDomain())) {
							error = Authorization.NOT_ALLOWED;
							errorMsg = "Expire value is not allowed to be changed by user";
						} else {
							try {
								long val = Long.parseLong(expire);
								if (val <= 0) {
									error = Authorization.NOT_ACCEPTABLE;
									errorMsg = "Value of expire attribute must be bigger than 0";
									break;
								}
							} catch (NumberFormatException ex) {
								error = Authorization.BAD_REQUEST;
								errorMsg = "Value of expire attribute must be a number";
								break;
							}
						}
					}
					storeMuc = elem.getAttributeStaticStr(MUC_SAVE);
					if (storeMuc != null) {
						if (StoreMuc.User != messageArchivePlugin.getRequiredStoreMucMessages(session)) {
							error = Authorization.NOT_ALLOWED;
							errorMsg = "Store MUC value is not allowed to be changed by user";
						} else if ((!"true".equals(storeMuc)) && (!"false".equals(storeMuc))) {
							error = Authorization.BAD_REQUEST;
							errorMsg = "Value of muc-save attribute must be 'true' or 'false'";
						} else {
							StoreMethod sm = storeMethod;
							if (sm == StoreMethod.False) {
								error = Authorization.NOT_ACCEPTABLE;
								errorMsg = "Can not change MUC message storage configuration as Message Archiving is disabled";
							}
						}
					}
					break;
				case "auto":
					autoSave = Boolean.valueOf(elem.getAttributeStaticStr("save"));
					if (requiredStoreMethod != StoreMethod.False && (autoSave == null || autoSave == false)) {
						error = Authorization.NOT_ACCEPTABLE;
						errorMsg = "Required minimal message archiving level is " + requiredStoreMethod.toString() +
								" and that requires automatic archiving to be enabled";
					}
					if (autoSave && !VHostItemHelper.isEnabled(session.getDomain())) {
						error = Authorization.NOT_ALLOWED;
						errorMsg = "Message archiving is not allowed for domain " + session.getDomainAsJID().toString();
					}
					break;
				default:
					error = Authorization.FEATURE_NOT_IMPLEMENTED;
					errorMsg = null;
			}
		}
		if (error != null) {
			results.offer(error.getResponseMessage(packet, errorMsg, true));
		} else {
			try {
				if (autoSave != null) {
					settings.setAuto(autoSave);
				}
				if (storeMethod != null) {
					settings.setStoreMethod(storeMethod);
				}
				if (expire != null) {
					session.setData(SETTINGS, EXPIRE, expire);
				}
				if (storeMuc != null) {
					settings.setArchiveMucMessages(Boolean.parseBoolean(storeMuc));
				}
				settings.setArchiveOnlyForContactsInRoster(false);

				session.setData(ARCHIVE, "settings", settings.serialize());

				results.offer(packet.okResult((String) null, 0));

				// shouldn't we notify other connected resources? see section 2.4.of XEP-0136
			} catch (TigaseDBException ex) {
				results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, null, false));
			}
		}
	}

	protected void updateAutoSave(XMPPResourceConnection session, Packet packet, Element auto, Queue<Packet> results)
			throws PacketErrorTypeException, NotAuthorizedException {
		StoreMethod requiredStoreMethod = messageArchivePlugin.getRequiredStoreMethod(session);
		String val = auto.getAttributeStaticStr("save");
		if (val == null) {
			val = "";
		}
		boolean save = false;

		switch (val) {
			case "true":
			case "1":
				save = true;
				break;
			case "false":
			case "0":
				save = false;
				break;
			default:
				results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, "Save value is incorrect or missing",
																		   false));
				return;
		}

		if (!save && requiredStoreMethod != StoreMethod.False) {
			results.offer(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet,
																		  "Required minimal message archiving level is " +
																				  requiredStoreMethod.toString() +
																				  " and that requires automatic archiving to be enabled",
																		  false));
			return;
		}
		if (save && !VHostItemHelper.isEnabled(session.getDomain())) {
			results.offer(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet,
																		  "Message archiving is not allowed for domain " +
																				  session.getDomainAsJID().toString(),
																		  false));
			return;
		}

		try {
			Settings settings = messageArchivePlugin.getSettings(session);
			settings.setAuto(save);
			session.setData(ARCHIVE, "settings", settings.serialize());

			Element res = new Element("auto");

			res.setXMLNS(XEP0136NS);
			res.setAttribute("save", save ? "true" : "false");
			results.offer(packet.okResult(res, 0));

			return;
		} catch (TigaseDBException ex) {
			log.log(Level.WARNING, "Error setting Message Archive state: {0}", ex.getMessage());
			results.offer(
					Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, "Database error occured", true));
		}
	}

}

//~ Formatted in Tigase Code Convention on 13/03/13
