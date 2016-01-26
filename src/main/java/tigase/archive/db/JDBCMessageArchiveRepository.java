/*
 * JDBCMessageArchiveRepository.java
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
package tigase.archive.db;

//~--- non-JDK imports --------------------------------------------------------

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import tigase.archive.AbstractCriteria;
import tigase.archive.db.JDBCMessageArchiveRepository.Criteria;

import tigase.db.DBInitException;
import tigase.db.DataRepository;
import tigase.db.DataRepository.dbTypes;

import static tigase.db.DataRepository.dbTypes.derby;

import tigase.db.Repository;
import tigase.db.RepositoryFactory;
import tigase.db.TigaseDBException;

import tigase.util.Base64;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;

import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.RSM;
import tigase.xmpp.StanzaType;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/16
 * @author         Enter your name here...
 */
@Repository.Meta( supportedUris = { "jdbc:[^:]+:.*" } )
public class JDBCMessageArchiveRepository extends AbstractMessageArchiveRepository<Criteria> {
	private static final Logger log        =
		Logger.getLogger(JDBCMessageArchiveRepository.class.getCanonicalName());
	private static final long LONG_NULL              = 0;
	
	private static final SimpleParser parser      = SingletonFactory.getParserInstance();
			
	private static final String STORE_PLAINTEXT_BODY_KEY = "store-plaintext-body";
	private static final String GROUP_BY_TYPE_KEY = "group-by-chat-type";
	
	private static final String DELETE_EXPIRED_QUERY_TIMEOUT_KEY = "remove-expired-messages-query-timeout";
	private static final int DEF_DELETE_EXPIRED_QUERY_TIMEOUT_VAL = 5 * 60;

	private static final String DEF_GET_MESSAGES_QUERY = "{ call Tig_MA_GetMessages(?,?,?,?,?,?,?,?) }";
	private static final String DEF_GET_MESSAGES_COUNT_QUERY = "{ call Tig_MA_GetMessagesCount(?,?,?,?,?,?) }";
	private static final String DEF_GET_COLLECTIONS_QUERY = "{ call Tig_MA_GetCollections(?,?,?,?,?,?,?,?,?) }";
	private static final String DEF_GET_COLLECTIONS_COUNT_QUERY = "{ call Tig_MA_GetCollectionsCount(?,?,?,?,?,?,?) }";
	private static final String DEF_ADD_MESSAGE_QUERY = "{ call Tig_MA_AddMessage(?,?,?,?,?,?,?,?,?) }";
	private static final String DEF_ADD_TAG_TO_MESSAGE_QUERY = "{ call Tig_MA_AddTagToMessage(?,?) }";
	private static final String DEF_REMOVE_MESSAGES_QUERY = "{ call Tig_MA_RemoveMessages(?,?,?,?) }";
	private static final String DEF_DELETE_EXPIRED_MESSAGES_QUERY = "{ call Tig_MA_DeleteExpiredMessages(?,?) }";
	private static final String DEF_GET_TAGS_FOR_USER_QUERY = "{ call Tig_MA_GetTagsForUser(?,?,?,?) }";
	private static final String DEF_GET_TAGS_FOR_USER_COUNT_QUERY = "{ call Tig_MA_GetTagsForUserCount(?,?) }";
	
	protected static final String GET_MESSAGES_QUERY_KEY = "get-messages-query";
	protected static final String GET_MESSAGES_COUNT_QUERY_KEY = "get-messages-count-query";
	protected static final String GET_COLLECTIONS_QUERY_KEY = "get-collections-query";
	protected static final String GET_COLLECTIONS_COUNT_QUERY_KEY = "get-collections-count-query";
	protected static final String ADD_MESSAGE_QUERY_KEY = "add-message-query";
	protected static final String ADD_TAG_TO_MESSAGE_QUERY_KEY = "add-tag-to-message-query";
	protected static final String REMOVE_MESSAGES_QUERY_KEY = "remove-messages-query";
	protected static final String DELETE_EXPIRED_MESSAGES_QUERY_KEY = "delete-expired-messages-query";
	protected static final String GET_TAGS_FOR_USER_QUERY_KEY = "get-tags-for-user-query";
	protected static final String GET_TAGS_FOR_USER_COUNT_QUERY_KEY = "get-tags-for-user-count-query";
	
