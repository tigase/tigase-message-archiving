/*
 * RSM.java
 *
 * Tigase Jabber/XMPP Server
 * Copyright (C) 2004-2012 "Artur Hefczyc" <artur.hefczyc@tigase.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
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

//~--- non-JDK imports --------------------------------------------------------

import tigase.xml.Element;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/16
 * @author         Enter your name here...
 */
public class RSM {
	/** Field description */
	protected static final String XMLNS           = "http://jabber.org/protocol/rsm";
	private static final String[] SET_AFTER_PATH  = { "set", "after" };
	private static final String[] SET_BEFORE_PATH = { "set", "before" };
	private static final String[] SET_INDEX_PATH = { "set", "index" };

	//~--- fields ---------------------------------------------------------------

	String after  = null;
	String before = null;
	boolean hasBefore = false;
	Integer count = null;
	String first  = null;
	String last   = null;
	int max = 100;
	Integer index = null;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param e
	 */
	public RSM(Element e, int defaultMax) {
		this.max = defaultMax;
		if (e == null) {
			return;
		}

		Element param = e.getChild("max");

		if (param != null) {
			max = Integer.parseInt(param.getCData());
		}
		after  = e.getCDataStaticStr(SET_AFTER_PATH);
		Element beforeEl = e.findChildStaticStr(SET_BEFORE_PATH);
		if (beforeEl != null) {
			hasBefore = true;
			before = beforeEl.getCData();
		}
		String indexStr = e.getCDataStaticStr(SET_INDEX_PATH);
		if (indexStr != null) {
			index = Integer.parseInt(indexStr);
		}
	}
	
	public RSM(Element e) {
		this(e, 100);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public int getMax() {
		return max;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public Integer getIndex() {
		return index;
	}
	
	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getAfter() {
		return after;
	}

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public String getBefore() {
		return before;
	}

	public boolean hasBefore() {
		return hasBefore;
	}
	
	public Integer getCount() {
		return count;
	}
	
	//~--- set methods ----------------------------------------------------------

	public void setFirst(String first) {
		this.first = first;
	}
	
	public void setLast(String last) {
		this.last = last;
	}

	public void setIndex(Integer index) {
		this.index = index;
	}
	
	/**
	 * Method description
	 *
	 *
	 * @param count
	 * @param first
	 * @param last
	 */
	public void setResults(Integer count, String first, String last) {
		this.count = count;
		this.first = first;
		this.last  = last;
		this.index = null;
	}

	/**
	 * Set count and index of first result
	 * 
	 * @param count
	 * @param index 
	 */
	public void setResults(Integer count, Integer index) {
		this.count = count;
		this.index = index;
		this.first = null;
		this.last = null;
	}
	
	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public Element toElement() {
		Element set = new Element("set");

		set.setXMLNS(XMLNS);
		if ((first != null) && (last != null) || count != null) {
			if (first != null) {
				Element firstEl = new Element("first", first.toString());
				set.addChild(firstEl);
				if (index != null) {
					firstEl.setAttribute("index", index.toString());
				}
			}
			if (last != null) {
				set.addChild(new Element("last", last.toString()));
			}
			if (count != null) {
				set.addChild(new Element("count", count.toString()));
			}
		} else {
			set.addChild(new Element("max", String.valueOf(max)));
			if (after != null) {
				set.addChild(new Element("after", after));
			}
		}

		return set;
	}

	/**
	 * Method description
	 *
	 *
	 * @param e
	 *
	 * @return
	 */
	public static RSM parseRootElement(Element e, int defaultMax) {
		Element x = e.getChild("set", RSM.XMLNS);
		
		return new RSM(x, defaultMax);
	}
	
	/**
	 * Method description
	 *
	 *
	 * @param e
	 *
	 * @return
	 */
	public static RSM parseRootElement(Element e) {
		return RSM.parseRootElement(e, 100);
	}	
}


//~ Formatted in Tigase Code Convention on 13/02/16
