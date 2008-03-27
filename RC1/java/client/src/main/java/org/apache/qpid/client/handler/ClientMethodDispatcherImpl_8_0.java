package org.apache.qpid.client.handler;

import org.apache.qpid.framing.*;
import org.apache.qpid.framing.amqp_8_0.MethodDispatcher_8_0;

import org.apache.qpid.AMQException;
import org.apache.qpid.client.state.AMQStateManager;

public class ClientMethodDispatcherImpl_8_0 extends ClientMethodDispatcherImpl implements MethodDispatcher_8_0
{
    public ClientMethodDispatcherImpl_8_0(AMQStateManager stateManager)
    {
        super(stateManager);
    }

    public boolean dispatchBasicRecoverOk(BasicRecoverOkBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchChannelAlert(ChannelAlertBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestContent(TestContentBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestContentOk(TestContentOkBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestInteger(TestIntegerBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestIntegerOk(TestIntegerOkBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestString(TestStringBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestStringOk(TestStringOkBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestTable(TestTableBody body, int channelId) throws AMQException
    {
        return false;
    }

    public boolean dispatchTestTableOk(TestTableOkBody body, int channelId) throws AMQException
    {
        return false;  
    }
}
