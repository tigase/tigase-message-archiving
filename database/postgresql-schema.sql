/* 
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 * Author:  andrzej
 * Created: 2016-01-18
 */

create table tig_ma_jids (
	jid_id bigserial,
	jid varchar(2049),
	
	primary key(jid_id)
);

create unique index tig_ma_jids_jid on tig_ma_jids (jid);

create table tig_ma_msgs (
	msg_id bigserial,
	owner_id bigint not null,
	buddy_id bigint not null,
	ts timestamp,
	direction smallint,
	"type" varchar(20),
	body text,
	msg text,
	"hash" varchar(50),

	primary key (msg_id),
	foreign key (buddy_id) references tig_ma_jids (jid_id),
	foreign key (owner_id) references tig_ma_jisd (jid_id)
);

create index tig_ma_msgs_owner_id_index on tig_ma_msgs (owner_id);
create index tig_ma_msgs_owner_id_buddy_id_index on tig_ma_msgs (owner_id, buddy_id);
create index tig_ma_msgs_owner_id_buddy_id_ts_index on tig_ma_msgs (owner_id, buddy_id, ts);
create unique index tig_ma_msgs_owner_id_ts_buddy_id_hash_index on tig_ma_msgs (owner_id, ts, buddy_id, "hash");

create table tig_ma_tags (
	tag_id bigserial,
	owner_id bigint not null,
	tag varchar(255),

	primary key (tag_id),
	foreign key (owner_id) references tig_ma_jids (jid_id) on delete cascade
);

create index tig_ma_tags_owner_id on tig_ma_tags (owner_id);
create unique index tig_ma_tags_tag_owner_id on tig_ma_tags (owner_id, tag);

create table tig_ma_msgs_tags (
	msg_id bigint not null,
	tag_id bigint not null,
	
	foreign key (msg_id) references tig_ma_msgs (msg_id) on delete cascade,
	foreign key (tag_id) references tig_ma_tags (tag_id) on delete cascade
);

create index tig_ma_tags_msg_id on tig_ma_msgs_tags (msg_id);
create index tig_ma_tags_tag_id on tig_ma_msgs_tags (tag_id);

-- additional changes introduced later - after original schema clarified

-- addition of buddy_res field which should contain resource of buddy
alter table tig_ma_msgs add buddy_res varchar(1024);
create index tig_ma_msgs_owner_id_buddy_id_buddy_res_index on tig_ma_msgs (owner_id, buddy_id, buddy_res);

-- addition of domain field to jids table for easier removal of expired messages
alter table tig_ma_jids add "domain" varchar(1024);
update tig_ma_jids set "domain" = substr(jid, strpos(jid, '@') + 1) where "domain" is null;
create index tig_ma_jids_domain_index on tig_ma_jids ("domain");

-- additional index on tig_ma_msgs to improve removal of expired messages
create index tig_ma_msgs_ts_index on tig_ma_msgs (ts); 