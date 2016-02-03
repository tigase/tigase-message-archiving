--
--  Tigase Message Archiving Component
--  Copyright (C) 2016 "Tigase, Inc." <office@tigase.com>
--
--  This program is free software: you can redistribute it and/or modify
--  it under the terms of the GNU Affero General Public License as published by
--  the Free Software Foundation, either version 3 of the License.
--
--  This program is distributed in the hope that it will be useful,
--  but WITHOUT ANY WARRANTY; without even the implied warranty of
--  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
--  GNU Affero General Public License for more details.
--
--  You should have received a copy of the GNU Affero General Public License
--  along with this program. Look for COPYING file in the top folder.
--  If not, see http://www.gnu.org/licenses/.

-- QUERY START:
create procedure Tig_MA_GetMessages(ownerJid varchar(2049), buddyJid varchar(2049), "from" timestamp, "to" timestamp, "tags" varchar(32672), "contains" varchar(32672), "limit" int, "offset" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getMessages';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetMessagesCount(ownerJid varchar(2049), buddyJid varchar(2049), "from" timestamp, "to" timestamp, "tags" varchar(32672), "contains" varchar(32672))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getMessagesCount';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetCollections(ownerJid varchar(2049), buddyJid varchar(2049), "from" timestamp, "to" timestamp, "tags" varchar(32672), "contains" varchar(32672), byType smallint, "limit" int, "offset" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getCollections';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetCollectionsCount(ownerJid varchar(2049), buddyJid varchar(2049), "from" timestamp, "to" timestamp, "tags" varchar(32672), "contains" varchar(32672), byType smallint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getCollectionsCount';
-- QUERY END:

-- QUERY START:
create function Tig_MA_EnsureJid(jid varchar(2049))
	RETURNS bigint
	PARAMETER STYLE JAVA
	NO SQL
	LANGUAGE JAVA
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.ensureJid';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_AddMessage(ownerJid varchar(2049), buddyJid varchar(2049), buddyRes varchar(1024), ts timestamp, 
	direction smallint, "type" varchar(20), "body" varchar(32672), "msg" varchar(32672), "hash" varchar(50))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.addMessage';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_AddTagToMessage(msg_id bigint, tag varchar(255))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.addTagToMessage';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_RemoveMessages(ownerJid varchar(2049), buddyJid varchar(2049), "from" timestamp, "to" timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.removeMessages';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_DeleteExpiredMessages("domain" varchar(1024), "before" timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.deleteExpiredMessages';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetTagsForUser(ownerJid varchar(2049), tagStartsWith varchar(255), "limit" int, "offset" int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getTagsForUser';
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetTagsForUserCount(ownerJid varchar(2049), tagStartsWith varchar(255))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getTagsForUserCount';
-- QUERY END: