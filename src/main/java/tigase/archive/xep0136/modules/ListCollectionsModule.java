/*
 * ListCollectionsModule.java
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
import tigase.archive.db.MessageArchiveRepository;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static tigase.archive.processors.Xep0136MessageArchivingProcessor.LIST;
import static tigase.archive.processors.Xep0136MessageArchivingProcessor.XEP0136NS;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "listCollections", parent = MessageArchiveComponent.class, active = true)
public class ListCollectionsModule extends AbstractModule {

	private static final String LIST_ELEM = "list";

	@Inject
	private MessageArchiveRepository.CollectionHandler<QueryCriteria> collectionHandler;

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
		Element list = packet.getElement().getChild(LIST_ELEM, MA_XMLNS);
		listCollections(packet, list);
	}

	@Override
	public boolean canHandle(Packet packet) {
		return packet.getElement().getChild(LIST_ELEM, MA_XMLNS) != null;
	}

	private void listCollections(Packet packet, Element list) throws ComponentException, TigaseStringprepException {
		try {
			QueryCriteria query = msg_repo.newQuery();
			query.setUseMessageIdInRsm(false);
			queryParser.parseQuery(query, packet);

			msg_repo.queryCollections(query, collectionHandler);

			List<Element> chats = query.getCollections();

			Element retList = new Element(LIST);
			retList.setXMLNS(XEP0136NS);

			if (chats != null && !chats.isEmpty()) {
				retList.addChildren(chats);
			}

			query.prepareResult(retList);

			packetWriter.write(packet.okResult(retList, 0));
		} catch (TigaseDBException e) {
			throw new RuntimeException("Error listing collections", e);
		}
	}

	@Bean(name = "xep0136CollectionHandler", parent = MessageArchiveComponent.class, active = true)
	public static class Xep0136CollectionHandler<Q extends QueryCriteria> implements MessageArchiveRepository.CollectionHandler<Q> {

		private static final SimpleDateFormat TIMESTAMP_FORMATTER1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX");

		static {
			TIMESTAMP_FORMATTER1.setTimeZone(TimeZone.getTimeZone("UTC"));
		}

		@Override
		public void collectionFound(Q query, String with, Date start, String type) {
			String formattedStart = null;
			synchronized (TIMESTAMP_FORMATTER1) {
				formattedStart = TIMESTAMP_FORMATTER1.format(start);
			}
			Element elem = new Element("chat", new String[] { "with", "start" },
					new String[] { with, formattedStart });
			if (type != null && !type.isEmpty()) {
				elem.addAttribute("type", type);
			}
			query.addCollection(elem);
		}
	}

}
