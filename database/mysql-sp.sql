/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  andrzej
 * Created: 2016-01-16
 */

drop procedure if exists Tig_MA_GetMessages;

drop procedure if exists Tig_MA_GetMessagesCount;

drop procedure if exists Tig_MA_GetCollections;

drop procedure if exists Tig_MA_GetCollectionsCount;

drop function if exists Tig_MA_EnsureJid;

drop procedure if exists Tig_MA_AddMessage;

drop procedure if exists Tig_MA_AddTagToMessage;

drop procedure if exists Tig_MA_RemoveMessages;

drop procedure if exists Tig_MA_DeleteExpiredMessages;

drop procedure if exists Tig_MA_GetTagsForUser;

drop procedure if exists Tig_MA_GetTagsForUserCount;

delimiter //

create procedure Tig_MA_GetMessages( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp, _to timestamp, _tags text CHARSET utf8, _limit int, _offset int)
begin
	if _tags is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @limit = _limit;
		set @offset = _offset;
		set @tags_query = CONCAT(' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (', _tags, '))');
		set @msgs_query = 'select m.msg, m.ts, m.direction 
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(?) and o.jid = ?
			and (? is null or (b.jid_sha1 = SHA1(?) and b.jid = ?))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
		set @pagination_query = ' limit ? offset ?';
		set @query = CONCAT(@msgs_query, @tags_query, ' order by m.ts', @pagination_query);
		select @query;
		prepare stmt from @query;
		execute stmt using @ownerJid, @ownerJid, @buddyJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @limit, @offset;
		deallocate prepare stmt;
	else
		select m.msg, m.ts, m.direction 
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid
			and (_buddyJid is null or (b.jid_sha1 = SHA1(_buddyJid) and b.jid = _buddyJid))
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to)
		order by m.ts
		limit _limit offset _offset;
	end if;
end //

create procedure Tig_MA_GetMessagesCount( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp, _to timestamp, _tags text CHARSET utf8)
begin
	if _tags is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @tags_query = CONCAT(' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (', _tags, '))');
		set @msgs_query = 'select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(?) and o.jid = ?
			and (? is null or (b.jid_sha1 = SHA1(?) and b.jid = ?))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)';
		set @query = CONCAT(@msgs_query, @tags_query);
		select @query;
		prepare stmt from @query;
		execute stmt using @ownerJid, @ownerJid, @buddyJid, @buddyJid, @buddyJid, @from, @from, @to, @to;
		deallocate prepare stmt;
	else
		select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid
			and (_buddyJid is null or (b.jid_sha1 = SHA1(_buddyJid) and b.jid = _buddyJid))
			and (_from is null or m.ts >= _from)
			and (_to is null or m.ts <= _to);
	end if;
end //

create procedure Tig_MA_GetCollections( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp, _to timestamp, _tags text CHARSET utf8, _byType smallint, _limit int, _offset int)
begin
	if _tags is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @limit = _limit;
		set @offset = _offset;
		set @tags_query = CONCAT(' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (', _tags, '))');
		set @msgs_query = 'select min(m.ts), b.jid'
		if _byType = 1 then
			@msgs_query = CONCAT( @msgs_query, 
				', case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end as `type`');
		else
			@msgs_query = CONCAT( @msgs_query, ', cast(null as varchar(20)) as `type`');
		end if;
		set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(?) and o.jid = ?
			and (? is null or (b.jid_sha1 = SHA1(?) and b.jid = ?))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)');
		if _byType = 1 then
			set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid, case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end';
		else
			set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid';
		end if;
		set @pagination_query = ' limit ? offset ?';
		set @query = CONCAT(@msgs_query, @tags_query, @groupby_query, ' order by m.ts, b.jid', @pagination_query);
		select @query;
		prepare stmt from @query;
		execute stmt using @ownerJid, @ownerJid, @buddyJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @limit, @offset;
		deallocate prepare stmt;
	else
		if _byType = 1 then
			select min(m.ts), b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end as `type`
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid
				and (_buddyJid is null or (b.jid_sha1 = SHA1(_buddyJid) and b.jid = _buddyJid))
				and (_from is null or m.ts >= _from)
				and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end 
			order by m.ts, b.jid
			limit _limit offset _offset;
		else
			select min(m.ts), b.jid, null as `type`
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid
				and (_buddyJid is null or (b.jid_sha1 = SHA1(_buddyJid) and b.jid = _buddyJid))
				and (_from is null or m.ts >= _from)
				and (_to is null or m.ts <= _to)
			group by date(m.ts), m.buddy_id, b.jid
			order by m.ts, b.jid
			limit _limit offset _offset;
		end if;
	end if;
