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
package tigase.archive.db;

import tigase.archive.Query;
import tigase.db.DataSource;
import tigase.db.TigaseDBException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.rsm.RSM;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * AbstractMessageArchiveRepository contains methods commonly used by other implementations to eliminate code
 * multiplication.
 *
 * @author andrzej
 */
public abstract class AbstractMessageArchiveRepository<Q extends Query, DS extends DataSource, ADP extends AbstractMessageArchiveRepository.AddMessageAdditionalDataProvider>
		implements MessageArchiveRepository<Q, DS> {

	protected static final String[] MSG_BODY_PATH = {"message", "body"};
	protected static final String[] MSG_SUBJECT_PATH = {"message", "subject"};
	private static final SimpleDateFormat TIMESTAMP_FORMATTER1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX");

	static {
		TIMESTAMP_FORMATTER1.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	protected void calculateOffsetAndPosition(Q query, int count, Integer before, Integer after) {
		RSM rsm = query.getRsm();
		int index = rsm.getIndex() == null ? 0 : rsm.getIndex();
		int limit = rsm.getMax();

		if (after != null) {
			// it is ok, if we go out of range we will return empty result
			index = after + 1;
		} else if (before != null) {
			index = before - rsm.getMax();
			// if we go out of range we need to set index to 0 and reduce limit
			// to return proper results
			if (index < 0) {
				index = 0;
				limit = before;
			}
		} else if (rsm.hasBefore()) {
			index = count - rsm.getMax();
			if (index < 0) {
				index = 0;
			}
		}
		rsm.setIndex(index);
		rsm.setMax(limit);
		rsm.setCount(count);
	}

	protected String extractOriginId(Element msg) {
		Element originId = msg.getChild("origin-id", "urn:xmpp:sid:0");
		if (originId != null) {
			String id = originId.getAttributeStaticStr("id");
			if (id != null) {
				return id;
			}
		}
		return msg.getAttributeStaticStr("id");
	}

	protected String extractRefOriginId(Element msg) {
		Element chatMarkerEl = msg.findChild(el -> el.getXMLNS() == "urn:xmpp:chat-markers:0");
		if (chatMarkerEl != null) {
			String id = chatMarkerEl.getAttributeStaticStr("id");
			if (id != null) {
				return id;
			}
		}

		Element messageDeliveryReceipt = msg.getChild("received", "urn:xmpp:receipts");
		if (messageDeliveryReceipt != null) {
			String id = messageDeliveryReceipt.getAttributeStaticStr("id");
			if (id != null) {
				return id;
			}
		}

		Element applyToEl = msg.getChild("apply-to", "urn:xmpp:fasten:0");
		if (applyToEl != null) {
			String id = applyToEl.getAttributeStaticStr("id");
			if  (id != null) {
				return id;
			}
		}
		
		return null;
	}
	
	protected void archiveMessage(BareJID owner, BareJID buddy, Date timestamp, Element msg, String stableId,
								  Set<String> tags, ADP additionParametersProvider) {
		String stanzaId = extractOriginId(msg);
		String refOriginalId = extractRefOriginId(msg);
		String refStableId = null;
		if (refOriginalId != null) {
			try {
				refStableId = getStableId(owner, buddy, refOriginalId);
			} catch (TigaseDBException ex) {
				
			}
		}
		archiveMessage(owner, buddy, timestamp, msg, stableId, stanzaId, refStableId, tags, additionParametersProvider);
	}

	abstract protected void archiveMessage(BareJID owner, BareJID buddy, Date timestamp, Element msg, String stableId, String stanzaId, String refStableId,
								  Set<String> tags, ADP additionParametersProvider);

//	@Override
//	public void queryItems(Q query, ItemHandler<Q, MAMRepository.Item> itemHandler)
//			throws RepositoryException, ComponentException {
//		switch (query.getFasteningCollation()) {
//			case simplified:
//			case full:
//				queryItemsSimple(query, query.getFasteningCollation(), itemHandler);
//				break;
//			case collate:
//			case fastenings:
//				queryItemsCollated(query, itemHandler);
//				break;
//		}
//	}

//	abstract protected void queryItemsSimple(Q query, FasteningCollation collation, ItemHandler<Q, MAMRepository.Item> itemHandler);
//
//	abstract protected Item newItemInstance();
//
//	protected void queryItemsCollated(Q query, ItemHandler<Q, MAMRepository.Item> itemHandler) {
//		Map<String, Item> items = new HashMap<>();
//
//		// query only messages
//		if (query.getFasteningCollation() == FasteningCollation.collate) {
//			queryItemsSimple(query, FasteningCollation.collate, (query1, item) -> {
//				items.put(item.getId(), (Item) item);
//			});
//		}
//		queryItemsSimple(query, FasteningCollation.fastenings, (query1, refItem) -> {
//			Item item = (Item) refItem;
//			String refId = item.getRefId();
//			Item msgItem = items.computeIfAbsent(refId, (k) -> new NoMessageItem(refId, item.getTimestamp(), item.getWith()));
//			msgItem.getFastenings().add(item);
//		});
//
//		items.values()
//				.stream()
//				.sorted(Comparator.comparing(MAMRepository.Item::getTimestamp))
//				.forEach(item -> itemHandler.itemFound(query, item));
//	}
	  
	public interface AddMessageAdditionalDataProvider {
		
	}

	public class NoMessageItem
			implements Item {

		private final String id;
		private final Date ts;
		private final String with;
		private final List<MAMRepository.Item> fastenings = new ArrayList<>();

		public NoMessageItem(String id, Date ts, String with) {
			this.id = id;
			this.ts = ts;
			this.with = with;
		}

		@Override
		public String getRefId() {
			return null;
		}

		@Override
		public Direction getDirection() {
			return null;
		}

		@Override
		public String getWith() {
			return with;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public Element getMessage() {
			return null;
		}

		@Override
		public Date getTimestamp() {
			return ts;
		}

		@Override
		public List<MAMRepository.Item> getFastenings() {
			return fastenings;
		}
	}
}
