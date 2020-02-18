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

import tigase.annotations.TigaseDeprecated;
import tigase.db.DataSource;
import tigase.db.DataSourceAware;
import tigase.db.TigaseDBException;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * @author andrzej
 */
public interface MessageArchiveRepository<Q extends tigase.archive.xep0136.Query, DS extends DataSource>
		extends DataSourceAware<DS>, MAMRepository<Q, MAMRepository.Item> {

	enum Direction {
		incoming((short) 1, "from"),
		outgoing((short) 0, "to");

		private final String elemName;
		private final short value;

		public static Direction getDirection(BareJID owner, BareJID from) {
			return owner.equals(from) ? outgoing : incoming;
		}

		public static Direction getDirection(short val) {
			switch (val) {
				case 1:
					return incoming;
				case 0:
					return outgoing;
				default:
					return null;
			}
		}

		public static Direction getDirection(String val) {
			if (incoming.toElementName().equals(val)) {
				return incoming;
			}
			if (outgoing.toElementName().equals(val)) {
				return outgoing;
			}
			return null;
		}

		Direction(short val, String elemName) {
			value = val;
			this.elemName = elemName;
		}

		public short getValue() {
			return value;
		}

		public String toElementName() {
			return elemName;
		}

	}

	@TigaseDeprecated(since = "2.1.0", removeIn = "3.0.0", note = "New version of this method will be introduced in 3.0.0")
	@Deprecated
	void archiveMessage(BareJID owner, JID buddy, Direction direction, Date timestamp, Element msg, Set<String> tags);

	void deleteExpiredMessages(BareJID owner, LocalDateTime before) throws TigaseDBException;

	/**
	 * Destroys instance of this repository and releases resources allocated if possible
	 */
	default void destroy() {
	}

	;

	void removeItems(BareJID owner, String withJid, Date start, Date end) throws TigaseDBException;

	List<String> getTags(BareJID owner, String startsWith, Q criteria) throws TigaseDBException;

	void queryCollections(Q query, CollectionHandler<Q> collectionHandler) throws TigaseDBException;

	interface CollectionHandler<Q extends Query> {

		void collectionFound(Q query, String with, Date start, String type);

	}

	interface Item
			extends MAMRepository.Item {

		Direction getDirection();

		String getWith();

	}
}
