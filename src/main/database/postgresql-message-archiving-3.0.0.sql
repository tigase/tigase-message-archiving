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
declare temprow record;
begin
if not exists (select 1 from information_schema.columns where table_catalog = current_database() and table_schema = 'public' and table_name = 'tig_ma_msgs' and column_name = 'stable_id') then
    alter table tig_ma_msgs add column stable_id uuid;

    update tig_ma_msgs set stable_id = uuid_generate_v4() where stable_id is null;

    alter table tig_ma_msgs alter column stable_id set not null;

    create unique index tig_ma_msgs_owner_id_buddy_id_stable_id_index on tig_ma_msgs (owner_id, buddy_id, stable_id );
end if;

if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index')) is not null) then
    drop index tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index;
end if;
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_buddy_res_index')) is not null) then
    drop index tig_ma_msgs_owner_id_buddy_id_buddy_res_index;
end if;
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_index')) is not null) then
    drop index tig_ma_msgs_owner_id_buddy_id_index;
end if;
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_index')) is not null) then
    drop index tig_ma_msgs_owner_id_index;
end if;
for temprow in
    select constraint_name from information_schema.table_constraints tc join information_schema.constraint_column_usage ccu using (constraint_schema, constraint_name) where tc.table_catalog = current_database() and tc.table_schema = 'public' and tc.table_name = 'tig_ma_msgs_tags' and ccu.column_name = 'msg_id'
loop
    execute 'alter table tig_ma_msgs_tags drop constraint ' || quote_ident(temprow.constraint_name) || ';';
end loop;
if exists (select 1 from information_schema.table_constraints tc join information_schema.constraint_column_usage ccu using (constraint_schema, constraint_name) where tc.table_catalog = current_database() and tc.table_schema = 'public' and tc.table_name = 'tig_ma_msgs' and tc.constraint_type = 'PRIMARY KEY' and ccu.column_name = 'msg_id') then
for temprow in
    select constraint_name from information_schema.table_constraints tc join information_schema.constraint_column_usage ccu using (constraint_schema, constraint_name) where tc.table_catalog = current_database() and tc.table_schema = 'public' and tc.table_name = 'tig_ma_msgs' and tc.constraint_type = 'PRIMARY KEY' and ccu.column_name = 'msg_id'
loop
    execute 'alter table tig_ma_msgs drop constraint ' || quote_ident(temprow.constraint_name) || ';';
end loop;
alter table tig_ma_msgs
    add column stanza_id varchar(64),
    add column ref_stable_id uuid,
    add column is_ref smallint not null default 0,
    add primary key (owner_id, stable_id);
