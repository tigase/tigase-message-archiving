/*
 * Tigase Message Archiving Component - Implementation of Message Archiving component for Tigase XMPP Server.
 * Copyright (C) 2012 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */
package tigase.archive.db;

import tigase.db.util.importexport.RepositoryManagerExtensionBase;
import tigase.server.Message;
import tigase.util.ui.console.CommandlineParameter;
import tigase.xml.Element;
import tigase.xmpp.jid.BareJID;
import tigase.xmpp.jid.JID;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.util.MAMRepositoryManagerExtensionHelper;

import java.io.Writer;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static tigase.db.util.importexport.Exporter.EXPORT_MAM_SINCE;
import static tigase.db.util.importexport.RepositoryManager.isSet;

public class MAMRepositoryManagerExtension extends RepositoryManagerExtensionBase {

	private static final Logger log = Logger.getLogger(MAMRepositoryManagerExtension.class.getSimpleName());

	private final CommandlineParameter NO_USER_MAM = new CommandlineParameter.Builder(null, "exclude-user-mam").description("Exclude users MAM archives").type(
			Boolean.class).defaultValue("false").requireArguments(false).build();

	@Override
	public Stream<CommandlineParameter> getExportParameters() {
		return Stream.concat(super.getExportParameters(), Stream.of(NO_USER_MAM, EXPORT_MAM_SINCE));
	}

	@Override
	public Stream<CommandlineParameter> getImportParameters() {
		return Stream.concat(super.getImportParameters(), Stream.of(NO_USER_MAM, EXPORT_MAM_SINCE));
	}

	@Override
	public void exportDomainData(String domain, Writer writer) throws Exception {
		
	}

	@Override
	public void exportUserData(Path userDirPath, BareJID user, Writer writer) throws Exception {
		if (!isSet(NO_USER_MAM)) {
			log.info("exporting user " + user + " MAM archive...");
			MAMRepository mamRepository = getRepository(AbstractMessageArchiveRepository.class, user.getDomain());
			exportInclude(writer, userDirPath.resolve("archive.xml"), archiveWriter -> {
				MAMRepositoryManagerExtensionHelper.exportDataFromRepository(mamRepository, user, user, archiveWriter);
			});
		}
	}

	@Override
	public tigase.db.util.importexport.ImporterExtension startImportUserData(BareJID user, String name,
																			 Map<String, String> attrs) throws Exception {
		if (!"archive".equals(name)) {
			return null;
		}
		if (!"urn:xmpp:pie:0#mam".equals(attrs.get("xmlns"))) {
			return null;
		}
		MessageArchiveRepository mamRepository = getRepository(AbstractMessageArchiveRepository.class, user.getDomain());
		return new ImporterExtension(mamRepository, user, isSet(NO_USER_MAM));
	}
	
	private static class ImporterExtension extends MAMRepositoryManagerExtensionHelper.AbstractImporterExtension {
		private final MessageArchiveRepository mamRepository;
		private final boolean skipImport;
		private final BareJID user;

		private ImporterExtension(MessageArchiveRepository mamRepository, BareJID user, boolean skipImport) {
			this.mamRepository = mamRepository;
			this.user = user;
			this.skipImport = skipImport;
			if (!skipImport) {
				log.info("importing user " + user + " MAM archive...");
			}
		}

		@Override
		protected boolean handleMessage(Message message, String stableId, Date timestamp, Element source) {
			if (!skipImport) {
				JID buddy = null;
				if (message.getStanzaFrom() != null && !user.equals(message.getStanzaFrom().getBareJID())) {
					buddy = message.getStanzaFrom();
				}
				if (buddy == null && message.getStanzaTo() != null) {
					buddy = message.getStanzaTo();
				}

				mamRepository.archiveMessage(user, buddy, timestamp, message.getElement(), stableId, null);
			}
			return true;
		}

		@Override
		public void close() throws Exception {
			if (!skipImport) {
				log.finest("finished import of MAM archive for " + user + ".");
			}
			super.close();
		}
	}
}
