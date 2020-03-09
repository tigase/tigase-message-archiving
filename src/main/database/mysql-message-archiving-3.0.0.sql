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
drop procedure if exists Tig_MA_Upgrade;
-- QUERY END:

-- QUERY START:
drop function if exists Tig_MA_UuidToOrdered;
-- QUERY END:

-- QUERY START:
drop function if exists Tig_MA_OrderedToUuid;
-- QUERY END:

delimiter //

-- QUERY START:
create function Tig_MA_UuidToOrdered(_uuid varchar(36)) returns binary(16) deterministic
    return unhex(concat(substr(_uuid, 15, 4), substr(_uuid, 10, 4), substr(_uuid, 1, 8), substr(_uuid, 20, 4), substr(_uuid, 25)));
-- QUERY END:

-- QUERY START:
create function Tig_MA_OrderedToUuid(_uuid binary(16)) returns varchar(36) deterministic
begin
    declare hexed varchar(36);
    select hex(_uuid) into hexed;

    return concat(substr(hexed, 9, 8), '-', substr(hexed, 5, 4), '-', substr(hexed, 1, 4), '-', substr(hexed, 17, 4), '-', substr(hexed, 21));
end;
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_Upgrade()
begin
    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'stable_id') then
        alter table tig_ma_msgs add column stable_id binary(16);

        update tig_ma_msgs set stable_id = Tig_MA_UuidToOrdered(UUID()) where stable_id is null;

        alter table tig_ma_msgs modify stable_id binary(16) not null;

        create unique index tig_ma_msgs_owner_id_buddy_id_stable_id_index on tig_ma_msgs (owner_id, buddy_id, stable_id);
    end if;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
call Tig_MA_Upgrade();
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_Upgrade;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_AddMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetMessagePosition;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure Tig_MA_AddMessage(_ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8,
	 _buddyRes varchar(1024)  CHARSET utf8, _ts timestamp(6), _direction smallint, _type varchar(20) CHARSET utf8,
	 _body text CHARSET utf8mb4 collate utf8mb4_bin, _msg text CHARSET utf8mb4 collate utf8mb4_bin, _stableId varchar(36) CHARSET utf8)
begin
	declare _owner_id bigint;
	declare _buddy_id bigint;
	declare _msg_id bigint;
	declare x bigint;

	START TRANSACTION;
	select Tig_MA_EnsureJid(_ownerJid) into _owner_id;
	COMMIT;
	START TRANSACTION;
	select Tig_MA_EnsureJid(_buddyJid) into _buddy_id;
	COMMIT;

	START TRANSACTION;
    set x = LAST_INSERT_ID();
	insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, `type`, body, msg, stable_id)
		values (_owner_id, _buddy_id, _buddyRes, _ts, _direction, _type, _body, _msg, Tig_MA_UuidToOrdered(_stableId));

	select LAST_INSERT_ID() into _msg_id;
	COMMIT;

    if x <> _msg_id then
	    select _msg_id as msg_id;
    else
        select null as msg_id;
	end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetMessages( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _limit int, _offset int)
begin
	if _tags is not null or _contains is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @limit = _limit;
		set @offset = _offset;
		select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
		select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
		set @msgs_query = 'select m.msg, m.ts, m.direction, b.jid, Tig_MA_OrderedToUuid(m.stable_id) as stable_id
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
		set @pagination_query = ' limit ? offset ?';
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query, ' order by m.ts', @pagination_query);
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @limit, @offset;
		deallocate prepare stmt;
	else
		select m.msg, m.ts, m.direction, b.jid, Tig_MA_OrderedToUuid(m.stable_id) as stable_id
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(_ownerJid))
			and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to)
		order by m.ts
		limit _limit offset _offset;
	end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetMessagePosition( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _stableId varchar(36) CHARSET utf8)
begin
	if _tags is not null or _contains is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @stableId = _stableId;
		select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
		select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
		set @msgs_query = 'select x.position from (
		select @row_number := @row_number + 1 AS position, m.stable_id
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id,
			(select @row_number := 0) as t
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query, ' order by m.ts) x where x.stable_id = Tig_MA_UuidToOrdered(?)');
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @stableId;
		deallocate prepare stmt;
	else
	    set @row_number = 0;
	    select x.position from (
		    select @row_number := @row_number + 1 AS position, m.stable_id
		    from tig_ma_msgs m
			    inner join tig_ma_jids o on m.owner_id = o.jid_id
			    inner join tig_ma_jids b on b.jid_id = m.buddy_id
		    where
			    o.jid_sha1 = SHA1(LOWER(_ownerJid))
			    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
			    and (_from is null or m.ts >= _from)
			    and (_to is null or m.ts <= _to)
			order by m.ts
		) x where x.stable_id = Tig_MA_UuidToOrdered(_stableId);
	end if;
end //
-- QUERY END:

delimiter ;

call TigSetComponentVersion('message-archiving', '3.0.0');
