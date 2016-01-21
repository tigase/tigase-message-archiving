/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  andrzej
 * Created: 2016-01-17
 */

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetMessages')
	DROP PROCEDURE [dbo].[Tig_MA_GetMessages]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetMessages]
	@_ownerJid nvarchar(2049), 
	@_buddyJid nvarchar(2049), 
	@_from datetime, 
	@_to datetime, 
	@_tags nvarchar(max), 
	@_limit int, 
	@_offset int
AS
begin
	declare 
		@params_def nvarchar(max),
		@tags_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if _tags is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime, @_limit int, @_offset int';
		set @tags_query = N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (' + _tags + '))';
		set @msgs_query = N'select m.msg, m.ts, m.direction, row_number() over (order by m.ts) as row_num
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES(''SHA1'', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES(''SHA1'', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid, @_buddyJid, @_from, @_to, @_limit, @_offset
		end
	else
		begin
		;with results_cte as (
		select m.msg, m.ts, m.direction, row_number() over (order by m.ts) as row_num
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES('SHA1', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES('SHA1', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)
		)
		select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
		end
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetMessagesCount')
	DROP PROCEDURE [dbo].[Tig_MA_GetMessagesCount]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetMessagesCount]
	@_ownerJid nvarchar(2049), 
	@_buddyJid nvarchar(2049), 
	@_from datetime, 
	@_to datetime, 
	@_tags nvarchar(max)
AS
begin
	declare 
		@params_def nvarchar(max),
		@tags_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if _tags is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime, @_limit int, @_offset int';
		set @tags_query = N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (' + _tags + '))';
		set @msgs_query = N'select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES(''SHA1'', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES(''SHA1'', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = @msgs_query + @tags_query;
		execute sp_executesql @query_sql, @params_def, @_ownerJid, @_buddyJid, @_from, @_to, @_limit, @_offset
		end
	else
		begin
		select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES('SHA1', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES('SHA1', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)
		)
		end
end
-- QUERY END:
GO


-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetCollections')
	DROP PROCEDURE [dbo].[Tig_MA_GetCollections]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetCollections]
	@_ownerJid nvarchar(2049), 
	@_buddyJid nvarchar(2049), 
	@_from datetime, 
	@_to datetime, 
	@_tags nvarchar(max), 
	@_byType smallint,
	@_limit int, 
	@_offset int
AS
begin
	declare 
		@params_def nvarchar(max),
		@tags_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if _tags is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime, @_limit int, @_offset int';
		set @tags_query = N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (' + _tags + '))';
		set @msgs_query = N'select min(m.ts) as ts, b.jid, ROW_NUMBER() over (order by min(m.ts), j.jid) as row_num';

		if _byType = 1 
			set @msgs_query = @msgs_query + N', case when m.type = ''groupchat'' then ''groupchat'' else '''' end as type';
		else
			set @msgs_query = @msgs_query + N', null as type';

		set @msgs_query = @msgs_query + N' from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES(''SHA1'', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES(''SHA1'', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		if _byType = 1
			set @msgs_query = msgs_query + N' group by cast(m.ts as date), m.buddy_id, b.jid, case when m.type = ''groupchat'' then ''groupchat'' else '''' end';
		else
			set @msgs_query = msgs_query + N' group by cast(m.ts as date), m.buddy_id, b.jid';
		
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid, @_buddyJid, @_from, @_to, @_limit, @_offset
		end
	else
		begin
		if _byType = 1
			begin
			;with results_cte as (
			select min(ts) as ts, b.jid, row_number() over (order by min(m.ts), b.jid) as row_num, case when m.type = 'groupchat' then 'groupchat' else '' end as type
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = HASHBYTES('SHA1', @_ownerJid) and o.jid = @_ownerJid
				and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES('SHA1', @_buddyJid) and b.jid = @_buddyJid))
				and (@_from is null or m.ts >= @_from)
				and (@_to is null or m.ts <= @_to)
			group by cast(m.ts as date), m.buddy_id, b.jid, case when m.type = 'groupchat' then 'groupchat' else '' end
			)
			select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
			end
		else
			begin
			;with results_cte as (
			select min(ts) as ts, b.jid, row_number() over (order by min(m.ts), b.jid) as row_num, null as type
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = HASHBYTES('SHA1', @_ownerJid) and o.jid = @_ownerJid
				and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES('SHA1', @_buddyJid) and b.jid = @_buddyJid))
				and (@_from is null or m.ts >= @_from)
				and (@_to is null or m.ts <= @_to)
			group by cast(m.ts as date), m.buddy_id, b.jid
			)
			select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
			end
		end
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetCollectionsCount')
	DROP PROCEDURE [dbo].[Tig_MA_GetCollectionsCount]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetCollectionsCount]
	@_ownerJid nvarchar(2049), 
	@_buddyJid nvarchar(2049), 
	@_from datetime, 
	@_to datetime, 
	@_tags nvarchar(max), 
	@_byType smallint
