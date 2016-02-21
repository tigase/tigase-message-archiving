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

-- QUERY START:
SET QUOTED_IDENTIFIER ON
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (select * from sysobjects where name='tig_ma_jids' and xtype='U')
	CREATE  TABLE [dbo].[tig_ma_jids] (
		[jid_id] [bigint] IDENTITY(1,1) NOT NULL,
		[jid] [nvarchar](2049) NOT NULL,
		[jid_sha1] [varbinary](20) NOT NULL,
		[jid_fragment] AS CAST( [jid] AS NVARCHAR(255)),
		PRIMARY KEY ( [jid_id] ),
		CONSTRAINT UQ_tig_ma_jids_jids_sha1 UNIQUE (jid_sha1)
	);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_jids') AND NAME ='IX_tig_ma_jids_jid')
	CREATE INDEX IX_tig_ma_jids_jid ON [dbo].[tig_ma_jids](jid_fragment);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_jids') AND NAME ='IX_tig_ma_jids_jid_sha1')
	CREATE INDEX IX_tig_ma_jids_jid_sha1 ON [dbo].[tig_ma_jids](jid_sha1);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS (select * from sysobjects where name='tig_ma_msgs' and xtype='U')
	CREATE  TABLE [dbo].[tig_ma_msgs] (
		[msg_id] [bigint] IDENTITY(1,1) NOT NULL,
		[owner_id] [bigint],
		[buddy_id] [bigint],
		[ts] [datetime],
		[direction] [smallint],
		[type] [nvarchar](20),
		[body] [nvarchar](max),
		[msg] [nvarchar](max),
		[stanza_hash] [nvarchar](50),

		PRIMARY KEY ( [msg_id] ),
		CONSTRAINT [FK_tig_ma_msgs_owner_id] FOREIGN KEY ([owner_id])
			REFERENCES [dbo].[tig_ma_jids]([jid_id]),
		CONSTRAINT [FK_tig_ma_msgs_buddy_id] FOREIGN KEY ([buddy_id])
			REFERENCES [dbo].[tig_ma_jids]([jid_id])
	);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_index ON [dbo].[tig_ma_msgs] ([owner_id]);
-- QUERY END:
GO
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_buddy_id_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_buddy_id_index ON [dbo].[tig_ma_msgs] ([owner_id], [buddy_id]);
-- QUERY END:
GO
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_buddy_id_ts_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_buddy_id_ts_index ON [dbo].[tig_ma_msgs] ([owner_id], [buddy_id], [ts]);
-- QUERY END:
GO
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index')
CREATE INDEX IX_tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index ON [dbo].[tig_ma_msgs] ([owner_id], [ts], [buddy_id], [stanza_hash]);
-- QUERY END:
GO

IF NOT EXISTS (select * from sysobjects where name='tig_ma_tags' and xtype='U')
	CREATE TABLE [dbo].[tig_ma_tags] (
		[tag_id] [bigint] IDENTITY(1,1) NOT NULL,
		[owner_id] [bigint] NOT NULL,
		[tag] [nvarchar](255),

		PRIMARY KEY ([tag_id]),
		CONSTRAINT [FK_tig_ma_tags_owner_id] FOREIGN KEY ([owner_id]) 
			REFERENCES [tig_ma_jids] ([jid_id]) on delete cascade
	);
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_tags') AND NAME ='IX_tig_ma_tags_owner_id')
CREATE INDEX IX_tig_ma_tags_owner_id ON [dbo].[tig_ma_tags] ([owner_id]);
-- QUERY END:
GO
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_tags') AND NAME ='IX_tig_ma_tags_tag_owner_id')
CREATE UNIQUE INDEX IX_tig_ma_tags_tag_owner_id ON [dbo].[tig_ma_tags] ([owner_id], [tag]);
-- QUERY END:
GO

IF NOT EXISTS (select * from sysobjects where name='tig_ma_msgs_tags' and xtype='U')
	CREATE TABLE [dbo].[tig_ma_msgs_tags] (
		[msg_id] [bigint] NOT NULL,
		[tag_id] [bigint] NOT NULL,

		PRIMARY KEY ([msg_id], [tag_id]),
		CONSTRAINT [FK_tig_ma_msgs_tags_msg_id] FOREIGN KEY ([msg_id]) 
			REFERENCES [tig_ma_msgs] ([msg_id]) on delete cascade,
		CONSTRAINT [FK_tig_ma_msgs_tags_tag_id] FOREIGN KEY ([tag_id]) 
			REFERENCES [tig_ma_tags] ([tag_id]) on delete cascade
	);
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs_tags') AND NAME ='IX_tig_ma_msgs_tags_msg_id')
CREATE INDEX IX_tig_ma_msgs_tags_msg_id ON [dbo].[tig_ma_msgs_tags] ([msg_id]);
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs_tags') AND NAME ='IX_tig_ma_msgs_tags_tag_id')
CREATE INDEX IX_tig_ma_msgs_tags_tag_id ON [dbo].[tig_ma_msgs_tags] ([tag_id]);
-- QUERY END:
GO

