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
IF NOT EXISTS (SELECT * FROM sys.columns WHERE name = 'ref_stable_id' AND object_id = object_id('dbo.tig_ma_msgs'))
    ALTER TABLE [tig_ma_msgs] ADD [ref_stable_id] [uniqueidentifier];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT * FROM sys.columns WHERE name = 'is_ref' AND object_id = object_id('dbo.tig_ma_msgs'))
    ALTER TABLE [tig_ma_msgs] ADD [is_ref] [tinyint] NOT NULL DEFAULT 0;
-- QUERY END:
GO

-- QUERY START:
declare @sql NVARCHAR(MAX);
select @sql = 'alter table tig_ma_msgs_tags drop constraint ' + fk.name + ';'
from sys.foreign_keys fk
    inner join sys.tables t on fk.parent_object_id = t.object_id
    inner join sys.columns c on c.object_id = t.object_id
    inner join sys.foreign_key_columns fkc on fkc.constraint_object_id = fk.object_id and fkc.parent_column_id = c.column_id
where object_name(fk.parent_object_id) = N'tig_ma_msgs_tags' and c.name = 'msg_id';

if @sql is not null
begin
    exec sp_executeSQL @sql;
    select @sql = null;
end
-- QUERY END:
GO

-- QUERY START:
declare @sql NVARCHAR(MAX);
SELECT @sql = 'alter table tig_ma_msgs drop constraint ' + kc.name + ';'
FROM sys.key_constraints kc
inner join sys.indexes i on i.object_id = kc.parent_object_id and i.is_primary_key = 1
inner join sys.index_columns ic on ic.object_id = i.object_id and ic.index_id = i.index_id
inner join sys.columns c on c.object_id = i.object_id and c.column_id = ic.column_id
WHERE OBJECT_NAME(kc.parent_object_id) = N'tig_ma_msgs' and c.name = 'msg_id';
if @sql is not null
begin
    exec sp_executeSQL @sql;
    select @sql = null;
end
-- QUERY END:
GO

-- QUERY START:
declare @sql NVARCHAR(MAX);
SELECT @sql = (SELECT 'DROP INDEX [' + i.name + '] ON [dbo].[' + OBJECT_NAME(i.object_id) + ']; ' FROM sys.indexes i
INNER JOIN sys.index_columns ic on ic.object_id = i.object_id
INNER JOIN sys.columns c on c.object_id = ic.object_id and c.column_id = ic.column_id
WHERE i.object_id = object_id('dbo.tig_ma_msgs') and c.name = 'owner_id' AND i.name is not null and i.is_primary_key = 0
GROUP BY i.name, i.object_id for xml path(''));
if @sql is not null
begin
    exec sp_executeSQL @sql;
    select @sql = null;
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT 1 FROM sys.columns WHERE name = 'owner_id' AND object_id = object_id('dbo.tig_ma_msgs') AND is_nullable = 1)
    ALTER TABLE [tig_ma_msgs] ALTER COLUMN [owner_id] [bigint] NOT NULL;
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE  OBJECT_NAME(parent_object_id) = N'tig_ma_msgs')
ALTER TABLE tig_ma_msgs ADD PRIMARY KEY (owner_id, stable_id);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'msg_owner_id' AND object_id = object_id('dbo.tig_ma_msgs_tags'))
ALTER TABLE [tig_ma_msgs_tags] ADD [msg_owner_id] [bigint];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'msg_stable_id' AND object_id = object_id('dbo.tig_ma_msgs_tags'))
ALTER TABLE [tig_ma_msgs_tags] ADD [msg_stable_id] [uniqueidentifier];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'msg_id' AND object_id = object_id('dbo.tig_ma_msgs'))
    IF (SELECT count(1) FROM [tig_ma_msgs_tags] WHERE [msg_stable_id] IS NULL) > 0
	    exec sp_executeSQL 'UPDATE [tig_ma_msgs_tags] SET [tig_ma_msgs_tags].[msg_stable_id] = [tig_ma_msgs].[stable_id], [tig_ma_msgs_tags].[msg_owner_id] = [tig_ma_msgs].[owner_id] FROM [tig_ma_msgs] WHERE [tig_ma_msgs_tags].[msg_id] = [tig_ma_msgs].[msg_id] AND [tig_ma_msgs_tags].[msg_stable_id] IS NULL;';
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT 1 FROM sys.columns WHERE name = 'msg_owner_id' AND object_id = object_id('dbo.tig_ma_msgs_tags') AND is_nullable = 1)
ALTER TABLE [tig_ma_msgs_tags] ALTER COLUMN [msg_owner_id] [bigint] NOT NULL;
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT 1 FROM sys.columns WHERE name = 'msg_stable_id' AND object_id = object_id('dbo.tig_ma_msgs_tags') AND is_nullable = 1)
ALTER TABLE [tig_ma_msgs_tags] ALTER COLUMN [msg_stable_id] [uniqueidentifier] NOT NULL;
-- QUERY END:
GO

