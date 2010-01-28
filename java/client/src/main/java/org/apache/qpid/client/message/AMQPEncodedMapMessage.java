package org.apache.qpid.client.message;

import java.util.Map;

import javax.jms.JMSException;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;
import org.apache.qpid.transport.codec.BBDecoder;
import org.apache.qpid.transport.codec.BBEncoder;

public class AMQPEncodedMapMessage extends JMSMapMessage
{
    public static final String MIME_TYPE = "amqp/map";
    
    public AMQPEncodedMapMessage(AMQMessageDelegateFactory delegateFactory) throws JMSException
    {
        this(delegateFactory, null);
    }

    AMQPEncodedMapMessage(AMQMessageDelegateFactory delegateFactory, ByteBuffer data) throws JMSException
    {
        super(delegateFactory, data); 
    }

    AMQPEncodedMapMessage(AMQMessageDelegate delegate, ByteBuffer data) throws AMQException
    {
        super(delegate, data);
    }
    
    @ Override
    protected String getMimeType()
    {
        return MIME_TYPE;
    }

    // The super clas methods resets the buffer
    @ Override
    public ByteBuffer getData()
    {
        writeMapToData();
        return _data;
    }
    
    @ Override
    protected void populateMapFromData() throws JMSException
    {
        if (_data != null)
        {
            _data.rewind();
            BBDecoder decoder = new BBDecoder();
            decoder.init(_data.buf());
            _map = decoder.readMap();
        }
        else
        {
            _map.clear();
        }
    }

    @ Override
    protected void writeMapToData()
    {
        BBEncoder encoder = new BBEncoder(1024);
        encoder.writeMap(_map);
        _data = ByteBuffer.wrap(encoder.segment());
    }
    
    // for testing
    Map<String,Object> getMap()
    {
        return _map;
    }
    
    void setMap(Map<String,Object> map)
    {
        _map = map;
    }
}
