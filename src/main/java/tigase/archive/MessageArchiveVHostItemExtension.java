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
package tigase.archive;

import tigase.kernel.beans.Bean;
import tigase.server.Command;
import tigase.server.DataForm;
import tigase.server.Packet;
import tigase.vhosts.AbstractVHostItemExtension;
import tigase.vhosts.VHostItemExtensionBackwardCompatible;
import tigase.vhosts.VHostItemExtensionManager;
import tigase.vhosts.VHostItemExtensionProvider;
import tigase.xml.Element;

import java.util.Map;
import java.util.Optional;

/**
 * @author andrzej
 */
public class MessageArchiveVHostItemExtension
		extends AbstractVHostItemExtension<MessageArchiveVHostItemExtension> implements VHostItemExtensionBackwardCompatible<MessageArchiveVHostItemExtension> {

	public static final String ENABLED_KEY = "xep0136Enabled";

	public static final String DEFAULT_STORE_METHOD_KEY = "xep0136DefaultStoreMethod";

	public static final String REQUIRED_STORE_METHOD_KEY = "xep0136RequiredStoreMethod";

	public static final String RETENTION_TYPE_KEY = "xep0136Retention";
	public static final String RETENTION_PERIOD_KEY = "xep0136RetentionPerion";

	public static final String MUC_SAVE_KEY = "xep0136SaveMuc";
	
	public static final String ID = "message-archive";

	private boolean enabled = true;
	private Optional<StoreMethod> defaultStoreMethod = Optional.empty();
	private Optional<StoreMethod> requiredStoreMethod = Optional.empty();
	private RetentionType retentionType = RetentionType.userDefined;
	private Integer retentionDays = null;
	private Optional<StoreMuc> saveMuc = Optional.empty();

	public boolean isEnabled() {
		return enabled;
	}

	public Optional<StoreMethod> getDefaultStoreMethod() {
		return defaultStoreMethod;
	}

	public Optional<StoreMethod> getRequiredStoreMethod() {
		return requiredStoreMethod;
	}

	public RetentionType getRetentionType() {
		return retentionType;
	}

	public Integer getRetentionDays() {
		return retentionDays;
	}

	public Optional<StoreMuc> getSaveMuc() {
		return saveMuc;
	}

	@Override
	public void initFromData(Map<String, Object> data) {
		Boolean enabled = (Boolean) data.remove(ENABLED_KEY);
		if (enabled != null) {
			this.enabled = enabled;
		}
		String tmp = (String) data.remove(DEFAULT_STORE_METHOD_KEY);
		if (tmp != null) {
			this.defaultStoreMethod = Optional.of(StoreMethod.valueof(tmp));
		}
		tmp = (String) data.remove(REQUIRED_STORE_METHOD_KEY);
		if (tmp != null) {
			this.requiredStoreMethod = Optional.of(StoreMethod.valueof(tmp));
		}
		tmp = (String) data.remove(RETENTION_TYPE_KEY);
		if (tmp != null) {
			this.retentionType = RetentionType.valueOf(tmp);
		}
		this.retentionDays = (Integer) data.remove(RETENTION_PERIOD_KEY);
		tmp = (String) data.remove(MUC_SAVE_KEY);
		if (tmp != null) {
			this.saveMuc = Optional.of(StoreMuc.valueof(tmp));
		}
	}

	@Override
	public String getId() {
		return ID;
	}

	@Override
	public void initFromElement(Element item) {
		final String enabledAttribute = item.getAttributeStaticStr("enabled");
		this.enabled = enabledAttribute == null || Boolean.parseBoolean(enabledAttribute);
		defaultStoreMethod = Optional.ofNullable(item.getAttributeStaticStr("default-store-method"))
				.map(StoreMethod::valueof);
		requiredStoreMethod = Optional.ofNullable(item.getAttributeStaticStr("required-store-method"))
				.map(StoreMethod::valueof);
		retentionType = Optional.ofNullable(item.getAttributeStaticStr("retention-type"))
				.map(RetentionType::valueOf)
				.orElse(RetentionType.userDefined);
		retentionDays = Optional.ofNullable(item.getAttributeStaticStr("retention-days"))
				.map(Integer::parseInt)
				.orElse(null);
		saveMuc = Optional.ofNullable(item.getAttributeStaticStr("save-muc")).map(StoreMuc::valueof);
	}

	@Override
	public void initFromCommand(String prefix, Packet packet) throws IllegalArgumentException {
		enabled = Command.getCheckBoxFieldValue(packet, prefix + "-enabled");
		defaultStoreMethod = Optional.ofNullable(Command.getFieldValue(packet, prefix + "-default-store-method"))
				.filter(s -> !s.isEmpty())
				.map(StoreMethod::valueof);
		requiredStoreMethod = Optional.ofNullable(Command.getFieldValue(packet, prefix + "-required-store-method"))
				.filter(s -> !s.isEmpty())
				.map(StoreMethod::valueof);
		retentionType = Optional.ofNullable(Command.getFieldValue(packet, prefix + "-retention-type"))
				.map(RetentionType::valueOf)
				.orElse(RetentionType.userDefined);
		retentionDays = Optional.ofNullable(Command.getFieldValue(packet, prefix + "-retention-days"))
				.map(Integer::parseInt)
				.orElse(null);
		saveMuc = Optional.ofNullable(Command.getFieldValue(packet, prefix + "-save-muc"))
				.filter(s -> !s.isEmpty())
				.map(StoreMuc::valueof);
	}

	@Override
	public String toDebugString() {
		return "enabled: " + enabled + ", defaultStore: " +
				defaultStoreMethod.map(StoreMethod::toString).orElse("unset") + ", requiredStore: " +
				requiredStoreMethod.map(StoreMethod::toString).orElse("unset") + ", retentionType: " + retentionType +
				", saveMuc: " + saveMuc;
	}

	@Override
	public Element toElement() {
		Element el = new Element(getId());
		if (!enabled) {
			el.setAttribute("enabled", String.valueOf(enabled));
		}
		defaultStoreMethod.ifPresent(v -> el.setAttribute("default-store-method", v.toString()));
		requiredStoreMethod.ifPresent(v -> el.setAttribute("required-store-method", v.toString()));
		if (retentionType != RetentionType.userDefined) {
			el.setAttribute("retention-type", retentionType.toString());
		}
		if (retentionDays != null) {
			el.setAttribute("retention-days", String.valueOf(retentionDays));
		}
		saveMuc.ifPresent(v -> el.setAttribute("save-muc", v.toString()));
		return (el.getAttributes() == null || el.getAttributes().isEmpty()) ? null : el;
	}
	
	@Override
	public MessageArchiveVHostItemExtension mergeWithDefaults(MessageArchiveVHostItemExtension defaults) {
		MessageArchiveVHostItemExtension merged = new MessageArchiveVHostItemExtension();
		merged.enabled = this.enabled && defaults.isEnabled();
		merged.defaultStoreMethod = this.defaultStoreMethod.isPresent() ? this.defaultStoreMethod : defaults.getDefaultStoreMethod();
		merged.requiredStoreMethod = this.requiredStoreMethod.isPresent() ? this.requiredStoreMethod : defaults.getRequiredStoreMethod();
		switch (this.retentionType) {
			case unlimited:
				if (defaults.getRetentionType() == RetentionType.unlimited || defaults.getRetentionType() == RetentionType.userDefined) {
					merged.retentionType = RetentionType.unlimited;
				} else {
					merged.retentionType = RetentionType.numberOfDays;
					merged.retentionDays = defaults.getRetentionDays();
				}
			case numberOfDays:
				merged.retentionType = RetentionType.numberOfDays;
				merged.retentionDays = Math.min(this.retentionDays == null ? Integer.MAX_VALUE : this.retentionDays, defaults.getRetentionDays() == null ? Integer.MAX_VALUE : defaults.getRetentionDays());
			case userDefined:
				merged.retentionType = RetentionType.userDefined;
		}
		merged.saveMuc = this.saveMuc.isPresent() ? this.saveMuc : defaults.getSaveMuc();
		return merged;
	}

	@Override
	public void addCommandFields(String prefix, Packet packet, boolean forDefault) {
		Element commandEl = packet.getElemChild(Command.COMMAND_EL, Command.XMLNS);
		DataForm.addFieldValue(commandEl, prefix + "-enabled", enabled ? "true" : "false", "boolean", "Message Archiving enabled");
		DataForm.addFieldValue(commandEl, prefix + "-default-store-method",
							   defaultStoreMethod.map(Enum::toString).orElse(""), "Message Archiving - default store method",
							   new String[]{"Default", "False", "Body", "Message", "Stream"},
							   new String[]{"", "false", "body", "message", "stream"});
		DataForm.addFieldValue(commandEl, prefix + "-required-store-method",
							   requiredStoreMethod.map(Enum::toString).orElse(""), "Message Archiving - required store method",
							   new String[]{"Default", "False", "Body", "Message", "Stream"},
							   new String[]{"", "false", "body", "message", "stream"});
		DataForm.addFieldValue(commandEl, prefix + "-retention-type",
							   retentionType.name(), "Message Archiving - retention type",
							   new String[]{"User defined", "Unlimited", "Number of days"},
							   new String[]{"userDefined", "unlimited", "numberOfDays"});
		DataForm.addFieldValue(commandEl, prefix + "-retention-days",
							   retentionDays == null ? "" : String.valueOf(retentionDays), "text-single",
							   "Message Archiving - retention period (in days)");
		DataForm.addFieldValue(commandEl, prefix + "-save-muc", saveMuc.map(Enum::toString).orElse(""),
							   "Message Archiving - store MUC messages",
							   new String[]{"Default", StoreMuc.User.name(), StoreMuc.False.name(),
											StoreMuc.True.name()},
							   new String[]{"", StoreMuc.User.toString(), StoreMuc.False.toString(),
											StoreMuc.True.toString()});
	}

	@Bean(name = "message-archive", parent = VHostItemExtensionManager.class, active = true)
	public static class Provider implements VHostItemExtensionProvider<MessageArchiveVHostItemExtension> {

		@Override
		public String getId() {
			return ID;
		}

		@Override
		public Class<MessageArchiveVHostItemExtension> getExtensionClazz() {
			return MessageArchiveVHostItemExtension.class;
		}
	}
}
