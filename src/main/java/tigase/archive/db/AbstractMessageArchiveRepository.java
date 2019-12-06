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

import tigase.archive.xep0136.Query;
import tigase.db.DataSource;
import tigase.xml.Element;
import tigase.xmpp.rsm.RSM;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

/**
 * AbstractMessageArchiveRepository contains methods commonly used by other implementations to eliminate code
 * multiplication.
 *
 * @author andrzej
 */
public abstract class AbstractMessageArchiveRepository<Q extends Query, DS extends DataSource>
		implements MessageArchiveRepository<Q, DS> {

	protected static final String[] MSG_BODY_PATH = {"message", "body"};
	protected static final String[] MSG_SUBJECT_PATH = {"message", "subject"};
	private static final SimpleDateFormat TIMESTAMP_FORMATTER1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX");

	static {
		TIMESTAMP_FORMATTER1.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	protected byte[] generateHashOfMessage(Direction direction, Element msg, Date ts,
										   Map<String, Object> additionalData) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");

			String peer = direction == Direction.incoming
						  ? msg.getAttributeStaticStr("from")
						  : msg.getAttributeStaticStr("to");
			if (peer != null) {
				md.update(peer.getBytes());
			}
			String type = msg.getAttributeStaticStr("type");
			Element subjectEl = msg.getChild("subject");
			String subject = msg.getChildCData(MSG_SUBJECT_PATH);
			String id = msg.getAttributeStaticStr("id");
			if (id != null) {
				if (!"groupchat".equals(type) || subjectEl == null) {
					md.update(id.getBytes());
				} else {
					md.update(":".getBytes());
					md.update(new Long(ts.getTime() / 60000).toString().getBytes());
				}
			}

			if (type == null || !"groupchat".equals(type)) {
				md.update(new Long(ts.getTime() / 1000).toString().getBytes());
			}
			String body = msg.getChildCData(MSG_BODY_PATH);
			if (body != null) {
				md.update(body.getBytes());
			}
			if (subject != null) {
				md.update(subject.getBytes());
			}

			return md.digest();
		} catch (NoSuchAlgorithmException ex) {
			return null;
		}
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

}
