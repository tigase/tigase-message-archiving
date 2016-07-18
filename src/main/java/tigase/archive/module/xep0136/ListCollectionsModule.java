/*
 * ListCollectionsModule.java
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
package tigase.archive.module.xep0136;

import tigase.archive.QueryCriteria;
import tigase.archive.MessageArchiveComponent;
import tigase.archive.module.AbstractModule;
import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.db.TigaseDBException;
import tigase.kernel.beans.Bean;
import tigase.server.Packet;
import tigase.util.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

import java.text.ParseException;
import java.util.List;

import static tigase.archive.MessageArchivePlugin.LIST;
import static tigase.archive.MessageArchivePlugin.XEP0136NS;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "listCollections", parent = MessageArchiveComponent.class)
public class ListCollectionsModule extends AbstractModule {

	private static final String LIST_ELEM = "list";

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
		Element list = packet.getElement().getChild(LIST_ELEM, MA_XMLNS);
		listCollections(packet, list);
	}

	@Override
	public boolean canHandle(Packet packet) {
		return packet.getElement().getChild(LIST_ELEM, MA_XMLNS) != null;
	}

	private void listCollections(Packet packet, Element list) throws ComponentException, TigaseStringprepException {
		try {
			QueryCriteria criteria = msg_repo.newCriteriaInstance();
			criteria.fromElement(list, config.isTagSupportEnabled());

			List<Element> chats = msg_repo.getCollections(packet.getStanzaFrom().getBareJID(), criteria);

			Element retList = new Element(LIST);
			retList.setXMLNS(XEP0136NS);

			if (chats != null && !chats.isEmpty()) {
				retList.addChildren(chats);
			}

			criteria.prepareResult(retList);

			packetWriter.write(packet.okResult(retList, 0));
		} catch (ParseException e) {
			throw new ComponentException(Authorization.BAD_REQUEST, "Date parsing error", e);
		} catch (TigaseDBException e) {
			throw new RuntimeException("Error listing collections", e);
		}
	}

}
