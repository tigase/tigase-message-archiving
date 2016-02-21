/*
 * Activator.java
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

import java.util.logging.Logger;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import tigase.archive.db.JDBCMessageArchiveRepository;
import tigase.osgi.ModulesManager;

/**
 *
 * @author andrzej
 */
public class Activator implements BundleActivator, ServiceListener {

        private static final Logger log = Logger.getLogger(Activator.class.getCanonicalName());
        private Class<MessageArchivePlugin> archivePluginClass = null;
        private Class<MessageArchiveComponent> archiveComponentClass = null;
        private BundleContext context = null;
        private ServiceReference serviceReference = null;
        private ModulesManager serviceManager = null;

        @Override
        public void start(BundleContext bc) throws Exception {
                synchronized (this) {
                        context = bc;
                        bc.addServiceListener(this, "(&(objectClass=" + ModulesManager.class.getName() + "))");
                        archivePluginClass = MessageArchivePlugin.class;
                        archiveComponentClass = MessageArchiveComponent.class;
                        serviceReference = bc.getServiceReference(ModulesManager.class.getName());
                        if (serviceReference != null) {
                                serviceManager = (ModulesManager) bc.getService(serviceReference);
                                registerAddons();
                        }
                }
        }

        @Override
        public void stop(BundleContext bc) throws Exception {
                synchronized (this) {
                        if (serviceManager != null) {
                                unregisterAddons();
                                context.ungetService(serviceReference);
                                serviceManager = null;
                                serviceReference = null;
                        }
                        archivePluginClass = null;
                        archiveComponentClass = null;
                }
        }

        @Override
        public void serviceChanged(ServiceEvent event) {
                if (event.getType() == ServiceEvent.REGISTERED) {
                        if (serviceReference == null) {
                                serviceReference = event.getServiceReference();
                                serviceManager = (ModulesManager) context.getService(serviceReference);
                                registerAddons();
                        }
                }
                else if (event.getType() == ServiceEvent.UNREGISTERING) {
                        if (serviceReference == event.getServiceReference()) {
                                unregisterAddons();
                                context.ungetService(serviceReference);
                                serviceManager = null;
                                serviceReference = null;
                        }
                }
        }

        private void registerAddons() {
                if (serviceManager != null) {
                        serviceManager.registerPluginClass(archivePluginClass);
                        serviceManager.registerServerComponentClass(archiveComponentClass);
						serviceManager.registerClass(JDBCMessageArchiveRepository.class);
                        serviceManager.update();
                }
        }

        private void unregisterAddons() {
                if (serviceManager != null) {
                        serviceManager.unregisterPluginClass(archivePluginClass);
                        serviceManager.unregisterServerComponentClass(archiveComponentClass);
						serviceManager.unregisterClass(JDBCMessageArchiveRepository.class);
                        serviceManager.update();
                }
        }
}