AS
begin
	declare 
		@params_def nvarchar(max),
		@tags_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if _tags is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime, @_limit int, @_offset int';
		set @tags_query = N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (' + _tags + '))';
		set @msgs_query = N'select min(m.ts) as ts, b.jid';

		if _byType = 1 
			set @msgs_query = @msgs_query + N', case when m.type = ''groupchat'' then ''groupchat'' else '''' end as type';
		else
			set @msgs_query = @msgs_query + N', null as type';

		set @msgs_query = @msgs_query + N' from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES(''SHA1'', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES(''SHA1'', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		if _byType = 1
			set @msgs_query = msgs_query + N' group by cast(m.ts as date), m.buddy_id, b.jid, case when m.type = ''groupchat'' then ''groupchat'' else '''' end';
		else
			set @msgs_query = msgs_query + N' group by cast(m.ts as date), m.buddy_id, b.jid';
		
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + N') select count(1) from results_cte'
		execute sp_executesql @query_sql, @params_def, @_ownerJid, @_buddyJid, @_from, @_to, @_limit, @_offset
		end
	else
		begin
		if _byType = 1
			begin
			;with results_cte as (
			select min(ts) as ts, b.jid, case when m.type = 'groupchat' then 'groupchat' else '' end as type
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = HASHBYTES('SHA1', @_ownerJid) and o.jid = @_ownerJid
				and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES('SHA1', @_buddyJid) and b.jid = @_buddyJid))
				and (@_from is null or m.ts >= @_from)
				and (@_to is null or m.ts <= @_to)
			group by cast(m.ts as date), m.buddy_id, b.jid, case when m.type = 'groupchat' then 'groupchat' else '' end
			)
			 select count(1) from results_cte;
			end
		else
			begin
			;with results_cte as (
			select min(ts) as ts, b.jid, null as type
			from tig_ma_msgs m 
				inner join tig_ma_jids o on m.owner_id = o.jid_id 
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where 
				o.jid_sha1 = HASHBYTES('SHA1', @_ownerJid) and o.jid = @_ownerJid
				and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES('SHA1', @_buddyJid) and b.jid = @_buddyJid))
				and (@_from is null or m.ts >= @_from)
				and (@_to is null or m.ts <= @_to)
			group by cast(m.ts as date), m.buddy_id, b.jid
			)
			select count(1) from results_cte;
			end
		end
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_EnsureJid')
	DROP PROCEDURE Tig_MA_EnsureJid
-- QUERY END:
GO

-- QUERY START:
create procedure dbo.Tig_MA_EnsureJid
	@_jid nvarchar(2049),
	@_jid_id bigint OUTPUT
AS
begin
	declare @_jid_sha1 varbinary(20);

		set @_jid_sha1 = HASHBYTES('SHA1', @_jid);
		select @_jid_id=jid_id from tig_ma_jids
			where jid_sha1 = @_jid_sha1 and jid = @_jid;
		if @_jid_id is null
		begin
			BEGIN TRY
			insert into tig_ma_jids (jid,jid_sha1)
				select @_jid, @_jid_sha1 where not exists(
							select 1 from tig_ma_jids where jid_sha1 = @_jid_sha1 and jid = @_jid);
			select @_jid_id = @@IDENTITY;
			END TRY
			BEGIN CATCH
					IF ERROR_NUMBER() = 2627
						select @_jid_id=jid_id from tig_ma_jids
							where jid_sha1 = @_jid_sha1 and jid = @_jid;
					ELSE
						declare @ErrorMessage nvarchar(max), @ErrorSeverity int, @ErrorState int;
						select @ErrorMessage = ERROR_MESSAGE() + ' Line ' + cast(ERROR_LINE() as nvarchar(5)), @ErrorSeverity = ERROR_SEVERITY(), @ErrorState = ERROR_STATE();
						raiserror (@ErrorMessage, @ErrorSeverity, @ErrorState);
			END CATCH
		end
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_AddMessage')
	DROP PROCEDURE Tig_MA_AddMessage
-- QUERY END:
GO

