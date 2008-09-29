package org.apache.qpid.transport.codec;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;


/*
 * Extends BBDecoder to add some additional methods.
 *
 * FIXME (Gazza) : I don't like very much this class...this code should be part of BBDecoder
 */
public class ManagementDecoder
{
    private BBDecoder _decoder;
    private ByteBuffer _buffer;

    public void init(ByteBuffer buffer)
    {
        this._buffer = buffer;
        this._decoder = new BBDecoder();
        _decoder.init(_buffer);
    }

    public byte[] readReaminingBytes ()
    {
        byte[] result = new byte[_buffer.limit() - _buffer.position()];
        _decoder.get(result);
        return result;
    }

    public byte[] readBin128() {
        byte[] result = new byte[16];
        _decoder.get(result);
        return result;
    }

    public byte[] readBytes (int howMany)
    {
        byte [] result = new byte[howMany];
        _decoder.get(result);
        return result;
    }

    public long readDatetime ()
    {
        return _decoder.readDatetime();
    }

    public Map<String, Object> readMap ()
    {
        return _decoder.readMap();
    }

    public int readSequenceNo ()
    {
        return _decoder.readSequenceNo();
    }

    public String readStr16 ()
    {
        return _decoder.readStr16();
    }

    public String readStr8 ()
    {
        return _decoder.readStr8();
    }

    public int readUint16 ()
    {
        return _decoder.readUint16();
    }

    public long readUint32 ()
    {
        return _decoder.readUint32();
    }

    public long readUint64 ()
    {
        return _decoder.readUint64();
    }

    public short readUint8 ()
    {
        return _decoder.readUint8();
    }

    public UUID readUuid ()
    {
        return _decoder.readUuid();
    }

    public byte[] readVbin16 ()
    {
        return _decoder.readVbin16();
    }

    public byte[] readVbin32 ()
    {
        return _decoder.readVbin32();
    }

    public byte[] readVbin8 ()
    {
        return _decoder.readVbin8();
    }
}