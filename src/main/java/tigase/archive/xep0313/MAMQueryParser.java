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
package tigase.archive.xep0313;

import tigase.archive.FasteningCollation;
import tigase.archive.MessageArchiveComponent;
import tigase.archive.MessageArchiveConfig;
import tigase.component.exceptions.ComponentException;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.server.DataForm;
import tigase.server.Packet;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.mam.Query;

import java.util.*;
import java.util.stream.Stream;

/**
 * Created by andrzej on 19.07.2016.
 */
@Bean(name = "mamQueryParser", parent = MessageArchiveComponent.class, active = true)
public class MAMQueryParser
		extends tigase.xmpp.mam.MAMQueryParser {

	private static final String CONTAINS_FIELD_NAME = "tigase:body:contains";
	private static final String TAGS_FIELD_NAME = "tigase:tags";
	private static final String MAM2_XMLNS = "urn:xmpp:mam:2";
	private static final String MAMFC_XMLNS = "tigase:mamfc:0";
	private static final String MAMFC_SUMMARY = "{" + MAMFC_XMLNS + "}collation";

	protected static final Set<String> XMLNSs = Collections.unmodifiableSet(
			new HashSet<>(Arrays.asList(MAM_XMLNS, MAM2_XMLNS)));

	@Inject(bean = "service")
	private MessageArchiveConfig config;

	@Override
	public Set<String> getXMLNSs() {
		return XMLNSs;
	}

	@Override
	public Query parseQuery(Query query, Packet packet) throws ComponentException {
		Query result = super.parseQuery(query, packet);

		Element queryEl = packet.getElement().getChild("query");

		FasteningCollation mamfc = Optional.ofNullable(DataForm.getFieldValue(queryEl, MAMFC_SUMMARY))
				.map(FasteningCollation::forName)
				.orElse(FasteningCollation.full);
		((tigase.archive.Query) query).setFasteningCollation(mamfc);

		String[] contains = DataForm.getFieldValues(queryEl, CONTAINS_FIELD_NAME);
		if (contains != null && contains.length > 0) {
			if (!(query instanceof tigase.archive.Query)) {
				throw new ComponentException(Authorization.BAD_REQUEST, "Unsupported feature " + CONTAINS_FIELD_NAME);
			}

			for (String it : contains) {
				((tigase.archive.Query) query).addContains(it);
			}
		}

		String[] tags = DataForm.getFieldValues(queryEl, TAGS_FIELD_NAME);
		if (tags != null && tags.length > 0) {
			if (!(query instanceof tigase.archive.Query) || !config.isTagSupportEnabled()) {
				throw new ComponentException(Authorization.BAD_REQUEST, "Unsupported feature " + TAGS_FIELD_NAME);
			}

			for (String it : tags) {
				((tigase.archive.Query) query).addTag(it);
			}
		}

		return result;
	}

	@Override
	public Element prepareForm(Element elem, String xmlns) {
		Element form = super.prepareForm(elem, xmlns);
		Element x = form.getChild("x", "jabber:x:data");

		if (x != null) {
			String[] options = Stream.of(FasteningCollation.simplified, FasteningCollation.full,
										 FasteningCollation.collate, FasteningCollation.fastenings)
					.map(FasteningCollation::name)
					.toArray(String[]::new);
			DataForm.addFieldValue(x, MAMFC_SUMMARY, FasteningCollation.full.name(), "MAM Fastening Collation", options, options);

			addField(x, CONTAINS_FIELD_NAME, "text-multi", "Contains in body");

			if (config.isTagSupportEnabled()) {
				addField(x, TAGS_FIELD_NAME, "text-multi", "Contains tags");
			}
		}

		return form;
	}
}
