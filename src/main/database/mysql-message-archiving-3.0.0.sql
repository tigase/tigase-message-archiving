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
begin
    if _uuid is null then
        return null;
    end if;
    return unhex(concat(substr(_uuid, 15, 4), substr(_uuid, 10, 4), substr(_uuid, 1, 8), substr(_uuid, 20, 4), substr(_uuid, 25)));
end //
-- QUERY END:

-- QUERY START:
create function Tig_MA_OrderedToUuid(_uuid binary(16)) returns varchar(36) deterministic
begin
    declare hexed varchar(36);
    if _uuid is null then
        return null;
    end if;
    
    select hex(_uuid) into hexed;

    return concat(substr(hexed, 9, 8), '-', substr(hexed, 5, 4), '-', substr(hexed, 1, 4), '-', substr(hexed, 17, 4), '-', substr(hexed, 21));
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_Upgrade()
begin
    declare _cname varchar(64);
    if not exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'stable_id') then
        alter table tig_ma_msgs add column stable_id binary(16);

        update tig_ma_msgs set stable_id = Tig_MA_UuidToOrdered(UUID()) where stable_id is null;

        alter table tig_ma_msgs modify stable_id binary(16) not null;
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id_buddy_res_index') then
        drop index tig_ma_msgs_owner_id_buddy_id_buddy_res_index on tig_ma_msgs;
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id_stanza_hash_index') then
        drop index tig_ma_msgs_owner_id_buddy_id_stanza_hash_index on tig_ma_msgs;
    end if;
    if exists (select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'stanza_hash') then
        alter table tig_ma_msgs drop column stanza_hash;
    end if;
    if exists(select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'owner_id' and is_nullable = 'YES') then
        select constraint_name into _cname from information_schema.key_column_usage where table_schema = database() and table_name = 'tig_ma_msgs' and referenced_table_name = 'tig_ma_jids' and column_name = 'owner_id';
        set @query = CONCAT("alter table tig_ma_msgs drop foreign key ", _cname);
        prepare stmt from @query;
        execute stmt;
        deallocate prepare stmt;
        alter table tig_ma_msgs change owner_id owner_id bigint unsigned not null;
        alter table tig_ma_msgs add foreign key (owner_id) references tig_ma_jids (jid_id);
    end if;
    if exists(select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'buddy_id' and is_nullable = 'YES') then
        select constraint_name into _cname from information_schema.key_column_usage where table_schema = database() and table_name = 'tig_ma_msgs' and referenced_table_name = 'tig_ma_jids' and column_name = 'buddy_id';
        set @query = CONCAT("alter table tig_ma_msgs drop foreign key ", _cname);
        prepare stmt from @query;
        execute stmt;
        deallocate prepare stmt;
        alter table tig_ma_msgs change buddy_id buddy_id bigint unsigned not null;
        alter table tig_ma_msgs add foreign key (buddy_id) references tig_ma_jids (jid_id);
    end if;

    set _cname = null;

    select constraint_name into _cname from information_schema.key_column_usage where table_schema = database() and table_name = 'tig_ma_msgs_tags' and referenced_table_name = 'tig_ma_msgs' and column_name = 'msg_id';
    if _cname is not null then
        set @query = CONCAT("alter table tig_ma_msgs_tags drop foreign key ", _cname);
        prepare stmt from @query;
        execute stmt;
        deallocate prepare stmt;
    end if;

    if exists(select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'msg_id' and extra like '%auto_increment%') then
        alter table tig_ma_msgs
            add column stanza_id varchar(64),
            add column ref_stable_id binary(16),
            add column is_ref tinyint not null default 0,
            modify column msg_id bigint unsigned not null,
            drop primary key,
            add primary key (owner_id, stable_id);
    end if;

    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'owner_id') then
        drop index owner_id on tig_ma_msgs;
    end if;

    if not exists(select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs_tags' and column_name = 'msg_owner_id') then
        alter table tig_ma_msgs_tags
            add column msg_owner_id bigint unsigned,
            add column msg_stable_id binary(16),
            drop primary key;

        update tig_ma_msgs_tags
            inner join tig_ma_msgs on tig_ma_msgs_tags.msg_id = tig_ma_msgs.msg_id
        set tig_ma_msgs_tags.msg_owner_id = tig_ma_msgs.owner_id, tig_ma_msgs_tags.msg_stable_id = tig_ma_msgs.stable_id;
    end if;

    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs_tags' and INDEX_NAME = 'tig_ma_msgs_tags_msg_id') then
        drop index tig_ma_msgs_tags_msg_id on tig_ma_msgs_tags;
    end if;
    if not exists(select 1 from information_schema.key_column_usage where table_schema = database() and table_name = 'tig_ma_msgs_tags' and constraint_name = 'PRIMARY') then
        alter table tig_ma_msgs_tags
            modify column msg_owner_id bigint unsigned not null,
            modify column msg_stable_id binary(16) not null,
            add primary key (msg_owner_id, msg_stable_id, tag_id),
            add foreign key (msg_owner_id, msg_stable_id) references tig_ma_msgs (owner_id, stable_id) on delete cascade,
            drop column msg_id;
    end if;

    if exists(select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'msg_id' and extra not like '%auto_increment%') then
        if exists(select 1 from information_schema.columns where table_schema = database() and table_name = 'tig_ma_msgs' and column_name = 'offline') then
            alter table tig_ma_msgs
                drop column msg_id;
        else
            alter table tig_ma_msgs
                drop column direction,
                drop column type,
                drop column buddy_res,
                drop column msg_id;
        end if;
    end if;

    if not exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index') then
        create index tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index on tig_ma_msgs (owner_id, buddy_id, stanza_id, ts);
    end if;
    if not exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_ref_stable_id_owner_id_index') then
        create index tig_ma_msgs_ref_stable_id_owner_id_index on tig_ma_msgs (ref_stable_id, owner_id);
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id_stable_id_index') then
        drop index tig_ma_msgs_owner_id_buddy_id_stable_id_index on tig_ma_msgs;
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id_ts_index') then
        drop index tig_ma_msgs_owner_id_buddy_id_ts_index on tig_ma_msgs;
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_ts_index') then
        drop index tig_ma_msgs_owner_id_ts_index on tig_ma_msgs;
    end if;
    if not exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index') then
        create index tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index on tig_ma_msgs (owner_id, buddy_id, is_ref, ts);
    end if;
    if not exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_is_ref_ts_index') then
        create index tig_ma_msgs_owner_id_is_ref_ts_index on tig_ma_msgs (owner_id, is_ref, ts);
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
drop procedure if exists Tig_MA_AddTagToMessage;
-- QUERY END:

-- QUERY START:
drop function if exists Tig_MA_GetHasTagsQuery;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetMessagesCount;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetMessagePosition;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetCollections;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetCollectionsCount;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure Tig_MA_AddMessage(_ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _ts timestamp(6),
    _stableId varchar(36) CHARSET utf8,  _stanzaId varchar(64) CHARSET utf8, _refStableId varchar(36) CHARSET utf8,
    _body text CHARSET utf8mb4 collate utf8mb4_bin, _msg text CHARSET utf8mb4 collate utf8mb4_bin)
begin
	declare _owner_id bigint;
	declare _buddy_id bigint;

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
	select Tig_MA_EnsureJid(_ownerJid) into _owner_id;
	COMMIT;
	START TRANSACTION;
	select Tig_MA_EnsureJid(_buddyJid) into _buddy_id;
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

    START TRANSACTION;
    select Tig_MA_EnsureJid(_ownerJid) into _owner_id;
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

-- QUERY START:
create function Tig_MA_GetHasTagsQuery(_in_str text CHARSET utf8mb4 collate utf8mb4_bin) returns text CHARSET utf8mb4 collate utf8mb4_bin NO SQL
begin
    if _in_str is not null then
        return CONCAT(N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.owner_id = mt.msg_owner_id and m.stable_id = mt.msg_stable_id and t.owner_id = o.jid_id and t.tag IN (', _in_str, N'))');
    else
        return '';
    end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetMessages( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _refType tinyint, _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _limit int, _offset int)
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
		set @msgs_query = 'select m.msg, m.ts, b.jid, Tig_MA_OrderedToUuid(m.stable_id) as stable_id, Tig_MA_OrderedToUuid(m.ref_stable_id) as ref_stable_id
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            and (m.is_ref = 0 or m.is_ref = 1)
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
		set @pagination_query = ' limit ? offset ?';
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query, ' order by m.ts', @pagination_query);
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @limit, @offset;
		deallocate prepare stmt;
	else
	    case _refType
	        when 0 then
                select m.msg, m.ts, b.jid, Tig_MA_OrderedToUuid(m.stable_id) as stable_id, Tig_MA_OrderedToUuid(m.ref_stable_id) as ref_stable_id
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on b.jid_id = m.buddy_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    and m.is_ref = 0
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to)
                order by m.ts
                limit _limit offset _offset;
            when 1 then
                select m.msg, m.ts, b.jid, Tig_MA_OrderedToUuid(m.stable_id) as stable_id, Tig_MA_OrderedToUuid(m.ref_stable_id) as ref_stable_id
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on b.jid_id = m.buddy_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    and (m.is_ref = 0 or m.is_ref = 1)
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to)
                order by m.ts
                limit _limit offset _offset;
            else
                begin
                    declare _startTs timestamp(6);
                    declare _endTs timestamp(6);
                    select max(ts), min(ts) into _endTs, _startTs
                    from (
                        select m.ts as ts
                        from tig_ma_msgs m
                            inner join tig_ma_jids o on m.owner_id = o.jid_id
                            inner join tig_ma_jids b on b.jid_id = m.buddy_id
                        where
                            o.jid_sha1 = SHA1(LOWER(_ownerJid))
                            and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                            and m.is_ref = 0
                            and (_from is null or m.ts >= _from)
                            and (_to is null or m.ts <= _to)
                        order by m.ts
                        limit _limit offset _offset
                    ) x;

                    if _buddyJid is null then
                        select ref.msg, ref.ts, b.jid, Tig_MA_OrderedToUuid(ref.stable_id) as stable_id, Tig_MA_OrderedToUuid(ref.ref_stable_id) as ref_stable_id
                        from tig_ma_msgs m
                            inner join tig_ma_jids o on m.owner_id = o.jid_id
                            inner join tig_ma_jids b on m.buddy_id = b.jid_id
                            inner join tig_ma_msgs ref on ref.ref_stable_id = m.stable_id and ref.owner_id = o.jid_id and ref.buddy_id = m.buddy_id
                        where
                            o.jid_sha1 = SHA1(LOWER(_ownerJid))
                            and m.is_ref = 0
                            and m.ts >= _startTs
                            and m.ts <= _endTs
                        order by ref.ts;
                    else
                        select ref.msg, ref.ts, b.jid, Tig_MA_OrderedToUuid(ref.stable_id) as stable_id, Tig_MA_OrderedToUuid(ref.ref_stable_id) as ref_stable_id
                        from tig_ma_msgs m
                                 inner join tig_ma_jids o on m.owner_id = o.jid_id
                                 inner join tig_ma_jids b on m.buddy_id = b.jid_id
                                 inner join tig_ma_msgs ref on ref.ref_stable_id = m.stable_id and ref.owner_id = o.jid_id
                        where
                            o.jid_sha1 = SHA1(LOWER(_ownerJid))
                            and b.jid_sha1 = SHA1(LOWER(_buddyJid))
                            and m.is_ref = 0
                            and m.ts >= _startTs
                            and m.ts <= _endTs
                        order by ref.ts;
                    end if;
                end;
	    end case;
	end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetMessagesCount( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _refType tinyint, _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin)