-- QUERY START:
declare @sql NVARCHAR(MAX);
SELECT @sql = 'alter table tig_ma_msgs_tags drop constraint ' + name + ';'
FROM sys.key_constraints
WHERE  OBJECT_NAME(parent_object_id) = N'tig_ma_msgs_tags';
if @sql is not null
begin
    exec sp_executeSQL @sql;
    select @sql = null;
end
-- QUERY END:
GO

-- QUERY START:
declare @sql NVARCHAR(MAX);
SELECT @sql = (SELECT 'DROP INDEX [' + i.name + '] ON [dbo].[' + OBJECT_NAME(i.object_id) + ']; ' FROM sys.indexes i
INNER JOIN sys.index_columns ic on ic.object_id = i.object_id
INNER JOIN sys.columns c on c.object_id = ic.object_id and c.column_id = ic.column_id
WHERE i.object_id = object_id('dbo.tig_ma_msgs_tags') and c.name = 'msg_id' AND i.name is not null and i.is_primary_key = 0
GROUP BY i.name, i.object_id for xml path(''));
if @sql is not null
begin
    exec sp_executeSQL @sql;
    select @sql = null;
end
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.key_constraints WHERE  OBJECT_NAME(parent_object_id) = N'tig_ma_msgs_tags')
ALTER TABLE tig_ma_msgs_tags ADD PRIMARY KEY (msg_owner_id, msg_stable_id, tag_id);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1
from sys.foreign_keys fk
    inner join sys.tables t on fk.parent_object_id = t.object_id
    inner join sys.columns c on c.object_id = t.object_id
    inner join sys.foreign_key_columns fkc on fkc.constraint_object_id = fk.object_id and fkc.parent_column_id = c.column_id
where object_name(fk.parent_object_id) = N'tig_ma_msgs_tags' and c.name = 'msg_stable_id')
ALTER table tig_ma_msgs_tags ADD FOREIGN KEY (msg_owner_id, msg_stable_id) REFERENCES tig_ma_msgs (owner_id, stable_id) ON DELETE CASCADE;
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM tig_ma_msgs_tags WHERE msg_stable_id IS NULL or msg_owner_id IS NULL) AND EXISTS (SELECT 1 FROM sys.columns WHERE name = 'msg_id' AND object_id = object_id('dbo.tig_ma_msgs_tags'))
ALTER TABLE [tig_ma_msgs_tags] DROP COLUMN [msg_id];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'offline' AND object_id = object_id('dbo.tig_ma_msgs')) AND EXISTS (SELECT 1 FROM sys.columns WHERE name = 'direction' AND object_id = object_id('dbo.tig_ma_msgs'))
ALTER TABLE [tig_ma_msgs] DROP COLUMN [direction];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'offline' AND object_id = object_id('dbo.tig_ma_msgs')) AND EXISTS (SELECT 1 FROM sys.columns WHERE name = 'type' AND object_id = object_id('dbo.tig_ma_msgs'))
ALTER TABLE [tig_ma_msgs] DROP COLUMN [type];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE name = 'offline' AND object_id = object_id('dbo.tig_ma_msgs')) AND EXISTS (SELECT 1 FROM sys.columns WHERE name = 'buddy_res' AND object_id = object_id('dbo.tig_ma_msgs'))
ALTER TABLE [tig_ma_msgs] DROP COLUMN [buddy_res];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (SELECT 1 FROM tig_ma_msgs_tags WHERE msg_stable_id IS NULL or msg_owner_id IS NULL) AND EXISTS (SELECT 1 FROM sys.columns WHERE name = 'msg_id' AND object_id = object_id('dbo.tig_ma_msgs'))
ALTER TABLE [tig_ma_msgs] DROP COLUMN [msg_id];
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_buddy_id_is_ref_ts_index ON [dbo].[tig_ma_msgs] ([owner_id], [buddy_id], [is_ref], [ts]);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_is_ref_ts_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_is_ref_ts_index ON [dbo].[tig_ma_msgs] ([owner_id], [is_ref], [ts]);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_ref_stable_id_owner_id_index')
CREATE INDEX IX_tig_ma_msgs_ref_stable_id_owner_id_index ON [dbo].[tig_ma_msgs] ([ref_stable_id], [owner_id]);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_buddy_id_stanza_id_ts_index ON [dbo].[tig_ma_msgs] ([owner_id], [buddy_id], [stanza_id], [ts]);
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
	@_ts datetime,
	@_stableId nvarchar(36),
	@_stanzaId nvarchar(64),
	@_refStableId nvarchar(36),
	@_body nvarchar(max),
	@_msg nvarchar(max)

