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
create or replace function Tig_MA_GetMessage(_ownerJid varchar(2049), _stableId varchar(36)) returns table(
    "msg" text, "ts" timestamp with time zone, "buddyJid" varchar(2049), "stableId" varchar(36), "refStableId" varchar(36)
              ) as $$
              declare
              tags_query text;
begin
    return query select m.msg, m.ts,b.jid, cast(m.stable_id as varchar(36)) as stable_id, cast(m.ref_stable_id as varchar(36)) as ref_stable_id
                 from tig_ma_msgs m
                          inner join tig_ma_jids o on m.owner_id = o.jid_id
                          inner join tig_ma_jids b on b.jid_id = m.buddy_id
                 where
                    m.stable_id = uuid(_stableId)
                    and lower(o.jid) = lower(_ownerJid);
end;
$$ LANGUAGE 'plpgsql';
-- QUERY END:

