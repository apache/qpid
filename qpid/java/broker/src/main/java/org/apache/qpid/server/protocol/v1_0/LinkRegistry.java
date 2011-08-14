package org.apache.qpid.server.protocol.v1_0;

import java.util.HashMap;
import java.util.Map;

public class LinkRegistry
{
    private final Map<String, SendingLink_1_0> _sendingLinks = new HashMap<String, SendingLink_1_0>();
    private final Map<String, ReceivingLink_1_0> _receivingLinks = new HashMap<String, ReceivingLink_1_0>();

    public synchronized SendingLink_1_0 getDurableSendingLink(String name)
    {
        return _sendingLinks.get(name);
    }

    public synchronized boolean registerSendingLink(String name, SendingLink_1_0 link)
    {
        if(_sendingLinks.containsKey(name))
        {
            return false;
        }
        else
        {
            _sendingLinks.put(name, link);
            return true;
        }
    }

    public synchronized boolean unregisterSendingLink(String name)
    {
        if(!_sendingLinks.containsKey(name))
        {
            return false;
        }
        else
        {
            _sendingLinks.remove(name);
            return true;
        }
    }

    public synchronized ReceivingLink_1_0 getDurableReceivingLink(String name)
    {
        return _receivingLinks.get(name);
    }

    public synchronized  boolean registerReceivingLink(String name, ReceivingLink_1_0 link)
    {
        if(_receivingLinks.containsKey(name))
        {
            return false;
        }
        else
        {
            _receivingLinks.put(name, link);
            return true;
        }
    }
}
