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
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = N';with results_cte as (' + @msgs_query + @tags_query + @contains_query + N') select * from results_cte where row_num >= @_offset + 1 and row_num < @_offset + 1 + @_limit order by row_num'
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_limit=@_limit, @_offset=@_offset
		end
	else
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
		    if @_refType = 1
		        begin
		            ;with results_cte as (
		                select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id, row_number() over (order by m.ts) as row_num
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
		    else
		        begin
		        	;with results_cte as (
		                select m.owner_id, m.stable_id, row_number() over (order by m.ts) as row_num
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
		            select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id
		            from results_cte cte
		                inner join tigma_msgs m on m.owner_id = cte.owner_id and m.ref_stable_id = cte.stable_id
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
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = @msgs_query + @tags_query + @contains_query;
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to
		end
	else
		begin
		if @_refType = 1
		    begin
		        select count(1)
		        from tig_ma_msgs m
			        inner join tig_ma_jids o on m.owner_id = o.jid_id
			        inner join tig_ma_jids b on b.jid_id = m.buddy_id
		        where
			        o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
			        and (@_buddyJid is null or b.jid_sha1 = HASHBYTES('SHA1', LOWER(@_buddyJid)))
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
			        and (m.is_ref = 0)
			        and (@_from is null or m.ts >= @_from)
			        and (@_to is null or m.ts <= @_to)
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
			and (@_from is null or m.ts >= @_from)
			and (@_to is null or m.ts <= @_to)';
		set @query_sql = @msgs_query + @tags_query + @contains_query + N') x where x.stable_id = convert(uniqueidentifier,@_stableId)';
		execute sp_executesql @query_sql, @params_def, @_ownerJid=@_ownerJid, @_buddyJid=@_buddyJid, @_from=@_from, @_to=@_to, @_stableId = @_stableId
		end
	else
		begin
		if @_refType = 1
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
			            and (m.is_ref = 0)
        			    and (@_from is null or m.ts >= @_from)
		        	    and (@_to is null or m.ts <= @_to)) x
	            where x.stable_id = convert(uniqueidentifier,@_stableId)
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