-- additional changes introduced later - after original schema clarified

-- addition of buddy_res field which should contain resource of buddy
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('tig_ma_msgs') and NAME = 'buddy_res')
ALTER TABLE [dbo].[tig_ma_msgs] ADD [buddy_res] [nvarchar](1024);
-- QUERY END:
GO

-- addition of domain field to jids table for easier removal of expired messages
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('tig_ma_jids') and NAME = 'domain')
ALTER TABLE [dbo].[tig_ma_jids] ADD [domain] [nvarchar](1024);
-- QUERY END:
GO
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('tig_ma_jids') and NAME = 'domain_sha1')
ALTER TABLE [dbo].[tig_ma_jids] ADD [domain_sha1] [varbinary](20);
-- QUERY END:
GO
-- QUERY START:
UPDATE [dbo].[tig_ma_jids] SET [domain] = SUBSTRING([jid], CHARINDEX('@',[jid]) + 1, LEN([jid])) WHERE [domain] IS NULL;
-- QUERY END:
GO
-- QUERY START:
UPDATE [dbo].[tig_ma_jids] SET [domain_sha1] = HASHBYTES('SHA1', [domain]) WHERE [domain_sha1] IS NULL;
-- QUERY END:
GO

-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_jids') AND NAME ='IX_tig_ma_jids_domain_sha1_index')
CREATE INDEX IX_tig_ma_jids_domain_sha1_index ON [dbo].[tig_ma_jids] ([domain_sha1]);
-- QUERY END:
GO

-- additional index on tig_ma_msgs to improve removal of expired messages
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.indexes WHERE object_id = object_id('dbo.tig_ma_msgs') AND NAME ='IX_tig_ma_msgs_ts_index')
CREATE INDEX IX_tig_ma_msgs_ts_index ON [dbo].[tig_ma_msgs] ([ts]); 
-- QUERY END:
GO

-- add detection if column tig_ma_jids - jid_sha1 exists
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('tig_ma_jids') and NAME = 'jid_sha1')
ALTER TABLE [dbo].[tig_ma_jids] ADD [jid_sha1] [varbinary](20);
-- QUERY END:
GO
-- QUERY START:
UPDATE [dbo].[tig_ma_jids] SET [jid_sha1] = HASHBYTES('SHA1', [jid]) WHERE [jid_sha1] IS NULL;
-- QUERY END:
GO
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID('tig_ma_jids') and TYPE = 'UQ' and NAME = 'UQ_tig_ma_jids_jids_sha1')
ALTER TABLE [dbo].[tig_ma_jids] ADD CONSTRAINT UQ_tig_ma_jids_jids_sha1 UNIQUE (jid_sha1);
-- QUERY END:
GO

-- added unique constraint on tig_ma_msgs_tags
-- QUERY START:
IF NOT EXISTS(SELECT * FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID('tig_ma_msgs_tags') and TYPE = 'PK')
ALTER TABLE [dbo].[tig_ma_msgs_tags] ADD PRIMARY KEY (msg_id, tag_id);
-- QUERY END:
GO

-- ---------------------
-- Stored procedures
-- ---------------------

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
		set @_out_query = N' and exists(select 1 from tig_ma_msgs_tags mt inner join tig_ma_tags t on mt.tag_id = t.tag_id where m.msg_id = mt.msg_id and t.owner_id = o.jid_id and t.tag IN (' + @_in_str + N'))';
	else
		set @_out_query = N'';
end
-- QUERY END:
GO

-- QUERY START:
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetBodyContainsQuery')
	DROP PROCEDURE [dbo].[Tig_MA_GetBodyContainsQuery]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetBodyContainsQuery]
	@_in_str nvarchar(max),
	@_out_query nvarchar(max) OUTPUT
