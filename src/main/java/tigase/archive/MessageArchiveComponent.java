/*
 * MessageArchiveComponent.java
 *
 * Tigase Message Archiving Component
 * Copyright (C) 2004-2016 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 *
 */



package tigase.archive;

//~--- non-JDK imports --------------------------------------------------------

import tigase.archive.db.MessageArchiveRepository;
import tigase.component.AbstractKernelBasedComponent;
import tigase.component.modules.impl.DiscoveryModule;
import tigase.kernel.beans.Bean;
import tigase.kernel.beans.Inject;
import tigase.kernel.beans.config.ConfigField;
import tigase.kernel.core.Kernel;
import tigase.server.Message;
import tigase.server.Packet;
import tigase.stats.StatisticsList;
import tigase.vhosts.VHostItem;
import tigase.xmpp.JID;
import tigase.xmpp.mam.modules.GetFormModule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author andrzej
 */
@Bean(name = "message-archive", parent = Kernel.class, active = true)
public class MessageArchiveComponent
				extends AbstractKernelBasedComponent implements MessageArchiveConfig {
	private static final Logger log = Logger.getLogger(MessageArchiveComponent.class
			.getCanonicalName());

	private static final String			  TAGS_SUPPORT_PROP_KEY = "tags-support";
	private static final String			  REMOVE_EXPIRED_MESSAGES_KEY = "remove-expired-messages";
	private static final String			  REMOVE_EXPIRED_MESSAGES_DELAY_KEY = "remove-expired-messages-delay";
	private static final String			  REMOVE_EXPIRED_MESSAGES_PERIOD_KEY = "remove-expired-messages-period";
	
	//~--- fields ---------------------------------------------------------------

	@Inject
	protected MessageArchiveRepository msg_repo = null;

	@ConfigField(desc = "Tag support enabled", alias = TAGS_SUPPORT_PROP_KEY)
	private boolean tagsSupport = false;
	private float expiredMessagesRemovalTimeAvg = -1;
	private RemoveExpiredTask expiredMessagesRemovalTask = null;

	@ConfigField(desc = "Remove expired messages from repository", alias = REMOVE_EXPIRED_MESSAGES_KEY)
	private boolean removeExpiredMessages = false;
	@ConfigField(desc = "Initial delay since server statup until removal of expired messages", alias = REMOVE_EXPIRED_MESSAGES_DELAY_KEY)
	private Duration removeExpiredMessagesDelay = Duration.ofHours(1);
	@ConfigField(desc = "Period between expired message removals", alias = REMOVE_EXPIRED_MESSAGES_PERIOD_KEY)
	private Duration removeExpiredMessagesPeriod = Duration.ofDays(1);

	//~--- constructors ---------------------------------------------------------

	/**
	 * Constructs ...
	 *
	 */
	public MessageArchiveComponent() {
		super();
		VHostItemHelper.register();
		setName("message-archive");
	}

	//~--- methods --------------------------------------------------------------

	@Override
	public int hashCodeForPacket(Packet packet) {
		if (packet.getElemName() == Message.ELEM_NAME && packet.getPacketFrom() != null 
				&& !getComponentId().equals(packet.getPacketFrom())) {
			return packet.getPacketFrom().hashCode();
		}
		if (packet.getStanzaFrom() != null && !getComponentId().equals(packet.getStanzaFrom())) {
			return packet.getStanzaFrom().getBareJID().hashCode();
		}
		if (packet.getStanzaTo() != null) {
			return packet.getStanzaTo().hashCode();
		}
		return 1;
	}
	
	/**
	 * Method description
	 *
	 *
	 *
	 *
	 * @return a value of <code>int</code>
	 */
	@Override
	public int processingInThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	// ~--- methods
	// --------------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 *
	 *
	 * @return a value of <code>int</code>
	 */
	@Override
	public int processingOutThreads() {
		return Runtime.getRuntime().availableProcessors() * 4;
	}

	@Override
	public String getComponentVersion() {
		String version = this.getClass().getPackage().getImplementationVersion();
		return version == null ? "0.0.0" : version;
	}

	@Override
	public boolean isDiscoNonAdmin() {
		return false;
	}

	@Override
	protected void registerModules(Kernel kernel) {
		kernel.registerBean(DiscoveryModule.class).exec();
		kernel.registerBean(GetFormModule.class).exec();
	}

	//~--- get methods ----------------------------------------------------------

	/**
	 * Method description
	 *
	 *
	 * @return
	 */
	@Override
	public String getDiscoDescription() {
		return "Message Archiving (XEP-0136) Support";
	}

	@Override
	public void getStatistics(StatisticsList list) {
		super.getStatistics(list);
		list.add(getName(), "Removal time of expired messages (avg)", expiredMessagesRemovalTimeAvg, Level.FINE);
	}
	
	//~--- set methods ----------------------------------------------------------


	@Override
	public void initialize() {
		super.initialize();
	}

	@Override
	public void beanConfigurationChanged(Collection<String> changedFields) {
		super.beanConfigurationChanged(changedFields);
		if (changedFields.contains(REMOVE_EXPIRED_MESSAGES_KEY) || changedFields.contains(REMOVE_EXPIRED_MESSAGES_PERIOD_KEY) ||
				changedFields.contains(REMOVE_EXPIRED_MESSAGES_DELAY_KEY)) {
			if (expiredMessagesRemovalTask != null) {
				expiredMessagesRemovalTask.cancel();
				expiredMessagesRemovalTask = null;
			}

			if (removeExpiredMessages) {
				long initialDelay = removeExpiredMessagesDelay.toMillis();
				long period = removeExpiredMessagesPeriod.toMillis();
				log.log(Level.FINE, "scheduling removal of expired messages to once every {0}ms after initial delay of {1}ms",
						new Object[]{period, initialDelay});
				expiredMessagesRemovalTask = new RemoveExpiredTask();
				addTimerTask(expiredMessagesRemovalTask, initialDelay, period);
			}
		}
	}

	//~--- methods --------------------------------------------------------------

	@Override
	public boolean isTagSupportEnabled() {
		return tagsSupport;
	}

	private class RemoveExpiredTask extends tigase.util.TimerTask {

		@Override
		public void run() {
			float time = 0;
			float count = 0;
			for (JID vhost : vHostManager.getAllVHosts()) {
				try {
					VHostItem item = vHostManager.getVHostItem(vhost.getDomain());
					RetentionType retentionType = VHostItemHelper.getRetentionType(item);
					switch (retentionType) {
						case numberOfDays:
							Integer days = VHostItemHelper.getRetentionDays(item);
							if (days != null) {
								long start = System.currentTimeMillis();
								LocalDateTime timestamp = LocalDateTime.now(ZoneId.of("Z")).minusDays(days);
								msg_repo.deleteExpiredMessages(vhost.getBareJID(), timestamp);
								long stop = System.currentTimeMillis();
								long executedIn = stop - start;
								time += executedIn;
								log.log(Level.FINEST, "removed messsages older than {0} for domain {1} in {2}ms", 
										new Object[]{timestamp.toString(), vhost.getDomain(), executedIn});
								count++;
							}						
							break;
						case userDefined:
						// right now there is no implementation for this so let's handle it in same way as unlimited
						case unlimited:
							log.log(Level.FINEST, "skipping removal of expired messages for domain {0}"
									+ " as removal for retention type {1} is not supported", 
									new Object[]{vhost.getDomain(), retentionType});
							break;
					}
				} catch (Exception ex) {
					log.log(Level.FINE, "exception removing expired messages", ex);
				}
			}
			expiredMessagesRemovalTimeAvg = (count > 0) ? (time / count) : -1;
		}
		
	}
}


//~ Formatted in Tigase Code Convention on 13/10/15
