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
drop procedure if exists TigMAMUpgrade;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigMAMUpgrade()
begin
    if not exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_ts_index') then
        create index tig_ma_msgs_owner_id_ts_index on tig_ma_msgs (owner_id, ts);
    end if;
    if not exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id_ts_index') then
        create index tig_ma_msgs_owner_id_buddy_id_ts_index on tig_ma_msgs (owner_id, buddy_id, ts);
    end if;

    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_ts_index') then
        drop index tig_ma_msgs_ts_index on tig_ma_msgs;
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id') then
        drop index tig_ma_msgs_owner_id on tig_ma_msgs;
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_ts_buddy_id') then
        drop index tig_ma_msgs_owner_id_ts_buddy_id on tig_ma_msgs;
    end if;
    if exists (select 1 from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs' and INDEX_NAME = 'tig_ma_msgs_owner_id_buddy_id') then
        drop index tig_ma_msgs_owner_id_buddy_id on tig_ma_msgs;
    end if;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
call TigMAMUpgrade();
-- QUERY END:

-- QUERY START:
drop procedure if exists TigMAMUpgrade;
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
		from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            -- and (m.is_ref = 0 or m.is_ref = 1)
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
                from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on b.jid_id = m.buddy_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    -- and (m.is_ref = 0 or m.is_ref = 1) -- it is always 0 or 1
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
		from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            -- and (m.is_ref = 0 or m.is_ref = 1)
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
                from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on b.jid_id = m.buddy_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    -- and (m.is_ref = 0 or m.is_ref = 1) -- it is always 0 or 1
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
		from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            -- and (m.is_ref = 0 or m.is_ref = 1) -- it is always 0 or 1
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
                from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
                     join tig_ma_jids o on m.owner_id = o.jid_id
                    join tig_ma_jids b on m.buddy_id = b.jid_id
                where
                    o.jid_sha1 = SHA1(LOWER(_ownerJid))
                    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                    -- and (m.is_ref = 0 or m.is_ref = 1) -- it is always 0 or 1
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
        set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            -- and (m.is_ref = 0 or m.is_ref = 1)
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
        from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
            inner join tig_ma_jids o on m.owner_id = o.jid_id
            inner join tig_ma_jids b on b.jid_id = m.buddy_id
        where
            o.jid_sha1 = SHA1(LOWER(_ownerJid))
            and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
            -- and (m.is_ref = 0 or m.is_ref = 1) -- it is always 0 or 1
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
        set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
            -- and (m.is_ref = 0 or m.is_ref = 1) -- it is always 0 or 1
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
            from tig_ma_msgs m ignore index (buddy_id, tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index, tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index)
                inner join tig_ma_jids o on m.owner_id = o.jid_id
                inner join tig_ma_jids b on b.jid_id = m.buddy_id
            where
                o.jid_sha1 = SHA1(LOWER(_ownerJid))
                and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
                -- and (m.is_ref = 0 or m.is_ref = 1) -- it is always 0 or 1
                and (_from is null or m.ts >= _from)
                and (_to is null or m.ts <= _to)
                group by date(m.ts), m.buddy_id, b.jid
            ) x;
    end if;
end //
-- QUERY END:


delimiter ;