end //

create procedure Tig_MA_GetCollectionsCount( _ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp, _to timestamp, _tags text CHARSET utf8, _byType smallint)
begin
	if _tags is not null then
		set @ownerJid = _ownerJid;
		set @buddyJid = _buddyJid;
		set @from = _from;
		set @to = _to;
		set @tags_query = CONCAT(' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (', _tags, '))');
		set @msgs_query = 'select count(1) from (select min(m.ts), b.jid'
		if _byType = 1 then
			@msgs_query = CONCAT( @msgs_query, 
				', case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end as `type`');
		else
			@msgs_query = CONCAT( @msgs_query, ', cast(null as varchar(20)) as `type`');
		end if;
		set @msgs_query = CONCAT( @msgs_query,' from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = SHA1(?) and o.jid = ?
			and (? is null or (b.jid_sha1 = SHA1(?) and b.jid = ?))
			and (? is null or m.ts >= ?)
			and (? is null or m.ts <= ?)');
		if _byType = 1 then
			set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid, case when m.type = ''groupchat'' then cast(''groupchat'' as varchar(20)) else cast('''' as varchar(20)) end';
		else
			set @groupby_query = ' group by date(m.ts), m.buddy_id, b.jid';
		end if;
		set @query = CONCAT(@msgs_query, @tags_query, @groupby_query, ' ) x');
		select @query;
		prepare stmt from @query;
		execute stmt using @ownerJid, @ownerJid, @buddyJid, @buddyJid, @buddyJid, @from, @from, @to, @to, @limit, @offset;
		deallocate prepare stmt;
	else
		if _byType = 1 then
			select count(1) from (
				select min(m.ts), b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end as `type`
				from tig_ma_msgs m 
					inner join tig_ma_jids o on m.owner_id = o.jid_id 
					inner join tig_ma_jids b on b.jid_id = m.buddy_id
				where 
					o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid
					and (_buddyJid is null or (b.jid_sha1 = SHA1(_buddyJid) and b.jid = _buddyJid))
					and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
				group by date(m.ts), m.buddy_id, b.jid, case when m.type = 'groupchat' then cast('groupchat' as varchar(20)) else cast('' as varchar(20)) end 
			) x;
		else
			select count(1) from (
				select min(m.ts), b.jid, null as `type`
				from tig_ma_msgs m 
					inner join tig_ma_jids o on m.owner_id = o.jid_id 
					inner join tig_ma_jids b on b.jid_id = m.buddy_id
				where 
					o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid
					and (_buddyJid is null or (b.jid_sha1 = SHA1(_buddyJid) and b.jid = _buddyJid))
					and (_from is null or m.ts >= _from)
					and (_to is null or m.ts <= _to)
				group by date(m.ts), m.buddy_id, b.jid
			) x;
		end if;
	end if;
end //

create function Tig_MA_EnsureJid(_jid varchar(2049) CHARSET utf8) returns bigint DETERMINISTIC
begin
	declare _jid_id bigint;
	declare _jid_sha1 char(40);

	select SHA1(_jid) into _jid_sha1;
	select jid_id into _jid_id from tig_ma_jids where jid_sha1 = _jid_sha1;
	if _jid_id is null then
		insert into tig_ma_jids (jid, jid_sha1)
			select _jid, _jid_sha1
			where not exists(select 1 from tig_ma_jids where jid_sha1 = _jid_sha1 and jid = _jid for update)
			on duplicate key update jid_id = LAST_INSERT_ID(jid_id);
		select LAST_INSERT_ID() into _jid_id;
	end if;

	return (_jid_id);
end //

create procedure Tig_MA_AddMessage(_ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8,
	 _buddyRes varchar(1024)  CHARSET utf8, _ts timestamp, _direction smallint, _type varchar(20) CHARSET utf8,
	 _body text CHARSET utf8, _msg text CHARSET utf8, _hash varchar(50) CHARSET utf8)
begin
	declare owner_id bigint;
	declare buddy_id bigint;
	declare msg_id bigint;

	START TRANSACTION;
	select Tig_MA_EnsureJid(_ownerJid) into owner_id;
	select Tig_MA_EnsureJid(_buddyJid) into buddy_id;

	insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, `type`, body, msg, stanza_hash)
	select _owner_id, _buddy_id, _buddyRes, _ts, _direction, _type, _body, _msg, _hash
	where not exists (
		select 1 from tig_ma_msgs where owner_id = _owner_id and buddy_id = _buddy_id and ts = _ts and stanza_hash = _hash for update
	);

	select LAST_INSERT_ID() into _msg_id;
	COMMIT;

	select _msg_id as msg_id;
end //

create procedure Tig_MA_AddTagToMessage(_msgId bigint, _tag varchar(255) CHARSET utf8)
begin
	declare _owner_id bigint;
	declare _tag_id bigint;

	START TRANSACTION
	select owner_id into _owner_id from tig_ma_msgs where msg_id = _msgId;
	select tag_id into _tag_id from tig_ma_tags where owner_id = _owner_id and tag = _tag;
	if _tag_id is null then
		insert into tig_ma_tags (owner_id, tag) 
			values (_owner_id, _tag)
			on duplicate key update tag_id = LAST_INSERT_ID(tag_id);
		select LAST_INSERT_ID() into _tag_id;
	end if;
	insert into tig_ma_msgs_tags (msg_id, tag_id) select _msgId, _tag_id where not exists (
		select 1 from tig_ma_msgs_tags where msg_id = _msgId and tag_id = _tag_id for update
	);
	COMMIT;
end //

create procedure Tig_MA_RemoveMessages(_ownerJid varchar(2049) CHARSET utf8, _buddyJid varchar(2049) CHARSET utf8, _from timestamp, _to timestamp)
begin
	set @_owner_id = 0;
	set @_buddy_id = 0;
	select jid_id into @_owner_id from tig_ma_jids where j.jid_sha1 = SHA1(_ownerJid) and jid = _ownerJid;
	select jid_id into @_buddy_id from tig_ma_jids where j.jid_sha1 = SHA1(_buddyJid) and jid = _buddyJid;
	delete from tig_ma_msgs where owner_id = @_owner_id and buddy_id = @_buddy_id and ts >= _from and ts <= _to;
end //

create procedure Tig_MA_DeleteExpiredMessages(_domain varchar(1024) CHARSET utf8, _before timestamp)
begin
	delete from tig_ma_msgs where ts < _before and exists (select 1 from tig_ma_jids j where j.jid_id = owner_id and `domain` = _domain);
end //

create procedure Tig_MA_GetTagsForUser(_ownerJid varchar(2049) CHARSET utf8)
begin
	select tag from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid;
end //

create procedure Tig_MA_GetTagsForUserCount(_ownerJid varchar(2049) CHARSET utf8)
begin
	select count(tag_id) from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = SHA1(_ownerJid) and o.jid = _ownerJid;
end //

delimiter ;