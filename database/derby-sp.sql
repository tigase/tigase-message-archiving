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

drop procedure if exists Tig_MA_GetMessages;

drop procedure if exists Tig_MA_GetMessagesCount;

drop procedure if exists Tig_MA_GetCollections;

drop procedure if exists Tig_MA_GetCollectionsCount;

drop function if exists Tig_MA_EnsureJid;

drop procedure if exists Tig_MA_AddMessage;

drop procedure if exists Tig_MA_AddTagToMessage;

drop procedure if exists Tig_MA_RemoveMessages;

drop procedure if exists Tig_MA_DeleteExpiredMessages;

drop procedure if exists Tig_MA_GetTagsForUser;

drop procedure if exists Tig_MA_GetTagsForUserCount;

create procedure Tig_MA_GetMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags varchar(32672), _contains varchar(32672), _limit int, _offset int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getMessages';

create procedure Tig_MA_GetMessagesCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags varchar(32672), _contains varchar(32672))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getMessagesCount';

create procedure Tig_MA_GetCollections(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags varchar(32672), _contains varchar(32672), _byType smallint, _limit int, _offset int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getCollections';

create procedure Tig_MA_GetCollectionsCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags varchar(32672), _contains varchar(32672), _byType smallint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getCollectionsCount';

create function Tig_MA_EnsureJid(_jid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.ensureJid';

create procedure Tig_MA_AddMessage(_ownerJid varchar(2049), _buddyJid varchar(2049), _buddyRes varchar(1024), _ts timestamp, 
	_direction smallint, _type varchar(20), _body varchar(32672), _msg varchar(32672), _hash varchar(50))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.addMessage';

create procedure Tig_MA_AddTagToMessage(msg_id bigint, tag varchar(255))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.addTagToMessage';

create procedure Tig_MA_RemoveMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.removeMessages';

create procedure Tig_MA_DeleteExpiredMessages(_domain varchar(1024), _before timestamp)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.deleteExpiredMessages';

create procedure Tig_MA_GetTagsForUser(_ownerJid varchar(2049), _tagStartsWith varchar(255), _limit int, _offset int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getTagsForUser';

create procedure Tig_MA_GetTagsForUserCount(_ownerJid varchar(2049), _tagStartsWith)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getTagsForUserCount';