begin
    if _tags is not null or _contains is not null then
        set @ownerJid = _ownerJid;
        set @buddyJid = _buddyJid;
        set @from = _from;
        set @to = _to;
        select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
        select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
        set @msgs_query = 'select count(1)
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            and (m.is_ref = 0 or m.is_ref = 1)
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
        set @query = CONCAT(@msgs_query, @tags_query, @contains_query);
        prepare stmt from @query;
        execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to;
        deallocate prepare stmt;
    else
        case _refType
            when 1 then
                select count(1)
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on b.jid_id = m.buddy_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    and (m.is_ref = 0 or m.is_ref = 1)
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to);
            else
                select count(1)
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on b.jid_id = m.buddy_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    and m.is_ref = 0
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to);
        end case;
    end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetMessagePosition( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _refType tinyint, _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _stableId varchar(36) CHARSET utf8)
begin
	if _tags is not null or _contains is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @stableId = _stableId;
		select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
		select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
		set @msgs_query = 'select count(1) + 1
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            and (m.is_ref = 0 or m.is_ref = 1)
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)
            and m.ts < (
                select ts
                from tig_ma_msgs m1
                    join tig_ma_jids o1 on m1.owner_id = o1.jid_id
                where
                    o1.jid_sha1 = SHA1(LOWER(?))
                    and stable_id = Tig_MA_UuidToOrdered(?)
            )';
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query);
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @ownerJid, @stableId;
		deallocate prepare stmt;
	else
        case _refType
            when 1 then
                select count(1) + 1
                from tig_ma_msgs m
                     join tig_ma_jids o on m.owner_id = o.jid_id
                    join tig_ma_jids b on m.buddy_id = b.jid_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    and (m.is_ref = 0 or m.is_ref = 1)
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to)
                    and m.ts < (
                        select ts
                        from tig_ma_msgs m1
                            join tig_ma_jids o1 on m1.owner_id = o1.jid_id
                        where
                            o1.jid_sha1 = SHA1(LOWER(_ownerJid))
                        and stable_id = Tig_MA_UuidToOrdered(_stableId)
                    );
            else
                select count(1) + 1
                from tig_ma_msgs m
                    join tig_ma_jids o on m.owner_id = o.jid_id
                    join tig_ma_jids b on m.buddy_id = b.jid_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    and m.is_ref = 0
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to)
                    and m.ts < (
                        select ts
                        from tig_ma_msgs m1
                            join tig_ma_jids o1 on m1.owner_id = o1.jid_id
                        where
                            o1.jid_sha1 = SHA1(LOWER(_ownerJid))
                            and stable_id = Tig_MA_UuidToOrdered(_stableId)
                    );
        end case;
	end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetCollections( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _limit int, _offset int)
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
        set @msgs_query = 'select min(m.ts), b.jid';
        set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            and (m.is_ref = 0 or m.is_ref = 1)
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)');
        set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid';
        set @pagination_query = ' limit ? offset ?';
        set @query = CONCAT(@msgs_query, @tags_query, @contains_query, @groupby_query, ' order by min(m.ts), b.jid', @pagination_query);
        prepare stmt from @query;
        execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @limit, @offset;
        deallocate prepare stmt;
    else
        select min(m.ts), b.jid
        from tig_ma_msgs m
            inner join tig_ma_jids o on m.owner_id = o.jid_id
            inner join tig_ma_jids b on b.jid_id = m.buddy_id
        where
            o.jid_sha1 = SHA1(LOWER(_ownerJid))
            and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
            and (m.is_ref = 0 or m.is_ref = 1)
            and (_from is null or m.ts >= _from)
            and (_to is null or m.ts <= _to)
        group by date(m.ts), m.buddy_id, b.jid
        order by min(m.ts), b.jid
        limit _limit offset _offset;
    end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetCollectionsCount( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin)
begin
    if _tags is not null or _contains is not null then
        set @ownerJid = _ownerJid;
        set @buddyJid = _buddyJid;
        set @from = _from;
        set @to = _to;
        select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
        select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
        set @msgs_query = 'select count(1) from (select min(m.ts), b.jid';
        set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            and (m.is_ref = 0 or m.is_ref = 1)
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)');
        set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid';
        set @query = CONCAT(@msgs_query, @tags_query, @contains_query, @groupby_query, ' ) x');
        prepare stmt from @query;
        execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to;
        deallocate prepare stmt;
    else
        select count(1) from (
            select min(m.ts), b.jid
            from tig_ma_msgs m
                inner join tig_ma_jids o on m.owner_id = o.jid_id
                inner join tig_ma_jids b on b.jid_id = m.buddy_id
            where
                o.jid_sha1 = SHA1(LOWER(_ownerJid))
                and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                and (m.is_ref = 0 or m.is_ref = 1)
                and (_from is null or m.ts >= _from)
                and (_to is null or m.ts <= _to)
                group by date(m.ts), m.buddy_id, b.jid
            ) x;
    end if;
end //
-- QUERY END:


delimiter ;

call TigSetComponentVersion('message-archiving', '3.0.0');
