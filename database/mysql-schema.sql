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
	jid_id  bigint unsigned NOT NULL auto_increment,
	jid varchar(2049),
	primary key (jid_id)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- QUERY START:
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
-- QUERY END:

-- QUERY START:
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
-- QUERY END:

-- QUERY START:
create table if not exists tig_ma_msgs_tags (
	msg_id bigint unsigned NOT NULL,
	tag_id bigint unsigned NOT NULL,
	
	primary key (msg_id, tag_id),
	foreign key (msg_id) references tig_ma_msgs (msg_id) on delete cascade,
	foreign key (tag_id) references tig_ma_tags (tag_id) on delete cascade,
	key tig_ma_tags_msg_id (msg_id),
	key tig_ma_tags_tag_id (tag_id)
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;
-- QUERY END:

-- additional changes introduced later - after original schema clarified
-- QUERY START:
drop procedure if exists TigExecuteIfNot;
-- QUERY END:
-- QUERY START:
drop procedure if exists TigAddColumnIfNotExists;
-- QUERY END:

-- QUERY START:
drop procedure if exists TigAddIndexIfNotExists;
-- QUERY END:

-- QUERY START:
delimiter //
-- QUERY END:

-- QUERY START:
create procedure TigExecuteIfNot(cond int, query text)
begin
set @s = (select if (
	cond > 0,
'select 1',
query
));

prepare stmt from @s;
execute stmt;
deallocate prepare stmt;
end //
-- QUERY END:

-- QUERY START:
create procedure TigAddColumnIfNotExists(tab varchar(255), col varchar(255), def varchar(255))
begin
call TigExecuteIfNot((select count(1) from information_schema.COLUMNS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tab AND COLUMN_NAME = col), 
	CONCAT('alter table ', tab, ' add `', col, '` ', def)
);
end //
-- QUERY END:

-- QUERY START:
create procedure TigAddIndexIfNotExists(tab varchar(255), ix_name varchar(255), uni smallint, def varchar(255))
begin
call TigExecuteIfNot((select count(1) from information_schema.STATISTICS where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tab and INDEX_NAME = ix_name), 
	CONCAT('create ', IF(uni=1, 'unique', ''), ' ', ix_name, ' on ', tab, ' ', def)
);
end //
-- QUERY END:

-- QUERY START:
delimiter ;
-- QUERY END:

-- addition of buddy_res field which should contain resource of buddy
-- QUERY START:
call TigAddColumnIfNotExists('tig_ma_msgs',  'buddy_res', 'varchar(1024)');
-- QUERY END:
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_msgs', 'tig_ma_msgs_owner_id_buddy_id_buddy_res_index', 0, '(owner_id, buddy_id, buddy_res(255))');
-- QUERY END:

-- addition of domain field to jids table for easier removal of expired messages
-- QUERY START:
call TigAddColumnIfNotExists('tig_ma_jids', 'domain', 'varchar(1024)');
-- QUERY END:
-- QUERY START:
update tig_ma_jids set `domain` = SUBSTR(jid, LOCATE('@', jid) + 1) where `domain` is null;
-- QUERY END:
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_jids', 'tig_ma_jids_domain_index', 0, '(`domain`(255))');
-- QUERY END:

-- additional index on tig_ma_msgs to improve removal of expired messages
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_msgs', 'tig_ma_msgs_ts_index', 0, '(ts)'); 
-- QUERY END:

-- additional performace optimizations
-- QUERY START:
call TigAddColumnIfNotExists('tig_ma_jids', 'jid_sha1', 'char(40)');
-- QUERY END:
-- QUERY START:
update tig_ma_jids set jid_sha1 = SHA1(jid) where jid_sha1 is null;
-- QUERY END:
-- QUERY START:
call TigAddIndexIfNotExists('tig_ma_jids', 'tig_ma_jids_jid_sha1', 1, '(jid_sha1)');
-- QUERY END:

-- added unique constraint on tig_ma_msgs_tags
-- QUERY START:
call TigExecuteIfNot((select count(1) from information_schema.KEY_COLUMN_USAGE where TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tig_ma_msgs_tags' AND CONSTRAINT_NAME = 'PRIMARY'), 
'alter table tig_ma_msgs_tags add primary key (msg_id, tag_id)');
-- QUERY END:

--fixing collation of tables
-- QUERY START:
alter table tig_ma_jids collate utf8_general_ci;
-- QUERY END:
-- QUERY START:
alter table tig_ma_tags collate utf8_general_ci;
-- QUERY END:
-- QUERY START:
alter table tig_ma_msgs collate utf8_general_ci;
-- QUERY END: