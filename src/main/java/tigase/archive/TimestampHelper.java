/*
 * TimestampHelper.java
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
package tigase.archive;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 *
 * @author andrzej
 */
public class TimestampHelper {
	
	private final static SimpleDateFormat formatter4 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	private final static SimpleDateFormat formatter3 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss.SSSZ");
	private final static SimpleDateFormat formatter2 = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ssZ");
	private final static SimpleDateFormat formatter = new SimpleDateFormat(
			"yyyy-MM-dd'T'HH:mm:ss'Z'");

	static {
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		formatter2.setTimeZone(TimeZone.getTimeZone("UTC"));
		formatter3.setTimeZone(TimeZone.getTimeZone("UTC"));
		formatter4.setTimeZone(TimeZone.getTimeZone("UTC"));
	}	
	
	public static Date parseTimestamp(String tmp) throws ParseException {
		if (tmp == null)
			return null;
		
		Date date = null;

		if (tmp.endsWith("Z")) {
			if (tmp.contains(".")) {
				synchronized (formatter4) {
					date = formatter4.parse(tmp);
				}
			}
			else {
				synchronized (formatter) {
					date = formatter.parse(tmp);
				}
			}
		} else if (tmp.contains(".")) {
			synchronized (formatter3) {
				date = formatter3.parse(tmp);
			}
		} else {
			synchronized (formatter2) {
				date = formatter2.parse(tmp);
			}
		}

		return date;
	}	
	
	public static String format(Date ts) {
		synchronized (formatter2) {
			return formatter2.format(ts);
		}
	}
	
}
