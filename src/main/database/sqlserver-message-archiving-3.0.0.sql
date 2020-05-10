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
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'stable_id' AND object_id = object_id('dbo.tig_ma_msgs'))
	ALTER TABLE [tig_ma_msgs] ADD [stable_id] [uniqueidentifier];
-- QUERY END:
GO

-- QUERY START:
IF (SELECT count(1) FROM [tig_ma_msgs] WHERE [stable_id] IS NULL) > 0
	UPDATE [tig_ma_msgs] SET [stable_id] = NEWID() WHERE [stable_id] IS NULL;
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.columns WHERE name = 'stable_id' AND object_id = object_id('dbo.tig_ma_msgs') AND is_nullable = 1)
    ALTER TABLE [tig_ma_msgs] ALTER COLUMN [stable_id] [uniqueidentifier] NOT NULL;
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_buddy_id_stable_id_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_buddy_id_stable_id_index ON [dbo].[tig_ma_msgs] ([owner_id], [buddy_id], [stable_id]);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_buddy_id_stanza_hash_ts_index')
DROP INDEX IX_tig_ma_msgs_owner_id_buddy_id_stanza_hash_ts_index ON [dbo].[tig_ma_msgs];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_buddy_id_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_buddy_id_index ON [dbo].[tig_ma_msgs] ([owner_id], [buddy_id]);
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
	@_stableId nvarchar(36)
AS
begin
    set nocount on;

	declare @_owner_id bigint;
	declare @_buddy_id bigint;

	exec Tig_MA_EnsureJid @_jid=@_ownerJid, @_jid_id=@_owner_id output;
	exec Tig_MA_EnsureJid @_jid=@_buddyJid, @_jid_id=@_buddy_id output;

	insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, type, body, msg, stable_id)
		select @_owner_id, @_buddy_id, @_buddyRes, @_ts, @_direction, @_type, @_body, @_msg, @_stableId
		where not exists (
			select 1 from tig_ma_msgs
			    where owner_id = @_owner_id
			        and buddy_id = @_buddy_id
			        and stable_id = @_stableId
		);
	select @@IDENTITY as msg_id;

	set nocount off;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetMessagePosition')
	DROP PROCEDURE [dbo].[Tig_MA_GetMessagePosition]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetMessagePosition]
	@_ownerJid nvarchar(2049),
	@_buddyJid nvarchar(2049),
	@_from datetime,
	@_to datetime,
	@_tags nvarchar(max),
	@_contains nvarchar(max),
	@_stableId nvarchar(36)
AS
begin
	declare
		@params_def nvarchar(max),
		@tags_query nvarchar(max),
		@contains_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if @_tags is not null or @_contains is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime, @_stableId nvarchar(36)';
		exec Tig_MA_GetHasTagsQuery @_in_str = @_tags, @_out_query = @tags_query output;
		exec Tig_MA_GetBodyContainsQuery @_in_str = @_contains, @_out_query = @contains_query output;
		set @msgs_query = N'select x.position from (
		select m.stable_id, row_number() over (order by m.ts) as position
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_ownerJid))
			and (@_buddyJid is null or b.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_buddyJid)))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = @msgs_query + @tags_query + @contains_query + N') x where x.stable_id = convert(uniqueidentifier,@_stableId)';
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_stableId = @_stableId
		end
	else
		begin
		select x.position from (
		    select m.stable_id, row_number() over (order by m.ts) as position
		    from tig_ma_msgs m
			    inner join tig_ma_jids o on m.owner_id = o.jid_id
			    inner join tig_ma_jids b on b.jid_id = m.buddy_id
		    where
			    o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			    and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			    and (@_from is null or m.ts >= @_from)
			    and (@_to is null or m.ts <= @_to)) x
	    where x.stable_id = convert(uniqueidentifier,@_stableId)
		end
end
-- QUERY END:
GO

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
	@_contains nvarchar(max),
	@_limit int,
	@_offset int
AS
begin
	SET NOCOUNT ON;
	declare
		@params_def nvarchar(max),
		@contains_query nvarchar(max),
		@tags_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if @_tags is not null or @_contains is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime, @_limit int, @_offset int';
		exec Tig_MA_GetHasTagsQuery @_in_str = @_tags, @_out_query = @tags_query output;
		exec Tig_MA_GetBodyContainsQuery @_in_str = @_contains, @_out_query = @contains_query output;
		set @msgs_query = N'select m.msg, m.ts, m.direction, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, row_number() over (order by m.ts) as row_num
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_ownerJid))
			and (@_buddyJid is null or b.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_buddyJid)))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_limit=@_limit, @_offset=@_offset
		end
	else
		begin
		;with results_cte as (
		select m.msg, m.ts, m.direction, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, row_number() over (order by m.ts) as row_num
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)
		)
		select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
		end
end
-- QUERY END:
GO


exec TigSetComponentVersion 'message-archiving', '3.0.0';
GO
