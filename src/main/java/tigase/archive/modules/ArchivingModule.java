/*
 * ArchivingModule.java
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
package tigase.archive.modules;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.TagsHelper;
import tigase.archive.db.MessageArchiveRepository;
import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.kernel.beans.Bean;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.util.TimestampHelper;
import tigase.xml.Element;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;

import java.text.ParseException;
import java.util.Date;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static tigase.archive.processors.MessageArchivePlugin.OWNER_JID;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "archiving", parent = MessageArchiveComponent.class)
public class ArchivingModule extends AbstractModule {

	private static final Logger log = Logger.getLogger(ArchivingModule.class.getCanonicalName());

	private final TimestampHelper timestampHelper = new TimestampHelper();

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
		String ownerStr = packet.getAttributeStaticStr(OWNER_JID);

		if (ownerStr != null) {
			packet.getElement().removeAttribute(OWNER_JID);
			if (log.isLoggable(Level.FINEST)) {
				log.log(Level.FINEST, "for user {0} storing message: {1}",
						new Object[]{ownerStr, packet.toString()});
			}

			BareJID owner    = BareJID.bareJIDInstanceNS(ownerStr);
			MessageArchiveRepository.Direction direction = MessageArchiveRepository.Direction.getDirection(owner, packet.getStanzaFrom().getBareJID());
			JID buddy    = direction == MessageArchiveRepository.Direction.outgoing
					? packet.getStanzaTo()
					: packet.getStanzaFrom();

			Element msg = packet.getElement();
			Date timestamp  = null;
			Element delay= msg.findChildStaticStr(Message.MESSAGE_DELAY_PATH);
			if (delay != null) {
				try {
					String stamp = delay.getAttributeStaticStr("stamp");
					timestamp = timestampHelper.parseTimestamp(stamp);
				} catch (ParseException e1) {
					// we need to set timestamp to current date if parsing of timestamp failed
					timestamp = new java.util.Date();
				}
			} else {
				timestamp = new java.util.Date();
			}

			Set<String> tags = null;
			if (config.isTagSupportEnabled())
				tags = TagsHelper.extractTags(msg);

			msg_repo.archiveMessage(owner, buddy, direction, timestamp, msg, tags);
		} else {
			log.log(Level.INFO, "Owner attribute missing from packet: {0}", packet);
		}
	}

	@Override
	public boolean canHandle(Packet packet) {
		return  Message.ELEM_NAME == packet.getElemName() || (packet.getStanzaTo() != null && !config.getComponentId().equals(packet.getStanzaTo()) && packet.getAttributeStaticStr(OWNER_JID) != null);
	}
}
