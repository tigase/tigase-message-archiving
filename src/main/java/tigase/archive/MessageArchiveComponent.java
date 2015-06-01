/*
 * MessageArchiveComponent.java
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

import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import static tigase.archive.MessageArchivePlugin.*;
import tigase.archive.db.MessageArchiveRepository;
import tigase.archive.db.MessageArchiveRepository.Direction;
import tigase.conf.Configurable;
import tigase.conf.ConfigurationException;
import tigase.db.DBInitException;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;
import tigase.osgi.ModulesManagerImpl;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.*;

/**
 *
 * @author andrzej
 */
public class MessageArchiveComponent
				extends AbstractMessageReceiver {
	private static final Logger log = Logger.getLogger(MessageArchiveComponent.class
			.getCanonicalName());
	private static final String			  MSG_ARCHIVE_REPO_CLASS_PROP_KEY =
			"archive-repo-class";
	private static final String           MSG_ARCHIVE_REPO_URI_PROP_KEY =
			"archive-repo-uri";

	private static final boolean			  DEF_TAGS_SUPPORT_PROP_VAL = false;
	private static final String			  TAGS_SUPPORT_PROP_KEY = "tags-support";
	
	//~--- fields ---------------------------------------------------------------

	protected MessageArchiveRepository msg_repo = null;
	private boolean tagsSupport = false;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 */
	public MessageArchiveComponent() {
		super();
		setName("message-archive");
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 */
	@Override
	public void processPacket(Packet packet) {
		if ((packet.getStanzaTo() != null) &&!getComponentId().equals(packet.getStanzaTo())) {
			storeMessage(packet);

			return;
		}
		try {
			try {
				processActionPacket(packet);
			} catch (XMPPException ex) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, "internal server while processing packet = " + packet
							.toString(), ex);
				}
				addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
						(String) null, true));
			}
		} catch (PacketErrorTypeException ex) {
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "error with packet in error state - ignoring packet = {0}",
						packet);
			}
		}
	}

	@Override
	public void release() {
		super.release();
		
		if (msg_repo != null) {
			msg_repo.destroy();
			msg_repo = null;
		}
	}
	
	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param params
	 *
	 * @return
	 */
	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params) {
		Map<String, Object> defs   = super.getDefaults(params);
		String              db_uri = (String) params.get(Configurable.USER_REPO_URL_PROP_KEY);

		defs.put(TAGS_SUPPORT_PROP_KEY, DEF_TAGS_SUPPORT_PROP_VAL);
		
		if (db_uri == null) {
			db_uri = (String) params.get(Configurable.GEN_USER_DB_URI);
		}
		if (db_uri != null) {
			defs.put(MSG_ARCHIVE_REPO_URI_PROP_KEY, db_uri);
		}

		return defs;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getDiscoDescription() {
		return "Message Archiving (XEP-0136) Support";
	}

	//~--- set methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param props
	 * @throws tigase.conf.ConfigurationException
	 */
	@Override
	public void setProperties(Map<String, Object> props) throws ConfigurationException {
		try {
			super.setProperties(props);
			
			if (props.containsKey(TAGS_SUPPORT_PROP_KEY)) {
				tagsSupport = (Boolean) props.get(TAGS_SUPPORT_PROP_KEY);
			}
			
			if (props.size() == 1) {
				return;
			}

			Map<String, String> repoProps = new HashMap<String, String>(4);

			for (Entry<String, Object> entry : props.entrySet()) {
				if ((entry.getKey() == null) || (entry.getValue() == null)) {
					continue;
				}
				repoProps.put(entry.getKey(), entry.getValue().toString());
			}

			String repoClsName = (String) props.get(MSG_ARCHIVE_REPO_CLASS_PROP_KEY);
			String uri = (String) props.get(MSG_ARCHIVE_REPO_URI_PROP_KEY);

			if (uri != null) {
				Class<? extends MessageArchiveRepository> repoCls = null;
				if (repoClsName == null)
					repoCls = RepositoryFactory.getRepoClass(MessageArchiveRepository.class, uri);
				else {
					try {
						repoCls = (Class<? extends MessageArchiveRepository>) ModulesManagerImpl.getInstance().forName(repoClsName);
					} catch (ClassNotFoundException ex) {
						log.log(Level.SEVERE, "Could not find class " + repoClsName + " an implementation of MessageArchive repository", ex);
						throw new ConfigurationException("Could not find class " + repoClsName + " an implementation of MessageArchive repository", ex);
					}
				}
				if (repoCls == null && repoClsName == null) {
					throw new ConfigurationException("Not found implementation of MessageArchive repository for URI = " + uri);
				}
				MessageArchiveRepository old_msg_repo = msg_repo;
				msg_repo = repoCls.newInstance();
				msg_repo.initRepository(uri, repoProps);
				if (old_msg_repo != null) {
					// if we have old instance and new is initialized then
					// destroy the old one to release resources
					old_msg_repo.destroy();
				}
			} else {
				log.log(Level.SEVERE, "repository uri is NULL!");
			}
		} catch (DBInitException ex) {	
			throw new ConfigurationException("Could not initialize MessageArchive repository", ex);
		} catch (InstantiationException ex) {
			log.log(Level.SEVERE, "Could not initialize MessageArchive repository", ex);
			throw new ConfigurationException("Could not initialize MessageArchive repository", ex);
		} catch (IllegalAccessException ex) {
			log.log(Level.SEVERE, "Could not initialize MessageArchive repository", ex);
			throw new ConfigurationException("Could not initialize MessageArchive repository", ex);
		}
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param packet
	 *
	 * @throws PacketErrorTypeException
	 * @throws XMPPException
	 */
	protected void processActionPacket(Packet packet)
					throws PacketErrorTypeException, XMPPException {
		for (Element child : packet.getElement().getChildren()) {
			if (child.getName() == "list") {
				switch (packet.getType()) {
				case get :
					listCollections(packet, child);

					break;

				default :
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
							"Request type is incorrect", false));

					break;
				}
			} else if (child.getName() == "retrieve") {
				switch (packet.getType()) {
				case get :
					getMessages(packet, child);

					break;

				default :
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
							"Request type is incorrect", false));

					break;
				}
			} else if (child.getName() == "remove") {
				switch (packet.getType()) {
				case set :
					removeMessages(packet, child);

					break;

				default :
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
							"Request type is incorrect", false));

					break;
				}
			} else if (child.getName() == "save") {
				switch (packet.getType()) {
				case set :
					saveMessages(packet, child);
					break;
				default:
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet, "Request type is incorrect", false));
					break;
				}
			} else if (child.getName() == "tags") {
				switch (packet.getType()) {
				case set :
					queryTags(packet, child);
					break;
				default:
					addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
						"Request type is incorrect", false));						
				}
			}
		}
	}

	private void listCollections(Packet packet, Element list) throws XMPPException {
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.fromElement(list, tagsSupport);

			List<Element> chats = msg_repo.getCollections(packet.getStanzaFrom().getBareJID(), criteria);

			Element retList = new Element(LIST);
			retList.setXMLNS(XEP0136NS);

			if (chats != null && !chats.isEmpty()) {
				retList.addChildren(chats);
			}
			
			criteria.prepareResult(retList);
			
			addOutPacket(packet.okResult(retList, 0));
		} catch (ParseException e) {
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Date parsing error", true));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error listing collections", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}

	private void removeMessages(Packet packet, Element remove) throws XMPPException {
		if ((remove.getAttributeStaticStr("with") == null) || (remove.getAttributeStaticStr(
				"start") == null) || (remove.getAttributeStaticStr("end") == null)) {
			addOutPacket(Authorization.NOT_ACCEPTABLE.getResponseMessage(packet,
					"Parameters with, start, end cannot be null", true));

			return;
		}
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.fromElement(remove, tagsSupport);

			msg_repo.removeItems(packet.getStanzaFrom().getBareJID(), criteria.getWith(), 
					criteria.getStart(), criteria.getEnd());
			addOutPacket(packet.okResult((Element) null, 0));
		} catch (ParseException e) {
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Date parsing error", true));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error removing messages", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}

	private void storeMessage(Packet packet) {
		String ownerStr = packet.getAttributeStaticStr(OWNER_JID);

		if (ownerStr != null) {
			packet.getElement().removeAttribute(OWNER_JID);

			BareJID owner    = BareJID.bareJIDInstanceNS(ownerStr);
			Direction direction = Direction.getDirection(owner, packet.getStanzaFrom().getBareJID());
			JID buddy    = direction == Direction.outgoing
					? packet.getStanzaTo()
					: packet.getStanzaFrom();

			Element msg = packet.getElement();
			Date timestamp  = null;
			Element delay= msg.findChildStaticStr(Message.MESSAGE_DELAY_PATH);
			if (delay != null) {
				try {
					String stamp = delay.getAttributeStaticStr("stamp");
					timestamp = TimestampHelper.parseTimestamp(stamp);
				} catch (ParseException e1) {
					// we need to set timestamp to current date if parsing of timestamp failed
					timestamp = new java.util.Date();
				}
			} else {
				timestamp = new java.util.Date();
			}			
			
			Set<String> tags = null;
			if (tagsSupport) 
				tags = TagsHelper.extractTags(msg);
			
			msg_repo.archiveMessage(owner, buddy, direction, timestamp, msg, tags);
		} else {
			log.log(Level.INFO, "Owner attribute missing from packet: {0}", packet);
		}
	}

	//~--- get methods ----------------------------------------------------------

	private void getMessages(Packet packet, Element retrieve) throws XMPPException {
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.fromElement(retrieve, tagsSupport);

			List<Element> items = msg_repo.getItems(packet.getStanzaFrom().getBareJID(),
					criteria);

			Element retList = new Element("chat");

			if (criteria.getWith() != null)
				retList.setAttribute("with", criteria.getWith());
			if (criteria.getStart() != null)
				retList.setAttribute("start", TimestampHelper.format(criteria.getStart()));
			
			retList.setXMLNS(XEP0136NS);
			if (!items.isEmpty()) {
				retList.addChildren(items);
			}
			
			criteria.prepareResult(retList);

			addOutPacket(packet.okResult(retList, 0));
		} catch (ParseException e) {
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Date parsing error", true));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error retrieving messages", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}
	
	private void queryTags(Packet packet, Element tagsEl) throws XMPPException {
		try {
			AbstractCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.getRSM().fromElement(tagsEl);
			
			String startsWith = tagsEl.getAttributeStaticStr("like");
			if (startsWith == null)
				startsWith = "";
			
			List<String> tags = msg_repo.getTags(packet.getStanzaFrom().getBareJID(), startsWith, criteria);
			
			tagsEl = new Element("tags", new String[] {"xmlns" }, new String[] { AbstractCriteria.QUERTY_XMLNS});
			for (String tag : tags) {
				tagsEl.addChild(new Element("tag", tag));
			}
			
			RSM rsm = criteria.getRSM();
			if (rsm.getCount() == null || rsm.getCount() != 0)
				tagsEl.addChild(rsm.toElement());			
			
			addOutPacket(packet.okResult(tagsEl, 0));
		} catch (TigaseDBException e) {
			log.log(Level.SEVERE, "Error retrieving messages", e);
			addOutPacket(Authorization.INTERNAL_SERVER_ERROR.getResponseMessage(packet,
					"Database error occured", true));
		}
	}
	
	private void saveMessages(Packet packet, Element save) throws XMPPException {
		try {
			List<Element> chats = save.getChildren();
			Element saveResult = new Element("save");
			saveResult.setAttributes(save.getAttributes());
			if (chats != null) {
				for (Element chat : chats) {
					if ("chat" != chat.getName()) {
						continue;
					}

					Date start = TimestampHelper.parseTimestamp(chat.getAttributeStaticStr("start"));
					BareJID owner = packet.getStanzaFrom().getBareJID();
					String with = chat.getAttributeStaticStr("with");
					if (with == null) {
						// maybe we should ignore this??
						addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet, "Missing 'with' attribute", true));
						return;
					}
					JID buddy = JID.jidInstance(with);

					List<Element> children = chat.getChildren();
					if (children != null) {
						for (Element child : children) {
							Direction direction = Direction.getDirection(child.getName());
							// should we do something about this?
							if (direction == null) {
								continue;
							}

							Date timestamp = null;
							String secsAttr = child.getAttributeStaticStr("secs");
							String utcAttr = child.getAttributeStaticStr("utc");
							if (secsAttr != null) {
								long secs = Long.parseLong(secsAttr);
								timestamp = new Date(start.getTime() + (secs * 1000));
							} else if (utcAttr != null) {
								timestamp = TimestampHelper.parseTimestamp(utcAttr);
							}
							if (timestamp == null) {
								// if timestamp is not set assume that secs was 0
								timestamp = start;
							}
							Element msg = child.clone();
							msg.setName("message");
							msg.removeAttribute("secs");
							msg.removeAttribute("utc");

							switch (direction) {
								case incoming:
									msg.setAttribute("from", buddy.toString());
									msg.setAttribute("to", owner.toString());
									break;
								case outgoing:
									msg.setAttribute("from", owner.toString());
									msg.setAttribute("to", buddy.toString());
									break;
							}

							Set<String> tags = null;
							if (tagsSupport) {
								tags = TagsHelper.extractTags(msg);
							}

							msg_repo.archiveMessage(owner, buddy, direction, timestamp, msg, tags);
						}
					}
					Element chatResult = new Element("chat");
					chatResult.setAttributes(chat.getAttributes());
					saveResult.addChild(chatResult);
				}
			}
			
			if (!"true".equals(save.getAttributeStaticStr("auto"))) {
				Packet result = packet.okResult(saveResult, 0);
				addOutPacket(result);
			}
		} catch (ParseException e) {
			log.log(Level.SEVERE, "Error parsing timestamp", e);
			addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
					"Invalid format of timestamp", true));			
		} catch (TigaseStringprepException ex) {
			log.log(Level.SEVERE, "Error parsing with attribute as jid", ex);
			addOutPacket(Authorization.BAD_REQUEST.getResponseMessage(packet,
					"Invalid JID as with attribute", true));			
		}
	}
}


//~ Formatted in Tigase Code Convention on 13/10/15
