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
package tigase.archive.xep0313;

import tigase.archive.MessageArchiveComponent;
import tigase.component.exceptions.ComponentException;
import tigase.component.exceptions.RepositoryException;
import tigase.kernel.beans.Bean;
import tigase.xmpp.mam.MAMRepository;
import tigase.xmpp.mam.Query;
import tigase.xmpp.mam.modules.QueryModule;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * Created by andrzej on 29.12.2016.
 */
@Bean(name = "mamQueryModule", parent = MessageArchiveComponent.class, active = true)
public class MAMQueryModule
		extends QueryModule {

	@Override
	public String[] getFeatures() {
		return Stream.concat(Arrays.stream(super.getFeatures()), Stream.of("tigase:mamfc:0")).toArray(String[]::new);
	}

	@Override
	protected void queryItems(Query query, MAMRepository.ItemHandler itemHandler)
			throws RepositoryException, ComponentException {
		super.queryItems(query, itemHandler);
	}
}