end if;
if not exists(select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' AND table_name = 'tig_ma_msgs_tags' AND column_name = 'msg_owner_id') then
alter table tig_ma_msgs_tags
    add column msg_owner_id bigint,
    add column msg_stable_id uuid;
update tig_ma_msgs_tags
set msg_owner_id = owner_id, msg_stable_id = stable_id
from tig_ma_msgs
where tig_ma_msgs_tags.msg_id = tig_ma_msgs.msg_id;
alter table tig_ma_msgs_tags
    alter column msg_owner_id set not null,
    alter column msg_stable_id set not null,
    add primary key (msg_owner_id, msg_stable_id, tag_id),
    add foreign key (msg_owner_id, msg_stable_id) references tig_ma_msgs (owner_id, stable_id) on delete cascade,
    drop column msg_id;
end if;
if exists(select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' AND table_name = 'tig_ma_msgs' AND column_name = 'msg_id') then
    if exists(select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' AND table_name = 'tig_ma_msgs' AND column_name = 'msg_id') then
        alter table tig_ma_msgs
            drop column msg_id;
    else
        alter table tig_ma_msgs
            drop column direction,
            drop column type,
            drop column buddy_res,
            drop column stanza_hash,
            drop column msg_id;
    end if;
end if;
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_ts_index')) is not null) then
    drop index tig_ma_msgs_owner_id_buddy_id_ts_index;
end if;
if not exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index')) is not null) then
create index tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index on tig_ma_msgs (owner_id, buddy_id, is_ref, ts);
end if;
if not exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_is_ref_ts_index')) is not null) then
create index tig_ma_msgs_owner_id_is_ref_ts_index on tig_ma_msgs (owner_id, is_ref, ts);
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
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_AddMessage') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _buddyres character varying, _ts timestamp with time zone, _direction smallint, _type character varying, _body text, _msg text, _stableId character varying') then
    drop function Tig_MA_AddMessage(_ownerjid character varying, _buddyjid character varying, _buddyres character varying, _ts timestamp with time zone, _direction smallint, _type character varying, _body text, _msg text, _stableId character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_AddMessage(_ownerJid varchar(2049), _buddyJid varchar(2049), _ts timestamp with time zone,
              _stableId varchar(36), _stanzaId varchar(64), _refStableId varchar(36), _body text, _msg text) returns void as $$
declare
	_owner_id bigint;
	_buddy_id bigint;
	_msg_id bigint;
	_tsFrom timestamp with time zone;
	_tsTo timestamp with time zone;
begin
	select Tig_MA_EnsureJid(_ownerJid) into _owner_id;
	select Tig_MA_EnsureJid(_buddyJid) into _buddy_id;

    begin
        insert into tig_ma_msgs (owner_id, stable_id, buddy_id, ts, stanza_id, is_ref, ref_stable_id, body, msg)
        select _owner_id, uuid(_stableId), _buddy_id, _ts, _stanzaId, case when _refStableId is null then 0 else 1 end, uuid(_refStableId), _body, _msg
            where not exists (
                select 1 from tig_ma_msgs where owner_id = _owner_id and stable_id = uuid(_stableId)
            );
    exception when unique_violation then
    end;

end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_AddTagToMessage') and pg_get_function_arguments(oid) = '_msgid bigint, _tag character varying') then
    drop function Tig_MA_AddTagToMessage(_msgid bigint, _tag character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_AddTagToMessage(_ownerJid varchar(2049), _stableId varchar(36), _tag varchar(255)) returns void as $$
              declare
              _tag_id bigint;
_owner_id bigint;
begin
    select jid_id into _owner_id from tig_ma_jids where lower(jid) = lower(_ownerJid);
    select tag_id into _tag_id from tig_ma_tags where owner_id = _owner_id and tag = _tag;
    if _tag_id is null then
        begin
        with inserted as (
	    		insert into tig_ma_tags (owner_id, tag) select _owner_id, _tag where not exists(
				    select 1 from tig_ma_tags where owner_id = _owner_id and tag = _tag
			    ) returning tag_id
		    )
        select tag_id into _tag_id from inserted;
        exception when unique_violation then
        end;

        if _tag_id is null then
            select tag_id into _tag_id  from tig_ma_tags where owner_id = _owner_id and tag = _tag;
        end if;
    end if;
    insert into tig_ma_msgs_tags (msg_owner_id, msg_stable_id, tag_id) select _owner_id, uuid(_stableId), _tag_id where not exists (
        select 1 from tig_ma_msgs_tags where msg_owner_id = _owner_id and msg_stable_id = uuid(_stableId) and tag_id = _tag_id
    );
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetStableId(_ownerJid varchar(2049), _buddyJid varchar(2049), _stanzaId varchar(64)) returns table(
	"stable_id" varchar(36)
) as $$
begin
    return query select cast(m.stable_id as varchar(36))
        from tig_ma_msgs m
            inner join tig_ma_jids o on m.owner_id = o.jid_id
            inner join tig_ma_jids b on m.buddy_id = b.jid_id
        where
            LOWER(o.jid) = LOWER(_ownerJid)
            and (_buddyJid is null or LOWER(b.jid) = LOWER(_buddyJid))
            and m.stanza_id = _stanzaId
            order by m.ts desc;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetHasTagsQuery(_in_str text) returns text as $$
begin
    if _in_str is not null then
		return ' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.owner_id = mt.msg_owner_id and m.stable_id = mt.msg_stable_id and t.owner_id = o.jid_id and t.tag IN (' || _in_str || '))';
else
		return '';
end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetMessages') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _limit integer, _offset integer') then
    drop function Tig_MA_GetMessages(_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _limit integer, _offset integer);
end if;
end$$;
-- QUERY END:

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
            and (m.is_ref = 0 or m.is_ref = 1)
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
                        and (m.is_ref = 0 or m.is_ref = 1)
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to)
                    order by m.ts
                    limit _limit offset _offset;
            when 2 then
                return query select m.msg, m.ts, b.jid, cast(m.stable_id as varchar(36)) as stable_id, cast(m.ref_stable_id as varchar(36)) as ref_stable_id
                    from (
                        select m1.owner_id, coalesce(m1.ref_stable_id, m1.stable_id) as stable_id
                        from tig_ma_msgs m1
                            inner join tig_ma_jids o1 on m1.owner_id = o1.jid_id
                            inner join tig_ma_jids b1 on m1.buddy_id = b1.jid_id
                        where
                            lower(o1.jid) = lower(_ownerJid)
                            and (_buddyJid is null or lower(b1.jid) = lower(_buddyJid))
                            and (m1.is_ref = 0 or m1.is_ref = 1)
                            and (_from is null or m1.ts >= _from)
                            and (_to is null or m1.ts <= _to)
                        group by m1.owner_id, coalesce(m1.ref_stable_id, m1.stable_id)
                        order by min(m1.ts) asc
                        limit _limit offset _offset
                        ) x
                        inner join tig_ma_msgs m on m.owner_id = x.owner_id and m.stable_id = x.stable_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    order by m.ts asc;
            else
                return query select m.msg, m.ts, b.jid, cast(m.stable_id as varchar(36)) as stable_id, cast(m.ref_stable_id as varchar(36)) as ref_stable_id
                    from (
                        select m1.owner_id, coalesce(m1.ref_stable_id, m1.stable_id) as stable_id
                        from tig_ma_msgs m1
                            inner join tig_ma_jids o1 on m1.owner_id = o1.jid_id
                            inner join tig_ma_jids b1 on m1.buddy_id = b1.jid_id
                        where
                            lower(o1.jid) = lower(_ownerJid)
                            and (_buddyJid is null or lower(b1.jid) = lower(_buddyJid))
                            and (m1.is_ref = 0 or m1.is_ref = 1)
                            and (_from is null or m1.ts >= _from)
                            and (_to is null or m1.ts <= _to)
                        group by m1.owner_id, coalesce(m1.ref_stable_id, m1.stable_id)
                        order by min(m1.ts) asc
                        limit _limit offset _offset
                        ) x
                        inner join tig_ma_msgs m on m.ref_stable_id = x.stable_id and m.owner_id = x.owner_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    order by m.ts asc;
        end case;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetMessagesCount') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text') then
    drop function Tig_MA_GetMessagesCount(_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text);
end if;
end$$;
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
            and (m.is_ref = 0 or m.is_ref = 1)
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
        query_sql = msgs_query || tags_query || contains_query;
        return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to);
    else
        case _refType
            when 0 then
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
            when 1 then
		        return query select count(1)
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and (m.is_ref = 0 or m.is_ref = 1)
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to);
            when 2 then
                return query select count(distinct coalesce(m.ref_stable_id, m.stable_id))
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on m.buddy_id = b.jid_id
                where
                    lower(o.jid) = lower(_ownerJid)
                    and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                    and (m.is_ref = 0 or m.is_ref = 1)
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to);
            else
                return query select count(distinct coalesce(m.ref_stable_id, m.stable_id))
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on m.buddy_id = b.jid_id
                where
                    lower(o.jid) = lower(_ownerJid)
                    and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                    and m.is_ref = 1
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to);
        end case;
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
do $$
begin
    if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetMessagePosition') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _stableId character varying') then
