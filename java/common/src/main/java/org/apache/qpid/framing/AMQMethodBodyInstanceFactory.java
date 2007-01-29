package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;


public abstract interface AMQMethodBodyInstanceFactory
{
    public AMQMethodBody newInstance(byte major, byte minor, ByteBuffer buffer, long size) throws AMQFrameDecodingException;
    public AMQMethodBody newInstance(byte major, byte minor, int clazzID, int methodID, ByteBuffer buffer, long size) throws AMQFrameDecodingException;
}
