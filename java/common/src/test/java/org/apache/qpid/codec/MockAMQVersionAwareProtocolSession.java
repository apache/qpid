package org.apache.qpid.codec;

import java.nio.ByteBuffer;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQDataBlock;
import org.apache.qpid.framing.AMQMethodBody;
import org.apache.qpid.framing.ContentBody;
import org.apache.qpid.framing.ContentHeaderBody;
import org.apache.qpid.framing.HeartbeatBody;
import org.apache.qpid.framing.MethodRegistry;
import org.apache.qpid.framing.ProtocolVersion;
import org.apache.qpid.protocol.AMQVersionAwareProtocolSession;
import org.apache.qpid.transport.Sender;

public class MockAMQVersionAwareProtocolSession implements AMQVersionAwareProtocolSession
{

    public void contentBodyReceived(int channelId, ContentBody body) throws AMQException
    {
        // TODO Auto-generated method stub

    }

    public void contentHeaderReceived(int channelId, ContentHeaderBody body) throws AMQException
    {
        // TODO Auto-generated method stub

    }

    public MethodRegistry getMethodRegistry()
    {
        return MethodRegistry.getMethodRegistry(ProtocolVersion.v0_9);
    }

    public void heartbeatBodyReceived(int channelId, HeartbeatBody body) throws AMQException
    {
        // TODO Auto-generated method stub

    }

    public void init()
    {
        // TODO Auto-generated method stub

    }

    public void methodFrameReceived(int channelId, AMQMethodBody body) throws AMQException
    {
        // TODO Auto-generated method stub

    }

    public void setSender(Sender<ByteBuffer> sender)
    {
        // TODO Auto-generated method stub

    }

    public void writeFrame(AMQDataBlock frame)
    {
        // TODO Auto-generated method stub

    }

    public byte getProtocolMajorVersion()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    public byte getProtocolMinorVersion()
    {
        // TODO Auto-generated method stub
        return 0;
    }

    public ProtocolVersion getProtocolVersion()
    {
        // TODO Auto-generated method stub
        return null;
    }

}
