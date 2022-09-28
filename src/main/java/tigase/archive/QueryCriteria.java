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
package tigase.archive;

import tigase.archive.xep0136.Query;
import tigase.archive.xep0313.QueryImpl;
import tigase.xml.Element;
import tigase.xmpp.rsm.RSM;

import java.util.*;

/**
 * @author andrzej
 */
public class QueryCriteria
		extends QueryImpl
		implements Query {

	public static final String QUERTY_XMLNS = "http://tigase.org/protocol/archive#query";

	private final Set<String> contains = new HashSet<String>();
	private final Set<String> tags = new HashSet<String>();

	private List<Element> collections;
	private List<Element> items;

	private boolean useMessageIdInRsm = true;

	public Set<String> getContains() {
		return Collections.unmodifiableSet(contains);
	}

	public void addContains(String contain) {
		this.contains.add(contain);
	}

	public FasteningCollation getFasteningCollation() {
		return FasteningCollation.full;
	}

	public Set<String> getTags() {
		return Collections.unmodifiableSet(tags);
	}

	public void addTag(String tag) {
		tags.add(tag);
	}

	public boolean getUseMessageIdInRsm() {
		return useMessageIdInRsm;
	}

	public void setUseMessageIdInRsm(boolean value) {
		useMessageIdInRsm = value;
	}

	public void prepareResult(Element retList) {
		RSM rsm = getRsm();
		if (rsm.getCount() == null || rsm.getCount() != 0) {
			retList.addChild(rsm.toElement());
		}
	}

	public void addCollection(Element collection) {
		if (collections == null) {
			collections = new ArrayList<>();
		}
		collections.add(collection);
	}

	public List<Element> getCollections() {
		return collections;
	}

	public void addItem(Element item) {
		if (items == null) {
			items = new ArrayList<>();
		}
		items.add(item);
	}

	public List<Element> getItems() {
		if (items == null) {
			return Collections.emptyList();
		}
		return items;
	}

	@Override
	public String toString() {
		final StringBuffer sb = new StringBuffer("QueryCriteria{");
		sb.append("QueryImpl='").append(super.toString()).append('\'');
		sb.append(", contains=").append(contains);
		sb.append(", tags=").append(tags);
		sb.append(", collections=").append(collections);
		sb.append(", useMessageIdInRsm=").append(useMessageIdInRsm);
		sb.append(", fasteningCollation=").append(getFasteningCollation());
		sb.append('}');
		return sb.toString();
	}
}