drop function Tig_MA_GetMessagePosition(_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _stableId character varying);
end if;
end$$;
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
            and (m.is_ref = 0 or m.is_ref = 1)
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		query_sql = msgs_query || tags_query || contains_query || ' window w as (order by ts) ) x where x.stable_id = %L';
		return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to, uuid(_stableId));
	else
        case _refType
            when 0 then
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
            when 1 then
		        return query select x.position from (
                    select row_number() over (w) as position, m.stable_id
                    from tig_ma_msgs m
                        inner join tig_ma_jids o on m.owner_id = o.jid_id
                        inner join tig_ma_jids b on b.jid_id = m.buddy_id
                    where
                        lower(o.jid) = lower(_ownerJid)
                        and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                        and (m.is_ref = 0 or m.is_ref = 1)
                        and (_from is null or m.ts >= _from)
                        and (_to is null or m.ts <= _to)
                    window w as (order by ts)
                ) x where x.stable_id = uuid(_stableId);
            when 2 then
                return query select count(distinct coalesce(m.ref_stable_id, m.stable_id)) + 1
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on m.buddy_id = b.jid_id
                where
                    lower(o.jid) = lower(_ownerJid)
                    and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                    and (m.is_ref = 0 or m.is_ref = 1)
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to)
                    and m.ts < (
                        select ts
                        from tig_ma_msgs m1
                            inner join tig_ma_jids o1 on m1.owner_id = o1.jid_id
                        where
                            lower(o1.jid) = lower(_ownerJid)
                            and m1.stable_id = uuid(_stableId)
                    );
            else
                return query select count(distinct coalesce(m.ref_stable_id, m.stable_id)) + 1
                from tig_ma_msgs m
                    inner join tig_ma_jids o on m.owner_id = o.jid_id
                    inner join tig_ma_jids b on m.buddy_id = b.jid_id
                where
                    lower(o.jid) = lower(_ownerJid)
                    and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
                    and m.is_ref = 1
                    and (_from is null or m.ts >= _from)
                    and (_to is null or m.ts <= _to)
                    and m.ts < (
                        select ts
                        from tig_ma_msgs m1
                            inner join tig_ma_jids o1 on m1.owner_id = o1.jid_id
                        where
                            lower(o1.jid) = lower(_ownerJid)
                            and m1.stable_id = uuid(_stableId)
                    );
        end case;
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetCollections') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, bytype smallint, _limit integer, _offset integer' and pg_get_function_result(oid) = 'TABLE(ts timestamp with time zone, "with" character varying, type character varying)') then
    drop function Tig_MA_GetCollections(_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, bytype smallint, _limit integer, _offset integer);
end if;
end$$;
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
            and (m.is_ref = 0 or m.is_ref = 1)
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
                and (m.is_ref = 0 or m.is_ref = 1)
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
do $$
begin
    if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetCollectionsCount') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, bytype smallint') then
drop function Tig_MA_GetCollectionsCount(_ownerjid character varying, _buddyjid character varying, _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, bytype smallint);
end if;
end$$;
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
            and (m.is_ref = 0 or m.is_ref = 1)
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
                and (m.is_ref = 0 or m.is_ref = 1)
                and (_from is null or m.ts >= _from)
                and (_to is null or m.ts <= _to)
            group by date(m.ts), m.buddy_id, b.jid) x;
    end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:


-- select TigSetComponentVersion('message-archiving', '3.0.0');
