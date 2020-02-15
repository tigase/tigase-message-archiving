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
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- QUERY END:

-- QUERY START:
do $$
begin
if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_ma_msgs' and column_name = 'stable_id') then
    alter table tig_ma_msgs add column stable_id uuid;

    update tig_ma_msgs set stable_id = uuid_generate_v4() where stable_id is null;

    alter table tig_ma_msgs alter column stable_id set not null;

    create unique index tig_ma_msgs_owner_id_buddy_id_stable_id_index on tig_ma_msgs (owner_id, buddy_id, stable_id );
end if;
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_stanza_hash_ts_index')) is not null) then
    drop index tig_ma_msgs_owner_id_buddy_id_stanza_hash_ts_index;
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_AddMessage') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _buddyres character varying, _ts timestamp with time zone, _direction smallint, _type character varying, _body text, _msg text, _hash character varying') then
    drop function Tig_MA_AddMessage(_ownerjid character varying, _buddyjid character varying, _buddyres character varying, _ts timestamp with time zone, _direction smallint, _type character varying, _body text, _msg text, _hash character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_AddMessage(_ownerJid varchar(2049), _buddyJid varchar(2049), _buddyRes varchar(1024), _ts timestamp with time zone,
	_direction smallint, _type varchar(20), _body text, _msg text, _stableId varchar(36)) returns bigint as $$
declare
	_owner_id bigint;
	_buddy_id bigint;
	_msg_id bigint;
	_tsFrom timestamp with time zone;
	_tsTo timestamp with time zone;
begin
    if _type = 'groupchat' then
        select _ts - interval '30 minutes', _ts + interval '30 minutes' into _tsFrom, _tsTo;
    else
        select _ts, _ts into _tsFrom, _tsTo;
    end if;

	select Tig_MA_EnsureJid(_ownerJid) into _owner_id;
	select Tig_MA_EnsureJid(_buddyJid) into _buddy_id;

    begin
	    with inserted_msg as (
		    insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, "type", body, msg, stable_id)
		    select _owner_id, _buddy_id, _buddyRes, _ts, _direction, _type, _body, _msg, uuid(_stableId)
		    where not exists (
			    select 1
			    from tig_ma_msgs
			    where owner_id = _owner_id
			        and buddy_id = _buddy_id
			        and stable_id = uuid(_stableId)
			        and ts between _tsFrom  and _tsTo
		    )
		    returning msg_id
	    )
	    select msg_id into _msg_id from inserted_msg;
	exception when unique_violation then
	end;

	return _msg_id;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetMessages') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _limit integer, _offset integer' and pg_get_function_result(oid) = 'TABLE(msg text, ts timestamp with time zone, direction smallint, "buddyJid" character varying, stanza_hash character varying)') then
    drop function Tig_MA_GetMessages(_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _limit integer, _offset integer);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _limit int, _offset int) returns table(
                                                                                                                                                                                                                                    "msg" text, "ts" timestamp with time zone, "direction" smallint, "buddyJid" varchar(2049), "stableId" varchar(36)
                                                                                                                                                                                                                                ) as $$
declare
    tags_query text;
    contains_query text;
    msgs_query text;
    pagination_query text;
    query_sql text;
begin
    if _tags is not null or _contains is not null then
        select Tig_MA_GetHasTagsQuery(_tags) into tags_query;
        select Tig_MA_GetBodyContainsQuery(_contains) into contains_query;
        msgs_query := 'select m.msg, m.ts, m.direction, b.jid, cast(m.stable_id as varchar(36)) as stable_id
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
        return query select m.msg, m.ts, m.direction, b.jid, cast(m.stable_id as varchar(36)) as stable_id
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
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetMessagePosition') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _hash character varying') then
    drop function Tig_MA_GetMessagePosition(_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _hash character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetMessagePosition(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _stableId varchar(36)) returns table(
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
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:


select TigSetComponentVersion('message-archiving', '3.0.0');
