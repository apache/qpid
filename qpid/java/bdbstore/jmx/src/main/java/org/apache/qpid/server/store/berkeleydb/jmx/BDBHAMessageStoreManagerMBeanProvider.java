/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.server.store.berkeleydb.jmx;

import javax.management.JMException;
import javax.management.StandardMBean;

import org.apache.log4j.Logger;
import org.apache.qpid.server.jmx.MBeanProvider;
import org.apache.qpid.server.jmx.ManagedObject;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.store.berkeleydb.BDBHAMessageStore;

/**
 * This provide will create a {@link BDBHAMessageStoreManagerMBean} if the child is a virtual
 * host and of type {@link BDBHAMessageStore#TYPE}.
 *
 */
public class BDBHAMessageStoreManagerMBeanProvider implements MBeanProvider
{
    private static final Logger LOGGER = Logger.getLogger(BDBHAMessageStoreManagerMBeanProvider.class);

    public BDBHAMessageStoreManagerMBeanProvider()
    {
        super();
    }

    @Override
    public boolean isChildManageableByMBean(ConfiguredObject child)
    {
        return (child instanceof VirtualHost
            && BDBHAMessageStore.TYPE.equals(child.getAttribute(VirtualHost.STORE_TYPE)));
    }

    @Override
    public StandardMBean createMBean(ConfiguredObject child, StandardMBean parent) throws JMException
    {
        VirtualHost virtualHostChild = (VirtualHost) child;

        BDBHAMessageStore messageStore = (BDBHAMessageStore) virtualHostChild.getMessageStore();

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Creating mBean for child " + child);
        }

        return new BDBHAMessageStoreManagerMBean(messageStore, (ManagedObject) parent);
    }
}
