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
create or replace function Tig_MA_GetMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _refType smallint, _tags text, _contains text, _limit int, _offset int) returns table(
                                                                                                                                                                                                                                    "msg" text, "ts" timestamp with time zone, "buddyJid" varchar(2049), "stableId" varchar(36), "refStableId" varchar(36)
                                                                                                                                                                                                                                ) as $$
declare
    tags_query text;
    contains_query text;
    msgs_query text;
    pagination_query text;
    query_sql text;
    startTs timestamp with time zone;
    endTs timestamp with time zone;
begin
    if _tags is not null or _contains is not null then
        select Tig_MA_GetHasTagsQuery(_tags) into tags_query;
        select Tig_MA_GetBodyContainsQuery(_contains) into contains_query;
        msgs_query := 'select m.msg, m.ts, b.jid, cast(m.stable_id as varchar(36)) as stable_id, cast(m.ref_stable_id as varchar(36)) as ref_stable_id
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			lower(o.jid) = lower(%L)
			and (%L is null or lower(b.jid) = lower(%L))
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
        pagination_query := ' limit %s offset %s';
        query_sql = msgs_query || tags_query || contains_query || ' order by m.ts' || pagination_query;
        return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to, _limit, _offset);
    else
        case _refType
            when 0 then
                return query select m.msg, m.ts,b.jid, cast(m.stable_id as varchar(36)) as stable_id, cast(m.ref_stable_id as varchar(36)) as ref_stable_id
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and m.is_ref = 0
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to)
                    order by m.ts
                    limit _limit offset _offset;
            when 1 then
                return query select m.msg, m.ts, b.jid, cast(m.stable_id as varchar(36)) as stable_id, cast(m.ref_stable_id as varchar(36)) as ref_stable_id
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to)
                    order by m.ts
                    limit _limit offset _offset;
            else
                select into endTs, startTs max(x.ts), min(x.ts)
                from (
                    select m.ts as ts
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and m.is_ref = 0
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to)
                    order by m.ts
                    limit _limit offset _offset
                ) x;

                return query select ref.msg, ref.ts, b.jid, cast(ref.stable_id as varchar(36)) as stable_id, cast(ref.ref_stable_id as varchar(36)) as ref_stable_id
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on m.buddy_id = b.jid_id
                        inner join tig_ma_msgs ref on ref.ref_stable_id = m.stable_id and ref.owner_id = o.jid_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and m.is_ref = 0
                        and m.ts >= startTs
                        and m.ts <= endTs
                    order by ref.ts;
        end case;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetMessagesCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _refType smallint, _tags text, _contains text) returns table(
    "count" bigint
) as $$
declare
    tags_query text;
    contains_query text;
    msgs_query text;
    query_sql text;
begin
    if _tags is not null or _contains is not null then
        select Tig_MA_GetHasTagsQuery(_tags) into tags_query;
        select Tig_MA_GetBodyContainsQuery(_contains) into contains_query;
        msgs_query := 'select count(1)
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			lower(o.jid) = lower(%L)
			and (%L is null or lower(b.jid) = lower(%L))
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
        query_sql = msgs_query || tags_query || contains_query;
        return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to);
    else
        case _refType
            when 1 then
		        return query select count(1)
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to);
            else
		        return query select count(1)
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and m.is_ref = 0
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to);
        end case;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetMessagePosition(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _refType smallint, _tags text, _contains text, _stableId varchar(36)) returns table(
	"position" bigint
) as $$
declare
	tags_query text;
	contains_query text;
	msgs_query text;
	query_sql text;
begin
	if _tags is not null or _contains is not null then
		select Tig_MA_GetHasTagsQuery(_tags) into tags_query;
		select Tig_MA_GetBodyContainsQuery(_contains) into contains_query;
		msgs_query := 'select x.position from (
		select row_number() over (w) as position, m.stable_id
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			lower(o.jid) = lower(%L)
			and (%L is null or lower(b.jid) = lower(%L))
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		query_sql = msgs_query || tags_query || contains_query || ' window w as (order by ts) ) x where x.stable_id = %L';
		return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to, uuid(_stableId));
	else
        case _refType
            when 1 then
		        return query select x.position from (
                    select row_number() over (w) as position, m.stable_id
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to)
                    window w as (order by ts)
                ) x where x.stable_id = uuid(_stableId);
            else
		        return query select x.position from (
		            select row_number() over (w) as position, m.stable_id
		            from tig_ma_msgs m
			            inner join tig_ma_jids o on m.owner_id = o.jid_id
			            inner join tig_ma_jids b on b.jid_id = m.buddy_id
		            where
			            lower(o.jid) = lower(_ownerJid)
			            and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
		                and m.is_ref = 0
			            and (_from is null or m.ts >= _from)
			            and (_to is null or m.ts <= _to)
		            window w as (order by ts)
                ) x where x.stable_id = uuid(_stableId);
        end case;
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetCollections(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _limit int, _offset int) returns table(
    "ts" timestamp with time zone, "with" varchar(2049)
) as $$
declare
    tags_query text;
    contains_query text;
    msgs_query text;
    pagination_query text;
    groupby_query text;
    query_sql text;
begin
    if _tags is not null or _contains is not null then
        select Tig_MA_GetHasTagsQuery(_tags) into tags_query;
        select Tig_MA_GetBodyContainsQuery(_contains) into contains_query;
        msgs_query := 'select min(m.ts), b.jid';
        msgs_query := msgs_query ||
		' from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			lower(o.jid) = lower(%L)
			and (%L is null or lower(b.jid) = lower(%L))
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		groupby_query := ' group by date(m.ts), m.buddy_id, b.jid';
        pagination_query := ' limit %s offset %s';
        query_sql := msgs_query || tags_query || contains_query || groupby_query || ' order by min(m.ts), b.jid' || pagination_query;
        return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to, _limit, _offset);
    else
		return query select min(m.ts), b.jid
            from tig_ma_msgs m
                inner join tig_ma_jids o on m.owner_id = o.jid_id
                inner join tig_ma_jids b on b.jid_id = m.buddy_id
            where
                lower(o.jid) = lower(_ownerJid)
                and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                and (_from is null or m.ts >= _from)
                and (_to is null or m.ts <= _to)
            group by date(m.ts), m.buddy_id, b.jid
            order by min(m.ts), b.jid
            limit _limit offset _offset;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetCollectionsCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text) returns table(
    "count" bigint
) as $$
declare
    tags_query text;
    contains_query text;
    msgs_query text;
    groupby_query text;
    query_sql text;
begin
    if _tags is not null or _contains is not null then
        select Tig_MA_GetHasTagsQuery(_tags) into tags_query;
        select Tig_MA_GetBodyContainsQuery(_contains) into contains_query;
        msgs_query := 'select count(1) from (select min(m.ts), b.jid';
        msgs_query := msgs_query ||
		' from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			lower(o.jid) = lower(%L)
			and (%L is null or lower(b.jid) = lower(%L))
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		groupby_query := ' group by date(m.ts), m.buddy_id, b.jid';
        query_sql := msgs_query || tags_query || contains_query || groupby_query || ') x';
        return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to);
    else
		return query select count(1) from (select min(m.ts), b.jid
            from tig_ma_msgs m
                inner join tig_ma_jids o on m.owner_id = o.jid_id
                inner join tig_ma_jids b on b.jid_id = m.buddy_id
            where
                lower(o.jid) = lower(_ownerJid)
                and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                and (_from is null or m.ts >= _from)
                and (_to is null or m.ts <= _to)
            group by date(m.ts), m.buddy_id, b.jid) x;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:
