package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;


public abstract interface AMQMethodBodyInstanceFactory
{
    public AMQMethodBody newInstance(ByteBuffer buffer, long size) throws AMQFrameDecodingException;
}