AS
begin
    set nocount on;

	declare @_owner_id bigint;
	declare @_buddy_id bigint;

	exec Tig_MA_EnsureJid @_jid=@_ownerJid, @_jid_id=@_owner_id output;
	exec Tig_MA_EnsureJid @_jid=@_buddyJid, @_jid_id=@_buddy_id output;

	insert into tig_ma_msgs (owner_id, stable_id, buddy_id, ts, stanza_id, is_ref, ref_stable_id, body, msg)
		select @_owner_id, CONVERT(uniqueidentifier, @_stableId), @_buddy_id, @_ts, @_stanzaId, case when @_refStableId is null then 0 else 1 end, CONVERT(uniqueidentifier, @_refStableId), @_body, @_msg
		where not exists (
			select 1 from tig_ma_msgs
			    where owner_id = @_owner_id
			        and stable_id = CONVERT(uniqueidentifier, @_stableId)
		);
	set nocount off;
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
	@_ownerJid nvarchar(2049),
	@_stableId nvarchar(36),
	@_tag nvarchar(255)
AS
begin
	declare @_owner_id bigint;
	declare @_tag_id bigint;

	select @_owner_id = jid_id from tig_ma_jids where jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid));
	select @_tag_id = tag_id from tig_ma_tags where owner_id = @_owner_id and tag = @_tag;
	if @_tag_id is null
		begin
		insert into tig_ma_tags (owner_id, tag) select @_owner_id, @_tag where not exists(
			select 1 from tig_ma_tags where owner_id = @_owner_id and tag = @_tag
		)
		select @_tag_id = @@IDENTITY;
		if @_tag_id is null
			begin
			select @_tag_id = tag_id from tig_ma_tags where owner_id = @_owner_id and tag = @_tag;
			end
		end
	insert into tig_ma_msgs_tags (msg_owner_id, msg_stable_id, tag_id) select @_owner_id, @_stableId, @_tag_id where not exists (
		select 1 from tig_ma_msgs_tags where msg_owner_id = @_owner_id and msg_stable_id = @_stableId and tag_id = @_tag_id
	);
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetStableId')
	DROP PROCEDURE [dbo].[Tig_MA_GetStableId]
-- QUERY END:
GO
-- QUERY START:
create procedure Tig_MA_GetStableId
	@_ownerJid nvarchar(2049),
	@_buddyJid nvarchar(2049),
	@_stanzaId nvarchar(64)
AS
begin
    select convert(nvarchar(36),m.stable_id) as stable_id
        from tig_ma_msgs m
            inner join tig_ma_jids o on m.owner_id = o.jid_id
            inner join tig_ma_jids b on m.buddy_id = b.jid_id
        where
            o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
            and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
            and m.stanza_id = @_stanzaId
            order by m.ts desc;
