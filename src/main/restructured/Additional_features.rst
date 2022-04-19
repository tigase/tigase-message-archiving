Additional features
=====================

Tigase Message Archiving Component contains few additional features useful for working with message archives.

Querying in all messages
-------------------------

Feature allows user to search all of his archived messages without a need to specify who was send/receiver of this message. To search in all messages, request sent to retrieve archived messages should not contain ``with`` attribute. As a result, when ``with`` attribute is omitted ``<chat/>`` element of response will not contain ``with`` attribute but every ``<to/>`` and ``<from/>`` element will contain ``with`` attribute.

Querying by part of message body
------------------------------------------

This feature allows user to query for messages or collections containing messages which in body of a message contained text passed by user.

To execute request in which user wants to find messages with "test failed" string XMPP client needs to add following element

.. code:: xml

   <query xmlns="http://tigase.org/protocol/archive#query">
       <contains>test failed</contains>
   </query>

as child element of @retrieve@ or @list@ element of request.

Example query requests
^^^^^^^^^^^^^^^^^^^^^^^

Example 1
~~~~~~~~~~~~~

Retrieving messages with "test failed" string with user ``juliet@capulet.com`` between ``2014-01-01 00:00:00`` and ``2014-05-01 00:00:00``

.. code:: xml

   <iq type="get" id="query2">
       <retrieve xmlns='urn:xmpp:archive'
           with='juliet@capulet.com'
           from='2014-01-01T00:00:00Z'
           end='2014-05-01T00:00:00Z'>
             <query xmlns="http://tigase.org/protocol/archive#query">
                 <contains>test failed</contains>
             </query>
       </retrieve>
   </iq>


Example 2
~~~~~~~~~~~

Retrieving collections containing messages with "test failed" string with user ``juliet@capulet.com`` between ``2014-01-01 00:00:00`` and ``2014-05-01 00:00:00``

.. code:: xml

   <iq type="get" id="query2">
       <list xmlns='urn:xmpp:archive'
           with='juliet@capulet.com'
           from='2014-01-01T00:00:00Z'
           end='2014-05-01T00:00:00Z'>
             <query xmlns="http://tigase.org/protocol/archive#query">
                 <contains>test failed</contains>
             </query>
       </list>
   </iq>


Querying by tags
------------------

This feature adds support for tagging messages archived by Message Archiving component and by default is disabled (to learn how to enable this feature please see `??? <#Enabling support for tags>`__ section).

Tagging can be done only by user sending message as to tag message tag needs to be included in message content (message body to be exact).

Currently there are 2 types of tags supported:

-  ``hashtag`` - words prefixed by "hash" (#) are stored with prefix and used as tag, i.e. ``#Tigase``

-  ``mention`` - words prefixed by "at" (@) are stored with prefix and used as tag, i.e. ``@Tigase``

Custom feature allows user to query/retrieve messages or collections from archive only containing particular tag or tags. To execute request in which user wants to retrieve only messages tagged with ``@User1`` and ``#People`` XMPP client executing request needs to add following element as child element of ``<retrieve/>`` element or ``<list/>`` element:

.. code:: xml

   <query xmlns="http://tigase.org/protocol/archive#query">
       <tag>#People</tag>
       <tag>@User1</tag>
   </query>

Querying/retrieving list of messages or collections
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Example 1
~~~~~~~~~~~~~

Request to retrieve messages tagged with ``@User1`` and ``#People`` from chat with user ``juliet@capulet.com`` between ``2014-01-01 00:00:00`` and ``2014-05-01 00:00:00``

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

Example 2:
~~~~~~~~~~~~

Request to retrieve collections containing messages tagged with ``@User1`` and ``#People`` from chat with user ``juliet@capulet.com`` between ``2014-01-01 00:00:00`` and ``2014-05-01 00:00:00``

.. code:: xml

   <iq type="get" id="query2">
       <list xmlns='urn:xmpp:archive'
           with='juliet@capulet.com'
           from='2014-01-01T00:00:00Z'
           end='2014-05-01T00:00:00Z'>
             <query xmlns="http://tigase.org/protocol/archive#query">
                 <tag>#People</tag>
                 <tag>@User1</tag>
             </query>
       </list>
   </iq>


Retrieving list of tags used by user starting with some text
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

To search for hashtags or user names already used following request might be used:

.. code:: xml

   <iq type="set" id="query2">
       <tags xmlns="http://tigase.org/protocol/archive#query" like="#test"/>
   </iq>

which will return suggested similar hashtags which where found in database for particular user if following response:

.. code:: xml

   <iq type="result" id="query2">
       <tags xmlns="http://tigase.org/protocol/archive#query" like="#test">
           <tag>#test1</tag>
           <tag>#test123</tag>
           <set xmlns="http://jabber.org/protocol/rsm">
                <first index='0'>0</first>
                <last>1</last>
                <count>2</count>
           </set>
       </tags>
   </iq>


Automatic archiving of MUC messages
------------------------------------

If this feature is enabled MUC messages are stored in Message Archiving repository and are added in same way as for any other messages and ``jid`` of MUC room is used as ``jid`` of message sender, so if MUC message sent from ``test@muc.example.com`` was stored then to retrieve this messages ``test@muc.example.com`` needs to be passed as ``with`` attribute to message retrieve request. Retrieved MUC messages will be retrieved in same format as normal message with one exception - each message will contain ``name`` attribute with name of occupant in room which sent this message.

This feature is by default disabled but it is possible to enable it for particular user. Additionally it is possible to change default setting on installation level and on hosted domain level to enable this feature, disable feature or allow user to decide if user want this feature to be enabled. For more information about configuration of this feature look at `??? <#Configuration of automatic archiving of MUC messages>`__

.. Note::

   -  It is worth to mention that even if more than on user resource joined same room and each resource will receive same messages then only single message will be stored in Message Archving repository.

   -  It is also important to note that MUC messages are archived to user message archive only when user is joined to MUC room (so if message was sent to room but it was not sent to particular user)
