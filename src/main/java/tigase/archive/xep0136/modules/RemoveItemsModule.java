/*
 * RemoveItemsModule.java
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
package tigase.archive.xep0136.modules;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.QueryCriteria;
import tigase.archive.modules.AbstractModule;
import tigase.archive.xep0136.Xep0136QueryParser;
import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.db.TigaseDBException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "removeItems", parent = MessageArchiveComponent.class, active = true)
public class RemoveItemsModule
		extends AbstractModule {

	private static final String REMOVE_ELEM = "remove";

	@Inject
	private Xep0136QueryParser queryParser;

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
		Element remove = packet.getElement().getChild(REMOVE_ELEM, MA_XMLNS);

		if ((remove.getAttributeStaticStr("with") == null) || (remove.getAttributeStaticStr("start") == null) ||
				(remove.getAttributeStaticStr("end") == null)) {
			throw new ComponentException(Authorization.NOT_ACCEPTABLE, "Parameters with, start, end cannot be null");
		}

		try {
			QueryCriteria query = msg_repo.newQuery();
			queryParser.parseQuery(query, packet);

			msg_repo.removeItems(packet.getStanzaFrom().getBareJID(), query.getWith().toString(), query.getStart(),
								 query.getEnd());
			packetWriter.write(packet.okResult((Element) null, 0));
		} catch (TigaseDBException e) {
			throw new RuntimeException("Error removing items", e);
		}

	}

	@Override
	public boolean canHandle(Packet packet) {
		return packet.getElement().getChild(REMOVE_ELEM, MA_XMLNS) != null;
	}
}
