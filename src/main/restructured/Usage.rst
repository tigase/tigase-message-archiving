Usage
=======

Now that we have the archive component running, how do we use it? Currently, the only way to activate and modify the component is through XMPP stanzas. Lets first begin by getting our default settings from the component:

.. code:: xml

   <iq type='get' id='prefq'>
     <pref xmlns='urn:xmpp:archive'/>
   </iq>

It’s a short stanza, but it will tell us what we need to know, Note that you do not need a from or a to for this stanza. The result is as follows:

.. code:: xml

   <iq type='result' id='prefq' to='admin@domain.com/cpu'>
   <pref xmlns='urn:xmpp:archive'>
   <auto save='false'/>
   <default otr='forbid' muc-save="false" save="body"/>
   <method use="prefer" type="auto"/>
   <method use="prefer" type="local"/>
   <method use="prefer" type="manual"/>
   </prefq>
   </iq>

See below for what these settings mean.

XEP-0136 Field Values
-----------------------

<**auto**/>
   -  **Required Attributes**

      -  ``save=`` Boolean turning archiving on or off

   -  **Optional Settings**

      -  ``scope=`` Determines scope of archiving, default is ``'stream'`` which turns off after stream end, or may be ``'global'`` which keeps auto save permanent,

<**default**/>
   Default element sets default settings for OTR and save modes, includes an option for archive expiration.

   -  **Required Attribures**

      -  ``otr=`` Specifies setting for Off The Record mode. Available settings are:

         -  ``approve`` The user MUST explicitly approve OTR communication.

         -  ``concede`` Communications MAY be OTR if requested by another user.

         -  ``forbid`` Communications MUST NOT be OTR.

         -  ``oppose`` Communications SHOULD NOT be OTR.

         -  ``prefer`` Communications SHOULD be OTR.

         -  ``require`` Communications MUST be OTR.

      -  ``save=`` Specifies the portion of messages to archive, by default it is set to ``body``.

         -  ``body`` Archives only the items within the <body/> elements.

         -  ``message`` Archive the entire XML content of each message.

         -  ``stream`` Archive saves every byte of communication between server and client. (Not recommended, high resource use)

   -  **Optional Settings**

      -  ``expire=`` Specifies after how many seconds should the server delete saved messages.

<**item**/>
   The Item element specifies settings for a particular entity. These settings will override default settings for the specified JIDS.

   -  **Required Attributes**

      -  ``JID=`` The Jabber ID of the entity that you wish to put these settings on, it may be a full JID, bare JID, or just a domain.

      -  ``otr=`` Specifies setting for Off The Record mode. Available settings are:

         -  ``approve`` The user MUST explicitly approve OTR communication.

         -  ``concede`` Communications MAY be OTR if requested by another user.

         -  ``forbid`` Communications MUST NOT be OTR.

         -  ``oppose`` Communications SHOULD NOT be OTR.

         -  ``prefer`` Communications SHOULD be OTR.

         -  ``require`` Communications MUST be OTR.

      -  ``save=`` Specifies the portion of messages to archive, by default it is set to ``body``.

         -  ``body`` Archives only the items within the <body/> elements.

         -  ``message`` Archive the entire XML content of each message.

         -  ``stream`` Archive saves every byte of communication between server and client. (Not recommended, high resource use)

   -  **Optional Settings**

      -  ``expire=`` Specifies after how many seconds should the server delete saved messages.

<**method**/>
   This element specifies the user preference for available archiving methods.

   -  **Required Attributes**

      -  ``type=`` The type of archiving to set

         -  ``auto`` Preferences for use of automatic archiving on the user’s server.

         -  ``local`` Set to use local archiving on user’s machine or device.

         -  ``manual`` Preferences for use of manual archiving to the server.

      -  ``use=`` Sets level of use for the type

         -  ``prefer`` The selected method should be used if it is available.

         -  ``concede`` This will be used if no other methods are available.

         -  ``forbid`` The associated method MUST not be used.

Now that we have established settings, lets send a stanza changing a few of them:

.. code:: xml

   <iq type='set' id='pref2'>
     <pref xmlns='urn:xmpp:archive'>
       <auto save='true' scope='global'/>
       <item jid='domain.com' otr='forbid' save='body'/>
       <method type='auto' use='prefer'/>
       <method type='local' use='forbid'/>
       <method type='manual' use='concede'/>
     </pref>
   </iq>

This now sets archiving by default for all users on the domain.com server, forbids OTR, and prefers auto save method for archiving.

Manual Activation
----------------------

Turning on archiving requires a simple stanza which will turn on archiving for the use sending the stanza and using default settings.

.. code:: xml

   <iq type='set' id='turnon'>
     <pref xmlns='urn:xmpp:archive'>
       <auto save='true'/>
     </pref>
   </iq>

A sucessful result will yield this response from the server:

.. code:: xml

   <iq type='result' to='user@domain.com' id='turnon'/>

Once this is turned on, incoming and outgoing messages from the user will be stored in ``tig_ma_msgs`` table in the database.


