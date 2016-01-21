/*
 * AbstractCriteria.java
 *
 * Tigase Message Archiving Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
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
package tigase.archive;

import tigase.xmpp.RSM;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tigase.xml.Element;

/**
 *
 * @author andrzej
 */
public abstract class AbstractCriteria<D extends Date> {
	
	private static final String CONTAINS = "contains";
	private static final String TAG = "tag";
	private static final String NAME = "query";
	public static final String ARCHIVE_XMLNS = MessageArchivePlugin.XEP0136NS;
	public static final String QUERTY_XMLNS = "http://tigase.org/protocol/archive#query";
	
	private String with = null;
	private D start = null;
	private D end = null;
	private final RSM rsm = new RSM();
	private int index = 0;
	private int limit = 0;
	
	private final Set<String> contains = new HashSet<String>();
	private final Set<String> tags = new HashSet<String>();
	
	public AbstractCriteria fromElement(Element el, boolean tagsSupport) throws IllegalArgumentException, ParseException {
		if (el.getXMLNS() != ARCHIVE_XMLNS)
			throw new IllegalArgumentException("Not supported XMLNS of element");

		rsm.fromElement(el);
		
		with     = el.getAttributeStaticStr("with");
		start = convertTimestamp(TimestampHelper.parseTimestamp(el.getAttributeStaticStr("start")));
		end  = convertTimestamp(TimestampHelper.parseTimestamp(el.getAttributeStaticStr("end")));
		
		
		Element query = el.getChild(NAME, QUERTY_XMLNS);
		if (query != null) {
			List<Element> children = query.getChildren();
			if (children != null) {
				for (Element child : children) {
					String cdata = null;
					switch (child.getName()) {
						case CONTAINS:
							cdata = child.getCData();
							
							if (cdata == null)
								break;
							
							contains.add(cdata);
							if (tagsSupport) {
								TagsHelper.extractTags(tags, cdata);
							}
							break;
						case TAG:
							cdata = child.getCData();
							
							if (cdata == null)
								break;
							
							tags.add(cdata.trim());
						default:
							break;
					}
				}
			}
		}
		
		return this;
	}
	
	public Set<String> getContains() {
		return Collections.unmodifiableSet(contains);
	}
	
	public void addContains(String contain) {
		this.contains.add(contain);
	}
	
	public Set<String> getTags() {
		return Collections.unmodifiableSet(tags);
	}
	
	public void addTag(String tag) {
		tags.add(tag);
	}
	
	public RSM getRSM() {
		return rsm;
	}
	
	public String getWith() {
		return with;
	}
	
	public void setWith(String with) {
		this.with = with;
	}
	
	public D getStart() {
		return start;
	}
	
	public D getEnd() {
		return end;
	}
	
	public void setStart(Date start) {
		this.start = convertTimestamp(start);
	}
	
	public void setEnd(Date end) {
		this.end = convertTimestamp(end);
	}
	
	public int getOffset() {
		return index;
	}
	
	public int getLimit() {
		return limit;
	}
	
	public void setSize(int count) {
		index = rsm.getIndex() == null ? 0 : rsm.getIndex();
		limit = rsm.getMax();
		if (rsm.getAfter() != null) {
			int after = Integer.parseInt(rsm.getAfter());
			// it is ok, if we go out of range we will return empty result
			index = after + 1;
		} else if (rsm.getBefore() != null) {
			int before = Integer.parseInt(rsm.getBefore());
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
	}
	
	public void prepareResult(Element retList) {
		RSM rsm = getRSM();
		if (rsm.getCount() == null || rsm.getCount() != 0) {
			retList.addChild(rsm.toElement());
		}	
	}
	
	protected abstract D convertTimestamp(Date date);
	
}
