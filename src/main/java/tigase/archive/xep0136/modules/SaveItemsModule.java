/*
 * SaveItemsModule.java
 *
 * Tigase Message Archiving Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
package tigase.archive.xep0136.modules;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.TagsHelper;
import tigase.archive.TimestampHelper;
import tigase.archive.db.MessageArchiveRepository;
import tigase.archive.modules.AbstractModule;
import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.kernel.beans.Bean;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "saveItems", parent = MessageArchiveComponent.class)
public class SaveItemsModule extends AbstractModule {

	private static final String SAVE_ELEM = "save";

	@Override
	public String[] getFeatures() {
		return new String[0];
	}

	@Override
	public Criteria getModuleCriteria() {
		return null;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		Element save = packet.getElement().getChild(SAVE_ELEM, MA_XMLNS);

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
						throw new ComponentException(Authorization.BAD_REQUEST, "Missing 'with' attribute");
					}
					JID buddy = JID.jidInstance(with);

					List<Element> children = chat.getChildren();
					if (children != null) {
						for (Element child : children) {
							MessageArchiveRepository.Direction direction = MessageArchiveRepository.Direction.getDirection(child.getName());
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
							if (config.isTagSupportEnabled()) {
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
				packetWriter.write(result);
			}
		} catch (ParseException e) {
			throw new ComponentException(Authorization.BAD_REQUEST,
					"Invalid format of timestamp");
		}

	}

	@Override
	public boolean canHandle(Packet packet) {
		return packet.getElement().getChild(SAVE_ELEM, MA_XMLNS) != null;
	}
}
