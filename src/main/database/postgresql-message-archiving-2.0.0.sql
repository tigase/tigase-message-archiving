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
create table if not exists tig_ma_jids (
	jid_id bigserial,
	jid varchar(2049),
	
	primary key(jid_id)
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_jids_jid')) is null) then
	create unique index tig_ma_jids_jid on tig_ma_jids ( lower(jid) );
end if;
end$$;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select pg_get_indexdef(oid) from pg_class i  where i.relname = 'tig_ma_jids_jid') not like '%lower((jid)%') then
    drop index tig_ma_jids_jid;
    create unique index tig_ma_jids_jid on tig_ma_jids ( lower(jid) );
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_msgs (
	msg_id bigserial,
	owner_id bigint not null,
	buddy_id bigint not null,
	ts timestamp with time zone,
	direction smallint,
	"type" varchar(20),
	body text,
	msg text,
	stanza_hash varchar(50),

	primary key (msg_id),
	foreign key (buddy_id) references tig_ma_jids (jid_id),
	foreign key (owner_id) references tig_ma_jids (jid_id)
);
-- QUERY END:

-- QUERY START:
alter table tig_ma_msgs
    alter column ts type timestamp with time zone;
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_index')) is null) then
	create index tig_ma_msgs_owner_id_index on tig_ma_msgs (owner_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_index')) is null) then
	create index tig_ma_msgs_owner_id_buddy_id_index on tig_ma_msgs (owner_id, buddy_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_ts_index')) is null) then
	create index tig_ma_msgs_owner_id_buddy_id_ts_index on tig_ma_msgs (owner_id, buddy_id, ts);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index')) is not null) then
	drop index tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index;
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_stanza_hash_ts_index')) is null) then
	create unique index tig_ma_msgs_owner_id_buddy_id_stanza_hash_ts_index on tig_ma_msgs (owner_id, buddy_id, stanza_hash, ts);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_tags (
	tag_id bigserial,
	owner_id bigint not null,
	tag varchar(255),

	primary key (tag_id),
	foreign key (owner_id) references tig_ma_jids (jid_id) on delete cascade
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_owner_id')) is null) then
create index tig_ma_tags_owner_id on tig_ma_tags (owner_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_tag_owner_id')) is null) then
create unique index tig_ma_tags_tag_owner_id on tig_ma_tags (owner_id, tag);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_msgs_tags (
	msg_id bigint not null,
	tag_id bigint not null,
	
	primary key (msg_id, tag_id),
	foreign key (msg_id) references tig_ma_msgs (msg_id) on delete cascade,
	foreign key (tag_id) references tig_ma_tags (tag_id) on delete cascade
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_msg_id')) is null) then
	create index tig_ma_tags_msg_id on tig_ma_msgs_tags (msg_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_tag_id')) is null) then
	create index tig_ma_tags_tag_id on tig_ma_msgs_tags (tag_id);
end if;
end$$;
-- QUERY END:

-- additional changes introduced later - after original schema clarified

do $$ 
if not exists (select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' 
		AND table_name = 'tig_ma_msgs' and column_name = 'msg_id' and data_type = 'int') then
	alter table tig_ma_msgs alter column msg_id set data type bigint; 
end if;
end $$;

-- addition of buddy_res field which should contain resource of buddy
-- QUERY START:
do $$
begin
if not exists(select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' 
		AND table_name = 'tig_ma_msgs' AND column_name = 'buddy_res') then
	alter table tig_ma_msgs add buddy_res varchar(1024);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_buddy_res_index')) is null) then
	create index tig_ma_msgs_owner_id_buddy_id_buddy_res_index on tig_ma_msgs (owner_id, buddy_id, buddy_res);
end if;
end$$;
-- QUERY END:

-- addition of domain field to jids table for easier removal of expired messages
-- QUERY START:
do $$
begin
if not exists(select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' 
		AND table_name = 'tig_ma_jids' AND column_name = 'domain') then
	alter table tig_ma_jids add "domain" varchar(1024);
end if;
end$$;
-- QUERY END:
-- QUERY START:
update tig_ma_jids set "domain" = substr(jid, strpos(jid, '@') + 1) where "domain" is null;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_jids_domain_index')) is null) then
	create index tig_ma_jids_domain_index on tig_ma_jids ("domain");
end if;
end$$;
-- QUERY END:

-- additional index on tig_ma_msgs to improve removal of expired messages
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_ts_index')) is null) then
	create index tig_ma_msgs_ts_index on tig_ma_msgs (ts); 
end if;
end$$;
-- QUERY END:

-- added unique constraint on tig_ma_msgs_tags
-- QUERY START:
do $$
begin
if not exists(select 1 from information_schema.key_column_usage where table_catalog = current_database() AND table_schema = 'public' 
		AND table_name = 'tig_ma_msgs_tags' AND constraint_name = 'tig_ma_msgs_tags_pkey') then
	alter table tig_ma_msgs_tags add primary key (msg_id, tag_id);
end if;
end$$;
-- QUERY END:

-- ---------------------
-- Stored procedures
-- ---------------------

-- QUERY START:
create or replace function Tig_MA_GetHasTagsQuery(_in_str text) returns text as $$
begin
	if _in_str is not null then
		return ' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (' || _in_str || '))';
	else
		return '';
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetBodyContainsQuery(_in_str text) returns text as $$
begin
	if _in_str is not null then
		return ' and m.body like ' || replace(replace(_in_str, ''',''', ''' and m.body like '''), '%', '%%');
	else
		return '';
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = 'tig_ma_getmessages' and (
        pg_get_function_result(oid) = 'TABLE(msg text, ts timestamp without time zone, direction smallint, "buddyJid" character varying)'
        or pg_get_function_result(oid) = 'TABLE(msg text, ts timestamp without time zone, direction smallint, "buddyJid" character varying, "stanza_hash" character varying)')) then
    drop function Tig_MA_GetMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _contains text, _limit int, _offset int);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _limit int, _offset int) returns table(
	"msg" text, "ts" timestamp with time zone, "direction" smallint, "buddyJid" varchar(2049), "stanza_hash" varchar(50)
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
		msgs_query := 'select m.msg, m.ts, m.direction, b.jid, m.stanza_hash
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
		return query select m.msg, m.ts, m.direction, b.jid, m.stanza_hash
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
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetMessagesCount') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text') then
    drop function Tig_MA_GetMessagesCount(_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetMessagesCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text) returns table(
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
		msgs_query := 'select count(m.msg_id)
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
		return query select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			lower(o.jid) = lower(_ownerJid)
			and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to);
	end if;
end;
$$ LANGUAGE 'plpgsql'; 
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetMessagePosition') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text, _hash character varying') then
    drop function Tig_MA_GetMessagePosition(_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text, _hash character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetMessagePosition(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, _hash varchar(50)) returns table(
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
		select row_number() over (w) as position, m.stanza_hash
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			lower(o.jid) = lower(%L)
			and (%L is null or lower(b.jid) = lower(%L))
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		query_sql = msgs_query || tags_query || contains_query || ' window w as (order by ts) ) x where x.stanza_hash = %L';
		return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to, _hash);
	else
		return query select x.position from (
		select row_number() over (w) as position, m.stanza_hash
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			lower(o.jid) = lower(_ownerJid)
			and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to)
		window w as (order by ts)
        ) x where x.stanza_hash = _hash;
	end if;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetCollections') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text, bytype smallint, _limit integer, _offset integer') then
    drop function Tig_MA_GetCollections(_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text, bytype smallint, _limit integer, _offset integer);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetCollections(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, byType smallint, _limit int, _offset int) returns table(
	"ts" timestamp with time zone, "with" varchar(2049), "type" varchar(20)
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
		if byType = 1 then
			msgs_query := msgs_query || ', case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end as "type"';
		else
			msgs_query := msgs_query || ', cast(null as varchar(20)) as "type"';
		end if;
		msgs_query := msgs_query ||
		' from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			lower(o.jid) = lower(%L)
			and (%L is null or lower(b.jid) = lower(%L))
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		if byType = 1 then
			groupby_query := ' group by date(m.ts), m.buddy_id, b.jid, case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end';
		else
			groupby_query := ' group by date(m.ts), m.buddy_id, b.jid';
		end if;
		pagination_query := ' limit %s offset %s';
		query_sql := msgs_query || tags_query || contains_query || groupby_query || ' order by min(m.ts), b.jid' || pagination_query;
		return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to, _limit, _offset);
	else
		if byType = 1 then
			return query select min(m.ts), b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end as "type"
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				lower(o.jid) = lower(_ownerJid)
				and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
				and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end
			order by min(m.ts), b.jid
			limit _limit offset _offset;
		else
			return query select min(m.ts), b.jid, cast(null as varchar(20)) as "type"
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
	end if;
end;
$$ LANGUAGE 'plpgsql'; 
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_GetCollectionsCount') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text, bytype smallint') then
    drop function Tig_MA_GetCollectionsCount(_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone, _tags text, _contains text, bytype smallint);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetCollectionsCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone, _tags text, _contains text, byType smallint) returns table(
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
		if byType = 1 then
			msgs_query := msgs_query || ', case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end as "type"';
		else
			msgs_query := msgs_query || ', cast(null as varchar(20)) as "type"';
		end if;
		msgs_query := msgs_query ||
		' from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid = %L
			and (%L is null or b.jid = %L)
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		if byType = 1 then
			groupby_query := ' group by date(m.ts), m.buddy_id, b.jid, case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end';
		else
			groupby_query := ' group by date(m.ts), m.buddy_id, b.jid';
		end if;
		query_sql := msgs_query || tags_query || contains_query || groupby_query || ') x';
		return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to);
	else
		if byType = 1 then
			return query select count(1) from (select min(m.ts), b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end as "type"
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				lower(o.jid) = lower(_ownerJid)
				and (_buddyJid is null or lower(b.jid) = lower(_buddyJid))
				and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end) x;
		else
			return query select count(1) from (select min(m.ts), b.jid, cast(null as varchar(20)) as "type"
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
	end if;
end;
$$ LANGUAGE 'plpgsql'; 
-- QUERY END:


-- QUERY START:
create or replace function Tig_MA_EnsureJid(_jid varchar(2049)) returns bigint as $$
declare
	_jid alias for $1;
	_jid_id bigint;
begin
	select jid_id into _jid_id from tig_ma_jids where lower(jid) = lower(_jid);
	if _jid_id is null then
	    begin
		    with inserted as (
			    insert into tig_ma_jids (jid, "domain") select _jid, substr(_jid, strpos(_jid, '@') + 1) where not exists(
				    select 1 from tig_ma_jids where lower(jid) = lower(_jid)
			    ) returning jid_id
		    )
		    select jid_id into _jid_id from inserted;
		exception when unique_violation then
		end;
		if _jid_id is null then
			select jid_id into _jid_id from tig_ma_jids where lower(jid) = lower(_jid);
		end if;
	end if;
	return _jid_id;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_AddMessage') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _buddyres character varying, _ts timestamp without time zone, _direction smallint, _type character varying, _body text, _msg text, _hash character varying') then
    drop function Tig_MA_AddMessage(_ownerjid character varying, _buddyjid character varying, _buddyres character varying, _ts timestamp without time zone, _direction smallint, _type character varying, _body text, _msg text, _hash character varying);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_AddMessage(_ownerJid varchar(2049), _buddyJid varchar(2049), _buddyRes varchar(1024), _ts timestamp with time zone,
	_direction smallint, _type varchar(20), _body text, _msg text, _hash varchar(50)) returns bigint as $$
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
		    insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, "type", body, msg, stanza_hash)
		    select _owner_id, _buddy_id, _buddyRes, _ts, _direction, _type, _body, _msg, _hash
		    where not exists (
			    select 1
			    from tig_ma_msgs
			    where owner_id = _owner_id
			        and buddy_id = _buddy_id
			        and stanza_hash = _hash
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
create or replace function Tig_MA_AddTagToMessage(_msgId bigint, _tag varchar(255)) returns void as $$
declare
	_tag_id bigint;
	_owner_id bigint;
begin
	select owner_id into _owner_id from tig_ma_msgs where msg_id = _msgId;
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
	insert into tig_ma_msgs_tags (msg_id, tag_id) select _msgId, _tag_id where not exists (
		select 1 from tig_ma_msgs_tags where msg_id = _msgId and tag_id = _tag_id
	);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_RemoveMessages') and pg_get_function_arguments(oid) = '_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone') then
    drop function Tig_MA_RemoveMessages(_ownerjid character varying, _buddyjid character varying, _from timestamp without time zone, _to timestamp without time zone);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_RemoveMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp with time zone, _to timestamp with time zone) returns void as $$
