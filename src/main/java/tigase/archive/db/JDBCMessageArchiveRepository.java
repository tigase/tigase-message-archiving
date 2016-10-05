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

import tigase.archive.QueryCriteria;
import tigase.component.exceptions.ComponentException;
import tigase.db.DataRepository;
import tigase.db.Repository;
import tigase.db.TigaseDBException;
import tigase.kernel.beans.config.ConfigField;
import tigase.util.Base64;
import tigase.xml.DomBuilderHandler;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xmpp.Authorization;
import tigase.xmpp.BareJID;
import tigase.xmpp.JID;
import tigase.xmpp.RSM;
import tigase.xmpp.mam.MAMRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class description
 *
 *
 * @version        Enter version here..., 13/02/16
 * @author         Enter your name here...
 */
@Repository.Meta( supportedUris = { "jdbc:[^:]+:.*" } )
public class JDBCMessageArchiveRepository<Q extends QueryCriteria> extends AbstractMessageArchiveRepository<Q, DataRepository> {
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
	private static final String DEF_GET_MESSAGES_POSITION_QUERY = "{ call Tig_MA_GetMessagePosition(?,?,?,?,?,?,?) }";
	private static final String DEF_GET_COLLECTIONS_QUERY = "{ call Tig_MA_GetCollections(?,?,?,?,?,?,?,?,?) }";
	private static final String DEF_GET_COLLECTIONS_COUNT_QUERY = "{ call Tig_MA_GetCollectionsCount(?,?,?,?,?,?,?) }";
	private static final String DEF_ADD_MESSAGE_QUERY = "{ call Tig_MA_AddMessage(?,?,?,?,?,?,?,?,?) }";
	private static final String DEF_ADD_TAG_TO_MESSAGE_QUERY = "{ call Tig_MA_AddTagToMessage(?,?) }";
	private static final String DEF_REMOVE_MESSAGES_QUERY = "{ call Tig_MA_RemoveMessages(?,?,?,?) }";
	private static final String DEF_DELETE_EXPIRED_MESSAGES_QUERY = "{ call Tig_MA_DeleteExpiredMessages(?,?) }";
	private static final String DEF_GET_TAGS_FOR_USER_QUERY = "{ call Tig_MA_GetTagsForUser(?,?,?,?) }";
	private static final String DEF_GET_TAGS_FOR_USER_COUNT_QUERY = "{ call Tig_MA_GetTagsForUserCount(?,?) }";

	@ConfigField(desc = "Query to retrieve list of messages", alias = "get-messages-query")
	protected String GET_MESSAGES_QUERY = DEF_GET_MESSAGES_QUERY;
	@ConfigField(desc = "Query to retrieve number of messages", alias = "get-messages-count-query")
	protected String GET_MESSAGES_COUNT_QUERY = DEF_GET_MESSAGES_COUNT_QUERY;
	@ConfigField(desc = "Query to retrieve message possition", alias = "get-message-position-query")
	protected String GET_MESSAGE_POSITION_QUERY = DEF_GET_MESSAGES_POSITION_QUERY;
	@ConfigField(desc = "Query to retrieve list of collections", alias = "get-collections-query")
	protected String GET_COLLECTIONS_QUERY = DEF_GET_COLLECTIONS_QUERY;
	@ConfigField(desc = "Query to retrieve number of collections", alias = "get-collections-count-query")
	protected String GET_COLLECTIONS_COUNT_QUERY = DEF_GET_COLLECTIONS_COUNT_QUERY;
	@ConfigField(desc = "Query to add message to store", alias = "add-message-query")
	protected String ADD_MESSAGE_QUERY = DEF_ADD_MESSAGE_QUERY;
	@ConfigField(desc = "Query to add tag to message in store", alias = "add-tag-to-message-query")
	protected String ADD_TAG_TO_MESSAGE_QUERY = DEF_ADD_TAG_TO_MESSAGE_QUERY;
	@ConfigField(desc = "Query to remove messages", alias = "remove-messages-query")
	protected String REMOVE_MESSAGES_QUERY = DEF_REMOVE_MESSAGES_QUERY;
	@ConfigField(desc = "Query to delete expired messages", alias = "delete-expired-messages-query")
	protected String DELETE_EXPIRED_MESSAGES_QUERY = DEF_DELETE_EXPIRED_MESSAGES_QUERY;
	@ConfigField(desc = "Query to retrieve tags used by user", alias = "get-tags-for-user-query")
	protected String GET_TAGS_FOR_USER_QUERY = DEF_GET_TAGS_FOR_USER_QUERY;
	@ConfigField(desc = "Query to retrieve number of tags used by user", alias = "get-tags-for-user-count-query")
	protected String GET_TAGS_FOR_USER_COUNT_QUERY = DEF_GET_TAGS_FOR_USER_COUNT_QUERY;

