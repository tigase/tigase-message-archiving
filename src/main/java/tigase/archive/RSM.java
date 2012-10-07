/*
 * Tigase Message Archiving Component
 * Copyright (C) 2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
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

import tigase.xml.Element;

public class RSM {
	protected static final String XMLNS = "http://jabber.org/protocol/rsm";
	Integer limit = 30;
	String after = null;
	String before = null;
	String first = null;
	String last = null;
	Integer count = null;

	public RSM(Element e) {
		if(e == null)
			return;
		Element param = e.getChild("max");
		if(param != null)
			limit = Integer.parseInt(param.getCData());
		after = e.getCData("/set/after");
		before = e.getCData("/set/before");
	}
	
	public Integer getLimit() {
		return limit;
	}
	
	public String getAfter() {
		return after;
	}

	public String getBefore() {
		return before;
	}
	
	public void setResults(Integer count, String first, String last) {
		this.count = count;
		this.first = first;
		this.last = last;
	}
	
	public Element toElement() {
		Element set = new Element("set");
		set.setXMLNS(XMLNS);
		if(first != null && last != null) {
			set.addChild(new Element("first",first.toString(),new String[] {"index"}, new String[] {first.toString()}));
			set.addChild(new Element("last",last.toString()));
			if(count != null)
				set.addChild(new Element("count",count.toString()));
		}
		else {
			set.addChild(new Element("max",limit.toString()));
			if(after != null) {
				set.addChild(new Element("after",after));					
			}
		}
		return set;
	}
	
	public static RSM parseRootElement(Element e) {
		Element x = e.getChild("set",RSM.XMLNS);
		return new RSM(x);
	}
}
