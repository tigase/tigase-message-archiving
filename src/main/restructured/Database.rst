Database
==========

.. _Preparationofdatabase:

Preparation of database
---------------------------------

Before you will be able to use Tigase Message Archiving Component and store messages in particular database you need to initialize this database. We provide few schemas for this component for MySQL, PostgreSQL, SQLServer and DerbyDB.

They are placed in ``database/`` directory of installation package and named in ``dbtype-message-archiving-version.sql``, where ``dbname`` in name of database type which this schema supports and ``version`` is version of a Message Archiving Component for which this schema is designed.

You need to manually select schema for correct database and component and load this schema to database. For more information about loading database schema look into `Database Preparation <#Database Preparation>`__ section of `Tigae XMPP Server Administration Guide <#Tigase XMPP Server Administration Guide>`__

Upgrade of database schema
----------------------------

Database schema for our components may change between versions and if so it needs to be updated before new version may be started. To upgrade schema please follow instructions from :ref:`Preparation of database<Preparationofdatabase>` section.

.. Note::

   If you use SNAPSHOT builds then schema may change for same version as this are versions we are still working on.

Schema description
-------------------

Tigase Message Archiving component uses few tables and stored procedures. To make it easier to find them on database level they are prefixed with ``tig_ma_``.

Table ``tig_ma_jids``
^^^^^^^^^^^^^^^^^^^^^^

This table stores all jids related to stored messages, ie. from ``to`` and ``from`` attributes of archived stanzas.

+----------+-----------------------------------+----------------------------------------------------------------------------+
| Field    | Description                       | Comments                                                                   |
+==========+===================================+============================================================================+
| jid_id   | Database ID of a JID              |                                                                            |
+----------+-----------------------------------+----------------------------------------------------------------------------+
| jid      | Value of a bare JID               |                                                                            |
+----------+-----------------------------------+----------------------------------------------------------------------------+
| jid_sha1 | SHA1 value of lowercased bare JID | Used for proper bare JID comparison during lookup.                         |
|          |                                   |                                                                            |
|          |                                   | (N/A to PostgreSQL schema)                                                 |
+----------+-----------------------------------+----------------------------------------------------------------------------+
| domain   | Domain part of a bare JID         | Stored for easier lookup of messages owned by users of a particular domain |
+----------+-----------------------------------+----------------------------------------------------------------------------+

Table ``tig_ma_msgs``
^^^^^^^^^^^^^^^^^^^^^^^^^^

Table stores archived messages.

+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| Field         | Description                                                           | Comments                                       |
+===============+=======================================================================+================================================+
| stable_id     | Database ID of a message                                              | Unique with matching ``owner_id``              |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| owner_id      | ID of a bare JID of a message owner                                   | References ``jid_id`` from ``tig_ma_jids``     |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| buddy_id      | ID of a bare JID of a message recipient/sender (different than owner) | References ``jid_id`` from ``tig_ma_jids``     |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| ts            | Timestamp of a message                                                | Timestamp of archivization or delayed delivery |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| body          | Body of a message                                                     |                                                |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| msg           | Serialized message                                                    |                                                |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| stanza_id     | ID attribute of archived message                                      |                                                |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| is_ref        | Marks if message is a reference to other message                      |                                                |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+
| ref_stable_id | ``stable_id`` of referenced message                                   |                                                |
+---------------+-----------------------------------------------------------------------+------------------------------------------------+


Table ``tig_ma_tags``
^^^^^^^^^^^^^^^^^^^^^^^^^

Table stores tags of archived messages. It stores one tag for many messages using ``tig_ma_msgs_tags`` to store relation between tag and a message.

+----------+---------------------------------+------------------------------------------------------------------------+
| Field    | Description                     | Comments                                                               |
+==========+=================================+========================================================================+
| tag_id   | Database ID of a tag            |                                                                        |
+----------+---------------------------------+------------------------------------------------------------------------+
| owner_id | ID of a bare JID of a tag owner | ID of bare JID of owner for which messages with this tag were archived |
+----------+---------------------------------+------------------------------------------------------------------------+
| tag      | Actual tag value                |                                                                        |
+----------+---------------------------------+------------------------------------------------------------------------+

Table ``tig_ma_msgs_tags``
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Table stores relations between tags and archived messages with this tags.

+---------------+---------------------------------+------------------------------------------------------------------------+
| Field         | Description                     | Comments                                                               |
+===============+=================================+========================================================================+
| msg_owner_id  | ID of a bare JID of a tag owner | ID of bare JID of owner for which messages with this tag were archived |
+---------------+---------------------------------+------------------------------------------------------------------------+
| msg_stable_id | Database ID of a message        | Unique with matching ``msg_owner_id``                                  |
+---------------+---------------------------------+------------------------------------------------------------------------+
| tag_id        | Database ID of a tag            | References ``tag_id`` from ``tig_ma_tags``                             |
+---------------+---------------------------------+------------------------------------------------------------------------+