declare
	_owner_id bigint;
	_buddy_id bigint;
begin
	_owner_id = 0;
	_buddy_id = 0;
	select jid_id into _owner_id from tig_ma_jids where lower(jid) = lower(_ownerJid);
	if _buddyJid is not null then
	    select jid_id into _buddy_id from tig_ma_jids where lower(jid) = lower(_buddyJid);
	end if;

	with deleted as (
	    delete from tig_ma_msgs
	        where owner_id = _owner_id
	        and (_buddyJid is null or buddy_id = _buddy_id)
	        and (_from is null or ts >= _from) and (_to is null or ts <= _to)
	    returning buddy_id
	)
	delete from tig_ma_jids
	    where
	        jid_id in (select buddy_id from deleted group by buddy_id)
	        and not exists (
	            select 1 from tig_ma_msgs m where m.owner_id = jid_id
	        )
	        and not exists (
	            select 1 from tig_ma_msgs m where m.buddy_id = jid_id
	        );

	delete from tig_ma_jids
	    where
	        jid_id = _owner_id
	        and not exists (
	            select 1 from tig_ma_msgs m where m.owner_id = jid_id
	        )
	        and not exists (
	            select 1 from tig_ma_msgs m where m.buddy_id = jid_id
	        );
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
do $$
begin
if exists( select 1 from pg_proc where proname = lower('Tig_MA_DeleteExpiredMessages') and pg_get_function_arguments(oid) = '_domain character varying, _before timestamp without time zone') then
    drop function Tig_MA_DeleteExpiredMessages(_domain character varying, _before timestamp without time zone);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_DeleteExpiredMessages(_domain varchar(1024), _before timestamp with time zone) returns void as $$