end
-- QUERY END:
GO


-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetHasTagsQuery')
	DROP PROCEDURE [dbo].[Tig_MA_GetHasTagsQuery]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetHasTagsQuery]
	@_in_str nvarchar(max),
	@_out_query nvarchar(max) OUTPUT
AS
begin
	if @_in_str is not null
		set @_out_query = N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.owner_id = mt.msg_owner_id and m.stable_id = mt.msg_stable_id and t.owner_id = o.jid_id and t.tag IN (' + @_in_str + N'))';
	else
		set @_out_query = N'';
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
	@_refType tinyint,
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
		set @msgs_query = N'select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id, row_number() over (order by m.ts) as row_num
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_ownerJid))
			and (@_buddyJid is null or b.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_buddyJid)))
			and (m.is_ref = 0 or m.is_ref = 1)
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_limit=@_limit, @_offset=@_offset
		end
	else
		begin
		if @_refType < 2
		    begin
		    if @_refType = 0
		        begin
		    	    ;with results_cte as (
		                select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id, row_number() over (order by m.ts) as row_num
		                from tig_ma_msgs m
			                inner join tig_ma_jids o on m.owner_id = o.jid_id
			                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where
			                o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			                and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			                and (m.is_ref = 0)
			                and (@_from is null or m.ts >= @_from)
			                and (@_to is null or m.ts <= @_to)
		            )
		            select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
		        end
		    else
		        begin
		            ;with results_cte as (
		                select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id, row_number() over (order by m.ts) as row_num
		                from tig_ma_msgs m
			                inner join tig_ma_jids o on m.owner_id = o.jid_id
			                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where
			                o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			                and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			                and (m.is_ref = 0 or m.is_ref = 1)
			                and (@_from is null or m.ts >= @_from)
			                and (@_to is null or m.ts <= @_to)
		            )
		            select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
		        end
		    end
		else
		    begin
		    if @_refType = 2
		        begin
		        	;with results_cte as (
		                select m.owner_id, coalesce(m.ref_stable_id, m.stable_id) as stable_id, row_number() over (order by min(m.ts)) as row_num
		                from tig_ma_msgs m
			                inner join tig_ma_jids o on m.owner_id = o.jid_id
			                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where
			                o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			                and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			                and (m.is_ref = 0 or m.is_ref = 1)
			                and (@_from is null or m.ts >= @_from)
			                and (@_to is null or m.ts <= @_to)
			            group by m.owner_id, coalesce(m.ref_stable_id, m.stable_id)
		            )
		            select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id
		            from results_cte cte
		                inner join tig_ma_msgs m on m.owner_id = cte.owner_id and m.stable_id = cte.stable_id
		                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where cte.row_num >= @_offset + 1 and cte.row_num < @_offset + 1 + @_limit order by m.ts;
		        end
		    else
		        begin
		        	;with results_cte as (
		                select m.owner_id, coalesce(m.ref_stable_id, m.stable_id) as stable_id, row_number() over (order by min(m.ts)) as row_num
		                from tig_ma_msgs m
			                inner join tig_ma_jids o on m.owner_id = o.jid_id
			                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where
			                o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			                and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			                and (m.is_ref = 0 or m.is_ref = 1)
			                and (@_from is null or m.ts >= @_from)
			                and (@_to is null or m.ts <= @_to)
			            group by m.owner_id, coalesce(m.ref_stable_id, m.stable_id)
		            )
		            select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id
		            from results_cte cte
		                inner join tig_ma_msgs m on m.ref_stable_id = cte.stable_id and m.owner_id = cte.owner_id
		                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where cte.row_num >= @_offset + 1 and cte.row_num < @_offset + 1 + @_limit order by m.ts;
		        end
		    end
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
	@_refType tinyint,
	@_tags nvarchar(max),
	@_contains nvarchar(max)
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
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime';
		exec Tig_MA_GetHasTagsQuery @_in_str = @_tags, @_out_query = @tags_query output;
		exec Tig_MA_GetBodyContainsQuery @_in_str = @_contains, @_out_query = @contains_query output;
		set @msgs_query = N'select count(1)
		from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_ownerJid))
			and (@_buddyJid is null or b.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_buddyJid)))
			and (m.is_ref = 0 or m.is_ref = 1)
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = @msgs_query + @tags_query + @contains_query;
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to
		end
	else
		begin
		if @_refType < 2
		    begin
		    if @_refType = 0
		        begin
		            select count(1)
		            from tig_ma_msgs m
			            inner join tig_ma_jids o on m.owner_id = o.jid_id
			            inner join tig_ma_jids b on b.jid_id = m.buddy_id
		            where
			            o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			        and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			        and (m.is_ref = 0)
			        and (@_from is null or m.ts >= @_from)
			        and (@_to is null or m.ts <= @_to)
		        end
		    else
		        begin
		            select count(1)
		            from tig_ma_msgs m
			            inner join tig_ma_jids o on m.owner_id = o.jid_id
			            inner join tig_ma_jids b on b.jid_id = m.buddy_id
		            where
			            o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			            and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			            and (m.is_ref = 0 or m.is_ref = 1)
			            and (@_from is null or m.ts >= @_from)
			            and (@_to is null or m.ts <= @_to)
		        end
		    end
		else
		    begin
		    if @_refType = 2
		        begin
                    select count(distinct coalesce(m.ref_stable_id, m.stable_id))
                        from tig_ma_msgs m
                            inner join tig_ma_jids o on m.owner_id = o.jid_id
                            inner join tig_ma_jids b on m.buddy_id = b.jid_id
                        where
			            o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			            and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
                        and (m.is_ref = 0 or m.is_ref = 1)
			            and (@_from is null or m.ts >= @_from)
			            and (@_to is null or m.ts <= @_to);
                end
		    else
		        begin
                    select count(distinct coalesce(m.ref_stable_id, m.stable_id))
                        from tig_ma_msgs m
                            inner join tig_ma_jids o on m.owner_id = o.jid_id
                            inner join tig_ma_jids b on m.buddy_id = b.jid_id
                        where
			            o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			            and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
                        and (m.is_ref = 1)
			            and (@_from is null or m.ts >= @_from)
			            and (@_to is null or m.ts <= @_to);
		        end
			end
		end
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
	@_refType tinyint,
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
			and (m.is_ref = 0 or m.is_ref = 1)
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = @msgs_query + @tags_query + @contains_query + N') x where x.stable_id = convert(uniqueidentifier,@_stableId)';
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_stableId = @_stableId
		end
	else
		begin
		if @_refType < 2
		    begin
		    if @_refType = 0
		        begin
		            select x.position from (
		                select m.stable_id, row_number() over (order by m.ts) as position
		                from tig_ma_msgs m
			                inner join tig_ma_jids o on m.owner_id = o.jid_id
			                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where
			                o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			                and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			                and (m.is_ref = 0)
        			        and (@_from is null or m.ts >= @_from)
		        	        and (@_to is null or m.ts <= @_to)) x
	                where x.stable_id = convert(uniqueidentifier,@_stableId)
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
			                and (m.is_ref = 0 or m.is_ref = 1)
        			        and (@_from is null or m.ts >= @_from)
		        	        and (@_to is null or m.ts <= @_to)) x
	                where x.stable_id = convert(uniqueidentifier,@_stableId)
		        end
		    end
		else
		    begin
		    if @_refType = 2
		        begin
		            select x.position from (
		                select coalesce(m.ref_stable_id, m.stable_id) as stable_id, row_number() over (order by min(m.ts)) as position
		                from tig_ma_msgs m
			                inner join tig_ma_jids o on m.owner_id = o.jid_id
			                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where
			                o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			                and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			                and (m.is_ref = 0 or m.is_ref = 1)
        			        and (@_from is null or m.ts >= @_from)
		        	        and (@_to is null or m.ts <= @_to)
		        	    group by coalesce(m.ref_stable_id, m.stable_id)
		        	    ) x
	                where x.stable_id = convert(uniqueidentifier,@_stableId)
		        end
		    else
		        begin
		            select x.position from (
		                select coalesce(m.ref_stable_id, m.stable_id) as stable_id, row_number() over (order by min(m.ts)) as position
		                from tig_ma_msgs m
			                inner join tig_ma_jids o on m.owner_id = o.jid_id
			                inner join tig_ma_jids b on b.jid_id = m.buddy_id
		                where
			                o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			                and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
			                and (m.is_ref = 1)
        			        and (@_from is null or m.ts >= @_from)
		        	        and (@_to is null or m.ts <= @_to)
		        	    group by coalesce(m.ref_stable_id, m.stable_id)
		        	    ) x
	                where x.stable_id = convert(uniqueidentifier,@_stableId)
		        end
	        end
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
	@_contains nvarchar(max),
	@_limit int,
	@_offset int
AS
begin
	declare
		@params_def nvarchar(max),
		@tags_query nvarchar(max),
		@contains_query nvarchar(max),
		@groupby_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if @_tags is not null or @_contains is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime, @_limit int, @_offset int';
		exec Tig_MA_GetHasTagsQuery @_in_str = @_tags, @_out_query = @tags_query output;
		exec Tig_MA_GetBodyContainsQuery @_in_str = @_contains, @_out_query = @contains_query output;
		set @msgs_query = N'select min(m.ts) as ts, b.jid, ROW_NUMBER() over (order by min(m.ts), b.jid) as row_num';

		set @msgs_query = @msgs_query + N' from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_ownerJid))
			and (@_buddyJid is null or b.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_buddyJid)))
			and (m.is_ref = 0 or m.is_ref = 1)
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @groupby_query = N' group by cast(m.ts as date), m.buddy_id, b.jid';

		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + @groupby_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_limit=@_limit, @_offset=@_offset
		end
	else
		begin
			;with results_cte as (
			select min(ts) as ts, b.jid, row_number() over (order by min(m.ts), b.jid) as row_num
			from tig_ma_msgs m
				inner join tig_ma_jids o on m.owner_id = o.jid_id
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where
				o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
				and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
				and (m.is_ref = 0 or m.is_ref = 1)
				and (@_from is null or m.ts >= @_from)
				and (@_to is null or m.ts <= @_to)
			group by cast(m.ts as date), m.buddy_id, b.jid
			)
			select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
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
	@_contains nvarchar(max)
