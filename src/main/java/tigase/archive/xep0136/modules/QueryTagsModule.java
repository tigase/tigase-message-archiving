/**
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
package tigase.archive.xep0136.modules;

import tigase.archive.MessageArchiveComponent;
import tigase.archive.QueryCriteria;
import tigase.archive.modules.AbstractModule;
import tigase.component.exceptions.ComponentException;
import tigase.criteria.Criteria;
import tigase.db.TigaseDBException;
import tigase.kernel.beans.Bean;
import tigase.server.Packet;
import tigase.util.stringprep.TigaseStringprepException;
import tigase.xml.Element;
import tigase.xmpp.rsm.RSM;

import java.util.List;

/**
 * Created by andrzej on 16.07.2016.
 */
@Bean(name = "queryTags", parent = MessageArchiveComponent.class, active = true)
public class QueryTagsModule
		extends AbstractModule {

	private static final String TAGS_ELEM = "tags";

	@Override
	public String[] getFeatures() {
		return new String[0];
	}

	@Override
	public Criteria getModuleCriteria() {
		return null;
	}

	@Override
	public void process(Packet packet) throws ComponentException, TigaseStringprepException {
		Element tagsEl = packet.getElement().getChild(TAGS_ELEM, QueryCriteria.QUERTY_XMLNS);
		try {
			QueryCriteria query = msg_repo.newQuery();
			query.getRsm().fromElement(tagsEl);

			String startsWith = tagsEl.getAttributeStaticStr("like");
			if (startsWith == null) {
				startsWith = "";
			}

			List<String> tags = msg_repo.getTags(packet.getStanzaFrom().getBareJID(), startsWith, query);

			tagsEl = new Element("tags", new String[]{"xmlns"}, new String[]{QueryCriteria.QUERTY_XMLNS});
			for (String tag : tags) {
				tagsEl.addChild(new Element("tag", tag));
			}

			RSM rsm = query.getRsm();
			if (rsm.getCount() == null || rsm.getCount() != 0) {
				tagsEl.addChild(rsm.toElement());
			}

			packetWriter.write(packet.okResult(tagsEl, 0));
		} catch (TigaseDBException e) {
			throw new RuntimeException("Error retrieving list of used tags", e);
		}
	}

	@Override
	public boolean canHandle(Packet packet) {
		return config.isTagSupportEnabled() &&
				packet.getElement().getChild(TAGS_ELEM, QueryCriteria.QUERTY_XMLNS) != null;
	}
}
