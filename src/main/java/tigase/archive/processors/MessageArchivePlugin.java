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

//~--- non-JDK imports --------------------------------------------------------

import tigase.archive.MessageArchiveVHostItemExtension;
import tigase.archive.Settings;
import tigase.archive.StoreMethod;
import tigase.archive.StoreMuc;
import tigase.component.exceptions.RepositoryException;
import tigase.db.AuthRepository;
import tigase.db.NonAuthUserRepository;
import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.eventbus.EventBus;
import tigase.eventbus.HandleEvent;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Initializable;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.UnregisterAware;
import tigase.kernel.beans.config.ConfigField;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.server.xmppsession.SessionManager;
import tigase.server.xmppsession.SessionManagerHandler;
import tigase.server.xmppsession.UserConnectedEvent;
import tigase.util.cache.LRUConcurrentCache;
import tigase.util.datetime.TimestampHelper;
import tigase.util.dns.DNSResolverFactory;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.vhosts.VHostItem;
import tigase.vhosts.VHostItemImpl;
import tigase.vhosts.VHostManagerIfc;
import tigase.xml.Element;
import tigase.xmpp.*;
import tigase.xmpp.impl.C2SDeliveryErrorProcessor;
import tigase.xmpp.impl.JabberIqPrivacy;
import tigase.xmpp.impl.MessageDeliveryLogic;
import tigase.xmpp.impl.annotation.AnnotatedXMPPProcessor;
import tigase.xmpp.impl.annotation.Handle;
import tigase.xmpp.impl.annotation.Handles;
import tigase.xmpp.impl.annotation.Id;
import tigase.xmpp.impl.roster.RosterAbstract;
import tigase.xmpp.impl.roster.RosterFactory;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MessageArchingPlugin is implementation of plugin which forwards messages with type set to "chat" to
 * MessageArchivingComponent to store this messages in message archive.
 */
@Id(MessageArchivePlugin.ID)
@Handles({@Handle(path = {"message"}, xmlns = "jabber:client")})
@Bean(name = MessageArchivePlugin.ID, parents = {Xep0136MessageArchivingProcessor.class,
												 Xep0313MessageArchiveManagementProcessor.class}, active = true, exportable = true)
