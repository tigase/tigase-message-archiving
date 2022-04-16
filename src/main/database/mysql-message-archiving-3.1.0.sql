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
drop procedure if exists Tig_MA_GetMessage;
-- QUERY END:

delimiter //

-- QUERY START:
create procedure Tig_MA_GetMessage( _ownerJid varchar(2049) CHARSET utf8, _stableId varchar(36) CHARSET utf8)
begin
    select m.msg, m.ts, b.jid, Tig_MA_OrderedToUuid(m.stable_id) as stable_id, Tig_MA_OrderedToUuid(m.ref_stable_id) as ref_stable_id
    from tig_ma_msgs m
             inner join tig_ma_jids o on m.owner_id = o.jid_id
             inner join tig_ma_jids b on b.jid_id = m.buddy_id
    where
        m.stable_id = Tig_MA_UuidToOrdered(_stableId)
        and o.jid_sha1 = SHA1(LOWER(_ownerJid));
end //
-- QUERY END:

delimiter ;