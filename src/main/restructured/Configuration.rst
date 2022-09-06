Configuration
===============

To enable Tigase Message Archiving Component you need to add following block to ``etc/config.tdsl`` file:

::

   message-archive () {
   }

It will enable component and configure it under name ``message-archive``. By default it will also use database configured as ``default`` data source to store data.

Custom Database
---------------------------

You can specify a custom database to be used for message archiving. To do this, define the archive-repo-uri property.

.. code:: text

   'message-archive' () {
       'archive-repo-uri' = 'jdbc:mysql://localhost/messagearchivedb?user=test&password=test'
   }

Here, ``messagearchivedb`` hosted on localhost is used.

XEP-0136 Support
------------------

To be able to use Message Archiving component with `XEP-0136: Message Archiving <http://xmpp.org/extensions/xep-0136.html:>`__ protocol, you additionally need to enable ``message-archive-xep-0136`` SessionManager processor:

::

   sess-man {
       message-archive-xep-0136 () {
       }
   }

This is required for some advanced options.

.. _Support_for_MAM:

Support for MAM
--------------------

If you want to use Message Archiving component with `XEP-0313: Message Archive Management <http://xmpp.org/extensions/xep-0313.html:>`__ protocol, then you need to enable ``urn:xmpp:mam:1`` SessionManager processor:

::

   sess-man {
       'urn:xmpp:mam:1' () {
       }
   }

.. _setting_default_value_of_archiving_level_for_message_on_a_server:

Setting default value of archiving level for message on a server
-----------------------------------------------------------------

Setting this property will change default archiving level for messages for every account on server for which per account default archiving level is not set. User will be able to change this value setting default modes as described in `XEP-0136 section 2.4 <http://xmpp.org/extensions/xep-0136.html#pref-default>`__

Possible values are:

**false**
   Messages are not archived

**body**
   Only message body will be stored. Message without a body will not be stored with this value set

**message**
   While message stanza will be archived (if message should be stored, see :ref:`Saving Options<SavingOptions>`)

**stream**
   In this mode every stanza should be archived. *(Not supported)*

To set default level to ``message`` you need to set ``default-store-method`` of ``message-archive`` processor to ``message``:

::

   sess-man {
       message-archive {
           default-store-method = 'message'
       }
   }


Setting required value of archiving level for messages on a server
--------------------------------------------------------------------------

Setting this property will change required archiving level for messages for every account on server. User will be able to change this to any lower value by setting default modes as described in `XEP-0136 section 2.4 <http://xmpp.org/extensions/xep-0136.html#pref-default>`__ but user will be allowed to set higher archiving level. If this property is set to higher value then default archiving level is set then this setting will be used as default archiving level setting.

Possible values for this setting are the same as values for default archiving level setting, see :ref:`Setting default value of archiving level for message on a server<setting_default_value_of_archiving_level_for_message_on_a_server>` for list of possible values.

To set required level to ``body`` you need to set ``required-store-method`` of ``message-archive`` processor to ``body``:

::

   sess-man {
       message-archive {
           required-store-method = 'body'
       }
   }

.. _Enabling_support_for_tags:

Enabling support for tags
--------------------------

To enable this feature Message Archiving component needs to be configured properly. You need to add ``tags-support = true`` line to ``message-archiving`` configuration section of ``etc/config.tdsl`` file. Like in following example:

::

   message-archive {
       tags-support = true
   }

where:

-  ``message-archive`` - is name of configuration section under which Message Archiving component is configured to run

.. _SavingOptions:

Saving Options
^^^^^^^^^^^^^^^^^

By default, Tigase Message Archive will only store the message body with some metadata, this can exclude messages that are lacking a body. If you decide you wish to save non-body elements within Message Archive, you can now can now configure this by setting ``msg-archive-paths`` to list of elements paths which should trigger saving to Message Archive. To additionally store messages with ``<subject/>`` element:

::

   sess-man {
       message-archive {
           msg-archive-paths = [ '-/message/result[urn:xmpp:mam:1]' '/message/body', '/message/subject' ]
       }
   }

Where above will set the archive to store messages with <body/> or <subject/> elements and for message with ``<result xmlns="urn:xmpp:mam:1"/>`` element not to be stored.

.. Warning::

    It is recommended to keep entry for not storing message with ``<result xmlns="urn:xmpp:mam:1"/>`` element as this are results of MAM query and contain messages already stored in archive!

.. Tip::

   Enabling this for elements such as iq, or presence will quickly load the archive. Configure this setting carefully!

.. _Configuration_of_automatic archiving of MUC messages:

Configuration of automatic archiving of MUC messages
------------------------------------------------------

As mentioned above no additional configuration options than default configuration of Message Archiving component and plugin is needed to let user decide if he wants to enable or disable this feature (but it is disabled by default). In this case user to enable this feature needs to set settings of message archiving adding ``muc-save`` attribute to ``<default/>`` element of request with value set to ``true`` (or to ``false`` to disable this feature).

To configure state of this feature on installation level, it is required to set ``store-muc-messages`` property of ``message-archive`` SessionManager processor:

::

   sess-man {
       message-archive {
           store-muc-messages = 'value'
       }
   }

where ``value`` may be one of following values:

``user``
   allows value to be set on domain level or by user if domain level setting allows that

``true``
   enables feature for every user in every hosted domain (cannot be overridden by on domain or user level)

``false``
   disables feature for every user in every hosted domain (cannot be overridden by on domain or user level)

To configure state of this feature on domain level, you need to execute vhost configuration command. In list of fields to configure domain, field to set this will be available with following values:

``user``
   allows user to stat of this feature (if allowed on installation level)

``true``
   enables feature for users of configured domain (user will not be able to disable)

``false``
   disables feature for users of configured domain (user will not be able to disable)