	//~--- fields ---------------------------------------------------------------
	
	protected DataRepository data_repo = null;
	private boolean storePlaintextBody = true;
	private boolean groupByType = false;
	private int delete_expired_timeout = DEF_DELETE_EXPIRED_QUERY_TIMEOUT_VAL;

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param conn_str
	 * @param params
	 *
	 * @throws SQLException
	 */
	@Override
	public void initRepository(String conn_str, Map<String, String> params)
					throws DBInitException {
		try {
			data_repo = RepositoryFactory.getDataRepository( null, conn_str, params );
			if (params.containsKey(STORE_PLAINTEXT_BODY_KEY)) {
				storePlaintextBody = Boolean.parseBoolean(params.get(STORE_PLAINTEXT_BODY_KEY));
			} else {
				storePlaintextBody = true;
			}

			// this parameter is set by plugins as we need to initialize statements
			// using config from component and not from processors
			if (!params.containsKey("ignoreStatementInitialization")) {
				if (params.containsKey(GROUP_BY_TYPE_KEY)) {
					groupByType = Boolean.parseBoolean(params.get(GROUP_BY_TYPE_KEY));
				} else {
					groupByType = false;
				}
			}
			
			if (params.containsKey(DELETE_EXPIRED_QUERY_TIMEOUT_KEY)) {
				delete_expired_timeout = Integer.parseInt(params.get(DELETE_EXPIRED_QUERY_TIMEOUT_KEY));
				log.log(Level.FINEST, "setting " + DELETE_EXPIRED_QUERY_TIMEOUT_KEY + " to {0}", delete_expired_timeout);
			}
			
			initPreparedStatements(params);
		} catch (Exception ex) {
			log.log(Level.WARNING, "MessageArchiveDB initialization exception", ex);
		}
	}
	
	protected Map<String,String> getQueries(Map<String,String> params) {
		Map<String,String> queries = new HashMap<>();
		
		queries.put(GET_MESSAGES_QUERY_KEY, DEF_GET_MESSAGES_QUERY);
		queries.put(GET_MESSAGES_COUNT_QUERY_KEY, DEF_GET_MESSAGES_COUNT_QUERY);
		queries.put(GET_COLLECTIONS_QUERY_KEY, DEF_GET_COLLECTIONS_QUERY);
		queries.put(GET_COLLECTIONS_COUNT_QUERY_KEY, DEF_GET_COLLECTIONS_COUNT_QUERY);
		queries.put(ADD_MESSAGE_QUERY_KEY, DEF_ADD_MESSAGE_QUERY);
		queries.put(ADD_TAG_TO_MESSAGE_QUERY_KEY, DEF_ADD_TAG_TO_MESSAGE_QUERY);
		queries.put(REMOVE_MESSAGES_QUERY_KEY, DEF_REMOVE_MESSAGES_QUERY);
		queries.put(DELETE_EXPIRED_MESSAGES_QUERY_KEY, DEF_DELETE_EXPIRED_MESSAGES_QUERY);
		queries.put(GET_TAGS_FOR_USER_QUERY_KEY, DEF_GET_TAGS_FOR_USER_QUERY);
		queries.put(GET_TAGS_FOR_USER_COUNT_QUERY_KEY, DEF_GET_TAGS_FOR_USER_COUNT_QUERY);
		
		for (Map.Entry<String,String> e : params.entrySet()) {
			if (!e.getKey().endsWith("-query"))
				continue;
			
			queries.put(e.getKey(), e.getValue());
		}
		
		return queries;
	}
	
	protected void initPreparedStatements(Map<String,String> params) throws SQLException {
		if (params.containsKey("ignoreStatementInitialization"))
			return;
		
		Map<String,String> queries = getQueries(params);
		
		for (Map.Entry<String,String> e : queries.entrySet()) {
			data_repo.initPreparedStatement(e.getKey(), e.getValue());
		}
	}
	
