package org.apache.qpid.server.management.plugin.servlet.rest.action;

import java.util.Map;

import org.apache.qpid.server.management.plugin.servlet.rest.Action;
import org.apache.qpid.server.model.Broker;

public class ListMessageStoreTypes implements Action
{

    @Override
    public String getName()
    {
        return ListMessageStoreTypes.class.getSimpleName();
    }

    @Override
    public Object perform(Map<String, Object> request, Broker broker)
    {
        return broker.getAttribute(Broker.SUPPORTED_VIRTUALHOST_STORE_TYPES);
    }

}