-- QUERY START:
create procedure Tig_MA_AddMessage
	@_ownerJid nvarchar(2049), 
	@_buddyJid nvarchar(2049),
	@_buddyRes nvarchar(1024), 
	@_ts datetime, 
	@_direction smallint,
	@_type varchar(20),
	@_body nvarchar(max),
	@_msg nvarchar(max),
	@_hash nvarchar(50)
AS
begin
	declare @owner_id bigint;
	declare @buddy_id bigint;
	
	exec Tig_MA_EnsureJid @_jid=@_ownerJid, @_jid_id=@_owner_id output;
	exec Tig_MA_EnsureJid @_jid=@_buddyJid, @_jid_id=@_buddy_id output;

	insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, type, body, msg, stanza_hash)
		select @_owner_id, @_buddy_id, @_buddyRes, @_ts, @_direction, @_type, @_body, @_msg, @_hash
		where not exists (
			select 1 from tig_ma_msgs where owner_id = @_owner_id and buddy_id = @_buddy_id and ts = @_ts and stanza_hash = @_hash 
		);
	select _msg_id = @@IDENTITY;	
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_AddTagToMessage')
	DROP PROCEDURE Tig_MA_AddTagToMessage
-- QUERY END:
GO

-- QUERY START:
create procedure Tig_MA_AddTagToMessage
	@_msgId bigint,
	@_tag nvarchar(255)
AS
begin
	declare @_owner_id bigint;
	declare @_tag_id bigint;
	
	select @_owner_id = owner_id from tig_ma_msgs where msg_id = @_msgId;
	select @_tag_id = tag_id from tig_ma_tags where owner_id = @_owner_id and tag = @_tag;
	if @_tag_id is null
		begin
		insert into tig_ma_tags (owner_id, tag) select @_owner_id, @_tag where not exists(
			select 1 from tig_ma_tags where owner_id = @_owner_id and tag = @_tag;
		);
		select @_tag_id = @@IDENTITY;
		if @_tag_id is null then
			select @_tag_id = tag_id from tig_ma_tags where owner_id = @_owner_id and tag = @_tag;
		end if;
		end
	end if;
	insert into tig_ma_msgs_tags (msg_id, tag_id) select @_msgId, @_tag_id where not exists (
		select 1 from tig_ma_msgs_tags where msg_id = @_msgId and tag_id = @_tag_id; 
	);
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_RemoveMessages')
	DROP PROCEDURE Tig_MA_RemoveMessages
-- QUERY END:
GO

-- QUERY START:
create procedure Tig_MA_RemoveMessages
	@_ownerJid nvarchar(2049),
	@_buddyJid nvarchar(2049),
	@_from datetime,
	@_to datetime
AS
begin
	declare @_owner_id bigint;
	daclare @_buddy_id bigint;
	set @_owner_id = 0;
	set @_buddy_id = 0;
	select @_owner_id = jid_id from tig_ma_jids where jid_sha1 = HASHBYTES('SHA1', @_ownerJid) and jid = @_ownerJid;
	select @_buddy_id = jid_id from tig_ma_jids where jid_sha1 = HASHBYTES('SHA1', @_buddyJid) and jid = @_buddyJid;
	delete from tig_ma_msgs where owner_id = @_owner_id and buddy_id = @_buddy_id and ts >= @_from and ts <= @_to;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_DeleteExpiredMessages')
	DROP PROCEDURE Tig_MA_DeleteExpiredMessages
-- QUERY END:
GO

-- QUERY START:
create procedure Tig_MA_DeleteExpiredMessages
	@_domain nvarchar(1024),
	@_before datetime
AS
begin
	delete from tig_ma_msgs where ts < @_before and exists (select 1 from tig_ma_jids j where j.jid_id = owner_id and [domain] = @_domain);
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetTagsForUser')
	DROP PROCEDURE Tig_MA_GetTagsForUser
-- QUERY END:
GO

-- QUERY START:
create procedure Tig_MA_GetTagsForUser
	@_ownerJid nvarchar(2049)
AS
begin
	select tag from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = HASHBYTES('SHA1',@_ownerJid) and o.jid = @_ownerJid;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetTagsForUserCount')
	DROP PROCEDURE Tig_MA_GetTagsForUserCount
-- QUERY END:
GO

-- QUERY START:
create procedure Tig_MA_GetTagsForUserCount
	@_ownerJid nvarchar(2049)
AS
begin
	select count(tag_id) from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = HASHBYTES('SHA1',@_ownerJid) and o.jid = @_ownerJid;
end
-- QUERY END:
GO
