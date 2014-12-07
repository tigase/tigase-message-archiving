/*
 * MessageArchiveRepository.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2014 "Tigase, Inc." <office@tigase.com>
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
package tigase.archive.db;

import java.util.Date;
import java.util.List;
import tigase.archive.AbstractCriteria;
import tigase.archive.RSM;
import tigase.db.Repository;
import tigase.db.TigaseDBException;
import tigase.xml.Element;
import tigase.xmpp.BareJID;

/**
 *
 * @author andrzej
 */
public interface MessageArchiveRepository<Crit extends AbstractCriteria> extends Repository {
	
	enum Direction {
		incoming((short) 1, "from"),
		outgoing((short) 0, "to");
		
		private final short value;
		private final String elemName;
		
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
		
	}
	
	void archiveMessage(BareJID owner, BareJID buddy, Direction direction, Date timestamp, Element msg);
	
	/**
	 * Destroys instance of this repository and releases resources allocated if possible
	 */
	void destroy();
	
	AbstractCriteria newCriteriaInstance();
	
	List<Element> getCollections(BareJID owner, Crit criteria) throws TigaseDBException;
	
	List<Element> getItems(BareJID owner, Crit criteria) throws TigaseDBException;
	
	public void removeItems(BareJID owner, String withJid, Date start, Date end) throws TigaseDBException;
}
