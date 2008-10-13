package org.apache.qpid.management.messages;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class AmqpCoDec
{
    private byte [] _buffer;
    private int _position;
        
    AmqpCoDec()
    {
        _buffer = new byte [1000];
        _buffer[0] = 'A';
        _buffer[1] = 'M';
        _buffer[2] = '1';
        _position = 3;
    }
    
    
    /**
     * Int32-to-4 byte array marshalling.
     * Marshalles an integer using four bytes.
     * 
     * @param data the result array.
     * @param pos the starting position of the array to be filled.
     * @param value the value to be marshalled.
     */
    public final void pack32(int value) {
        _buffer[_position++] = (byte) (value >> 24 & 0xff);
        _buffer[_position++] = (byte) (value >> 16 & 0xff);
        _buffer[_position++] = (byte) (value >> 8 & 0xff);
        _buffer[_position++] = (byte) (value & 0xff);
    }

    /**
     * Int32-to-4 byte array marshalling.
     * Marshalles an integer using four bytes.
     * 
     * @param data the result array.
     * @param pos the starting position of the array to be filled.
     * @param value the value to be marshalled.
     */
    public final void pack16(int value) {
        _buffer[_position++] = (byte) (value >> 8 & 0xff);
        _buffer[_position++] = (byte) (value & 0xff);
    }
    
    /**
     * Int32-to-4 byte array marshalling.
     * Marshalles an integer using four bytes.
     * 
     * @param data the result array.
     * @param pos the starting position of the array to be filled.
     * @param value the value to be marshalled.
     */
    public final void pack64(long value) {
        _buffer[_position++] = (byte) (value >> 56 & 0xff);
        _buffer[_position++] = (byte) (value >> 48 & 0xff);
        _buffer[_position++] = (byte) (value >> 40 & 0xff);
        _buffer[_position++] = (byte) (value >> 32 & 0xff);
        _buffer[_position++] = (byte) (value >> 24 & 0xff);
        _buffer[_position++] = (byte) (value >> 16 & 0xff);
        _buffer[_position++] = (byte) (value >> 8 & 0xff);
        _buffer[_position++] = (byte) (value & 0xff);
    }
    
    /**
     * Int32-to-byte array marshalling.
     * Marshalles an integer using two bytes.
     * 
     * @param data the result array.
     * @param pos the starting position of the array to be filled.
     * @param value the value to be marshalled.
     */
    public final void pack24(int value) {
        _buffer[_position++] = (byte) (value >> 16 & 0xff);
        _buffer[_position++] = (byte) (value >> 8 & 0xff);
        _buffer[_position++] = (byte) (value & 0xff);
    }    

    public final void pack8(int value) {
        _buffer[_position++] = (byte) (value & 0xff);
    }    

    public void pack8 (byte aByte)
    {
        _buffer[_position++] = aByte;
    }    

    public void packStr8(String aString)
    {
        try
        {
            byte [] toBytes = aString.getBytes("UTF-8");
            int length = toBytes.length;
            pack8(length);
            System.arraycopy(toBytes, 0, _buffer, _position, length);  
            _position+=length;
        } catch (UnsupportedEncodingException exception)
        {
            throw new RuntimeException(exception);
        }
    }
    
    public void packStr16(String aString)
    {
        try
        {
            byte [] toBytes = aString.getBytes("UTF-8");
            int length = toBytes.length;
            pack16(length);
            System.arraycopy(toBytes, 0, _buffer, _position, length);  
            _position+=length;
        } catch (UnsupportedEncodingException exception)
        {
            throw new RuntimeException(exception);
        }
    }

    public void pack (byte[] bytes)
    {
        System.arraycopy(bytes, 0, _buffer, _position, bytes.length);
        _position+=bytes.length;
    }
    
    /**
     * Retruns the byte buffer that is wrapping the backing array of this codec.
     * 
     * @return the byte buffer that is wrapping the backing array of this codec.
     */
    public ByteBuffer getEncodedBuffer ()
    {
        return ByteBuffer.wrap(_buffer,0,_position);
    }
}