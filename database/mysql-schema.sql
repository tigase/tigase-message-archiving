/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  andrzej
 * Created: 2016-01-18
 */

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
	`hash` varchar(50),

	primary key (msg_id),
	foreign key (buddy_id) references tig_ma_jids (jid_id),
	foreign key (owner_id) references tig_ma_jids (jid_id),
	key tig_ma_msgs_owner_id (owner_id),
	key tig_ma_msgs_owner_id_buddy_id (owner_id, buddy_id),
	key tig_ma_msgs_owner_id_ts_buddy_id (owner_id, ts, buddy_id),
	unique index using hash (owner_id, ts, buddy_id, `hash`)
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
	
	foreign key (msg_id) references tig_ma_msgs (msg_id) on delete cascade,
	foreign key (tag_id) references tig_ma_tags (tag_id) on delete cascade,
	key index tig_ma_tags_msg_id on tig_ma_msgs_tags (msg_id);
	key tig_ma_tags_tag_id on tig_ma_msgs_tags (tag_id);
)
ENGINE=InnoDB default character set utf8 ROW_FORMAT=DYNAMIC;

-- additional changes introduced later - after original schema clarified

-- addition of buddy_res field which should contain resource of buddy
alter table tig_ma_msgs add buddy_res varchar(1024);
create index tig_ma_msgs_owner_id_buddy_id_buddy_res_index on tig_ma_msgs (owner_id, buddy_id, buddy_res(255));

-- addition of domain field to jids table for easier removal of expired messages
alter table tig_ma_jids add `domain` varchar(1024);
update tig_ma_jids set `domain` = SUBSTR(jid, LOCATE('@', jid) + 1) where `domain`ą is null;
create index tig_ma_jids_domain_index on tig_ma_jids (`domain`(255));

-- additional index on tig_ma_msgs to improve removal of expired messages
create index tig_ma_msgs_ts_index on tig_ma_msgs (ts); 

-- additional performace optimizations
alter table tig_ma_jids add jid_sha1 char(40);
update tig_ma_jids set jid_sha1 = SHA1(jid) where jid_sha1 is null;
create unique index tig_ma_jids_jids_sha1 on tig_ma_jids (jid_sha1);