AS
begin
	if @_in_str is null
		set @_out_query = N'';
	else
		set @_out_query = N' and m.body like ' + replace(@_in_str, N''',''', N''' and m.body like = ''');
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
		set @msgs_query = N'select m.msg, m.ts, m.direction, b.jid, row_number() over (order by m.ts) as row_num
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES(''SHA1'', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES(''SHA1'', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_limit=@_limit, @_offset=@_offset
		end
	else
		begin
		;with results_cte as (
		select m.msg, m.ts, m.direction, b.jid, row_number() over (order by m.ts) as row_num
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
		set @msgs_query = N'select count(m.msg_id)
		from tig_ma_msgs m 
			inner join tig_ma_jids o on m.owner_id = o.jid_id 
			inner join tig_ma_jids b on b.jid_id = m.buddy_id
		where 
			o.jid_sha1 = HASHBYTES(''SHA1'', @_ownerJid) and o.jid = @_ownerJid
			and (@_buddyJid is null or (b.jid_sha1 = HASHBYTES(''SHA1'', @_buddyJid) and b.jid = @_buddyJid))
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = @msgs_query + @tags_query + @contains_query;
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to
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
	@_byType smallint,
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

		if @_byType = 1 
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
		if @_byType = 1
			set @groupby_query = N' group by cast(m.ts as date), m.buddy_id, b.jid, case when m.type = ''groupchat'' then ''groupchat'' else '''' end';
		else
			set @groupby_query = N' group by cast(m.ts as date), m.buddy_id, b.jid';
		
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + @groupby_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_limit=@_limit, @_offset=@_offset
		end
	else
		begin
		if @_byType = 1
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
	@_contains nvarchar(max),
	@_byType smallint
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

		if @_byType = 1 
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
		if @_byType = 1
			set @groupby_query = N' group by cast(m.ts as date), m.buddy_id, b.jid, case when m.type = ''groupchat'' then ''groupchat'' else '''' end';
		else
			set @groupby_query = N' group by cast(m.ts as date), m.buddy_id, b.jid';
		
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + @groupby_query + N') select count(1) from results_cte'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to
		end
	else
		begin
		if @_byType = 1
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
	declare @_jid_sha1 varbinary(20),
			@_domain nvarchar(1024);

		set @_jid_sha1 = HASHBYTES('SHA1', @_jid);
		select @_jid_id=jid_id from tig_ma_jids
			where jid_sha1 = @_jid_sha1 and jid = @_jid;
		if @_jid_id is null
		begin
			BEGIN TRY
			select @_domain = SUBSTRING(@_jid, CHARINDEX('@',@_jid) + 1, LEN(@_jid));
			insert into tig_ma_jids (jid,jid_sha1,[domain],[domain_sha1])
				select @_jid, @_jid_sha1, @_domain, HASHBYTES('SHA1', @_domain) where not exists(
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
	declare @_owner_id bigint;
	declare @_buddy_id bigint;
	
	exec Tig_MA_EnsureJid @_jid=@_ownerJid, @_jid_id=@_owner_id output;
	exec Tig_MA_EnsureJid @_jid=@_buddyJid, @_jid_id=@_buddy_id output;

	insert into tig_ma_msgs (owner_id, buddy_id, buddy_res, ts, direction, type, body, msg, stanza_hash)
		select @_owner_id, @_buddy_id, @_buddyRes, @_ts, @_direction, @_type, @_body, @_msg, @_hash
		where not exists (
			select 1 from tig_ma_msgs where owner_id = @_owner_id and buddy_id = @_buddy_id and ts = @_ts and stanza_hash = @_hash 
		);
	select @@IDENTITY as msg_id
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
			select 1 from tig_ma_tags where owner_id = @_owner_id and tag = @_tag
		)
		select @_tag_id = @@IDENTITY;
		if @_tag_id is null
			begin
			select @_tag_id = tag_id from tig_ma_tags where owner_id = @_owner_id and tag = @_tag;
			end
		end
	insert into tig_ma_msgs_tags (msg_id, tag_id) select @_msgId, @_tag_id where not exists (
		select 1 from tig_ma_msgs_tags where msg_id = @_msgId and tag_id = @_tag_id
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
	declare @_buddy_id bigint;
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
	delete from tig_ma_msgs where ts < @_before and exists (select 1 from tig_ma_jids j where j.jid_id = owner_id and [domain_sha1] = HASHBYTES('SHA1', @_domain) and [domain] = @_domain);
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
	@_ownerJid nvarchar(2049),
	@_tagStartsWith nvarchar(255),
	@_limit int,
	@_offset int
AS
begin
	with results_cte as (
		select tag, ROW_NUMBER() over (order by t.tag) as row_num
			from tig_ma_tags t
			inner join tig_ma_jids o on o.jid_id = t.owner_id 
			where o.jid_sha1 = HASHBYTES('SHA1',@_ownerJid) and o.jid = @_ownerJid
				and t.tag like @_tagStartsWith
	)
	select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num;
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
	@_ownerJid nvarchar(2049),
	@_tagStartsWith nvarchar(255)
AS
begin
	select count(tag_id) from tig_ma_tags t inner join tig_ma_jids o on o.jid_id = t.owner_id where o.jid_sha1 = HASHBYTES('SHA1',@_ownerJid) and o.jid = @_ownerJid and t.tag like @_tagStartsWith;
end
-- QUERY END:
GO