AS
begin
	declare
		@params_def nvarchar(max),
		@tags_query nvarchar(max),
		@contains_query nvarchar(max),
		@groupby_query nvarchar(max),
		@msgs_query nvarchar(max),
		@query_sql nvarchar(max);

	if @_tags is not null or @_contains is not null
		begin
		set @params_def = N'@_ownerJid nvarchar(2049), @_buddyJid nvarchar(2049), @_from datetime, @_to datetime';
		exec Tig_MA_GetHasTagsQuery @_in_str = @_tags, @_out_query = @tags_query output;
		exec Tig_MA_GetBodyContainsQuery @_in_str = @_contains, @_out_query = @contains_query output;
		set @msgs_query = N'select min(m.ts) as ts, b.jid';

		set @msgs_query = @msgs_query + N' from tig_ma_msgs m
			inner join tig_ma_jids o on m.owner_id = o.jid_id
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where
			o.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_ownerJid))
			and (@_buddyJid is null or b.jid_sha1 = HASHBYTES(''SHA1'', LOWER(@_buddyJid)))
			and (m.is_ref = 0 or m.is_ref = 1)
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @groupby_query = N' group by cast(m.ts as date), m.buddy_id, b.jid';

		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + @groupby_query + N') select count(1) from results_cte'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to
		end
	else
		begin
			;with results_cte as (
			select min(ts) as ts, b.jid
			from tig_ma_msgs m
				inner join tig_ma_jids o on m.owner_id = o.jid_id
				inner join tig_ma_jids b on b.jid_id = m.buddy_id
			where
				o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
				and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
				and (m.is_ref = 0 or m.is_ref = 1)
				and (@_from is null or m.ts >= @_from)
				and (@_to is null or m.ts <= @_to)
			group by cast(m.ts as date), m.buddy_id, b.jid
			)
			select count(1) from results_cte;
		end
end
-- QUERY END:
GO

exec TigSetComponentVersion 'message-archiving', '3.0.0';
GO