	//~--- fields ---------------------------------------------------------------
	
	protected DataRepository data_repo = null;
	@ConfigField(desc = "Store plaintext body in separate field", alias = STORE_PLAINTEXT_BODY_KEY)
	private boolean storePlaintextBody = true;
	@ConfigField(desc = "Group collections by stanza type", alias = GROUP_BY_TYPE_KEY)
	private boolean groupByType = false;
	@ConfigField(desc = "Delete expired messages statement query timeout", alias = DELETE_EXPIRED_QUERY_TIMEOUT_KEY)
	private int delete_expired_timeout = DEF_DELETE_EXPIRED_QUERY_TIMEOUT_VAL;

	//~--- methods --------------------------------------------------------------

	@Override
	public void setDataSource(DataRepository data_repo) {
		try {
			initPreparedStatements(data_repo);
			this.data_repo = data_repo;
		} catch (SQLException ex) {
			throw new RuntimeException("MessageArchiveDB initialization exception", ex);
		}
	}

	protected void initPreparedStatements(DataRepository data_repo) throws SQLException {
		data_repo.initPreparedStatement(GET_MESSAGES_QUERY, GET_MESSAGES_QUERY);
		data_repo.initPreparedStatement(GET_MESSAGES_COUNT_QUERY, GET_MESSAGES_COUNT_QUERY);
		data_repo.initPreparedStatement(GET_MESSAGE_POSITION_QUERY, GET_MESSAGE_POSITION_QUERY);
		data_repo.initPreparedStatement(GET_COLLECTIONS_QUERY, GET_COLLECTIONS_QUERY);
		data_repo.initPreparedStatement(GET_COLLECTIONS_COUNT_QUERY, GET_COLLECTIONS_COUNT_QUERY);
		data_repo.initPreparedStatement(ADD_MESSAGE_QUERY, ADD_MESSAGE_QUERY);
		data_repo.initPreparedStatement(ADD_TAG_TO_MESSAGE_QUERY, ADD_TAG_TO_MESSAGE_QUERY);
		data_repo.initPreparedStatement(REMOVE_MESSAGES_QUERY, REMOVE_MESSAGES_QUERY);
		data_repo.initPreparedStatement(DELETE_EXPIRED_MESSAGES_QUERY, DELETE_EXPIRED_MESSAGES_QUERY);
		data_repo.initPreparedStatement(GET_TAGS_FOR_USER_QUERY, GET_TAGS_FOR_USER_QUERY);
		data_repo.initPreparedStatement(GET_TAGS_FOR_USER_COUNT_QUERY, GET_TAGS_FOR_USER_COUNT_QUERY);
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
					ADD_MESSAGE_QUERY);

			Long msgId = null;
			
