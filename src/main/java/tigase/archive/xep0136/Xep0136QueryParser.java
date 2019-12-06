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
package tigase.archive.xep0136;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.MessageArchiveConfig;
import tigase.archive.QueryCriteria;
import tigase.archive.TagsHelper;
import tigase.archive.processors.Xep0136MessageArchivingProcessor;
import tigase.component.exceptions.ComponentException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.Packet;
import tigase.util.datetime.TimestampHelper;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.QueryParser;

import java.text.ParseException;
import java.util.HashSet;
import java.util.List;

/**
 * Created by andrzej on 19.07.2016.
 */
@Bean(name = "xep0136QueryParser", parent = MessageArchiveComponent.class, active = true)
public class Xep0136QueryParser<Q extends QueryCriteria>
		implements QueryParser<Q> {

	public static final String ARCHIVE_XMLNS = Xep0136MessageArchivingProcessor.XEP0136NS;
	public static final String QUERTY_XMLNS = "http://tigase.org/protocol/archive#query";
	private static final String CONTAINS = "contains";
	private static final String TAG = "tag";
	private static final String NAME = "query";
	private final TimestampHelper timestampHelper = new TimestampHelper();
	@Inject
	private MessageArchiveConfig config;

	@Override
	public Q parseQuery(Q query, Packet packet) throws ComponentException {
		query.setQuestionerJID(packet.getStanzaFrom());
		query.setComponentJID(packet.getStanzaTo());

		Element el = packet.getElement().findChild(element -> element.getXMLNS() == ARCHIVE_XMLNS);
		if (el == null) {
			throw new IllegalArgumentException("Not supported XMLNS of element");
		}

		String withStr = el.getAttributeStaticStr("with");
		if (withStr != null && !withStr.isEmpty()) {
			try {
				query.setWith(JID.jidInstance(withStr));
			} catch (TigaseStringprepException ex) {
				throw new ComponentException(Authorization.BAD_REQUEST, "Invalid value in 'with' attribute", ex);
			}
		}

		String start = el.getAttributeStaticStr("start");
		try {
			query.setStart(timestampHelper.parseTimestamp(start));
		} catch (ParseException ex) {
			throw new ComponentException(Authorization.BAD_REQUEST, "Invalid value in 'start' field", ex);
		}

		String end = el.getAttributeStaticStr("end");
		try {
			query.setEnd(timestampHelper.parseTimestamp(end));
		} catch (ParseException ex) {
			throw new ComponentException(Authorization.BAD_REQUEST, "Invalid value in 'end' field", ex);
		}

		query.getRsm().fromElement(el);

		Element queryEl = el.getChild(NAME, QUERTY_XMLNS);
		if (queryEl != null) {
			List<Element> children = queryEl.getChildren();
			if (children != null) {
				HashSet<String> tags = new HashSet<>();
				for (Element child : children) {
					String cdata = null;
					switch (child.getName()) {
						case CONTAINS:
							cdata = child.getCData();

							if (cdata == null) {
								break;
							}

							query.addContains(cdata);
							if (config.isTagSupportEnabled()) {
								TagsHelper.extractTags(tags, cdata);
							}
							break;
						case TAG:
							cdata = child.getCData();

							if (cdata == null) {
								break;
							}

							query.addTag(cdata.trim());
						default:
							break;
					}
				}
				for (String tag : tags) {
					query.addTag(tag);
				}
			}
		}

		return query;
	}

	@Override
	public Element prepareForm(Element elem) {
		return null;
	}
}
