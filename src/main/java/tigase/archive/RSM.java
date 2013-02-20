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

	//~--- fields ---------------------------------------------------------------

	String after  = null;
	String before = null;
	Integer count = null;
	String first  = null;
	String last   = null;
	Integer limit = 30;

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 *
	 * @param e
	 */
	public RSM(Element e) {
		if (e == null) {
			return;
		}

		Element param = e.getChild("max");

		if (param != null) {
			limit = Integer.parseInt(param.getCData());
		}
		after  = e.getCDataStaticStr(SET_AFTER_PATH);
		before = e.getCDataStaticStr(SET_BEFORE_PATH);
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	public Integer getLimit() {
		return limit;
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

	//~--- set methods ----------------------------------------------------------

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
		if ((first != null) && (last != null)) {
			set.addChild(new Element("first", first.toString(), new String[] { "index" },
															 new String[] { first.toString() }));
			set.addChild(new Element("last", last.toString()));
			if (count != null) {
				set.addChild(new Element("count", count.toString()));
			}
		} else {
			set.addChild(new Element("max", limit.toString()));
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
	public static RSM parseRootElement(Element e) {
		Element x = e.getChild("set", RSM.XMLNS);

		return new RSM(x);
	}
}


//~ Formatted in Tigase Code Convention on 13/02/16
