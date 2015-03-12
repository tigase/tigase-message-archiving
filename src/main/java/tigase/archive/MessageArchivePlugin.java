/*
 * MessageArchivePlugin.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2013 "Tigase, Inc." <office@tigase.com>
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import tigase.server.Iq;
import tigase.xmpp.impl.C2SDeliveryErrorProcessor;

/**
 * MessageArchingPlugin is implementation of plugin which forwards messages
 * with type set to "chat" to MessageArchivingComponent to store this messages
 * in message archive.
 */
public class MessageArchivePlugin
				extends XMPPProcessor
				implements XMPPProcessorIfc {
	
	public static final String DEFAULT_SAVE = "default-save";
	public static final String MUC_SAVE = "muc-save";
	
	/** Field description */
	public static final String LIST = "list";

	/** Field description */
	public static final String OWNER_JID = "owner";

	/** Field description */
	public static final String REMOVE = "remove";

	/** Field description */
	public static final String RETRIEVE = "retrieve";

	/** Field description */
	public static final String  XEP0136NS = "urn:xmpp:archive";
	private static final String ARCHIVE   = "message-archive";
	private static final String AUTO      = "auto";
	private static final String EXPIRE    = "expire";
	private static final String ID        = "message-archive-xep-0136";
	private static final Logger log = Logger.getLogger(MessageArchivePlugin.class
			.getCanonicalName());
	private static final String    MESSAGE  = "message";
	private static final String	   SAVE		= "save";
	private static final String    SETTINGS = ARCHIVE + "/settings";
	private static final String    XMLNS    = "jabber:client";
	private static final String[][]  ELEMENT_PATHS = { {MESSAGE}, {Iq.ELEM_NAME, AUTO}, 
		{Iq.ELEM_NAME, RETRIEVE}, {Iq.ELEM_NAME, LIST}, {Iq.ELEM_NAME, REMOVE}, 
		{Iq.ELEM_NAME, SAVE}, {Iq.ELEM_NAME, "pref"}, {Iq.ELEM_NAME, "tags"} };
	private static final String[] XMLNSS = { Packet.CLIENT_XMLNS, XEP0136NS, 
		XEP0136NS, XEP0136NS, XEP0136NS, XEP0136NS, XEP0136NS, AbstractCriteria.QUERTY_XMLNS };
	//private static final Set<StanzaType> TYPES;
	private static final Element[] DISCO_FEATURES = { new Element("feature", new String[] {
			"var" }, new String[] { XEP0136NS + ":" + AUTO }),
			new Element("feature", new String[] { "var" }, new String[] { XEP0136NS +
					":manage" }) };
	
	private static final String DEFAULT_STORE_METHOD_KEY = "default-store-method";
	private static final String REQUIRED_STORE_METHOD_KEY = "required-store-method";
	private static final String STORE_MUC_MESSAGES_KEY = "store-muc-messages";
	
//	static {
//		HashSet tmpTYPES = new HashSet<StanzaType>();
//		tmpTYPES.add(null);
//		tmpTYPES.addAll(EnumSet.of(StanzaType.normal, StanzaType.chat, 
//			StanzaType.get, StanzaType.set, StanzaType.error, StanzaType.result));
//		TYPES = Collections.unmodifiableSet(tmpTYPES);
//	}

	//~--- fields ---------------------------------------------------------------

	private StoreMethod globalDefaultStoreMethod = StoreMethod.Body;
	private StoreMethod globalRequiredStoreMethod = StoreMethod.False;
	private StoreMuc globalStoreMucMessages = StoreMuc.User;
	
	private final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	private JID              ma_jid    = null;

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
		
		VHostItemHelper.register();

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
		
		// setting required and default level of archiving
		if (settings.containsKey(REQUIRED_STORE_METHOD_KEY)) {
			globalRequiredStoreMethod = StoreMethod.valueof((String) settings.get(REQUIRED_STORE_METHOD_KEY));
		}
		if (settings.containsKey(DEFAULT_STORE_METHOD_KEY)) {
			globalDefaultStoreMethod = StoreMethod.valueof((String) settings.get(DEFAULT_STORE_METHOD_KEY));
		}
		if (globalDefaultStoreMethod.ordinal() < globalRequiredStoreMethod.ordinal()) {
			globalDefaultStoreMethod  = globalRequiredStoreMethod;
		}
		if (settings.containsKey(STORE_MUC_MESSAGES_KEY)) {
			globalStoreMucMessages = StoreMuc.valueof((String) settings.get(STORE_MUC_MESSAGES_KEY));
		}
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
			NonAuthUserRepository repo, Queue<Packet> results, Map<String, Object> settings)
					throws XMPPException {
		if (session == null) {
			return;
		}
		try {
			if (Message.ELEM_NAME == packet.getElemName()) {
				
				// ignoring packets resent from c2s for redelivery as processing
				// them would create unnecessary duplication of messages in archive
				if (C2SDeliveryErrorProcessor.isDeliveryError(packet)) {
					log.log(Level.FINEST, "not processong packet as it is delivery error = {0}", packet);
					return;
				}
				
				StanzaType type = packet.getType();
				Element body = packet.getElement().findChildStaticStr(Message.MESSAGE_BODY_PATH);

				if ((body == null) || (
						(type != null) 
						&& (type != StanzaType.chat) 
						&& (type != StanzaType.normal))) {
						//&& ((!isStoreMucMessages(session)) && type == StanzaType.groupchat) )) {
					if (type != StanzaType.groupchat || body == null || !isStoreMucMessages(session)) {
						if (log.isLoggable(Level.FINEST)) {
							log.log(Level.FINEST, "not logging packet as type is {0}, {1}, {2}, {3}, {4}",
									new Object[]{type, type != StanzaType.chat, type != StanzaType.normal,
										!isStoreMucMessages(session), type != StanzaType.groupchat});
						}
						return;
					}
				}
				
				// we need to log groupchat messages only sent from MUC to user as MUC needs to confirm
				// message delivery by sending message back
				if (type == StanzaType.groupchat && session.isUserId(packet.getStanzaFrom().getBareJID())) {
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "not storing message sent to MUC room from user = {0}", packet.toString());
					}
					return;
				}

				boolean auto = getAutoSave(session);
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "got state of automatic storage of messages as {0}", auto);
				}

				if (auto && body != null) {
					StoreMethod storeMethod = getStoreMethod(session);
					if (log.isLoggable(Level.FINEST)) {
						log.log(Level.FINEST, "store method is {0}", storeMethod);
					}
					if (storeMethod == StoreMethod.False) {
						// ignoring as False means we should not store anything
						return;
					}
					
					// redirecting to message archiving component
					Packet result = packet.copyElementOnly();

					result.setPacketTo(ma_jid);
					result.getElement().addAttribute(OWNER_JID, session.getBareJID().toString());
					switch (storeMethod) {
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
			} else if (Iq.ELEM_NAME == packet.getElemName()) {
				if (ma_jid.equals(packet.getPacketFrom())) {
					JID    connId = session.getConnectionId(packet.getStanzaTo());
					Packet result = packet.copyElementOnly();

					result.setPacketTo(connId);
					results.offer(result);

					return;
				}
				if ((packet.getType() != StanzaType.get) && (packet.getType() != StanzaType
						.set)) {
					return;
				}

				Element auto = packet.getElement().getChild("auto");
				Element pref = packet.getElement().getChild("pref");
				StoreMethod requiredStoreMethod = getRequiredStoreMethod(session);

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

						boolean isStoreMuc = isStoreMucMessages(session);
						defaultEl.setAttribute(MUC_SAVE, Boolean.toString(isStoreMuc));
								
						StoreMethod storeMethod = getStoreMethod(session);
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
					} else if (packet.getType() == StanzaType.set) {
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
												errorMsg = "Required minimal message archiving level is " + requiredStoreMethod.toString();
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
										}
										else {
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
										if (StoreMuc.User != VHostItemHelper.getStoreMucMessages(session.getDomain(), globalStoreMucMessages)) {
											error = Authorization.NOT_ALLOWED;
											errorMsg = "Store MUC value is not allowed to be changed by user";	
										} else if ((!"true".equals(storeMuc)) && (!"false".equals(storeMuc))) {
											error = Authorization.BAD_REQUEST;
											errorMsg = "Value of muc-save attribute must be 'true' or 'false'";
										} else {
											StoreMethod sm = storeMethod == null ? getStoreMethod(session) : storeMethod;
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
										errorMsg = "Required minimal message archiving level is " + requiredStoreMethod.toString() 
												+ " and that requires automatic archiving to be enabled";
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
							results.offer(error.getResponseMessage(
									packet, errorMsg, true));
						}
						else {
							try {
								if (autoSave != null) {
									this.setAutoSave(session, autoSave);
								}
								if (storeMethod != null) {
									this.setStoreMethod(session, storeMethod);
								}
								if (expire != null) {
									session.setData(SETTINGS, EXPIRE, expire);
								}
								if (storeMuc != null) {
									session.setData(SETTINGS, MUC_SAVE, storeMuc);
									session.putCommonSessionData(ID + "/" + MUC_SAVE, Boolean.parseBoolean(storeMuc) ? StoreMuc.True : StoreMuc.False);
								}
								results.offer(packet.okResult((String) null, 0));
								
								// shouldn't we notify other connected resources? see section 2.4.of XEP-0136
							}
							catch (TigaseDBException ex) {
								results.offer(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet, null, false));
							}
						}
					} else {
						results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet, null,
								true));
					}
				} else {
					String  val  = auto.getAttributeStaticStr("save");
					if (val == null) val = "";
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
							results.offer(Authorization.BAD_REQUEST.getResponseMessage(packet,
									"Save value is incorrect or missing", false));
							return;
					}
					
					if (!save && requiredStoreMethod != StoreMethod.False) {
						results.offer(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet, 
								"Required minimal message archiving level is " + requiredStoreMethod.toString() 
								+ " and that requires automatic archiving to be enabled", false));
						return;
					}
					if (save && !VHostItemHelper.isEnabled(session.getDomain())) {
						results.offer(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet, 
								"Message archiving is not allowed for domain " + session.getDomainAsJID().toString(), false));
						return;
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
						log.log(Level.WARNING, "Error setting Message Archive state: {0}", ex
								.getMessage());
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
	public String[][] supElementNamePaths() {
		return ELEMENT_PATHS;
		
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

//	/**
//	 * Method description
//	 *
//	 *
//	 * @return
//	 */
//	@Override
//	public Set<StanzaType> supTypes() {
//		return TYPES;
//	}
	
	//~--- get methods ----------------------------------------------------------

	private boolean getAutoSave(final XMPPResourceConnection session)
					throws NotAuthorizedException {
		StoreMethod requiredStoreMethod = getRequiredStoreMethod(session);

		if (requiredStoreMethod != StoreMethod.False)
			return true;
		
		Boolean auto = (Boolean) session.getCommonSessionData(ID + "/" + AUTO);

		if (auto == null) {
			try {
				String data = session.getData(SETTINGS, AUTO, "false");

				auto = Boolean.parseBoolean(data);
				
				// if message archive is enabled but it is not allowed for domain
				// then we should disable it
				if (!VHostItemHelper.isEnabled(session.getDomain()) && auto) {
					auto = false;
					session.setData(SETTINGS, AUTO, String.valueOf(auto));
				}
				
				session.putCommonSessionData(ID + "/" + AUTO, auto);
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Error getting Message Archive state: {0}", ex
						.getMessage());
				auto = false;
			}
		}

		return auto;
	}

	private StoreMethod getRequiredStoreMethod(XMPPResourceConnection session) {
		return StoreMethod.valueof(VHostItemHelper.getRequiredStoreMethod(session.getDomain(), globalRequiredStoreMethod.toString()));
	}
	
	private StoreMethod getStoreMethod(XMPPResourceConnection session) 
					throws NotAuthorizedException {
		StoreMethod save = (StoreMethod) session.getCommonSessionData(ID + "/" + DEFAULT_SAVE);
		
		if (save == null) {
			try {
				String data = session.getData(SETTINGS, DEFAULT_SAVE, null);
				if (data == null) {
					data = VHostItemHelper.getDefaultStoreMethod(session.getDomain(), globalDefaultStoreMethod.toString());
				}

				save = StoreMethod.valueof(data);
				session.putCommonSessionData(ID + "/" + DEFAULT_SAVE, save);
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Error getting Message Archive state: {0}", ex
						.getMessage());
				save = StoreMethod.False;
			}		
			
			StoreMethod requiredStoreMethod = getRequiredStoreMethod(session);
			if (save.ordinal() < requiredStoreMethod.ordinal()) {
				save = requiredStoreMethod;
				session.putCommonSessionData(ID + "/" + DEFAULT_SAVE, save);
				try {
					setStoreMethod(session, save);
				} catch (TigaseDBException ex) {
					log.log(Level.WARNING, "Error updating message archiving level to required level {0}", ex.getMessage());
				}
			}
		}
		
		return save;
	}
	
	private boolean isStoreMucMessages(XMPPResourceConnection session) 
			throws NotAuthorizedException {
		StoreMuc save = (StoreMuc) session.getCommonSessionData(ID + "/" + MUC_SAVE);
		if (save == null) {
			try {
				String val = session.getData(SETTINGS, MUC_SAVE, null);
				if (val == null) {
					save = VHostItemHelper.getStoreMucMessages(session.getDomain(), globalStoreMucMessages);
				} else {
					save = StoreMuc.valueof(val);
				}		
			} catch (TigaseDBException ex) {
				log.log(Level.WARNING, "Error getting Message Archive state of storage of MUC messages: {0}", ex
						.getMessage());
				save = StoreMuc.User;
			}	
			session.putCommonSessionData(ID + "/" + MUC_SAVE, save);
		}
		return save == StoreMuc.True;
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
		session.putCommonSessionData(ID + "/" + AUTO, auto);
	}
	
	public void setStoreMethod(XMPPResourceConnection session, StoreMethod save) 
					throws NotAuthorizedException, TigaseDBException {
		session.setData(SETTINGS, DEFAULT_SAVE, (save == null ? StoreMethod.False : save).toString());
		session.putCommonSessionData(ID + "/" + DEFAULT_SAVE, save);
	}
}


//~ Formatted in Tigase Code Convention on 13/03/13
