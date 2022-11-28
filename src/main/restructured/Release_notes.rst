Tigase Message Archiving Release Notes
=======================================

Welcome to Tigase Message Archiving 3.0.0! This is a feature release for with a number of fixes and updates.

Tigase Message Archiving 3.1.0 Release Notes
---------------------------------------------

- Added support for mam2#extended; #mam-73
- Fixed issues with retrieval of archived messages stored in DerbyDB; #mam-73
- Adjust log levels; #server-1115
- Fixed issue with scheduling message retention cleanup; #mam-76
- Improve MAM logging; #servers-384
- Disabled storage of errors for sent PubSub notifications; #mam-77

Tigase Message Archiving 3.0.0 Release Notes
---------------------------------------------

Major Changes
^^^^^^^^^^^^^^

-  Add support for urn:xmpp:mam:2

-  Add support for `XEP-0308: Last Message Correction <https://xmpp.org/extensions/xep-0308.html>`__

All Changes
^^^^^^^^^^^^^^

-  `#mam-47 <https://projects.tigase.net/issue/mam-47>`__: Add support for urn:xmpp:mam:2

-  `#mam-49 <https://projects.tigase.net/issue/mam-49>`__: Historical message duplication

-  `#mam-50 <https://projects.tigase.net/issue/mam-50>`__: XEP-0308: Last Message Correction

-  `#mam-51 <https://projects.tigase.net/issue/mam-51>`__: Fix OMEMO encrypted messages are not stored by MA or MAM

-  `#mam-54 <https://projects.tigase.net/issue/mam-54>`__: Fix NPE in MAM/Message Archiving

-  `#mam-55 <https://projects.tigase.net/issue/mam-55>`__: Fix IllegalArgumentException in MessageArchiveVHostItemExtension

-  `#mam-56 <https://projects.tigase.net/issue/mam-56>`__: Fix upgrade-schema failes

-  `#mam-58 <https://projects.tigase.net/issue/mam-58>`__: Change message archiving rules

-  `#mam-60 <https://projects.tigase.net/issue/mam-60>`__: Fix Message carbons stored in MAM

-  `#mam-61 <https://projects.tigase.net/issue/mam-61>`__: Adjust schema to use new primary keys

-  `#mam-65 <https://projects.tigase.net/issue/mam-65>`__: Fix archiveMessage: Data truncation: Data too long for column ``_body``

-  `#mam-66 <https://projects.tigase.net/issue/mam-66>`__: Fix NPE in AbstractMAMProcessor.updatePrefrerences()

-  `#mam-67 <https://projects.tigase.net/issue/mam-67>`__: Fix Incorrect datetime value in JDBCMessageArchiveRepository

-  `#mam-68 <https://projects.tigase.net/issue/mam-68>`__: Add option to disable local MAM archive

-  `#mam-69 <https://projects.tigase.net/issue/mam-69>`__: Fix Data truncation: Data too long for column '_stanzaId'

-  `#mam-70 <https://projects.tigase.net/issue/mam-70>`__: Fix Schema is inconsistent (tigase.org mysql vs clean postgresql)

-  `#mam-72 <https://projects.tigase.net/issue/mam-72>`__: Fix Deadlock on inserting message

Previous Releases
-------------------

Tigase Message Archiving 2.x release
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Major changes
~~~~~~~~~~~~~~

Tigase Message Archiving component has undergone a few major changes to our code and structure. To continue to use Tigase Message Archiving component, a few changes may be needed to be made to your systems. Please see them below:

Database schema changes

We decided to no longer use *in-code* database upgrade to update database schema of Message Archiving component and rather provide separate schema files for every supported database.

Additionally we moved from *in-code* generated SQL statements to stored procedures which are now part of provided database schema files.

To continue usage of new versions of Message Archiving component it is required to manually load new component database schema, see :ref:`Preparation of database<Preparationofdatabase>` section for informations about that.

.. Warning::

    Loading of new database schema is required to use new version of Message Archiving component.

New features
~~~~~~~~~~~~~~

Support for Message Archive Management protocol
'''''''''''''''''''''''''''''''''''''''''''''''''

Now Tigase Message Archiving component supports searching of archived message using `XEP-0313: Message Archive Management <http://xmpp.org/extensions/xep-0313.html:>`__ protocol.

For details on how to enable this feature look into :ref:`Support for MAM<Support_for_MAM>`

Support for using separate database for different domains
''''''''''''''''''''''''''''''''''''''''''''''''''''''''''

Since this version it is possible to use separate archived messages based on domains. This allows you to configure component to store archived message for particular domain to different database.

For more informations please look into :ref:`Using seperate store for archived messages<Using_seperate_store_fora_archived_messages>`