	@Override
	public void destroy() {
		// here we use cached instance of repository pool cached by RepositoryFactory
		// so we should not close it
	}
		
	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param buddy
	 * @param direction
	 * @param timestamp
	 * @param msg
	 * @param tags
	 */
	@Override
	public void archiveMessage(BareJID owner, JID buddy, Direction direction, Date timestamp, Element msg, Set<String> tags) {
		archiveMessage(owner, buddy, direction, timestamp, msg, tags, null);
	}
		
	protected void archiveMessage(BareJID owner, JID buddy, Direction direction, Date timestamp, Element msg, Set<String> tags, Map<String,Object> additionalData) {
		try {
			ResultSet rs = null;
			java.sql.Timestamp mtime = new java.sql.Timestamp(timestamp.getTime());
			msg.addAttribute("time", String.valueOf(mtime.getTime()));

			String type                      = msg.getAttributeStaticStr("type");
			String msgStr                    = msg.toString();
			String body                      = storePlaintextBody ? msg.getChildCData(MSG_BODY_PATH) : null;
			String hash						 = generateHashOfMessageAsString(direction, msg, additionalData);
			PreparedStatement add_message_st = data_repo.getPreparedStatement(owner,
																					 ADD_MESSAGE_QUERY_KEY);

			Long msgId = null;
			
			synchronized (add_message_st) {
				try {
					int i = 1;
					add_message_st.setString(i++, owner.toString());
					add_message_st.setString(i++, buddy.getBareJID().toString());
					add_message_st.setString(i++, buddy.getResource());
					add_message_st.setTimestamp(i++, mtime);
					add_message_st.setShort(i++, direction.getValue());
					add_message_st.setString(i++, type);
					add_message_st.setString(i++, body);
					add_message_st.setString(i++, msgStr);
					add_message_st.setString(i++, hash);

					i = addMessageAdditionalInfo(add_message_st, i, additionalData);

					add_message_st.executeUpdate();

					if (tags != null) {
						rs = add_message_st.getResultSet();
						if (rs.next()) {
								msgId = rs.getLong(1);
						}
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
			
			// in case we tried to archive message which was already archived (ie. by other 
			// session or cluster node) server may ignore insert so it will not return id of inserted
			// record as insert was not executed 
			// in this case we need to exit from this function
			if (msgId == null)
				return;
			
			if (tags != null && !tags.isEmpty()) {
				PreparedStatement add_message_tag_st = data_repo.getPreparedStatement(owner, ADD_TAG_TO_MESSAGE_QUERY_KEY);
				synchronized (add_message_tag_st) {
					for (String tag : tags) {
						add_message_tag_st.setLong(1, msgId);
						add_message_tag_st.setString(2, tag);
						add_message_tag_st.addBatch();
					}
					add_message_tag_st.executeBatch();
				}
			}
		} catch (SQLException ex) {
			log.log(Level.WARNING, "Problem adding new entry to DB: " + msg, ex);
		}
	}

	protected int addMessageAdditionalInfo(PreparedStatement stmt, int i, Map<String,Object> additionalData) throws SQLException {
		return i;
	}	
	
	@Override
	public void deleteExpiredMessages(BareJID owner, LocalDateTime before) throws TigaseDBException {
		try {
			PreparedStatement delete_expired_msgs_st = data_repo.getPreparedStatement(owner, DELETE_EXPIRED_MESSAGES_QUERY_KEY);
			long timestamp_long = before.toEpochSecond(ZoneOffset.UTC) * 1000;
			Timestamp ts = new java.sql.Timestamp(timestamp_long);
			synchronized (delete_expired_msgs_st) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "executing removal of expired messages for domain {0} with timeout set to {1} seconds", 
							new Object[]{owner, delete_expired_timeout});
				}
				delete_expired_msgs_st.setQueryTimeout(delete_expired_timeout);
				delete_expired_msgs_st.setString(1, owner.toString());
				delete_expired_msgs_st.setTimestamp(2, ts);
				delete_expired_msgs_st.executeUpdate();
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Could not remove expired messages", ex);
		}
	}
		
	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param crit
	 *
	 * @return
	 * @throws tigase.db.TigaseDBException
	 */
	@Override
	public List<Element> getCollections(BareJID owner, Criteria crit)
					 throws TigaseDBException {
		try {
			crit.setGroupByType(groupByType);
			Integer count = getCollectionsCount(owner, crit);
			if (count == null)
				count = 0;
			crit.setSize(count);

			List<Element> results = getCollectionsItems(owner, crit);

			RSM rsm = crit.getRSM();
			rsm.setResults(count, crit.getOffset());
			if (!results.isEmpty()) {
				rsm.setFirst(String.valueOf(crit.getOffset()));
				rsm.setLast(String.valueOf(crit.getOffset() + (results.size() - 1)));
			}

			return results;
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve collections", ex);
		}		
	}

	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param crit
	 *
	 * @return
	 * @throws tigase.db.TigaseDBException
	 */
	@Override
	public List<Element> getItems(BareJID owner, Criteria crit)
					 throws TigaseDBException {
		try {
			Integer count = getItemsCount(owner, crit);
			if (count == null) {
				count = 0;
			}
			crit.setSize(count);

			List<Element> items = getItemsItems(owner, crit);

			RSM rsm = crit.getRSM();
			rsm.setResults(count, crit.getOffset());
			if (items!= null && !items.isEmpty()) {
				rsm.setFirst(String.valueOf(crit.getOffset()));
				rsm.setLast(String.valueOf(crit.getOffset() + (items.size() - 1)));
			}

			return items;
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve items", ex);
		}		
	}

	//~--- methods --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @param owner
	 * @param withJid
	 * @param start
	 * @param end
	 *
	 * @throws TigaseDBException
	 */
	@Override
	public void removeItems(BareJID owner, String withJid, Date start, Date end)
					throws TigaseDBException {
		try {
			if (start == null) {
				start = new Date(0);
			}
			if (end == null) {
				end = new Date(0);
			}

			java.sql.Timestamp start_ = new java.sql.Timestamp(start.getTime());
			java.sql.Timestamp end_ = new java.sql.Timestamp(end.getTime());
			PreparedStatement remove_msgs_st = data_repo.getPreparedStatement(owner, REMOVE_MESSAGES_QUERY_KEY);

			synchronized (remove_msgs_st) {
				synchronized (remove_msgs_st) {
					remove_msgs_st.setString(1, owner.toString());
					remove_msgs_st.setString(2, withJid);
					remove_msgs_st.setTimestamp(3, start_);
					remove_msgs_st.setTimestamp(4, end_);
					remove_msgs_st.executeUpdate();
				}
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not remove items", ex);
		}
	}

	/**
	 * Method description
	 * 
	 * @param owner
	 * @param startsWith
	 * @param crit
	 * @return
	 * @throws TigaseDBException 
	 */
	@Override
	public List<String> getTags(BareJID owner, String startsWith, Criteria crit) throws TigaseDBException {
		List<String> results = new ArrayList<String>();
		try {
			ResultSet rs = null;
			int count = 0;
			startsWith = startsWith + "%";
			
			PreparedStatement get_tags_count_st = data_repo.getPreparedStatement(owner, GET_TAGS_FOR_USER_COUNT_QUERY_KEY);
			synchronized (get_tags_count_st) {
				try {
					get_tags_count_st.setString(1, owner.toString());
					get_tags_count_st.setString(2, startsWith);

					rs = get_tags_count_st.executeQuery();
					if (rs.next()) {
						count = rs.getInt(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
			crit.setSize(count);

			PreparedStatement get_tags_st = data_repo.getPreparedStatement(owner, GET_TAGS_FOR_USER_QUERY_KEY);
			synchronized (get_tags_st) {
				try {
					int i = 1;
					get_tags_st.setString(i++, owner.toString());
					get_tags_st.setString(i++, startsWith);

					get_tags_st.setInt(i++, crit.getLimit());
					get_tags_st.setInt(i++, crit.getOffset());

					rs = get_tags_st.executeQuery();
					while (rs.next()) {
						results.add(rs.getString(1));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
			
			RSM rsm = crit.getRSM();
			rsm.setResults(count, crit.getOffset());
			if (results!= null && !results.isEmpty()) {
				rsm.setFirst(String.valueOf(crit.getOffset()));
				rsm.setLast(String.valueOf(crit.getOffset() + (results.size() - 1)));
			}			
		} catch (SQLException ex) {
			throw new TigaseDBException("Could not retrieve known tags from database", ex);
		}
		
		return results;
	}

	private List<Element> getCollectionsItems(BareJID owner, Criteria crit)
					throws SQLException {
		List<Element> results = new LinkedList<Element>();
		ResultSet selectRs = null;
		PreparedStatement get_collections_st = data_repo.getPreparedStatement(owner, GET_COLLECTIONS_QUERY_KEY);

		int i = 2;
		synchronized (get_collections_st) {
			try {
				crit.setItemsQueryParams(get_collections_st, data_repo.getDatabaseType(), owner.toString());

				selectRs = get_collections_st.executeQuery();
				while (selectRs.next()) {
					Timestamp startTs = selectRs.getTimestamp(1);
					String with = selectRs.getString(2);
					String type = selectRs.getString(3);
					addCollectionToResults(results, crit, with, startTs, type);
				}
			} finally {
				data_repo.release(null, selectRs);
			}
		}
		return results;
	}	
	
	private Integer getCollectionsCount(BareJID owner, Criteria crit) throws SQLException {
		ResultSet countRs = null;
		Integer count = null;
		PreparedStatement get_collections_count = data_repo.getPreparedStatement(owner, GET_COLLECTIONS_COUNT_QUERY_KEY);
		synchronized (get_collections_count) {
			try {
				crit.setCountQueryParams(get_collections_count, data_repo.getDatabaseType(), owner.toString());
				countRs = get_collections_count.executeQuery();
				if (countRs.next()) {
					count = countRs.getInt(1);
				}
			} finally {
				data_repo.release(null, countRs);
			}
		}
		return count;
	}
	
	private List<Element> getItemsItems(BareJID owner, Criteria crit) throws SQLException {
		ResultSet rs      = null;		
		Queue<Item> results = new ArrayDeque<Item>();
		PreparedStatement get_messages_st = data_repo.getPreparedStatement(owner, GET_MESSAGES_QUERY_KEY);
		synchronized (get_messages_st) {
			try {
				crit.setItemsQueryParams(get_messages_st, data_repo.getDatabaseType(), owner.toString());

				rs = get_messages_st.executeQuery();
				while (rs.next()) {
					Item item = newItemInstance();
					item.read(rs, crit);
					results.offer(item);
				}
			} finally {
				data_repo.release(null, rs);
			}
		}

		List<Element> msgs = new LinkedList<Element>();

		if (!results.isEmpty()) {
			DomBuilderHandler domHandler = new DomBuilderHandler();

			Date startTimestamp = crit.getStart();
			Item item = null;
			while ((item = results.poll()) != null) {
				// workaround for case in which start was not specified
				if (startTimestamp == null)
					startTimestamp = item.timestamp;
				
				parser.parse(domHandler, item.message.toCharArray(), 0, item.message.length());

				Queue<Element> queue = domHandler.getParsedElements();
				Element msg = null;

				while ((msg = queue.poll()) != null) {
					addMessageToResults(msgs, crit, startTimestamp, item, msg);
				}			
			}

			crit.setStart(startTimestamp);
			
			// no point in sorting messages by secs attribute as messages are already
			// sorted in SQL query and also this sorting is incorrect
//			Collections.sort(msgs, new Comparator<Element>() {
//				@Override
//				public int compare(Element m1, Element m2) {
//					return m1.getAttributeStaticStr("secs").compareTo(
//							m2.getAttributeStaticStr("secs"));
//				}
//			});
		}

		return msgs;		
	}
	
	protected Element addMessageToResults(List<Element> msgs, Criteria crit, Date startTimestamp, Item item, Element msg) {
		return addMessageToResults(msgs, crit, startTimestamp, msg, item.timestamp, item.direction, item.with);
	}
	
	protected Item newItemInstance() {
		return new Item();
	}
	
	private Integer getItemsCount(BareJID owner, Criteria crit) throws SQLException {
		Integer count = null;
		ResultSet rs = null;
		PreparedStatement get_messages_st = data_repo.getPreparedStatement(owner, GET_MESSAGES_COUNT_QUERY_KEY);
		synchronized (get_messages_st) {
			try {
				crit.setCountQueryParams(get_messages_st, data_repo.getDatabaseType(), owner.toString());

				rs = get_messages_st.executeQuery();
				if (rs.next()) {
					count = rs.getInt(1);
				}
			} finally {
				data_repo.release(null, rs);
			}
		}
		return count;
	}
		
	private String generateHashOfMessageAsString(Direction direction, Element msg, Map<String,Object> additionalData) {
		byte[] result = generateHashOfMessage(direction, msg, additionalData);
		return result != null ? Base64.encode(result) : null;
	}

	@Override
	public AbstractCriteria newCriteriaInstance() {
		return new Criteria();
	}
	
	public static class Item<Crit extends Criteria> {
		String message;
		Date timestamp;
		Direction direction;
		String with;
		
		protected int read(ResultSet rs, Crit crit) throws SQLException {
			int i = 1;
			message = rs.getString(i++);
			timestamp = rs.getTimestamp(i++);
			direction = Direction.getDirection(rs.getShort(i++));
			if (crit.getWith() == null) {
				with = rs.getString(i++);
			}
			return i;
		}
	}
	
	public static class Criteria extends AbstractCriteria<Timestamp> {
		
		private Boolean groupByType = null;
		
		public void setGroupByType(Boolean groupByType) {
			this.groupByType = groupByType;
		}
		
		@Override
		protected Timestamp convertTimestamp(Date date) {
			if (date == null)
				return null;
			return new Timestamp(date.getTime());
		}
				
		public int setCountQueryParams(PreparedStatement stmt, DataRepository.dbTypes dbType, String ownerJid) throws SQLException {
			stmt.setString(1, ownerJid);
			return setQueryParams(stmt, dbType, 2);
		}
		
		protected int setQueryParams(PreparedStatement stmt, DataRepository.dbTypes dbType, int i) throws SQLException {	
			if (getWith() != null) {
				stmt.setString(i++, getWith());
			} else {
				stmt.setObject(i++, null);
			}
			if (getStart() != null) {
				stmt.setTimestamp(i++, getStart());
			} else {
				stmt.setObject(i++, null);
			}
			if (getEnd() != null) {
				stmt.setTimestamp(i++, getEnd());
			} else {
				stmt.setObject(i++, null);
			}
			if (getTags().isEmpty()) {
				stmt.setObject(i++, null);
			} else {
				StringBuilder sb = new StringBuilder();
				for (String tag : getTags()) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append('\'').append(tag.replace("'", "''")).append("'");
				}
				stmt.setString(i++, sb.toString());
			}
			if (getContains().isEmpty()) {
				stmt.setObject(i++, null);
			} else {
				StringBuilder sb = new StringBuilder();
				for (String contain : getContains()) {
					if (sb.length() != 0)
						sb.append(",");
					sb.append("'%").append(contain.replace("'", "''")).append("%'");
				}
				stmt.setString(i++, sb.toString());
			}
			if (groupByType != null) {
				stmt.setShort(i++, (short) (groupByType ? 1 : 0));
			}
			return i;
		}
		
		public void setItemsQueryParams(PreparedStatement stmt, DataRepository.dbTypes dbType, String ownerJid) throws SQLException {
			stmt.setString(1, ownerJid);
			int i = setQueryParams(stmt, dbType, 2);
			stmt.setInt(i++, getLimit());
			stmt.setInt(i++, getOffset());
		}

	}
}


//~ Formatted in Tigase Code Convention on 13/02/20