begin
	delete from tig_ma_msgs where ts < _before and exists (select 1 from tig_ma_jids j where j.jid_id = owner_id and "domain" = _domain);
	delete from tig_ma_jids
	    where
	        not exists (
	            select 1 from tig_ma_msgs m where m.owner_id = jid_id
	        )
	        and not exists (
	            select 1 from tig_ma_msgs m where m.buddy_id = jid_id
	        );
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetTagsForUser(_ownerJid varchar(2049), _tagStartsWith varchar(255), _limit int, _offset int) returns table (
	tag varchar(255)
) as $$
begin
	return query select t.tag
		from tig_ma_tags t 
		inner join tig_ma_jids o on o.jid_id = t.owner_id 
		where lower(o.jid) = lower(_ownerJid)
			and t.tag like _tagStartsWith
		order by t.tag
		limit _limit offset _offset;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
create or replace function Tig_MA_GetTagsForUserCount(_ownerJid varchar(2049), _tagStartsWith varchar(255)) returns bigint as $$
declare
	result bigint;
begin
	result := 0;
	select count(tag_id) into result from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where lower(o.jid) = lower(_ownerJid) and t.tag like _tagStartsWith;
	return result;
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

-- QUERY START:
select TigSetComponentVersion('message-archiving', '2.0.0');
-- QUERY END:
