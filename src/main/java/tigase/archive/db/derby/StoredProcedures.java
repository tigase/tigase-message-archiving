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
package tigase.archive.db.derby;

import tigase.util.Algorithms;
import tigase.xmpp.jid.BareJID;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.logging.Logger;

/**
 * @author andrzej
 */
public class StoredProcedures {

	private static final Logger log = Logger.getLogger(StoredProcedures.class.getName());
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private static void addJid(BareJID bareJid, String jidSha1) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		ResultSet rs = null;
		try {
			PreparedStatement ps = conn.prepareStatement(
					"insert into tig_ma_jids (jid, \"domain\", jid_sha1) select ?, ?, ? from sysibm.sysdummy1 where not exists (select 1 from tig_ma_jids where jid_sha1 = ?)",
					Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, bareJid.toString());
			ps.setString(2, bareJid.getDomain());
			ps.setString(3, jidSha1);
			ps.setString(4, jidSha1);
			ps.executeUpdate();
		} finally {
			if (rs != null) {
				rs.close();
			}
		}
	}

	public static void addMessage(String ownerJid, String buddyJid, String buddyRes, Timestamp ts, short direction,
								  String type, String body, String msg, String stableId, ResultSet[] data)
			throws SQLException {
		long ownerId = ensureJid(ownerJid);
		long buddyId = ensureJid(buddyJid);

		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("" +
																 "insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, \"type\", body, msg, stable_id)" +
																 " select ?, ?, ?, ?, ?, ?, ?, ?, ?" +
																 " from SYSIBM.SYSDUMMY1" + " where not exists (" +
																 " select 1 from tig_ma_msgs where owner_id = ? and buddy_id = ? and stable_id = ? and ts between ? and ?" +
																 ")", Statement.RETURN_GENERATED_KEYS);

			Timestamp from = ts;
			Timestamp to = ts;

			if ("groupchat".equals(type)) {
				from = new Timestamp(ts.getTime() - 30 * 60 * 1000);
				to = new Timestamp(ts.getTime() + 30 * 60 * 1000);
			}

			int i = 0;
			ps.setLong(++i, ownerId);
			ps.setLong(++i, buddyId);
			ps.setString(++i, buddyRes);
			ps.setTimestamp(++i, ts);
			ps.setShort(++i, direction);
			ps.setString(++i, type);
			ps.setString(++i, body);
			ps.setString(++i, msg);
			ps.setString(++i, stableId);

			ps.setLong(++i, ownerId);
			ps.setLong(++i, buddyId);
			ps.setString(++i, stableId);
			ps.setTimestamp(++i, from);
			ps.setTimestamp(++i, to);

			ps.execute();

			ps = conn.prepareStatement(
					"select msg_id from tig_ma_msgs where owner_id = ? and buddy_id = ? and stable_id = ? and ts between ? and ?");
			i = 0;
			ps.setLong(++i, ownerId);
			ps.setLong(++i, buddyId);
			ps.setString(++i, stableId);
			ps.setTimestamp(++i, from);
			ps.setTimestamp(++i, to);

			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void addTagToMessage(long msgId, String tag) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement("select owner_id from tig_ma_msgs where msg_id = ?");

			ps.setLong(1, msgId);
			ResultSet rs = ps.executeQuery();
			rs.next();
			long ownerId = rs.getLong(1);
			rs.close();

			ps = conn.prepareStatement("select tag_id from tig_ma_tags where owner_id = ? and tag = ?");

			ps.setLong(1, ownerId);
			ps.setString(2, tag);

			rs = ps.executeQuery();
			long tagId = -1;
			if (!rs.next()) {
				rs.close();
				ps = conn.prepareStatement("insert into tig_ma_tags (owner_id, tag) values (?,?)",
										   Statement.RETURN_GENERATED_KEYS);
				ps.setLong(1, ownerId);
				ps.setString(2, tag);
				ps.execute();
				rs = ps.getGeneratedKeys();
				rs.next();
			}
			tagId = rs.getLong(1);
			rs.close();

			ps = conn.prepareStatement(
					"insert into tig_ma_msgs_tags (msg_id, tag_id) select ?, ? from SYSIBM.SYSDUMMY1" +
							" where not exists (select 1 from tig_ma_msgs_tags mt where mt.msg_id = ? and mt.tag_id = ?)");

			ps.setLong(1, msgId);
			ps.setLong(2, tagId);
			ps.setLong(3, msgId);
			ps.setLong(4, tagId);

			ps.executeUpdate();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	protected static StringBuilder appendContainsQuery(StringBuilder sb, String contains) {
		if (contains != null) {
			sb.append(" and m.body like ").append(contains.replace("','", "' and m.body like '"));
		}
		return sb;
	}

	protected static StringBuilder appendTagsQuery(StringBuilder sb, String tags) {
		if (tags != null) {
			sb.append(" and exists(select 1 from tig_ma_msgs_tags mt " +
							  "inner join tig_ma_tags t on mt.tag_id = t.tag_id " +
							  "where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (")
					.append(tags)
					.append("))");
		}
		return sb;
	}

	private static int countMessages(Connection conn) throws SQLException {
		ResultSet rs = conn.prepareStatement("select count(msg_id) from tig_ma_msgs").executeQuery();
		if (rs.next()) {
			return rs.getInt(1);
		}
		return -1;
	}

	public static void deleteExpiredMessages(String domain, Timestamp before) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"delete from tig_ma_msgs where ts <= ? and exists (select 1 from tig_ma_jids o where owner_id = o.jid_id and o.\"domain\" = ?)");

			ps.setTimestamp(1, before);
			ps.setString(2, domain);

			ps.execute();

			ps = conn.prepareStatement("delete from tig_ma_jids" +
											   " where" +
											   "  not exists (" +
											   "    select 1 from tig_ma_msgs m where m.owner_id = jid_id" +
											   "  )" +
											   "  and not exists (" +
											   "    select 1 from tig_ma_msgs m where m.buddy_id = jid_id" +
											   "  )");
			ps.execute();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static synchronized Long ensureJid(String jid) throws SQLException {
		BareJID bareJid = BareJID.bareJIDInstanceNS(jid);
		String jidSha1 = sha1OfLower(jid);

		addJid(bareJid, jidSha1);

		return getJidId(bareJid, jidSha1);
	}

	public static void getCollections(String ownerJid, String buddyJid, Timestamp from, Timestamp to, String tags,
									  String contains, short byType, Integer limit, Integer offset, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			StringBuilder sb = new StringBuilder();

			sb.append("select min(m.ts), b.jid");
			if (byType == 1) {
				sb.append(
						", case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end as \"type\"");
			} else {
				sb.append(", cast(null as varchar(20)) as \"type\"");
			}

			sb.append(" from tig_ma_msgs m" + " inner join tig_ma_jids o on m.owner_id = o.jid_id" +
							  " inner join tig_ma_jids b on b.jid_id = m.buddy_id" + " where " + " o.jid_sha1 = ?");
			if (buddyJid != null) {
				sb.append(" and b.jid_sha1 = ?");
			}
			if (from != null) {
				sb.append(" and m.ts >= ?");
			}
			if (to != null) {
				sb.append(" and m.ts <= ?");
			}
			appendTagsQuery(sb, tags);
			appendContainsQuery(sb, contains);
			if (byType == 1) {
				sb.append(
						" group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end");
			} else {
				sb.append(" group by date(m.ts), m.buddy_id, b.jid");
			}

			sb.append(" order by min(m.ts), b.jid");
			sb.append(" offset ? rows fetch next ? rows only");

			PreparedStatement ps = conn.prepareStatement(sb.toString());

			int i = 0;
			ps.setString(++i, sha1OfLower(ownerJid));
			if (buddyJid != null) {
				ps.setString(++i, sha1OfLower(buddyJid));
			}
			if (from != null) {
				ps.setTimestamp(++i, from);
			}
			if (to != null) {
				ps.setTimestamp(++i, to);
			}
			ps.setInt(++i, offset);
			ps.setInt(++i, limit);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void getCollectionsCount(String ownerJid, String buddyJid, Timestamp from, Timestamp to, String tags,
										   String contains, short byType, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			StringBuilder sb = new StringBuilder();

			sb.append("select count(1) from (select min(m.ts), b.jid");
			if (byType == 1) {
				sb.append(
						", case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end as \"type\"");
			} else {
				sb.append(", cast(null as varchar(20)) as \"type\"");
			}

			sb.append(" from tig_ma_msgs m" + " inner join tig_ma_jids o on m.owner_id = o.jid_id" +
							  " inner join tig_ma_jids b on b.jid_id = m.buddy_id" + " where " + " o.jid_sha1 = ?");
			if (buddyJid != null) {
				sb.append(" and b.jid_sha1 = ?");
			}
			if (from != null) {
				sb.append(" and m.ts >= ?");
			}
			if (to != null) {
				sb.append(" and m.ts <= ?");
			}
			appendTagsQuery(sb, tags);
			appendContainsQuery(sb, contains);
			if (byType == 1) {
				sb.append(
						" group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end");
			} else {
				sb.append(" group by date(m.ts), m.buddy_id, b.jid");
			}

			sb.append(") x");

			PreparedStatement ps = conn.prepareStatement(sb.toString());

			int i = 0;
			ps.setString(++i, sha1OfLower(ownerJid));
			if (buddyJid != null) {
				ps.setString(++i, sha1OfLower(buddyJid));
			}
			if (from != null) {
				ps.setTimestamp(++i, from);
			}
			if (to != null) {
				ps.setTimestamp(++i, to);
			}
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static Long getJidId(BareJID bareJid, String jidSha1) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		ResultSet rs = null;
		try {
			PreparedStatement ps = conn.prepareStatement("select jid_id from tig_ma_jids where jid_sha1 = ?");

			ps.setString(1, jidSha1);
			rs = ps.executeQuery();
			if (rs.next()) {
				return rs.getLong(1);
			}
			return null;
		} finally {
			if (rs != null) {
				rs.close();
			}
		}
	}

	public static void getMessagePosition(String ownerJid, String buddyJid, Timestamp from, Timestamp to, String tags,
										  String contains, String stableId, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			StringBuilder sb = new StringBuilder();

			sb.append("select m.stable_id, row_number() over () as position" + " from tig_ma_msgs m" +
							  " inner join tig_ma_jids o on m.owner_id = o.jid_id" +
							  " inner join tig_ma_jids b on b.jid_id = m.buddy_id" + " where " + " o.jid_sha1 = ?");
			if (buddyJid != null) {
				sb.append(" and b.jid_sha1 = ?");
			}
			if (from != null) {
				sb.append(" and m.ts >= ?");
			}
			if (to != null) {
				sb.append(" and m.ts <= ?");
			}
			appendTagsQuery(sb, tags);
			appendContainsQuery(sb, contains);
			sb.append(" order by m.ts");

			PreparedStatement ps = conn.prepareStatement(sb.toString());

			int i = 0;
			ps.setString(++i, sha1OfLower(ownerJid));
			if (buddyJid != null) {
				ps.setString(++i, sha1OfLower(buddyJid));
			}
			if (from != null) {
				ps.setTimestamp(++i, from);
			}
			if (to != null) {
				ps.setTimestamp(++i, to);
			}

			i = 0;
			ResultSet rs = ps.executeQuery();
			while (rs.next()) {
				if (stableId.equals(rs.getString(1))) {
					i = rs.getInt(2);
					break;
				}
			}
			rs.close();

			String q = "select " + i + " as position from SYSIBM.SYSDUMMY1 where " + i + " <> 0";
			data[0] = conn.prepareStatement(q).executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void getMessages(String ownerJid, String buddyJid, Timestamp from, Timestamp to, String tags,
								   String contains, Integer limit, Integer offset, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			StringBuilder sb = new StringBuilder();

			sb.append("select m.msg, m.ts, m.direction, b.jid, m.stable_id" + " from tig_ma_msgs m" +
							  " inner join tig_ma_jids o on m.owner_id = o.jid_id" +
							  " inner join tig_ma_jids b on b.jid_id = m.buddy_id" + " where " + " o.jid_sha1 = ?");
			if (buddyJid != null) {
				sb.append(" and b.jid_sha1 = ?");
			}
			if (from != null) {
				sb.append(" and m.ts >= ?");
			}
			if (to != null) {
				sb.append(" and m.ts <= ?");
			}
			appendTagsQuery(sb, tags);
			appendContainsQuery(sb, contains);

			sb.append(" order by m.ts");
			sb.append(" offset ? rows fetch next ? rows only");

			PreparedStatement ps = conn.prepareStatement(sb.toString());

			int i = 0;
			ps.setString(++i, sha1OfLower(ownerJid));
			if (buddyJid != null) {
				ps.setString(++i, sha1OfLower(buddyJid));
			}
			if (from != null) {
				ps.setTimestamp(++i, from);
			}
			if (to != null) {
				ps.setTimestamp(++i, to);
			}
			ps.setInt(++i, offset);
			ps.setInt(++i, limit);
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void getMessagesCount(String ownerJid, String buddyJid, Timestamp from, Timestamp to, String tags,
										String contains, ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			StringBuilder sb = new StringBuilder();

			sb.append("select count(m.msg_id)" + " from tig_ma_msgs m" +
							  " inner join tig_ma_jids o on m.owner_id = o.jid_id" +
							  " inner join tig_ma_jids b on b.jid_id = m.buddy_id" + " where " + " o.jid_sha1 = ?");
			if (buddyJid != null) {
				sb.append(" and b.jid_sha1 = ?");
			}
			if (from != null) {
				sb.append(" and m.ts >= ?");
			}
			if (to != null) {
				sb.append(" and m.ts <= ?");
			}
			appendTagsQuery(sb, tags);
			appendContainsQuery(sb, contains);

			PreparedStatement ps = conn.prepareStatement(sb.toString());

			int i = 0;
			ps.setString(++i, sha1OfLower(ownerJid));
			if (buddyJid != null) {
				ps.setString(++i, sha1OfLower(buddyJid));
			}
			if (from != null) {
				ps.setTimestamp(++i, from);
			}
			if (to != null) {
				ps.setTimestamp(++i, to);
			}
			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void getTagsForUser(String ownerJid, String tagStartsWith, Integer limit, Integer offset,
									  ResultSet[] data) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select t.tag from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = ? and t.tag like ? order by t.tag offset ? rows fetch next ? rows only");

			ps.setString(1, sha1OfLower(ownerJid));
			ps.setString(2, tagStartsWith);
			ps.setInt(3, offset);
			ps.setInt(4, limit);

			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void getTagsForUserCount(String ownerJid, String tagStartsWith, ResultSet[] data)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			PreparedStatement ps = conn.prepareStatement(
					"select count(t.tag_id) from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = ? and t.tag like ?");

			ps.setString(1, sha1OfLower(ownerJid));
			ps.setString(2, tagStartsWith);

			data[0] = ps.executeQuery();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	public static void removeMessages(String ownerJid, String buddyJid, Timestamp from, Timestamp to)
			throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:default:connection");

		conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

		try {
			Long ownerId = getJidId(BareJID.bareJIDInstanceNS(ownerJid), sha1OfLower(ownerJid));
			if (ownerId == null) {
				return;
			}
			
			if (buddyJid != null) {
				Long buddyId = getJidId(BareJID.bareJIDInstanceNS(buddyJid), sha1OfLower(buddyJid));
				if (buddyId == null) {
					return;
				}
				StringBuilder sb = new StringBuilder("delete from tig_ma_msgs where owner_id = ? and buddy_id = ?");
				if (from != null) {
					sb.append(" and ts >= ?");
				}
				if (to != null) {
					sb.append(" and ts <= ?");
				}
				PreparedStatement ps = conn.prepareStatement(sb.toString());
				int i=1;
				ps.setLong(i++, ownerId);
				ps.setLong(i++, buddyId);
				if (from != null) {
					ps.setTimestamp(i++, from);
				}
				if (to != null) {
					ps.setTimestamp(i++, to);
				}
				ps.execute();

				ps = conn.prepareStatement("delete from tig_ma_jids" +
												   " where jid_id = ?" +
												   " and not exists (" +
												   "  select 1 from tig_ma_msgs m where m.buddy_id = jid_id" +
												   " )" +
												   " and not exists (" +
												   "  select 1 from tig_ma_msgs m where m.owner_id = jid_id" +
												   " )");
				ps.setLong(1, buddyId);
				ps.execute();
			} else {
				StringBuilder sb = new StringBuilder("delete from tig_ma_msgs where owner_id = ?");
				if (from != null) {
					sb.append(" and ts >= ?");
				}
				if (to != null) {
					sb.append(" and ts <= ?");
				}
				PreparedStatement ps = conn.prepareStatement(sb.toString());
				int i=1;
				ps.setLong(i++, ownerId);
				if (from != null) {
					ps.setTimestamp(i++, from);
				}
				if (to != null) {
					ps.setTimestamp(i++, to);
				}
				ps.execute();
			}

			PreparedStatement ps = conn.prepareStatement("delete from tig_ma_jids" +
											   " where jid_id = ?" +
											   " and not exists (" +
											   "  select 1 from tig_ma_msgs m where m.buddy_id = jid_id" +
											   " )" +
											   " and not exists (" +
											   "  select 1 from tig_ma_msgs m where m.owner_id = jid_id" +
											   " )");
			ps.setLong(1, ownerId);
			ps.execute();
		} catch (SQLException e) {
			throw e;
		} finally {
			conn.close();
		}
	}

	protected static String sha1OfLower(String data) throws SQLException {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] hash = md.digest(data.toLowerCase().getBytes(UTF8));
			return Algorithms.bytesToHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new SQLException(e);
		}
	}

}
