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