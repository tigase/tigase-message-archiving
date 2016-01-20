/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  andrzej
 * Created: 2016-01-19
 */

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

create procedure Tig_MA_GetMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _limit int, _offset int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getMessages';

create procedure Tig_MA_GetMessagesCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _limit int, _offset int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getMessagesCount';

create procedure Tig_MA_GetCollections(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _byType smallint, _limit int, _offset int)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getCollections';

create procedure Tig_MA_GetCollectionsCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _byType smallint)
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getCollectionsCount';

create procedure Tig_MA_EnsureJid(_jid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.';

create procedure Tig_MA_AddMessage(_jid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	MODIFIES SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.addMessage';

create procedure Tig_MA_AddTagToMessage(_ownerJid varchar(2049), _buddyJid varchar(2049), _buddyRes varchar(1024), _ts timestamp, 
	_direction smallint, _type varchar(20), _body varchar(32672), _msg varchar(32672), _hash varchar(50))
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

create procedure Tig_MA_GetTagsForUser(_ownerJid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getTagsForUser';

create procedure Tig_MA_GetTagsForUserCount(_ownerJid varchar(2049))
	PARAMETER STYLE JAVA
	LANGUAGE JAVA
	READS SQL DATA
	DYNAMIC RESULT SETS 1
	EXTERNAL NAME 'tigase.archive.db.derby.StoredProcedures.getTagsForUserCount';
