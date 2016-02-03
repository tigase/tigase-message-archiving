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
create table if not exists tig_ma_jids (
	jid_id bigserial,
	jid varchar(2049),
	
	primary key(jid_id)
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_jids_jid')) is null) then
	create unique index tig_ma_jids_jid on tig_ma_jids (jid);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_msgs (
	msg_id bigserial,
	owner_id bigint not null,
	buddy_id bigint not null,
	ts timestamp,
	direction smallint,
	"type" varchar(20),
	body text,
	msg text,
	stanza_hash varchar(50),

	primary key (msg_id),
	foreign key (buddy_id) references tig_ma_jids (jid_id),
	foreign key (owner_id) references tig_ma_jisd (jid_id)
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_index')) is null) then
	create index tig_ma_msgs_owner_id_index on tig_ma_msgs (owner_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_index')) is null) then
	create index tig_ma_msgs_owner_id_buddy_id_index on tig_ma_msgs (owner_id, buddy_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_ts_index')) is null) then
	create index tig_ma_msgs_owner_id_buddy_id_ts_index on tig_ma_msgs (owner_id, buddy_id, ts);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index')) is null) then
	create unique index tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index on tig_ma_msgs (owner_id, ts, buddy_id, stanza_hash);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_tags (
	tag_id bigserial,
	owner_id bigint not null,
	tag varchar(255),

	primary key (tag_id),
	foreign key (owner_id) references tig_ma_jids (jid_id) on delete cascade
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_owner_id')) is null) then
create index tig_ma_tags_owner_id on tig_ma_tags (owner_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_tag_owner_id')) is null) then
create unique index tig_ma_tags_tag_owner_id on tig_ma_tags (owner_id, tag);
end if;
end$$;
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_msgs_tags (
	msg_id bigint not null,
	tag_id bigint not null,
	
	primary key (msg_id, tag_id),
	foreign key (msg_id) references tig_ma_msgs (msg_id) on delete cascade,
	foreign key (tag_id) references tig_ma_tags (tag_id) on delete cascade
);
-- QUERY END:

-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_msg_id')) is null) then
	create index tig_ma_tags_msg_id on tig_ma_msgs_tags (msg_id);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_tags_tag_id')) is null) then
	create index tig_ma_tags_tag_id on tig_ma_msgs_tags (tag_id);
end if;
end$$;
-- QUERY END:

-- additional changes introduced later - after original schema clarified

-- addition of buddy_res field which should contain resource of buddy
-- QUERY START:
do $$
begin
if not exists(select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' 
		AND table_name = 'tig_ma_msgs' AND column_name = 'buddy_res') then
	alter table tig_ma_msgs add buddy_res varchar(1024);
end if;
end$$;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_owner_id_buddy_id_buddy_res_index')) is null) then
	create index tig_ma_msgs_owner_id_buddy_id_buddy_res_index on tig_ma_msgs (owner_id, buddy_id, buddy_res);
end if;
end$$;
-- QUERY END:

-- addition of domain field to jids table for easier removal of expired messages
-- QUERY START:
do $$
begin
if not exists(select 1 from information_schema.columns where table_catalog = current_database() AND table_schema = 'public' 
		AND table_name = 'tig_ma_jids' AND column_name = 'domain') then
	alter table tig_ma_jids add "domain" varchar(1024);
end if;
end$$;
-- QUERY END:
-- QUERY START:
update tig_ma_jids set "domain" = substr(jid, strpos(jid, '@') + 1) where "domain" is null;
-- QUERY END:
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_jids_domain_index')) is null) then
	create index tig_ma_jids_domain_index on tig_ma_jids ("domain");
end if;
end$$;
-- QUERY END:

-- additional index on tig_ma_msgs to improve removal of expired messages
-- QUERY START:
do $$
begin
if exists (select 1 where (select to_regclass('public.tig_ma_msgs_ts_index')) is null) then
	create index tig_ma_msgs_ts_index on tig_ma_msgs (ts); 
end if;
end$$;
-- QUERY END:

-- added unique constraint on tig_ma_msgs_tags
-- QUERY START:
do $$
begin
if not exists(select 1 from information_schema.key_column_usage where table_catalog = current_database() AND table_schema = 'public' 
		AND table_name = 'tig_ma_msgs_tags' AND constraint_name = 'tig_ma_msgs_tags_pkey') then
	alter table tig_ma_msgs_tags add primary key (msg_id, tag_id);
end if;
end$$;
-- QUERY END: