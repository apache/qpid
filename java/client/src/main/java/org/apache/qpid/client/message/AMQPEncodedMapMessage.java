package org.apache.qpid.client.message;

import java.util.Map;

import javax.jms.JMSException;

import org.apache.mina.common.ByteBuffer;
import org.apache.qpid.AMQException;

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
            _map = _delegate.decodeMap(_data.buf());
        }
        else
        {
            _map.clear();
        }
    }

    @ Override
    protected void writeMapToData()
    {
        _data = ByteBuffer.wrap(_delegate.encodeMap(_map));
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
