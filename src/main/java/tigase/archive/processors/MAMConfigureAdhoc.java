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
package tigase.archive.processors;

import tigase.archive.MessageArchiveVHostItemExtension;
import tigase.component.adhoc.AdHocCommand;
import tigase.component.adhoc.AdHocCommandException;
import tigase.component.adhoc.AdHocResponse;
import tigase.component.adhoc.AdhHocRequest;
import tigase.db.TigaseDBException;
import tigase.db.UserRepository;
import tigase.form.Field;
import tigase.form.Form;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.vhosts.VHostItem;
import tigase.vhosts.VHostManagerIfc;
import tigase.xml.Element;
import tigase.xmpp.Authorization;
import tigase.xmpp.jid.JID;

import java.util.logging.Level;
import java.util.logging.Logger;

import static tigase.archive.processors.MAMConfigureAdhoc.XMLNS;

@Bean(name = XMLNS, parent = MAM2Processor.class, active = true)
public class MAMConfigureAdhoc implements AdHocCommand {

	protected static final String XMLNS = "urn:xmpp:mam#configure";

	private static final Logger log = Logger.getLogger(MAMConfigureAdhoc.class.getCanonicalName());

	@Inject
	private UserRepository userRepository;
	@Inject
	private VHostManagerIfc vHostManagerIfc;

	public void execute(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			Element data = request.getCommand().getChild("x", "jabber:x:data");
			if (request.isAction("cancel")) {
				response.cancelSession();
			} else if (data == null) {
				response.getElements().add(this.prepareForm(request, response).getElement());
				response.startSession();
			} else {
				Form form = new Form(data);
				if (form.isType("submit")) {
					Form responseForm = this.submitForm(request, response, form);
					if (responseForm != null) {
						response.getElements().add(responseForm.getElement());
					}
				}
			}

		} catch (AdHocCommandException var6) {
			AdHocCommandException ex = var6;
			throw ex;
		} catch (Exception var7) {
			Exception e = var7;
			log.log(Level.FINE, "Exception during execution of adhoc command " + this.getNode(), e);
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, e.getMessage());
		}
	}

	@Override
	public String getName() {
		return "MAM Advanced Configuration";
	}

	@Override
	public String getNode() {
		return "urn:xmpp:mam#configure";
	}

	@Override
	public boolean isAllowedFor(JID jid) {
		return false;
	}

	@Override
	public boolean isAllowedFor(JID from, JID to) {
		return to == null || from.getBareJID().equals(to.getBareJID());
	}

	@Override
	public boolean isForSelf() {
		return true;
	}

	protected Form prepareForm(AdhHocRequest request, AdHocResponse response) throws AdHocCommandException {
		try {
			Form form = new Form("form", "Configure message archiving",
								 "Fill out and submit this form to configure message archiving");
			VHostItem vHost = vHostManagerIfc.getVHostItem(request.getSender().getDomain());
			MessageArchiveVHostItemExtension extension = vHost.getExtension(MessageArchiveVHostItemExtension.class);
			String retentionDays = userRepository.getData(request.getSender().getBareJID(), MessageArchivePlugin.ID,
														  "retention");
			switch(extension.getRetentionType()) {
				case unlimited -> {
					Field field = Field.fieldFixed("");
					field.setVar("retentionInDays");
					field.setLabel("Retention in days");
					form.addField(field);
				}
				case numberOfDays -> {
					Field field = Field.fieldFixed("" + extension.getRetentionDays());
					field.setVar("retentionInDays");
					field.setLabel("Retention in days");
					form.addField(field);
				}
				case userDefined -> {
					form.addField(Field.fieldTextSingle("retentionInDays", retentionDays, "Retention in days"));
				}
			}

			return form;
		} catch (TigaseDBException ex) {
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
	}

	protected Form submitForm(AdhHocRequest request, AdHocResponse response, Form form) throws AdHocCommandException {
		VHostItem vHost = vHostManagerIfc.getVHostItem(request.getSender().getDomain());
		MessageArchiveVHostItemExtension extension = vHost.getExtension(MessageArchiveVHostItemExtension.class);
		Integer retentionDays = form.getAsInteger("retentionInDays");
		try {
			switch (extension.getRetentionType()) {
				case unlimited -> {
					if (retentionDays != null) {
						throw new AdHocCommandException(Authorization.POLICY_VIOLATION,
														"Unlimited retention is required for this domain.");
					}
				} case numberOfDays -> {
					if (retentionDays != null) {
						throw new AdHocCommandException(Authorization.POLICY_VIOLATION,
														"Retention time is enforced for this domain.");
					}
				} case userDefined -> {
					if (retentionDays != null) {
						userRepository.setData(request.getSender().getBareJID(), MessageArchivePlugin.ID, "retention",
											   "" + retentionDays);
					} else {
						userRepository.removeData(request.getSender().getBareJID(), MessageArchivePlugin.ID,
												  "retention");
					}
					form.addField(Field.fieldTextSingle("retentionInDays", "" + retentionDays, "Retention in days"));
				}
			}
		} catch (TigaseDBException ex) {
			throw new AdHocCommandException(Authorization.INTERNAL_SERVER_ERROR, ex.getMessage());
		}
		return null;
	}

	protected String assertNotEmpty(String input, String message) throws AdHocCommandException {
		if (input != null && !input.isBlank()) {
			return input.trim();
		} else {
			throw new AdHocCommandException(Authorization.BAD_REQUEST, message);
		}
	}

}