			synchronized (add_message_st) {
				try {
					int i = 1;
					add_message_st.setString(i++, owner.toString().toLowerCase());
					add_message_st.setString(i++, buddy.getBareJID().toString().toLowerCase());
					add_message_st.setString(i++, buddy.getResource());
					add_message_st.setTimestamp(i++, mtime);
					add_message_st.setShort(i++, direction.getValue());
					add_message_st.setString(i++, type);
					add_message_st.setString(i++, body);
					add_message_st.setString(i++, msgStr);
					add_message_st.setString(i++, hash);

					i = addMessageAdditionalInfo(add_message_st, i, additionalData);

					// works for MSSQL, MySQL and PostgreSQL
					rs = add_message_st.executeQuery();
					if (tags != null) {
						if (rs.next()) {
								msgId = rs.getLong(1);
						}
					}

					// below works for MySQL and PostgreSQL
//					add_message_st.executeUpdate();
//					if (tags != null) {
//						rs = add_message_st.getResultSet();
//						if (rs.next()) {
//								msgId = rs.getLong(1);
//						}
//					}

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
				PreparedStatement add_message_tag_st = data_repo.getPreparedStatement(owner, ADD_TAG_TO_MESSAGE_QUERY);
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
			PreparedStatement delete_expired_msgs_st = data_repo.getPreparedStatement(owner, DELETE_EXPIRED_MESSAGES_QUERY);
			long timestamp_long = before.toEpochSecond(ZoneOffset.UTC) * 1000;
			Timestamp ts = new java.sql.Timestamp(timestamp_long);
			synchronized (delete_expired_msgs_st) {
				if (log.isLoggable(Level.FINEST)) {
					log.log(Level.FINEST, "executing removal of expired messages for domain {0} with timeout set to {1} seconds", 
							new Object[]{owner, delete_expired_timeout});
				}
				delete_expired_msgs_st.setQueryTimeout(delete_expired_timeout);
				delete_expired_msgs_st.setString(1, owner.toString().toLowerCase());
				delete_expired_msgs_st.setTimestamp(2, ts);
				delete_expired_msgs_st.executeUpdate();
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Could not remove expired messages", ex);
		}
	}
		
	//~--- get methods ----------------------------------------------------------

	@Override
	public void queryCollections(Q crit, CollectionHandler<Q> collectionHandler)
					 throws TigaseDBException {
		try {
			Integer count = getCollectionsCount(crit);
			if (count == null)
				count = 0;

			Integer after = getColletionPosition(crit.getRsm().getAfter(), crit);
			Integer before = getColletionPosition(crit.getRsm().getBefore(), crit);

			calculateOffsetAndPosition(crit, count, before, after);

			getCollectionsItems(crit, collectionHandler);
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve collections", ex);
		}		
	}

	@Override
	public void queryItems(Q crit, ItemHandler<Q, MAMRepository.Item> itemHandler)
					 throws TigaseDBException, ComponentException {
		try {
			Integer count = getItemsCount(crit);
			if (count == null) {
				count = 0;
			}

			Integer after = getItemPosition(crit.getRsm().getAfter(), crit);
			Integer before = getItemPosition(crit.getRsm().getBefore(), crit);

			calculateOffsetAndPosition(crit, count, before, after);

			getItemsItems(crit, itemHandler);
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not retrieve items", ex);
		}		
	}

	//~--- methods --------------------------------------------------------------

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
			PreparedStatement remove_msgs_st = data_repo.getPreparedStatement(owner, REMOVE_MESSAGES_QUERY);

			synchronized (remove_msgs_st) {
				synchronized (remove_msgs_st) {
					remove_msgs_st.setString(1, owner.toString().toLowerCase());
					remove_msgs_st.setString(2, withJid.toLowerCase());
					remove_msgs_st.setTimestamp(3, start_);
					remove_msgs_st.setTimestamp(4, end_);
					remove_msgs_st.executeUpdate();
				}
			}
		} catch (SQLException ex) {
			throw new TigaseDBException("Cound not remove items", ex);
		}
	}

	@Override
	public List<String> getTags(BareJID owner, String startsWith, Q crit) throws TigaseDBException {
		List<String> results = new ArrayList<String>();
		try {
			ResultSet rs = null;
			int count = 0;
			startsWith = startsWith + "%";
			
			PreparedStatement get_tags_count_st = data_repo.getPreparedStatement(owner, GET_TAGS_FOR_USER_COUNT_QUERY);
			synchronized (get_tags_count_st) {
				try {
					get_tags_count_st.setString(1, owner.toString().toLowerCase());
					get_tags_count_st.setString(2, startsWith);

					rs = get_tags_count_st.executeQuery();
					if (rs.next()) {
						count = rs.getInt(1);
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
			String beforeStr = crit.getRsm().getBefore();
			String afterStr = crit.getRsm().getAfter();
			calculateOffsetAndPosition(crit, count, beforeStr == null ? null : Integer.parseInt(beforeStr), afterStr == null ? null : Integer.parseInt(afterStr));

			PreparedStatement get_tags_st = data_repo.getPreparedStatement(owner, GET_TAGS_FOR_USER_QUERY);
			synchronized (get_tags_st) {
				try {
					int i = 1;
					get_tags_st.setString(i++, owner.toString().toLowerCase());
					get_tags_st.setString(i++, startsWith);

					get_tags_st.setInt(i++, crit.getRsm().getMax());
					get_tags_st.setInt(i++, crit.getRsm().getIndex());

					rs = get_tags_st.executeQuery();
					while (rs.next()) {
						results.add(rs.getString(1));
					}
				} finally {
					data_repo.release(null, rs);
				}
			}
			
			RSM rsm = crit.getRsm();
			rsm.setResults(count, rsm.getIndex());
			if (results!= null && !results.isEmpty()) {
				rsm.setFirst(String.valueOf(rsm.getIndex()));
				rsm.setLast(String.valueOf(rsm.getIndex() + (results.size() - 1)));
			}			
		} catch (SQLException ex) {
			throw new TigaseDBException("Could not retrieve known tags from database", ex);
		}
		
		return results;
	}

	private void getCollectionsItems(Q crit, CollectionHandler<Q> collectionHandler)
					throws SQLException {
		ResultSet selectRs = null;
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_collections_st = data_repo.getPreparedStatement(owner, GET_COLLECTIONS_QUERY);

		int i = 2;
		synchronized (get_collections_st) {
			try {
				setItemsQueryParams(get_collections_st, owner.toString(), crit, groupByType);

				selectRs = get_collections_st.executeQuery();
				while (selectRs.next()) {
					Timestamp startTs = selectRs.getTimestamp(1);
					String with = selectRs.getString(2);
					String type = selectRs.getString(3);
					collectionHandler.collectionFound(crit, with, startTs, type);
				}
			} finally {
				data_repo.release(null, selectRs);
			}
		}

		List<Element> collections = crit.getCollections();
		if (collections != null) {
			int first = crit.getRsm().getIndex();
			crit.getRsm().setFirst(String.valueOf(first));
			crit.getRsm().setLast(String.valueOf(first + collections.size() - 1));
		}
	}
	
	private Integer getCollectionsCount(Q crit) throws SQLException {
		ResultSet countRs = null;
		Integer count = null;
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_collections_count = data_repo.getPreparedStatement(owner, GET_COLLECTIONS_COUNT_QUERY);
		synchronized (get_collections_count) {
			try {
				setCountQueryParams(get_collections_count, owner.toString(), crit, groupByType);
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

	private Integer getColletionPosition(String uid, Q query) {
		if (uid == null || uid.isEmpty())
			return null;

		return Integer.parseInt(uid);
	}
	
	private void getItemsItems(Q crit, ItemHandler<Q, MAMRepository.Item> itemHandler) throws SQLException {
		ResultSet rs      = null;		
		Queue<Item> results = new ArrayDeque<Item>();
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_messages_st = data_repo.getPreparedStatement(owner, GET_MESSAGES_QUERY);
		synchronized (get_messages_st) {
			try {
				setItemsQueryParams(get_messages_st, owner.toString(), crit, null);

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

		if (!results.isEmpty()) {
			DomBuilderHandler domHandler = new DomBuilderHandler();

			Date startTimestamp = crit.getStart();
			Item item = null;
			int idx = crit.getRsm().getIndex();
			int i = 0;
			while ((item = results.poll()) != null) {
				// workaround for case in which start was not specified
				if (startTimestamp == null)
					startTimestamp = item.timestamp;
				
				parser.parse(domHandler, item.messageStr.toCharArray(), 0, item.messageStr.length());

				Queue<Element> queue = domHandler.getParsedElements();
				Element msg = null;

				item.messageStr = null;
				item.messageEl = queue.poll();
				if (!crit.getUseMessageIdInRsm()) {
					item.id = String.valueOf(idx + i);
				}
				itemHandler.itemFound(crit, item);
				queue.clear();
				i++;
			}

			crit.setStart(startTimestamp);
		}
	}
	
	protected Timestamp convertToTimestamp(Date date) {
		if (date == null)
			return null;
		return new Timestamp(date.getTime());
	}

	protected int setCountQueryParams(PreparedStatement stmt, String ownerJid, Q crit, Boolean groupByType) throws SQLException {
		stmt.setString(1, ownerJid.toLowerCase());
		return setQueryParams(stmt, crit, groupByType, 2);
	}

	protected int setQueryParams(PreparedStatement stmt, Q crit, Boolean groupByType, int i) throws SQLException {
		if (crit.getWith() != null) {
			stmt.setString(i++, crit.getWith().getBareJID().toString().toLowerCase());
		} else {
			stmt.setObject(i++, null);
		}
		if (crit.getStart() != null) {
			stmt.setTimestamp(i++, convertToTimestamp(crit.getStart()));
		} else {
			stmt.setObject(i++, null);
		}
		if (crit.getEnd() != null) {
			stmt.setTimestamp(i++, convertToTimestamp(crit.getEnd()));
		} else {
			stmt.setObject(i++, null);
		}
		if (crit.getTags().isEmpty()) {
			stmt.setObject(i++, null);
		} else {
			StringBuilder sb = new StringBuilder();
			for (String tag : crit.getTags()) {
				if (sb.length() != 0)
					sb.append(",");
				sb.append('\'').append(tag.replace("'", "''")).append("'");
			}
			stmt.setString(i++, sb.toString());
		}
		if (crit.getContains().isEmpty()) {
			stmt.setObject(i++, null);
		} else {
			StringBuilder sb = new StringBuilder();
			for (String contain : crit.getContains()) {
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

	public void setItemsQueryParams(PreparedStatement stmt, String ownerJid, Q crit, Boolean groupByType) throws SQLException {
		stmt.setString(1, ownerJid.toLowerCase());
		int i = setQueryParams(stmt, crit, groupByType, 2);
		stmt.setInt(i++, crit.getRsm().getMax());
		stmt.setInt(i++, crit.getRsm().getIndex());
	}

	protected Item newItemInstance() {
		return new Item();
	}
	
	private Integer getItemsCount(Q crit) throws SQLException {
		Integer count = null;
		ResultSet rs = null;
		BareJID owner = crit.getQuestionerJID().getBareJID();
		PreparedStatement get_messages_st = data_repo.getPreparedStatement(owner, GET_MESSAGES_COUNT_QUERY);
		synchronized (get_messages_st) {
			try {
				setCountQueryParams(get_messages_st, owner.toString(), crit, null);

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

	private Integer getItemPosition(String uid, Q query) throws SQLException, ComponentException {
		if (uid == null || uid.isEmpty())
			return null;

		if (!query.getUseMessageIdInRsm())
			return Integer.parseInt(uid);

		Integer position = null;
		ResultSet rs = null;
		BareJID owner = query.getQuestionerJID().getBareJID();
		PreparedStatement get_message_position_st = data_repo.getPreparedStatement(owner, GET_MESSAGE_POSITION_QUERY);
		synchronized (get_message_position_st) {
			try {
				int i = setCountQueryParams(get_message_position_st, owner.toString(), query, null);
				get_message_position_st.setString(i++, uid);

				rs = get_message_position_st.executeQuery();
				if (rs.next()) {
					position = rs.getInt(1);
				}
			} finally {
				data_repo.release(null, rs);
			}
		}

		if (position == null || position < 1)
			throw new ComponentException(Authorization.BAD_REQUEST, "Item with " + uid + " not found");

		return position - 1;
	}

	private String generateHashOfMessageAsString(Direction direction, Element msg, Map<String,Object> additionalData) {
		byte[] result = generateHashOfMessage(direction, msg, additionalData);
		return result != null ? Base64.encode(result) : null;
	}

	@Override
	public Q newQuery() {
		return (Q) new QueryCriteria();
	}

	public static class Item<Q extends QueryCriteria> implements MessageArchiveRepository.Item {
		String id;
		String messageStr;
		Element messageEl;
		Date timestamp;
		Direction direction;
		String with;
		
		protected int read(ResultSet rs, Q crit) throws SQLException {
			int i = 1;
			messageStr = rs.getString(i++);
			timestamp = rs.getTimestamp(i++);
			direction = Direction.getDirection(rs.getShort(i++));
			if (crit.getWith() == null) {
				with = rs.getString(i);
			}
			i++;
			id = rs.getString(i++);
			return i;
		}

		@Override
		public String getId() {
			return id;
		}

		@Override
		public Direction getDirection() {
			return direction;
		}

		@Override
		public Element getMessage() {
			return messageEl;
		}

		@Override
		public Date getTimestamp() {
			return timestamp;
		}

		@Override
		public String getWith() {
			return with;
		}
	}
	
}


//~ Formatted in Tigase Code Convention on 13/02/20
