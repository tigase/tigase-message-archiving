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

create table if not exists tig_ma_jids (
	jid_id unsigned NOT NULL auto_increment,
	jid varchar(2049),
	primary key (jid_id)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;


create table if not exists tig_ma_msgs (
	msg_id bigint unsigned NOT NULL auto_increment,
	owner_id bigint unsigned NOT NULL,
	buddy_id bigint unsigned NOT NULL,
	ts timestamp,
	`direction` smallint,
	`type` varchar(20),
	body text, 
	msg text,
	stanza_hash varchar(50),

	primary key (msg_id),
	foreign key (buddy_id) references tig_ma_jids (jid_id),
	foreign key (owner_id) references tig_ma_jids (jid_id),
	key tig_ma_msgs_owner_id (owner_id),
	key tig_ma_msgs_owner_id_buddy_id (owner_id, buddy_id),
	key tig_ma_msgs_owner_id_ts_buddy_id (owner_id, ts, buddy_id),
	unique index tig_ma_msgs_owner_id_ts_buddy_id_stanza_hash_index using hash (owner_id, ts, buddy_id, stanza_hash)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;

create table if not exists tig_ma_tags (
	tag_id bigint unsigned NOT NULL auto_increment,
	owner_id bigint unsigned not null,
	tag varchar(255),

	primary key (tag_id),
	foreign key (owner_id) references tig_ma_jids (jid_id) on delete cascade,
	key tig_ma_tags_owner_id (owner_id),
	unique key tig_ma_tags_tag_owner_id (owner_id, tag)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;

create table if not exists tig_ma_msgs_tags (
	msg_id bigint unsigned NOT NULL,
	tag_id bigint unsigned NOT NULL,
	
	primary key (msg_id, tag_id),
	foreign key (msg_id) references tig_ma_msgs (msg_id) on delete cascade,
	foreign key (tag_id) references tig_ma_tags (tag_id) on delete cascade,
	key index tig_ma_tags_msg_id on tig_ma_msgs_tags (msg_id),
	key tig_ma_tags_tag_id on tig_ma_msgs_tags (tag_id);
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;

-- additional changes introduced later - after original schema clarified

-- addition of buddy_res field which should contain resource of buddy
alter table tig_ma_msgs add buddy_res varchar(1024);
create index tig_ma_msgs_owner_id_buddy_id_buddy_res_index on tig_ma_msgs (owner_id, buddy_id, buddy_res(255));

-- addition of domain field to jids table for easier removal of expired messages
alter table tig_ma_jids add `domain` varchar(1024);
update tig_ma_jids set `domain` = SUBSTR(jid, LOCATE('@', jid) + 1) where `domain` is null;
create index tig_ma_jids_domain_index on tig_ma_jids (`domain`(255));

-- additional index on tig_ma_msgs to improve removal of expired messages
create index tig_ma_msgs_ts_index on tig_ma_msgs (ts); 

-- additional performace optimizations
alter table tig_ma_jids add jid_sha1 char(40);
update tig_ma_jids set jid_sha1 = SHA1(jid) where jid_sha1 is null;
create unique index tig_ma_jids_jid_sha1 on tig_ma_jids (jid_sha1);

-- added unique constraint on tig_ma_msgs_tags
alter table add primary key (msgs_id, tag_id);

--fixing collation of tables
alter table tig_ma_jids collate utf8_general_ci;
alter table tig_ma_tags collate utf8_general_ci;
alter table tig_ma_msgs collate utf8_general_ci;