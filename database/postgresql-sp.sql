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

create or replace function Tig_MA_GetHasTagsQuery(_in_str text) returns text as $$
begin
	if _in_str is not null then
		return ' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (' || _in_str || '))';
	else
		return '';
	end if;
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_GetBodyContainsQuery(_in_str text) returns text as $$
begin
	if _in_str is not null then
		return ' and m.body like ' || replace(replace(_in_str, ''',''', ''' and m.body like '''), '%', '%%');
	else
		return '';
	end if;
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_GetMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _contains text, _limit int, _offset int) returns table(
	"msg" text, "ts" timestamp, "direction" smallint, "buddyJid" varchar(2049)
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
		msgs_query := 'select m.msg, m.ts, m.direction, b.jid 
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid = %L
			and (%L is null or b.jid = %L)
			and (%L is null or m.ts >= %L)
			and (%L is null or m.ts <= %L)';
		pagination_query := ' limit %s offset %s';
		query_sql = msgs_query || tags_query || contains_query || ' order by m.ts' || pagination_query;
		return query execute format(query_sql, _ownerJid, _buddyJid, _buddyJid, _from, _from, _to, _to, _limit, _offset);
	else
		return query select m.msg, m.ts, m.direction, b.jid
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid = _ownerJid
			and (_buddyJid is null or b.jid = _buddyJid)
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to)
		order by m.ts
		limit _limit offset _offset;
	end if;
end;
$$ LANGUAGE 'plpgsql'; 

create or replace function Tig_MA_GetMessagesCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _contains text) returns table(
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
			o.jid = %L
			and (%L is null or b.jid = %L)
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
			o.jid = _ownerJid
			and (_buddyJid is null or b.jid = _buddyJid)
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to);
	end if;
end;
$$ LANGUAGE 'plpgsql'; 

create or replace function Tig_MA_GetCollections(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _contains text, byType smallint, _limit int, _offset int) returns table(
	"ts" timestamp, "with" varchar(2049), "type" varchar(20)
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
			o.jid = %L
			and (%L is null or b.jid = %L)
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
				o.jid = _ownerJid
				and (_buddyJid is null or b.jid = _buddyJid)
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
				o.jid = _ownerJid
				and (_buddyJid is null or b.jid = _buddyJid)
				and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid
			order by min(m.ts), b.jid
			limit _limit offset _offset;
		end if;
	end if;
end;
$$ LANGUAGE 'plpgsql'; 

create or replace function Tig_MA_GetCollectionsCount(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp, _tags text, _contains text, byType smallint) returns table(
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
				o.jid = _ownerJid
				and (_buddyJid is null or b.jid = _buddyJid)
				and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end) x;
		else
			return query select count(1) from (select min(m.ts), b.jid, cast(null as varchar(20)) as "type"
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid = _ownerJid
				and (_buddyJid is null or b.jid = _buddyJid)
				and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid) x;
		end if;
	end if;
end;
$$ LANGUAGE 'plpgsql'; 


create or replace function Tig_MA_EnsureJid(_jid varchar(2049)) returns bigint as $$
declare
	_jid alias for $1;
	_jid_id bigint;
begin
	select jid_id into _jid_id from tig_ma_jids where jid = _jid;
	if _jid_id is null then
		with insered as (
			insert into tig_ma_jids (jid, "domain") select _jid, substr(_jid, strpos(_jid, '@') + 1) where not exists(
				select 1 from tig_ma_jids where jid = _jid
			) returning jid_id
		)
		select _jid_id = jid_id from inserted;
		if _jid_id is null then
			select jid_id into _jid_id from tig_ma_jids where jid = _jid;
		end if;
	end if;
	return _jid_id;
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_AddMessage(_ownerJid varchar(2049), _buddyJid varchar(2049), _buddyRes varchar(1024), _ts timestamp, 
	_direction smallint, _type varchar(20), _body text, _msg text, _hash varchar(50)) returns bigint as $$
declare
	_owner_id bigint;
	_buddy_id bigint;
	_msg_id bigint;
begin
	select Tig_MA_EnsureJid(_ownerJid) into _owner_id;
	select Tig_MA_EnsureJid(_buddyJid) into _buddy_id;

	with inserted_msg as (
		insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, "type", body, msg, stanza_hash)
		select _owner_id, _buddy_id, _buddyRes, _ts, _direction, _type, _body, _msg, _hash
		where not exists (
			select 1 from tig_ma_msgs where owner_id = _owner_id and buddy_id = _buddy_id and ts = _ts and stanza_hash = _hash 
		)
		returning msg_id
	)
	select msg_id into _msg_id from inserted_msg;
	return _msg_id;
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_AddTagToMessage(_msgId bigint, _tag varchar(255)) returns void as $$
declare
	_tag_id bigint;
	_owner_id bigint;
begin
	select owner_id into _owner_id from tig_ma_msgs where msg_id = _msgId;
	select tag_id into _tag_id from tig_ma_tags where owner_id = _owner_id and tag = _tag;
	if _tag_id is null then
		with inserted as (
			insert into tig_ma_tags (owner_id, tag) select _owner_id, _tag where not exists(
				select 1 from tig_ma_tags where owner_id = _owner_id and tag = _tag
			) returning tag_id
		)
		select tag_id into _tag_id from inserted;
		if _tag_id is null then
			select tag_id into _tag_id  from tig_ma_tags where owner_id = _owner_id and tag = _tag;
		end if;
	end if;
	insert into tig_ma_msgs_tags (msg_id, tag_id) select _msgId, _tag_id where not exists (
		select 1 from tig_ma_msgs_tags where msg_id = _msgId and tag_id = _tag_id
	);
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_RemoveMessages(_ownerJid varchar(2049), _buddyJid varchar(2049), _from timestamp, _to timestamp) returns void as $$
declare
	_owner_id bigint;
	_buddy_id bigint;
begin
	_owner_id = 0;
	_buddy_id = 0;
	select jid_id into _owner_id from tig_ma_jids where jid = _ownerJid;
	select jid_id into _buddy_id from tig_ma_jids where jid = _buddyJid;
	delete from tig_ma_msgs where owner_id = _owner_id and buddy_id = _buddy_id and ts >= _from and ts <= _to;
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_DeleteExpiredMessages(_domain varchar(1024), _before timestamp) returns void as $$
begin
	delete from tig_ma_msgs where ts < _before and exists (select 1 from tig_ma_jids j where j.jid_id = owner_id and "domain" = _domain);
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_GetTagsForUser(_ownerJid varchar(2049), _tagStartsWith varchar(255), _limit int, _offset int) returns table (
	tag varchar(255)
) as $$
begin
	return query select tag 
		from tig_ma_tags t 
		inner join tig_ma_jids o on o.jid_id = t.owner_id 
		where o.jid = _ownerJid
			and t.tag like _tagStartsWith
		order by t.tag
		limit _limit offset _offset;
end;
$$ LANGUAGE 'plpgsql';

create or replace function Tig_MA_GetTagsForUserCount(_ownerJid varchar(2049), _tagStartsWith varchar(255)) returns bigint as $$
declare
	result bigint;
begin
	result := 0;
	select count(tag_id) from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid = _ownerJid and t.tag like _tagStartsWith;
end;
$$ LANGUAGE 'plpgsql';