public class MessageArchivePlugin
		extends AnnotatedXMPPProcessor
		implements XMPPProcessorIfc, SessionManager.MessageArchive, Initializable, UnregisterAware {

	public static final String DEFAULT_SAVE = "default-save";
	public static final String MUC_SAVE = "muc-save";
	public static final String OWNER_JID = "owner";
	public static final String ARCHIVE = "message-archive";
	public static final String MSG_ARCHIVE_PATHS = "msg-archive-paths";
	protected static final String ID = "message-archive";
	protected static final String SETTINGS = ARCHIVE + "/settings";
	private static final Logger log = Logger.getLogger(MessageArchivePlugin.class.getCanonicalName());
	private static final String AUTO = "auto";
	private static final String DEFAULT_STORE_METHOD_KEY = "default-store-method";
	private static final String REQUIRED_STORE_METHOD_KEY = "required-store-method";
	private static final String STORE_MUC_MESSAGES_KEY = "store-muc-messages";

	private static final String[] MESSAGE_HINTS_NO_STORE = {Message.ELEM_NAME, "no-store"};
	private static final String[] MESSAGE_HINTS_NO_PERMANENT_STORE = {Message.ELEM_NAME, "no-permanent-store"};
	private static final String MESSAGE_HINTS_XMLNS = "urn:xmpp:hints";
	private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private final TimestampHelper timestampHelper = new TimestampHelper();
	@ConfigField(desc = "Message archiving component JID", alias = "component-jid")
	protected JID componentJid = null;
	@Inject
	private UserRepository userRepository;
	@ConfigField(desc = "Cache size", alias = "size")
	private int cacheSize = 10000;
	private LRUConcurrentCache<BareJID, Settings> cache = new LRUConcurrentCache<>(cacheSize);
	@ConfigField(desc = "Ignore PubSub notifications sent to full JID", alias = "ignore-pubsub-events-full-jid")
	protected boolean ignorePubSubEventsFullJid = true;
	//~--- fields ---------------------------------------------------------------
	@ConfigField(desc = "Matchers selecting messages that will be archived", alias = MSG_ARCHIVE_PATHS)
	private ElementMatcher[] archivingMatchers = new ElementMatcher[]{
			new ElementMatcher(new String[]{Message.ELEM_NAME, "result"}, "urn:xmpp:mam:1", false),
			new ElementMatcher(new String[]{Message.ELEM_NAME, "result"}, "urn:xmpp:mam:2", false),
			new ElementMatcher(new String[]{Message.ELEM_NAME}, true, "urn:xmpp:carbons:2", null, false),
			new ElementMatcher(new String[]{Message.ELEM_NAME, "no-store"}, MESSAGE_HINTS_XMLNS, false),
			new ElementMatcher(new String[]{Message.ELEM_NAME, "body"}, null, true),
			new ElementMatcher(new String[]{Message.ELEM_NAME, "store"}, MESSAGE_HINTS_XMLNS, true),
			new ElementMatcher(new String[]{Message.ELEM_NAME}, false, null,
							   new String[][]{new String[]{"type", "headline"}}, false),
			new ElementMatcher(new String[]{Message.ELEM_NAME}, true, "http://jabber.org/protocol/chatstates", null, false),
			new ElementMatcher(new String[]{Message.ELEM_NAME}, null, true),
			};
	@ConfigField(desc = "Global default store method", alias = DEFAULT_STORE_METHOD_KEY)
	private StoreMethod globalDefaultStoreMethod = StoreMethod.Message;
	@ConfigField(desc = "Global required store method", alias = REQUIRED_STORE_METHOD_KEY)
	private StoreMethod globalRequiredStoreMethod = StoreMethod.False;
	@ConfigField(desc = "Store MUC messages in archive using automatic archiving", alias = STORE_MUC_MESSAGES_KEY)
	private StoreMuc globalStoreMucMessages = StoreMuc.User;
	@Inject
	private MessageDeliveryLogic message;
	private RosterAbstract rosterUtil = RosterFactory.getRosterImplementation(true);
	@Inject
	private EventBus eventBus;
	@Inject
	private VHostManagerIfc vHostManager;

	@Inject(nullAllowed = true)
	private List<AbstractMAMProcessor> mamProcessors = new ArrayList<>();

	private boolean stanzaIdSupport = false;

	public void setCacheSize(int cacheSize) {
		this.cacheSize = cacheSize;
		if (cache.limit() != cacheSize) {
			cache = new LRUConcurrentCache<>(cacheSize);
		}
	}

	public void setMamProcessors(List<AbstractMAMProcessor> mamProcessors) {
		if (mamProcessors != null) {
			this.mamProcessors = mamProcessors;
			stanzaIdSupport = mamProcessors.stream().anyMatch(AbstractMAMProcessor::hasStanzaIdSupport);
		} else {
			this.mamProcessors = Collections.emptyList();
			stanzaIdSupport = false;
		}
	}

	public MessageArchivePlugin() {
		componentJid = JID.jidInstanceNS("message-archive", DNSResolverFactory.getInstance().getDefaultHost(), null);
	}

	//~--- methods --------------------------------------------------------------

	@Override
	public void process(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
						Queue<Packet> results, Map<String, Object> settings) throws XMPPException {
		try {
			processMessage(packet, session, results);
		} catch (NotAuthorizedException ex) {
			log.log(Level.FINE, "NotAuthorizedException for packet: {0}", packet);
			results.offer(
					Authorization.NOT_AUTHORIZED.getResponseMessage(packet, "You must authorize session first.", true));
		}
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

	public JID getComponentJid() {
		return componentJid;
	}

	public void setComponentJid(JID componentJid) {
		this.componentJid = componentJid;
	}

	//~--- get methods ----------------------------------------------------------	

	public StoreMethod getDefaultStoreMethod(Optional<MessageArchiveVHostItemExtension> maExt) {
		return maExt.flatMap(MessageArchiveVHostItemExtension::getDefaultStoreMethod).orElse(globalDefaultStoreMethod);
	}

	public StoreMethod getRequiredStoreMethod(Optional<MessageArchiveVHostItemExtension> maExt) {
		return maExt.flatMap(MessageArchiveVHostItemExtension::getRequiredStoreMethod)
				.orElse(globalRequiredStoreMethod);
	}

	public Settings getSettings(BareJID account, XMPPResourceConnection session) throws NotAuthorizedException {
		if (session == null) {
			Settings settings = cache.get(account);
			if (settings == null) {
				settings = loadSettings((String node, String key) -> userRepository.getData(account, node, key), (String node, String key, String value) -> {
					if (value != null) {
						userRepository.setData(account, node, key, value);
					} else {
						userRepository.removeData(account, node, key);
					}
				}, () -> Optional.ofNullable(vHostManager.getVHostItem(account.getDomain())));
				cache.put(account, settings);
			}
			return settings;
		} else {
			Settings storeMethod = (Settings) session.getCommonSessionData(ID + "/settings");

			if (storeMethod == null) {
				storeMethod = loadSettings(session);
			}

			return storeMethod;
		}
	}

	public StoreMuc getRequiredStoreMucMessages(XMPPResourceConnection session) {
		return Optional.ofNullable(session.getDomain().getExtension(MessageArchiveVHostItemExtension.class))
				.flatMap(MessageArchiveVHostItemExtension::getSaveMuc)
				.orElse(globalStoreMucMessages);
	}

	@Override
	public void initialize() {
		eventBus.registerAll(this);
	}

	@Override
	public void beforeUnregister() {
		if (eventBus != null) {
			eventBus.unregisterAll(this);
		}
	}

//	@Override
//	public void postProcess(Packet packet, XMPPResourceConnection session, NonAuthUserRepository repo,
//							Queue<Packet> results, Map<String, Object> settings) {
//		if (session == null || (packet.getElemName() == tigase.server.Message.ELEM_NAME &&
//				!message.hasConnectionForMessageDelivery(session))) {
//
//			if (packet.getStanzaTo() == null ||
//					packet.getStanzaTo().getResource() != null) {
//				return;
//			}
//
//			Element amp = packet.getElement().getChild("amp");
//
//			if ((amp == null) || (amp.getXMLNS() != "http://jabber.org/protocol/amp")) {
//				// this is a message which could go to offline storage..
//				try {
//					if (willArchive(packet, null)) {
//						BareJID owner = packet.getStanzaTo().getBareJID();
//						storeMessage(packet, owner, getSettings(owner, null), results);
//					}
//				} catch (NotAuthorizedException ex) {
//					log.log(Level.FINEST, "this should not happen!", ex);
//				}
//			}
//		}
//	}

	@HandleEvent
	protected void userConnected(UserConnectedEvent event) {
		cache.remove(event.getUserJid().getBareJID());
	}


	private interface RepoStringSupplier {

		String get(String node, String key) throws RepositoryException, NotAuthorizedException;

	}

	private interface RepoStringConsumer {

		void set(String node, String key, String value) throws RepositoryException, NotAuthorizedException;

	}

	public Settings loadSettings(XMPPResourceConnection session) throws NotAuthorizedException {
		Settings settings = loadSettings((String node, String key) -> session.getData(node, key, null),
										 (String node, String key, String value) -> {
											 if (value != null) {
												 session.setData(node, key, value);
											 } else {
												 session.removeData(node, key);
											 }
										 }, () -> Optional.ofNullable(session.getDomain()));
		session.putCommonSessionData(ID + "/settings", settings);
		return settings;
	}

	public Settings loadSettings(RepoStringSupplier dataSupplier, RepoStringConsumer dataConsumer, Supplier<Optional<VHostItem>> vhostSupplier) throws NotAuthorizedException {
		Settings settings = new Settings();
		boolean conversion = false;

		Optional<VHostItem> vhost = vhostSupplier.get();
		Optional<MessageArchiveVHostItemExtension> maExt = vhost.map(item -> item.getExtension(MessageArchiveVHostItemExtension.class));

		try {
			String prefs = dataSupplier.get(ID, "settings");
			if (prefs != null) {
				settings.parse(prefs);
			} else {
				Optional<Boolean> auto = Optional.ofNullable(dataSupplier.get(SETTINGS, AUTO))
						.map(Boolean::parseBoolean);
				if (auto.isPresent()) {
					conversion = true;
					settings.setAuto(auto.get());

					StoreMuc storeMuc = globalStoreMucMessages;
					if (globalStoreMucMessages == StoreMuc.User) {
						String val = dataSupplier.get(SETTINGS, MUC_SAVE);
						if (val == null) {
							storeMuc = maExt.flatMap(MessageArchiveVHostItemExtension::getSaveMuc).orElse(StoreMuc.False);
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

					StoreMethod storeMethod = Optional.ofNullable(dataSupplier.get(SETTINGS, DEFAULT_SAVE))
							.map(StoreMethod::valueof)
							.orElseGet(() -> getDefaultStoreMethod(maExt));
					settings.setStoreMethod(storeMethod);
				}
			}

			StoreMethod requiredStoreMethod = getRequiredStoreMethod(maExt);

			if (settings.updateRequirements(requiredStoreMethod, globalStoreMucMessages) || conversion) {
				dataConsumer.set(ID, "settings", settings.serialize());
				if (conversion) {
					dataConsumer.set(SETTINGS, AUTO, null);
					dataConsumer.set(SETTINGS, DEFAULT_STORE_METHOD_KEY, null);
					dataConsumer.set(SETTINGS, MUC_SAVE, null);
				}
			}
		} catch (RepositoryException ex) {
			log.log(Level.WARNING, "Exception reading settings from database", ex);
		}

		return settings;
	}

	@Override
	public void generateStableId(Packet packet) {
		if (packet.getStableId() == null) {
			if (packet.getType() == StanzaType.groupchat && packet.getElemChild("mix") == null) {
				StringBuilder sb = new StringBuilder();
				if (packet.getStanzaFrom() != null) {
					sb.append(packet.getStanzaFrom().toString());
				}
				sb.append(":");
				Element delay = packet.getElemChild("delay", "urn:xmpp:delay");
				if (packet.getElemChild("subject") == null && packet.getStanzaId() != null) {
					sb.append(packet.getStanzaId());
				}
				long ts = System.currentTimeMillis();
				if (delay != null) {
					try {
						Date stamp = timestampHelper.parseTimestamp(delay.getAttributeStaticStr("stamp"));
						if (stamp != null) {
							ts = stamp.getTime();
						}
					} catch (ParseException ex) {
						// invalid timestamp, lets ignore it..
					}
				}

				sb.append(":").append((ts/60000l));
				String body = packet.getElement().getChildCData(new String[] {"message", "body"});
				if (body != null) {
					sb.append(body);
				}
				String subject = packet.getElement().getCDataStaticStr(new String[]{"message", "subject"});
				if (subject != null) {
					sb.append(subject);
				}
				packet.setStableId(UUID.nameUUIDFromBytes(sb.toString().getBytes(StandardCharsets.UTF_8)).toString());
				return;
			}
			packet.setStableId(UUID.randomUUID().toString());
		}
	}

	@Override
	public void addStableId(Packet packet, XMPPResourceConnection session) {
		if (stanzaIdSupport) {
			try {
				if (willArchive(packet, session)) {
					try {
						synchronized (packet) {
							String by = session == null ? Optional.ofNullable(packet.getStanzaTo())
									.map(JID::getBareJID)
									.map(BareJID::toString)
									.get() : session.getBareJID().toString();
							String stableId = packet.getStableId();
							if (stableId != null && packet.getElement().findChild(stanzaIdMatcher(by)) == null) {
								Element stanzaIdEl = new Element("stanza-id");
								stanzaIdEl.setXMLNS("urn:xmpp:sid:0");
								stanzaIdEl.setAttribute("id", stableId);
								stanzaIdEl.setAttribute("by", by);
								packet.getElement().addChild(stanzaIdEl);
							}
						}
					} catch (NotAuthorizedException ex) {
						return;
					}
				}
			} catch (NotAuthorizedException ex) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "Session is not authorized yet:" + session, ex);
				}
			}
		}
	}

	protected Element.Matcher<Element> stanzaIdMatcher(String by) {
		return el -> el.getName() == "stanza-id" && el.getXMLNS() == "urn:xmpp:sid:0" &&
				by.equals(el.getAttributeStaticStr("by"));
	}

	private SessionManagerHandler loginHandler = new SessionManagerHandler() {
		@Override
		public JID getComponentId() {
			return getComponentJid();
		}

		@Override
		public void handleLogin(BareJID userId, XMPPResourceConnection conn) {

		}

		@Override
		public void handleDomainChange(String domain, XMPPResourceConnection conn) {

		}

		@Override
		public void handleLogout(BareJID userId, XMPPResourceConnection conn) {

		}

		@Override
		public void handlePresenceSet(XMPPResourceConnection conn) {

		}

		@Override
		public void handleResourceBind(XMPPResourceConnection conn) {

		}

		@Override
		public boolean isLocalDomain(String domain, boolean includeComponents) {
			return false;
		}
	};

	@Inject
	private AuthRepository authRepository;
	private final JID offlineConnectionId = JID.jidInstanceNS("offline-connection",
															  DNSResolverFactory.getInstance().getDefaultHost());

	@Override
	public boolean willArchive(Packet packet, XMPPResourceConnection session) throws NotAuthorizedException {
		if (packet.getStanzaFrom() == null) {
			// if packet/message has no "from" it most likely is an error or direct response from server to the client
			return false;
		}
		if (session != null) {
			return willArchive(packet, session.isUserId(packet.getStanzaFrom().getBareJID()) ? packet.getStanzaTo() : packet.getStanzaFrom(), getSettings(session.getBareJID(), session),
							   () -> Optional.ofNullable(session.getDomain()), (JID jid) -> {
						try {
							return rosterUtil.containsBuddy(session, jid);
						} catch (NotAuthorizedException | TigaseDBException ex) {
							log.log(Level.WARNING, session.toString() +
									", could not load roster to verify if sender/recipient is in roster, skipping archiving of packet: " +
									packet, ex);
							return false;
						}
					});
		} else {
			final BareJID userJid = packet.getStanzaTo().getBareJID();
			return willArchive(packet, packet.getStanzaFrom(), getSettings(userJid, null), () -> Optional.ofNullable(vHostManager.getVHostItem(userJid.getDomain())), (JID jid) -> {
				try {
					XMPPResourceConnection session1 = new JabberIqPrivacy.OfflineResourceConnection(offlineConnectionId,
																									userRepository,
																									authRepository, this.loginHandler);
					VHostItem vhost = new VHostItemImpl(userJid.getDomain());
					session1.setDomain(vhost);
					session1.authorizeJID(userJid, false);
					XMPPSession parentSession = new XMPPSession(userJid.getLocalpart());
					session1.setParentSession(parentSession);
					return rosterUtil.containsBuddy(session1, jid);
				} catch (TigaseStringprepException|NotAuthorizedException|TigaseDBException ex) {
					log.log(Level.WARNING, session.toString() +
							", could not load roster to verify if sender/recipient is in roster, skipping archiving of packet: " +
							packet, ex);
					return false;
				}
			});
		}
	}

	public boolean willArchive(Packet packet, JID buddyJid, Settings settings, Supplier<Optional<VHostItem>> vhostItemSupplier, Predicate<JID> isInRoster) throws NotAuthorizedException {
		// ignoring packets resent from c2s for redelivery as processing
		// them would create unnecessary duplication of messages in archive
		if (C2SDeliveryErrorProcessor.isDeliveryError(packet)) {
			log.log(Level.FINEST, "not processong packet as it is delivery error = {0}", packet);
			return false;
		}

		Optional<VHostItem> vhostItem = vhostItemSupplier.get();
		if (!vhostItem.isPresent()) {
			return false;
		}
		String domain = vhostItem.get().getVhost().getDomain();
		if (packet.getElement().findChild(el -> el.getName() == "delay" && el.getXMLNS() == "urn:xmpp:delay" && domain.equals(el.getAttributeStaticStr("from"))) != null) {
			// this packet was already archived by local offline storage, which means that it was already processed by MAM..
			return false;
		}

		StanzaType type = packet.getType();
		if (type == null) {
			type = StanzaType.normal;
		}

		Element body = packet.getElement().findChildStaticStr(Message.MESSAGE_BODY_PATH);

		if (!settings.isAutoArchivingEnabled()) {
			return false;
		}

		// support for XEP-0334 Message Processing Hints
		if (packet.getAttributeStaticStr(MESSAGE_HINTS_NO_STORE, "xmlns") == MESSAGE_HINTS_XMLNS ||
				packet.getAttributeStaticStr(MESSAGE_HINTS_NO_PERMANENT_STORE, "xmlns") == MESSAGE_HINTS_XMLNS) {
			StoreMethod requiredStoreMethod = getRequiredStoreMethod(vhostItemSupplier.get()
																			 .map(vHostItem -> vHostItem.getExtension(
																					 MessageArchiveVHostItemExtension.class)));
			if (requiredStoreMethod == StoreMethod.False) {
				return false;
			}
		}

		switch (type) {
			case groupchat:
				Element mix = packet.getElemChild("mix", "urn:xmpp:mix:core:1");
				if (mix != null) {
					if (!isInRoster.test(buddyJid)) {
						return false;
					}
				} else if (!settings.archiveMucMessages()) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "not storing message as archiving of MUC messages is disabled: {0}",
								packet);
					}
					return false;
				}
				// we need to log groupchat messages only sent from MUC to user as MUC needs to confirm
				// message delivery by sending message back
				if (packet.getStanzaTo() != null && buddyJid.getBareJID().equals(packet.getStanzaTo().getBareJID())) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "not storing message sent to MUC room from user = {0}",
								packet.toString());
					}
					return false;
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
					return false;
				}
				break;
			case Body:
				// we need to store message in this case only if it contains body element
				if (body == null) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "not logging packet as there is not body element: ",
								new Object[]{settings.getStoreMethod(), packet});
						return false;
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
		if (!archive) {
			return false;
		}
		if (ignorePubSubEventsFullJid) {
			if (packet.getStanzaTo() != null && packet.getStanzaTo().getResource() != null &&
					packet.getElemChild("event", "http://jabber.org/protocol/pubsub#event") != null) {
				return false;
			}
		}
			

		if (settings.archiveOnlyForContactsInRoster()) {
			// to and from should already be set at this point
			if (packet.getStanzaTo() == null || packet.getStanzaFrom() == null) {
				return false;
			}

			if (!isInRoster.test(buddyJid)) {
				return false;
			}
		}
		return true;
	}



	private void processMessage(Packet packet, XMPPResourceConnection session, Queue<Packet> results)
			throws NotAuthorizedException {
		if (!willArchive(packet, session)) {
			return;
		}

		BareJID userJid = session == null ? packet.getStanzaTo().getBareJID() : session.getBareJID();
		Settings settings = getSettings(userJid, session);
		// redirecting to message archiving component
		storeMessage(packet, userJid, settings, results);
	}

	private void storeMessage(Packet packet, BareJID owner, Settings settings, Queue<Packet> results)
			throws NotAuthorizedException {
		Packet result = packet.copyElementOnly();

		result.setPacketFrom(JID.jidInstance(owner));
		result.setPacketTo(componentJid);
		result.setStableId(packet.getStableId());
		result.getElement().addAttribute(OWNER_JID, owner.toString());
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
		Element stanzaIdEl = result.getElement().findChild(stanzaIdMatcher(owner.toString()));
		if (stanzaIdEl != null) {
			result.getElement().removeChild(stanzaIdEl);
		}
		results.offer(result);
	}
	
}

//~ Formatted in Tigase Code Convention on 13/03/13
