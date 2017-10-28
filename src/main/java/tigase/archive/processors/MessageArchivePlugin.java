/*
 * MessageArchivePlugin.java
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


package tigase.archive.processors;

//~--- non-JDK imports --------------------------------------------------------

import tigase.archive.Settings;
import tigase.archive.StoreMethod;
import tigase.archive.StoreMuc;
import tigase.archive.VHostItemHelper;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.RegistrarBean;
import tigase.kernel.beans.config.ConfigField;
import tigase.kernel.core.Kernel;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.dns.DNSResolverFactory;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.C2SDeliveryErrorProcessor;
import tigase.xmpp.impl.annotation.AnnotatedXMPPProcessor;
import tigase.xmpp.impl.annotation.Handle;
import tigase.xmpp.impl.annotation.Handles;
import tigase.xmpp.impl.annotation.Id;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.jid.JID;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MessageArchingPlugin is implementation of plugin which forwards messages
 * with type set to "chat" to MessageArchivingComponent to store this messages
 * in message archive.
 */
@Id(MessageArchivePlugin.ID)
@Handles({
		@Handle(path = {"message"}, xmlns = "jabber:client")
})
@Bean(name = MessageArchivePlugin.ID, parents = {Xep0136MessageArchivingProcessor.class, Xep0313MessageArchiveManagementProcessor.class}, active = true)
public class MessageArchivePlugin
		extends AnnotatedXMPPProcessor
		implements XMPPProcessorIfc, RegistrarBean {

	public static final String DEFAULT_SAVE = "default-save";
	public static final String MUC_SAVE = "muc-save";

	/**
	 * Field description
	 */
	protected static final String ID = "message-archive";
	private static final Logger log = Logger.getLogger(MessageArchivePlugin.class
			.getCanonicalName());
	private static final String AUTO = "auto";
	public static final String OWNER_JID = "owner";
	public static final String ARCHIVE = "message-archive";
	protected static final String SETTINGS = ARCHIVE + "/settings";


	public static final String MSG_ARCHIVE_PATHS = "msg-archive-paths";
	private static final String DEFAULT_STORE_METHOD_KEY = "default-store-method";
	private static final String REQUIRED_STORE_METHOD_KEY = "required-store-method";
	private static final String STORE_MUC_MESSAGES_KEY = "store-muc-messages";

	private static final String[] MESSAGE_HINTS_NO_STORE = {Message.ELEM_NAME, "no-store"};
	private static final String[] MESSAGE_HINTS_NO_PERMANENT_STORE = {Message.ELEM_NAME, "no-permanent-store"};
	private static final String MESSAGE_HINTS_XMLNS = "urn:xmpp:hints";

	//~--- fields ---------------------------------------------------------------
	@ConfigField(desc = "Matchers selecting messages that will be archived", alias = MSG_ARCHIVE_PATHS)
	private ElementMatcher[] archivingMatchers = new ElementMatcher[]{
			new ElementMatcher(new String[]{Message.ELEM_NAME, "result"}, "urn:xmpp:mam:1", false),
			new ElementMatcher(new String[]{Message.ELEM_NAME, "body"}, null, true)
	};
	@ConfigField(desc = "Global default store method", alias = DEFAULT_STORE_METHOD_KEY)
	private StoreMethod globalDefaultStoreMethod = StoreMethod.Body;
	@ConfigField(desc = "Global required store method", alias = REQUIRED_STORE_METHOD_KEY)
	private StoreMethod globalRequiredStoreMethod = StoreMethod.False;
	@ConfigField(desc = "Store MUC messages in archive using automatic archiving", alias = STORE_MUC_MESSAGES_KEY)
	private StoreMuc globalStoreMucMessages = StoreMuc.User;
	@Inject
	private tigase.xmpp.impl.Message message;
	private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	@ConfigField(desc = "Message archiving component JID", alias = "component-jid")
	protected JID componentJid = null;

	private RosterAbstract rosterUtil = RosterFactory.getRosterImplementation(true);
	;

	public MessageArchivePlugin() {
		VHostItemHelper.register();
		componentJid = JID.jidInstanceNS("message-archive", DNSResolverFactory.getInstance().getDefaultHost(), null);
	}

	//~--- methods --------------------------------------------------------------
	
	/**
	 * Method description
	 *
	 * @param packet
	 * @param session
	 * @param repo
	 * @param results
	 * @param settings
	 * @throws XMPPException
	 */
	@Override
	public void process(Packet packet, XMPPResourceConnection session,
	                    NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings)
			throws XMPPException {
		if (session == null) {
			return;
		}

		try {
			if (!message.hasConnectionForMessageDelivery(session) ) {
				if (packet.getStanzaTo() == null || packet.getStanzaTo().getResource() == null) {
					return;
				}
			}

			processMessage(packet, session, results);
		} catch (NotAuthorizedException ex) {
			log.log(Level.WARNING, "NotAuthorizedException for packet: {0}", packet);
			results.offer(Authorization.NOT_AUTHORIZED.getResponseMessage(packet,
					"You must authorize session first.", true));
		}
	}

	@Override
	public void register(Kernel kernel) {
		kernel.registerBean(tigase.xmpp.impl.Message.class).setActive(true).exec();
	}

	public void unregister(Kernel kernel) {

	}

	private void processMessage(Packet packet, XMPPResourceConnection session, Queue<Packet> results) throws NotAuthorizedException {
		// ignoring packets resent from c2s for redelivery as processing
		// them would create unnecessary duplication of messages in archive
		if (C2SDeliveryErrorProcessor.isDeliveryError(packet)) {
			log.log(Level.FINEST, "not processong packet as it is delivery error = {0}", packet);
			return;
		}

		StanzaType type = packet.getType();
		if (type == null) {
			type = StanzaType.normal;
		}

		Element body = packet.getElement().findChildStaticStr(Message.MESSAGE_BODY_PATH);

		Settings settings = getSettings(session);

		if (!settings.isAutoArchivingEnabled())
			return;

		// support for XEP-0334 Message Processing Hints
		if (packet.getAttributeStaticStr(MESSAGE_HINTS_NO_STORE, "xmlns") == MESSAGE_HINTS_XMLNS
				|| packet.getAttributeStaticStr(MESSAGE_HINTS_NO_PERMANENT_STORE, "xmlns") == MESSAGE_HINTS_XMLNS) {
			StoreMethod requiredStoreMethod = VHostItemHelper.getRequiredStoreMethod(session.getDomain(), globalRequiredStoreMethod);
			if (requiredStoreMethod == StoreMethod.False) {
				return;
			}
		}

		switch (type) {
			case groupchat:
				if (!settings.archiveMucMessages()) {
					if (log.isLoggable(Level.FINEST))
						log.log(Level.FINEST, "not storing message as archiving of MUC messages is disabled: {0}", packet);
					return;
				}
				// we need to log groupchat messages only sent from MUC to user as MUC needs to confirm
				// message delivery by sending message back
				if (session.isUserId(packet.getStanzaFrom().getBareJID())) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "not storing message sent to MUC room from user = {0}",
								packet.toString());
					}
					return;
				}
				break;

			default:
				break;
		}

		switch (settings.getStoreMethod()) {
			case False:
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "not logging packet due to storage method: {0}, {1}",
							new Object[]{settings.getStoreMethod(), packet});
					return;
				}
				break;
			case Body:
				// we need to store message in this case only if it contains body element
				if (body == null) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "not logging packet as there is not body element: ",
								new Object[]{settings.getStoreMethod(), packet});
						return;
					}
				}
				break;
			case Message:
			case Stream:
				// here we can store any message
				break;
		}

		// let's check if message should be stored using matchers to make it configurable
		// whether to archive this message or not
		boolean archive = false;
		for (ElementMatcher matcher : archivingMatchers) {
			if (matcher.matches(packet)) {
				archive = matcher.getValue();
				break;
			}
		}
		if (!archive)
			return;

		if (settings.archiveOnlyForContactsInRoster()) {
			// to and from should already be set at this point
			if (packet.getStanzaTo() == null || packet.getStanzaFrom() == null)
				return;

			try {
				if (!rosterUtil.containsBuddy(session, session.isUserId(packet.getStanzaFrom().getBareJID()) ? packet.getStanzaTo() : packet.getStanzaFrom()))
					return;
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, session.toString() + ", could not load roster to verify if sender/recipient is in roster, skipping archiving of packet: " + packet, ex);
				return;
			}
		}

		// redirecting to message archiving component
		storeMessage(packet, session, settings, results);
	}

	private void storeMessage(Packet packet, XMPPResourceConnection session, Settings settings, Queue<Packet> results) throws NotAuthorizedException {
		Packet result = packet.copyElementOnly();

		result.setPacketFrom(session.getJID().copyWithoutResource());
		result.setPacketTo(componentJid);
		result.getElement().addAttribute(OWNER_JID, session.getBareJID().toString());
		switch (settings.getStoreMethod()) {
			case Body:
				// in this store method we should only store <body/> element
				Element message = result.getElement();
				for (Element elem : message.getChildren()) {
					switch (elem.getName()) {
						case "body":
							break;
						case "delay":
							// we need to keep delay as well to have
							// a proper timestamp of a messages
							break;
						default:
							message.removeChild(elem);
							break;
					}
				}
				break;
			default:
				// in other case we store whole message
				break;
		}
		results.offer(result);
	}

	public String[] getArchivingMatchers() {
		String[] result = new String[archivingMatchers.length];
		for (int i = 0; i < archivingMatchers.length; i++) {
			result[i] = archivingMatchers[i].toString();
		}
		return result;
	}

	public void setArchivingMatchers(String[] matcherStrs) {
		List<ElementMatcher> matchers = new ArrayList<>();
		for (String matcherStr : matcherStrs) {
			ElementMatcher matcher = ElementMatcher.create(matcherStr);
			if (matcher != null) {
				matchers.add(matcher);
			}
		}
		archivingMatchers = matchers.toArray(new ElementMatcher[0]);
	}

	//~--- get methods ----------------------------------------------------------	

	public JID getComponentJid() {
		return componentJid;
	}

	public StoreMethod getDefaultStoreMethod(XMPPResourceConnection session) {
		return VHostItemHelper.getDefaultStoreMethod(session.getDomain(), globalDefaultStoreMethod);
	}

	public StoreMethod getRequiredStoreMethod(XMPPResourceConnection session) {
		return VHostItemHelper.getRequiredStoreMethod(session.getDomain(), globalRequiredStoreMethod);
	}

	public Settings getSettings(XMPPResourceConnection session) throws NotAuthorizedException {
		Settings storeMethod = (Settings) session.getCommonSessionData(ID + "/settings");

		if (storeMethod == null) {
			storeMethod = loadSettings(session);
		}

		return storeMethod;
	}

	public StoreMuc getRequiredStoreMucMessages(XMPPResourceConnection session) {
		return VHostItemHelper.getStoreMucMessages(session.getDomain(), globalStoreMucMessages);
	}

	public Settings loadSettings(XMPPResourceConnection session) throws NotAuthorizedException {
		Settings settings = new Settings();
		StoreMuc save = globalStoreMucMessages;
		boolean dbException = false;
		boolean conversion = false;
		try {
			String prefs = session.getData(ID, "settings", null);
			if (prefs != null) {
				settings.parse(prefs);
			} else {
				conversion = true;
				boolean auto = Boolean.parseBoolean(session.getData(SETTINGS, AUTO, "false"));
				settings.setAuto(auto);
				StoreMuc storeMuc = globalStoreMucMessages;
				if (globalStoreMucMessages == StoreMuc.User) {
					String val = session.getData(SETTINGS, MUC_SAVE, null);
					if (val == null) {
						storeMuc = VHostItemHelper.getStoreMucMessages(session.getDomain(), StoreMuc.False);
					} else {
						storeMuc = StoreMuc.valueof(val);
					}
				}
				switch (storeMuc) {
					case True:
						settings.setArchiveMucMessages(true);
						break;
					case False:
						settings.setArchiveMucMessages(true);
						break;
					default:
						break;
				}
				String storeMethodStr = session.getData(SETTINGS, DEFAULT_SAVE, null);

				StoreMethod storeMethod = StoreMethod.valueof(storeMethodStr);
				if (storeMethodStr == null) {
					storeMethod = getDefaultStoreMethod(session);
				}
				settings.setStoreMethod(storeMethod);
			}
		} catch (TigaseDBException ex) {
			dbException = true;
			log.log(Level.WARNING, "Exception reading settings from database", ex);
		}

		StoreMethod requiredStoreMethod = getRequiredStoreMethod(session);

		boolean changed = settings.updateRequirements(requiredStoreMethod, globalStoreMucMessages);

		session.putCommonSessionData(ID + "/settings", settings);

		if (!dbException) {
			try {
				if (changed || conversion) {
					session.setData(ID, "settings", settings.serialize());
				}
				if (conversion) {
					session.removeData(SETTINGS, AUTO);
					session.removeData(SETTINGS, DEFAULT_STORE_METHOD_KEY);
					session.removeData(SETTINGS, MUC_SAVE);
				}
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Exception updating settings in database", ex);
			}
		}

		return settings;
	}

	public void setComponentJid(JID componentJid) {
		this.componentJid = componentJid;
	}

}


//~ Formatted in Tigase Code Convention on 13/03/13
