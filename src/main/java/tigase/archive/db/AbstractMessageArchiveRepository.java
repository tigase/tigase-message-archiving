/*
 * AbstractMessageArchiveDB.java
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

import tigase.archive.AbstractCriteria;
import tigase.xml.Element;
import tigase.xmpp.JID;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * AbstractMessageArchiveRepository contains methods commonly used by other implementations
 * to eliminate code multiplication.
 * 
 * @author andrzej
 */
public abstract class AbstractMessageArchiveRepository<Crit extends AbstractCriteria> implements MessageArchiveRepository<Crit> {

	private static final SimpleDateFormat TIMESTAMP_FORMATTER1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX");

	protected static final String[] MSG_BODY_PATH = { "message", "body" };	
	
	static {
		TIMESTAMP_FORMATTER1.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	protected Element addCollectionToResults(List<Element> results, Crit criteria, String with, Date start, String type) {
		String formattedStart = null;
		synchronized (TIMESTAMP_FORMATTER1) {
			formattedStart = TIMESTAMP_FORMATTER1.format(start);
		}
		Element elem = new Element("chat", new String[] { "with", "start" },
				new String[] { with, formattedStart });
		if (type != null && !type.isEmpty()) {
			elem.addAttribute("type", type);
		}
		results.add(elem);
		return elem;
	}
	
	protected Element addMessageToResults(List<Element> results, Crit criteria, Date collectionStart, Element msg, Date timestamp, Direction direction, String with) {
		Element item = new Element(direction.toElementName());

		// Now we should send all elements of a message so as we can store not only <body/> 
		// element. If we will store only <body/> element then only this element will 
		// be available in store
		//item.addChild(msg.getChild("body"));
		item.addChildren(msg.getChildren());
		item.setAttribute("secs", String.valueOf((timestamp.getTime() - collectionStart.getTime()) / 1000));
		if (with != null) {
			item.setAttribute("with", with);
		}
		if ("groupchat".equals(msg.getAttributeStaticStr("type"))) {
			JID from = JID.jidInstanceNS(msg.getAttributeStaticStr("from"));
			if (from != null && from.getResource() != null) {
				item.setAttribute("name", from.getResource());
			}
		}
			
		results.add(item);
		return item;
	}
	
	protected byte[] generateHashOfMessage(Direction direction, Element msg, Date ts, Map<String,Object> additionalData) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");

			String peer = direction == Direction.incoming ? msg.getAttributeStaticStr("from") : msg.getAttributeStaticStr("to");
			if (peer != null) {
				md.update(peer.getBytes());
			}
			String id = msg.getAttributeStaticStr("id");
			if (id != null) {
				md.update(id.getBytes());
			}
			String type = msg.getAttributeStaticStr("type");
			if (type == null || !"groupchat".equals(type)) {
				md.update(new Long(ts.getTime() / 1000).toString().getBytes());
			}
			String body = msg.getChildCData(MSG_BODY_PATH);
			if (body != null) {
				md.update(body.getBytes());
			}
			
			return md.digest();
		} catch (NoSuchAlgorithmException ex) {
			return null;
		}
	}
}
