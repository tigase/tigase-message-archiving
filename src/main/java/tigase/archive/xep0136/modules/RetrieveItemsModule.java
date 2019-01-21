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
package tigase.archive.xep0136.modules;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.QueryCriteria;
import tigase.archive.db.MessageArchiveRepository;
import tigase.archive.modules.AbstractModule;
import tigase.archive.xep0136.Xep0136QueryParser;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.criteria.Criteria;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;

import java.util.List;

import static tigase.archive.processors.Xep0136MessageArchivingProcessor.XEP0136NS;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "retrieveItems", parent = MessageArchiveComponent.class, active = true)
public class RetrieveItemsModule
		extends AbstractModule {

	private static final String RETRIEVE_ELEM = "retrieve";
	@Inject
	private Xep0136ItemHandler itemHandler;
	@Inject
	private Xep0136QueryParser queryParser;
	private TimestampHelper timestampHelper = new TimestampHelper();

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
		Element retrieve = packet.getElement().getChild(RETRIEVE_ELEM, MA_XMLNS);
		getMessages(packet, retrieve);
	}

	@Override
	public boolean canHandle(Packet packet) {
		return packet.getElement().getChild(RETRIEVE_ELEM, MA_XMLNS) != null;
	}

	private void getMessages(Packet packet, Element retrieve) throws ComponentException, TigaseStringprepException {
		try {
			QueryCriteria query = msg_repo.newQuery();
			query.setUseMessageIdInRsm(false);
			queryParser.parseQuery(query, packet);

			msg_repo.queryItems(query, itemHandler);

			List<Element> items = query.getItems();

			Element retList = new Element("chat");

			if (query.getWith() != null) {
				retList.setAttribute("with", query.getWith().toString());
			}
			if (query.getStart() != null) {
				retList.setAttribute("start", timestampHelper.format(query.getStart()));
			}

			retList.setXMLNS(XEP0136NS);
			if (!items.isEmpty()) {
				retList.addChildren(items);
			}

			query.prepareResult(retList);

			packetWriter.write(packet.okResult(retList, 0));
		} catch (RepositoryException e) {
			throw new RuntimeException("Error retrieving items", e);
		}
	}

	@Bean(name = "xep0136ItemHandler", parent = MessageArchiveComponent.class, active = true)
	public static class Xep0136ItemHandler<Q extends QueryCriteria, I extends MessageArchiveRepository.Item>
			implements MAMRepository.ItemHandler<Q, MAMRepository.Item> {

		@Override
		public void itemFound(Q query, MAMRepository.Item item) {
			if (!(item instanceof MessageArchiveRepository.Item)) {
				throw new RuntimeException(
						"Invalid class of repository item, got = " + item.getClass().getCanonicalName());
			}

			itemFound(query, (I) item);
		}

		public void itemFound(Q query, I item) {
			Element itemEl = new Element(item.getDirection().toElementName());

			// Now we should send all elements of a message so as we can store not only <body/>
			// element. If we will store only <body/> element then only this element will
			// be available in store
			//item.addChild(msg.getChild("body"));
			Element msg = item.getMessage();
			itemEl.addChildren(msg.getChildren());

			if (query.getStart() == null) {
				query.setStart(item.getTimestamp());
				itemEl.setAttribute("secs", "0");
			} else {
				itemEl.setAttribute("secs", String.valueOf(
						(item.getTimestamp().getTime() - query.getStart().getTime()) / 1000));
			}
			if (item.getWith() != null) {
				itemEl.setAttribute("with", item.getWith());
			}
			if ("groupchat".equals(msg.getAttributeStaticStr("type"))) {
				JID from = JID.jidInstanceNS(msg.getAttributeStaticStr("from"));
				if (from != null && from.getResource() != null) {
					itemEl.setAttribute("name", from.getResource());
				}
			}

			query.addItem(itemEl);
			if (query.getRsm().getFirst() == null) {
				query.getRsm().setFirst(item.getId());
			}
			query.getRsm().setLast(item.getId());
		}
	}
}