Purging Information from Message Archive
-----------------------------------------

This feature allows for automatic removal of entries older than a configured number of days from the Message Archive. It is designed to clean up database and keep its size within reasonable boundaries. If it is set to 1 day and entry is older than 24 hours then it will be removed, i.e. entry from yesterday from 10:11 will be removed after 10:11 after next execution of purge.

There are 3 settings available for this feature: To enable the feature:

.. code:: text

   'message-archive' {
       'remove-expired-messages' = true
   }

This setting changes the initial delay after the server is started to begin removing old entries. In other words, MA purging will not take place until the specified time after the server starts. Default setting is PT1H, or one hour.

.. code:: text

       'remove-expired-messages-delay' = 'PT2H'

This setting sets how long MA purging will wait between passes to check for and remove old entries. Default setting is P1D which is once a day.

.. code:: text

       'remove-expired-messages-period' = 'PT2D'

You may use all settings at once if you wish.

**NOTE** that these commands are also compatible with ``unified-archive`` component, just replace ``message`` with ``unified``.


Configuration of number of days in VHost
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

VHost holds a setting that determines how long a message needs to be in archive for it to be considered old and removed. This can be set independently per Vhost. This setting can be modified by either using the HTTP admin, or the update item execution in adhoc command.

This configuration is done by execution of Update item configuration adhoc command of vhost-man component, where you should select domain for which messages should be removed and then in field XEP-0136 - retention type select value Number of days and in field XEP-0136 - retention period (in days) enter number of days after which events should be removed from MA.

In adhoc select domain for which messages should be removed and then in field XEP-0136 - retention type select value Number of days and in field XEP-0136 - retention period (in days) enter number of days after which events should be removed from MA.

In HTTP UI select Other, then Update Item Configuration (Vhost-man), select the domain, and from there you can set XEP-0136 retention type, and set number of days at XEP-0136 retention period (in days).

.. _Using_seperate_store_fora_archived_messages:

Using separate store for archived messages
-----------------------------------------------

It is possible to use separate store for archived messages, to do so you need to configure new ``DataSource`` in ``dataSource`` section. Here we will use ``message-archive-store`` as a name of a data source. Additionally you need to pass name of newly configured data source to ``dataSourceName`` property of ``default`` repository of Message Archiving component.

Example:

::

   dataSource {
       message-archive-store () {
           uri = 'jdbc:postgresql://server/message-archive-database'
       }
   }

   message-archive {
       repositoryPool {
           default () {
               dataSourceName = 'message-archive-store'
           }
       }
   }

It is also possible to configure separate store for particular domain, i.e. ``example.com``. Here we will configure data source with name ``example.com`` and use it to store data for archive:

::

   dataSource {
       'example.com' () {
           uri = 'jdbc:postgresql://server/example-database'
       }
   }

   message-archive {
       repositoryPool {
           'example.com' () {
             # we may not set dataSourceName as it matches name of domain
           }
       }
   }

.. Note::

   With this configuration messages for other domains than ``example.com`` will be stored in default data source.

Setting Pool Sizes
-------------------

There are a high number of prepared statements which are used to process and archive messages as they go through the server, and you may experience an increase in resource use with the archive turned on. It is recommended to decrease the repository connection pool to help balance server load from this component using the `Pool Size <dataRepoPoolSize>`__ property:

.. code:: text

   'message-archive' (class: tigase.archive.MessageArchiveComponent) {
       'archive-repo-uri' = 'jdbc:mysql://localhost/messagearchivedb?user=test&password=test'
       'pool-size' = 15
   }

Message Tagging Support
-----------------------------

Tigase now is able to support querying message archives based on tags created for the query. Currently, Tigase can support the following tags to help search through message archives: - ``hashtag`` Words prefixed by a hash (#) are stored with a prefix and used as a tag, for example #Tigase - ``mention`` Words prefixed by an at (@) are stored with a prefix and used as a tag, for example @Tigase

**NOTE:** Tags must be written in messages from users, they do not act as wildcards. To search for #Tigase, a message must have #Tigase in the <body> element.

This feature allows users to query and retrieve messages or collections from the archive that only contain one or more tags.

Activating Tagging
^^^^^^^^^^^^^^^^^^^^

To enable this feature, the following line must be in the config.tdsl file (or may be added with Admin or Web UI)

.. code:: text

   'message-archive' (class: tigase.archive.MessageArchiveComponent) {
       'tags-support' = true
   }

Usage
^^^^^^^^^

To execute a request, the tags must be individual children elements of the ``retrieve`` or ``list`` element like the following request:

.. code:: xml

   <query xmlns="http://tigase.org/protocol/archive#query">
       <tag>#People</tag>
       <tag>@User1</tag>
   </query>

You may also specify specific senders, and limit the time and date that you wish to search through to keep the resulting list smaller. That can be accomplished by adding more fields to the retrieve element such as ``'with'``, ``'from', and 'end'`` . Take a look at the below example:

.. code:: xml

   <iq type="get" id="query2">
       <retrieve xmlns='urn:xmpp:archive'
           with='juliet@capulet.com'
           from='2014-01-01T00:00:00Z'
           end='2014-05-01T00:00:00Z'>
             <query xmlns="http://tigase.org/protocol/archive#query">
                 <tag>#People</tag>
                 <tag>@User1</tag>
             </query>
       </retrieve>
   </iq>

This stanza is requesting to retrieve messages tagged with @User1 and #people from chats with the user juliet@capulet.com between January 1st, 2014 at 00:00 to May 1st, 2014 at 00:00.

**NOTE:** All times are in Zulu or GMT on a 24h clock.

You can add as many tags as you wish, but each one is an **AND** statement; so the more tags you include, the smaller the results.