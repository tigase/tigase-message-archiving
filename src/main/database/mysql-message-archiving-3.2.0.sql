--
-- Tigase Message Archiving Component - Implementation of Message Archiving component for Tigase XMPP Server.
-- Copyright (C) 2012 Tigase, Inc. (office@tigase.com)
--
-- This program is free software: you can redistribute it and/or modify
-- it under the terms of the GNU Affero General Public License as published by
-- the Free Software Foundation, version 3 of the License.
--
-- This program is distributed in the hope that it will be useful,
-- but WITHOUT ANY WARRANTY; without even the implied warranty of
-- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
-- GNU Affero General Public License for more details.
--
-- You should have received a copy of the GNU Affero General Public License
-- along with this program. Look for COPYING file in the top folder.
-- If not, see http://www.gnu.org/licenses/.
--

-- QUERY START:
drop procedure if exists Tig_MA_AddMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_AddTagToMessage;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure Tig_MA_AddMessage(_ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _ts timestamp(6),
    _stableId varchar(36) CHARSET utf8,  _stanzaId varchar(64) CHARSET utf8, _refStableId varchar(36) CHARSET utf8,
    _body mediumtext CHARSET utf8mb4 collate utf8mb4_bin, _msg mediumtext CHARSET utf8mb4 collate utf8mb4_bin)
begin
	declare _owner_id bigint;
	declare _buddy_id bigint;
	-- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
    END;

    set @is_ref = 0;
    if _refStableId is not null then
        set @is_ref = 1;
    end if;

    START TRANSACTION;
	call Tig_MA_EnsureJid(_ownerJid, _owner_id);
	COMMIT;
	START TRANSACTION;
	call Tig_MA_EnsureJid(_buddyJid, _buddy_id);
	COMMIT;

	START TRANSACTION;
    insert ignore into tig_ma_msgs (owner_id, stable_id, buddy_id, ts, stanza_id, is_ref, ref_stable_id, body, msg)
    values (_owner_id, Tig_MA_UuidToOrdered(_stableId), _buddy_id, _ts, _stanzaId,  @is_ref, Tig_MA_UuidToOrdered(_refStableId), _body, _msg);
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_AddTagToMessage(_ownerJid varchar(2049) CHARSET utf8, _stableId varchar(36) CHARSET utf8, _tag varchar(255) CHARSET utf8mb4 collate utf8mb4_bin)
begin
    declare _owner_id bigint;
    declare _tag_id bigint;
    declare x bigint;

    -- DO NOT REMOVE, required for properly handle exceptions within transactions!
    DECLARE exit handler for sqlexception
    BEGIN
        -- ERROR
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    call Tig_MA_EnsureJid(_ownerJid, _owner_id);
    COMMIT;

    START TRANSACTION;
    select tag_id into _tag_id from tig_ma_tags where owner_id = _owner_id and tag = _tag;
    if _tag_id is null then
        set x = LAST_INSERT_ID();
        insert into tig_ma_tags (owner_id, tag)
        values (_owner_id, _tag)
        on duplicate key update tag_id = LAST_INSERT_ID(tag_id);
        select LAST_INSERT_ID() into _tag_id;
        if _tag_id = x then
            select tag_id into _tag_id from tig_ma_tags where owner_id = _owner_id and tag = _tag;
        end if;
    end if;
    insert into tig_ma_msgs_tags (msg_owner_id, msg_stable_id, tag_id) values (_owner_id, Tig_MA_UuidToOrdered(_stableId), _tag_id) on duplicate key update tag_id = tag_id;
    COMMIT;
end //
-- QUERY END:

delimiter ;