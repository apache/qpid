package org.apache.qpid.server.store.berkeleydb;

import org.apache.qpid.server.store.DurableConfigurationStoreTest;
import org.apache.qpid.server.store.MessageStore;

public class BDBMessageStoreConfigurationTest extends DurableConfigurationStoreTest
{
    @Override
    protected MessageStore createStore() throws Exception
    {
        return new BDBMessageStore();
    }

}
