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
IF EXISTS (SELECT * FROM sys.objects WHERE type = 'P' AND name = 'Tig_MA_GetMessage')
    DROP PROCEDURE [dbo].[Tig_MA_GetMessage]
-- QUERY END:
GO

-- QUERY START:
create procedure [dbo].[Tig_MA_GetMessage]
	@_ownerJid nvarchar(2049),
	@_stableId nvarchar(36)
AS
begin
SET NOCOUNT ON;
    select m.msg, m.ts, b.jid, convert(nvarchar(36),m.stable_id) as stable_id, convert(nvarchar(36),m.ref_stable_id) as ref_stable_id, row_number() over (order by m.ts) as row_num
    from tig_ma_msgs m
             inner join tig_ma_jids o on m.owner_id = o.jid_id
             inner join tig_ma_jids b on b.jid_id = m.buddy_id
    where
            m.stable_id = convert(uniqueidentifier,@_stableId)
            and o.jid_sha1 = HASHBYTES('SHA1', LOWER(@_ownerJid))
end
-- QUERY END:
GO

