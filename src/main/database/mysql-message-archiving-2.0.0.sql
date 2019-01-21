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
	jid_id  bigint unsigned NOT NULL auto_increment,
	jid varchar(2049),
	primary key (jid_id)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_msgs (
	msg_id bigint unsigned NOT NULL auto_increment,
	owner_id bigint unsigned NOT NULL,
	buddy_id bigint unsigned NOT NULL,
	ts timestamp(6) null default null,
	`direction` smallint,
	`type` varchar(20),
	body text character set utf8mb4 collate utf8mb4_bin,
	msg text character set utf8mb4 collate utf8mb4_bin,
	stanza_hash varchar(50),

	primary key (msg_id),
	foreign key (buddy_id) references tig_ma_jids (jid_id),
	foreign key (owner_id) references tig_ma_jids (jid_id),
	key tig_ma_msgs_owner_id (owner_id),
	key tig_ma_msgs_owner_id_buddy_id (owner_id, buddy_id),
	key tig_ma_msgs_owner_id_ts_buddy_id (owner_id, ts, buddy_id),
	unique index tig_ma_msgs_owner_id_buddy_id_stanza_hash_index using hash (owner_id, buddy_id, stanza_hash)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_tags (
	tag_id bigint unsigned NOT NULL auto_increment,
	owner_id bigint unsigned not null,
	tag varchar(255) character set utf8mb4 collate utf8mb4_bin,

	primary key (tag_id),
	foreign key (owner_id) references tig_ma_jids (jid_id) on delete cascade,
	key tig_ma_tags_owner_id (owner_id),
	unique key tig_ma_tags_tag_owner_id (owner_id, tag(191))
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_msgs_tags (
	msg_id bigint unsigned NOT NULL,
	tag_id bigint unsigned NOT NULL,
	
	primary key (msg_id, tag_id),
	foreign key (msg_id) references tig_ma_msgs (msg_id) on delete cascade,
	foreign key (tag_id) references tig_ma_tags (tag_id) on delete cascade,
	key tig_ma_tags_msg_id (msg_id),
	key tig_ma_tags_tag_id (tag_id)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- additional changes introduced later - after original schema clarified
-- QUERY START:
drop procedure if exists TigExecuteIfNot;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigAddColumnIfNotExists;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigAddIndexIfNotExists;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure TigExecuteIfNot(cond int, query text)
begin
set @s = (select if (
	cond > 0,
'select 1',
query
));

prepare stmt from @s;
execute stmt;
deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigAddColumnIfNotExists(tab varchar(255), col varchar(255), def varchar(255))
begin
call TigExecuteIfNot((select count(1) from information_schema.COLUMNS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tab AND COLUMN_NAME = col), 
	CONCAT('alter table ', tab, ' add `', col, '` ', def)
);
end //
-- QUERY END:

-- QUERY START:
create procedure TigAddIndexIfNotExists(tab varchar(255), ix_name varchar(255), uni smallint, def varchar(255))
begin
call TigExecuteIfNot((select count(1) from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tab and INDEX_NAME = ix_name), 
	CONCAT('create ', IF(uni=1, 'unique', ''), ' index ', ix_name, ' on ', tab, ' ', def)
);
end //
-- QUERY END:

delimiter ;

-- addition of buddy_res field which should contain resource of buddy
-- QUERY START:
call TigAddColumnIfNotExists('tig_ma_msgs',  'buddy_res', 'varchar(1024)');
-- QUERY END:
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_msgs', 'tig_ma_msgs_owner_id_buddy_id_buddy_res_index', 0, '(owner_id, buddy_id, buddy_res(255))');
-- QUERY END:

-- addition of domain field to jids table for easier removal of expired messages
-- QUERY START:
call TigAddColumnIfNotExists('tig_ma_jids', 'domain', 'varchar(1024)');
-- QUERY END:
-- QUERY START:
update tig_ma_jids set `domain` = SUBSTR(jid, LOCATE('@', jid) + 1) where `domain` is null;
-- QUERY END:
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_jids', 'tig_ma_jids_domain_index', 0, '(`domain`(255))');
-- QUERY END:

-- additional index on tig_ma_msgs to improve removal of expired messages
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_msgs', 'tig_ma_msgs_ts_index', 0, '(ts)'); 
-- QUERY END:

-- additional performace optimizations
-- QUERY START:
call TigAddColumnIfNotExists('tig_ma_jids', 'jid_sha1', 'char(40)');
-- QUERY END:
-- QUERY START:
update tig_ma_jids set jid_sha1 = SHA1(LOWER(jid));
-- QUERY END:
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_jids', 'tig_ma_jids_jid_sha1', 1, '(jid_sha1)');
-- QUERY END:

-- added unique constraint on tig_ma_msgs_tags
-- QUERY START:
call TigExecuteIfNot((select count(1) from information_schema.KEY_COLUMN_USAGE where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs_tags' AND CONSTRAINT_NAME = 'PRIMARY'), 
'alter table tig_ma_msgs_tags add primary key (msg_id, tag_id)');
-- QUERY END:

-- fixing collate of tables
-- QUERY START:
alter table tig_ma_jids collate utf8_general_ci;
-- QUERY END:
-- QUERY START:
alter table tig_ma_tags collate utf8_general_ci;
-- QUERY END:
-- QUERY START:
alter table tig_ma_msgs collate utf8_general_ci;
-- QUERY END:

-- QUERY START:
call TigExecuteIfNot(
    (select 1-count(1) from information_schema.statistics where table_schema = database() and table_name = 'tig_ma_tags' and index_name = 'tig_ma_tags_tag_owner_id' and column_name = 'tag' and sub_part is null),
    "drop index tig_ma_tags_tag_owner_id on tig_ma_tags"
);
-- QUERY END:
-- QUERY START:
call TigExecuteIfNot(
    (select count(1) from information_schema.statistics where table_schema = database() and table_name = 'tig_ma_tags' and index_name = 'tig_ma_tags_tag_owner_id'),
    "create index tig_ma_tags_tag_owner_id on tig_ma_tags (owner_id, tag(191))"
);
-- QUERY END:

-- QUERY START:
alter table tig_ma_msgs
    modify body text character set utf8mb4 collate utf8mb4_bin,
    modify msg text character set utf8mb4 collate utf8mb4_bin;
-- QUERY END:

-- QUERY START:
alter table tig_ma_tags
    modify tag varchar(255) character set utf8mb4 collate utf8mb4_bin;
-- QUERY END:

-- ---------------------
-- Stored procedures
-- ---------------------

-- QUERY START:
drop procedure if exists Tig_MA_GetHasTagsQuery;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetBodyContainsQuery;
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

-- QUERY START:
drop function if exists Tig_MA_EnsureJid;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_AddMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_AddTagToMessage;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_RemoveMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_DeleteExpiredMessages;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetTagsForUser;
-- QUERY END:

-- QUERY START:
drop procedure if exists Tig_MA_GetTagsForUserCount;
-- QUERY END:

-- QUERY START:
drop function if exists Tig_MA_GetHasTagsQuery;
-- QUERY END:

-- QUERY START:
drop function if exists Tig_MA_GetBodyContainsQuery;
-- QUERY END:

delimiter //

-- QUERY START:
create function Tig_MA_GetHasTagsQuery(_in_str text CHARSET utf8mb4 collate utf8mb4_bin) returns text CHARSET utf8mb4 collate utf8mb4_bin NO SQL
begin
	if _in_str is not null then
		return CONCAT(N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (', _in_str, N'))');
	else
		return '';
	end if;
end //
-- QUERY END:

-- QUERY START:
create function Tig_MA_GetBodyContainsQuery(_in_str text CHARSET utf8mb4 collate utf8mb4_bin) returns text CHARSET utf8mb4 collate utf8mb4_bin NO SQL
begin
	if _in_str is not null then
		return CONCAT(N' and m.body like ', replace(_in_str, N''',''', N''' and m.body like = '''));
	else
		return '';
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
		set @msgs_query = 'select m.msg, m.ts, m.direction, b.jid, m.stanza_hash
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
		select m.msg, m.ts, m.direction, b.jid, m.stanza_hash
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
create procedure Tig_MA_GetMessagesCount( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin)
begin
	if _tags is not null or _contains is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
		select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
		set @msgs_query = 'select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query);
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to;
		deallocate prepare stmt;
	else
		select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(LOWER(_ownerJid))
			and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to);
	end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetMessagePosition( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _hash varchar(50) CHARSET utf8)
begin
	if _tags is not null or _contains is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @stanza_hash = _hash;
		select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
		select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
		set @msgs_query = 'select x.position from (
		select @row_number := @row_number + 1 AS position, m.stanza_hash
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id,
			(select @row_number := 0) as t
		where
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query, ' order by m.ts) x where x.stanza_hash = ?');
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @stanza_hash;
		deallocate prepare stmt;
	else
	    set @row_number = 0;
	    select x.position from (
		    select @row_number := @row_number + 1 AS position, m.stanza_hash
		    from tig_ma_msgs m
			    inner join tig_ma_jids o on m.owner_id = o.jid_id
			    inner join tig_ma_jids b on b.jid_id = m.buddy_id
		    where
			    o.jid_sha1 = SHA1(LOWER(_ownerJid))
			    and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
			    and (_from is null or m.ts >= _from)
			    and (_to is null or m.ts <= _to)
			order by m.ts
		) x where x.stanza_hash = _hash;
	end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetCollections( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _byType smallint, _limit int, _offset int)
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
		if _byType = 1 then
			set @msgs_query = CONCAT( @msgs_query, ', case when m.type = ''groupchat'' then ''groupchat'' else '''' end as `type`');
		else
			set @msgs_query = CONCAT( @msgs_query, ', null as `type`');
		end if;
		set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)');
		set @groupby_query = '';
		if _byType = 1 then
			select ' group by date(m.ts), m.buddy_id, b.jid, case when m.type = ''groupchat'' then ''groupchat'' else '''' end' into @groupby_query;
		else
			select ' group by date(m.ts), m.buddy_id, b.jid' into @groupby_query;
		end if;
		set @pagination_query = ' limit ? offset ?';
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query, @groupby_query, ' order by min(m.ts), b.jid', @pagination_query);
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @limit, @offset;
		deallocate prepare stmt;
	else
		if _byType = 1 then
			select min(m.ts), b.jid, case when m.type = 'groupchat' then 'groupchat' else '' end as `type`
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = SHA1(LOWER(_ownerJid))
				and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
				and (_from is null or m.ts >= _from)
				and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then 'groupchat' else '' end 
			order by min(m.ts), b.jid
			limit _limit offset _offset;
		else
			select min(m.ts), b.jid, null as `type`
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = SHA1(LOWER(_ownerJid))
				and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
				and (_from is null or m.ts >= _from)
				and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid
			order by min(m.ts), b.jid
			limit _limit offset _offset;
		end if;
	end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetCollectionsCount( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6), _tags text CHARSET utf8mb4 collate utf8mb4_bin, _contains text CHARSET utf8mb4 collate utf8mb4_bin, _byType smallint)
begin
	if _tags is not null or _contains is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		select Tig_MA_GetHasTagsQuery(_tags) into @tags_query;
		select Tig_MA_GetBodyContainsQuery(_contains) into @contains_query;
		set @msgs_query = 'select count(1) from (select min(m.ts), b.jid';
		if _byType = 1 then
			set @msgs_query = CONCAT( @msgs_query, ', case when m.type = ''groupchat'' then ''groupchat'' else '''' end as `type`');
		end if;
		set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(LOWER(?))
			and (? is null or b.jid_sha1 = SHA1(LOWER(?)))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)');
		if _byType = 1 then
			set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid, case when m.type = ''groupchat'' then ''groupchat'' else '''' end';
		else
			set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid';
		end if;
		set @query = CONCAT(@msgs_query, @tags_query, @contains_query, @groupby_query, ' ) x');
		prepare stmt from @query;
		execute stmt using @ownerJid, @buddyJid, @buddyJid, @from, @from, @to, @to;
		deallocate prepare stmt;
	else
		if _byType = 1 then
			select count(1) from (
				select min(m.ts), b.jid, case when m.type = 'groupchat' then 'groupchat' else '' end as `type`
				from tig_ma_msgs m 
					inner join tig_ma_jids o on m.owner_id = o.jid_id 
					inner join tig_ma_jids b on b.jid_id = m.buddy_id
				where 
					o.jid_sha1 = SHA1(LOWER(_ownerJid))
					and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
					and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
				group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then 'groupchat' else '' end 
			) x;
		else
			select count(1) from (
				select min(m.ts), b.jid
				from tig_ma_msgs m 
					inner join tig_ma_jids o on m.owner_id = o.jid_id 
					inner join tig_ma_jids b on b.jid_id = m.buddy_id
				where 
					o.jid_sha1 = SHA1(LOWER(_ownerJid))
					and (_buddyJid is null or b.jid_sha1 = SHA1(LOWER(_buddyJid)))
					and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
				group by date(m.ts), m.buddy_id, b.jid
			) x;
		end if;
	end if;
end //
-- QUERY END:

-- QUERY START:
create function Tig_MA_EnsureJid(_jid varchar(2049) CHARSET utf8) returns bigint DETERMINISTIC
begin
	declare _jid_id bigint;

	select jid_id into _jid_id from tig_ma_jids where jid_sha1 = SHA1(LOWER(_jid));
	if _jid_id is null then
		insert into tig_ma_jids (jid, jid_sha1, `domain`)
			values (_jid, SHA1(LOWER(_jid)), SUBSTR(jid, LOCATE('@', _jid) + 1))
			on duplicate key update jid_id = LAST_INSERT_ID(jid_id);
		select LAST_INSERT_ID() into _jid_id;
	end if;

	return (_jid_id);
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_AddMessage(_ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8,
	 _buddyRes varchar(1024)  CHARSET utf8, _ts timestamp(6), _direction smallint, _type varchar(20) CHARSET utf8,
	 _body text CHARSET utf8mb4 collate utf8mb4_bin, _msg text CHARSET utf8mb4 collate utf8mb4_bin, _hash varchar(50) CHARSET utf8)
begin
	declare _owner_id bigint;
	declare _buddy_id bigint;
	declare _msg_id bigint;
	declare x bigint;

	START TRANSACTION;
	select Tig_MA_EnsureJid(_ownerJid) into _owner_id;
	select Tig_MA_EnsureJid(_buddyJid) into _buddy_id;

    set x = LAST_INSERT_ID();
	insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, `type`, body, msg, stanza_hash)
		values (_owner_id, _buddy_id, _buddyRes, _ts, _direction, _type, _body, _msg, _hash)
		on duplicate key update direction = direction;

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
create procedure Tig_MA_AddTagToMessage(_msgId bigint, _tag varchar(255) CHARSET utf8mb4 collate utf8mb4_bin)
begin
	declare _owner_id bigint;
	declare _tag_id bigint;
	declare x bigint;

	START TRANSACTION;
	select owner_id into _owner_id from tig_ma_msgs where msg_id = _msgId;
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
	insert into tig_ma_msgs_tags (msg_id, tag_id) values (_msgId, _tag_id) on duplicate key update tag_id = tag_id;
	COMMIT;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_RemoveMessages(_ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp(6), _to timestamp(6))
begin
	set @_owner_id = 0;
	set @_buddy_id = 0;

	select jid_id into @_owner_id from tig_ma_jids j where j.jid_sha1 = SHA1(LOWER(_ownerJid));

	if _buddyJid is not null then
	    select jid_id into @_buddy_id from tig_ma_jids j where j.jid_sha1 = SHA1(LOWER(_buddyJid));
    end if;

    delete from tig_ma_msgs
        where
            owner_id = @_owner_id
            and (_from is null or ts >= _from)
            and (_to is null or ts <= _to)
            and (_buddyJid is null or buddy_id = @_buddy_id);
    
	delete from tig_ma_jids
	    where
	        jid_id = @_owner_id
	        and not exists (
	            select 1 from tig_ma_msgs m where m.owner_id = jid_id
	        )
	        and not exists (
	            select 1 from tig_ma_msgs m where m.buddy_id = jid_id
	        );

    if _buddyJid is null then
    	delete from tig_ma_jids
	        where
	            not exists (
	                select 1 from tig_ma_msgs m where m.owner_id = jid_id
    	        )
	            and not exists (
	                select 1 from tig_ma_msgs m where m.buddy_id = jid_id
	            );
    else
    	delete from tig_ma_jids
	        where
	            jid_id = @_buddy_id
	            and not exists (
	                select 1 from tig_ma_msgs m where m.owner_id = jid_id
	            )
	            and not exists (
	                select 1 from tig_ma_msgs m where m.buddy_id = jid_id
	            );
    end if;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_DeleteExpiredMessages(_domain varchar(1024) CHARSET utf8, _before timestamp(6))
begin
	delete from tig_ma_msgs where ts < _before and exists (select 1 from tig_ma_jids j where j.jid_id = owner_id and `domain` = _domain);
	delete from tig_ma_jids
	    where
	        not exists (
	            select 1 from tig_ma_msgs m where m.owner_id = jid_id
	        )
	        and not exists (
	            select 1 from tig_ma_msgs m where m.buddy_id = jid_id
	        );
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetTagsForUser(_ownerJid varchar(2049) CHARSET utf8, _tagStartsWith varchar(255) CHARSET utf8mb4 collate utf8mb4_bin, _limit int, _offset int)
begin
	select tag 
		from tig_ma_tags t 
		inner join tig_ma_jids o on o.jid_id = t.owner_id 
		where o.jid_sha1 = SHA1(LOWER(_ownerJid))
			and t.tag like _tagStartsWith
		order by t.tag
		limit _limit offset _offset;
end //
-- QUERY END:

-- QUERY START:
create procedure Tig_MA_GetTagsForUserCount(_ownerJid varchar(2049) CHARSET utf8, _tagStartsWith varchar(255) CHARSET utf8mb4 collate utf8mb4_bin)
begin
	select count(tag_id) from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = SHA1(LOWER(_ownerJid)) and t.tag like _tagStartsWith;
end //
-- QUERY END:

delimiter ;

-- QUERY START:
update tig_ma_jids set jid_sha1 = SHA1(LOWER(jid)), `domain` = LOWER(`domain`) where jid <> LOWER(jid) or `domain` <> LOWER(`domain`);
-- QUERY END:

-- QUERY START:
alter table tig_ma_msgs modify ts timestamp(6) null default null;
-- QUERY END:

-- QUERY START:
call TigSetComponentVersion('message-archiving', '2.0.0');
-- QUERY END:
