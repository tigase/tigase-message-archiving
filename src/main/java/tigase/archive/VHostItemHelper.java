/*
 * VHostItemHelper.java
 *
 * Tigase Message Archiving Component
 * Copyright (C) 2004-2017 "Tigase, Inc." <office@tigase.com>
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

import tigase.vhosts.VHostItem;
import tigase.vhosts.VHostItem.DataType;

import java.util.Arrays;

/**
 * @author andrzej
 */
public class VHostItemHelper {

	public static final String ENABLED_KEY = "xep0136Enabled";

	public static final String DEFAULT_STORE_METHOD_KEY = "xep0136DefaultStoreMethod";

	public static final String REQUIRED_STORE_METHOD_KEY = "xep0136RequiredStoreMethod";

	public static final String RETENTION_TYPE_KEY = "xep0136Retention";
	public static final String RETENTION_PERIOD_KEY = "xep0136RetentionPerion";

	public static final String MUC_SAVE_KEY = "xep0136SaveMuc";

	private static final DataType[] types = {
			new DataType(ENABLED_KEY, "XEP-0136 - Message Archiving enabled", Boolean.class, true),
			new DataType(DEFAULT_STORE_METHOD_KEY, "XEP-0136 - default store method", String.class, null, null,
						 new Object[]{null, StoreMethod.False.toString(), StoreMethod.Body.toString(),
									  StoreMethod.Message.toString(), StoreMethod.Stream.toString()}),
			new DataType(REQUIRED_STORE_METHOD_KEY, "XEP-0136 - required store method", String.class, null, null,
						 new Object[]{null, StoreMethod.False.toString(), StoreMethod.Body.toString(),
									  StoreMethod.Message.toString(), StoreMethod.Stream.toString()}),
			new DataType(RETENTION_TYPE_KEY, "XEP-0136 - retention type", String.class, null, null,
						 new Object[]{RetentionType.userDefined.name(), RetentionType.unlimited.name(),
									  RetentionType.numberOfDays.name()},
						 new String[]{"User defined", "Unlimited", "Number of days"}),
			new DataType(RETENTION_PERIOD_KEY, "XEP-0136 - retention period (in days)", Integer.class, null),
			new DataType(MUC_SAVE_KEY, "XEP-0136 - store MUC messages", String.class, null, null,
						 new Object[]{StoreMuc.User.toString(), StoreMuc.False.toString(), StoreMuc.True.toString()})};

	public static StoreMethod getDefaultStoreMethod(VHostItem item, StoreMethod defValue) {
		String val = item.getData(DEFAULT_STORE_METHOD_KEY);
		if (val == null || val.isEmpty()) {
			return defValue;
		}
		return StoreMethod.valueof(val);
	}

	public static StoreMethod getRequiredStoreMethod(VHostItem item, StoreMethod defValue) {
		String val = item.getData(REQUIRED_STORE_METHOD_KEY);
		if (val == null || val.isEmpty()) {
			return defValue;
		}
		return StoreMethod.valueof(val);
	}

	public static Integer getRetentionDays(VHostItem item) {
		return item.getData(RETENTION_PERIOD_KEY);
	}

	public static RetentionType getRetentionType(VHostItem item) {
		String val = item.getData(RETENTION_TYPE_KEY);
		return (val != null && !val.isEmpty()) ? RetentionType.valueOf(val) : RetentionType.userDefined;
	}

	public static StoreMuc getStoreMucMessages(VHostItem item, StoreMuc defValue) {
		String val = item.getData(MUC_SAVE_KEY);
		if (val == null || val.isEmpty()) {
			return StoreMuc.User;
		}
		return StoreMuc.valueof(val);
	}

	public static boolean isEnabled(VHostItem item) {
		return item.isData(ENABLED_KEY);
	}

	public static void register() {
		VHostItem.registerData(Arrays.asList(types));
	}

}
