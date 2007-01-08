package org.apache.qpid.framing;

import org.apache.mina.common.ByteBuffer;
import org.apache.log4j.Logger;

import java.util.Arrays;

/**
 * A short string is a representation of an AMQ Short String
 * Short strings differ from the Java String class by being limited to on ASCII characters (0-127)
 * and thus can be held more effectively in a byte buffer.
 *
 */
public final class AMQShortString implements CharSequence
{
    private static final Logger _logger = Logger.getLogger(AMQShortString.class);

    private final ByteBuffer _data;
    private int _hashCode;
    private static final char[] EMPTY_CHAR_ARRAY = new char[0];

    public AMQShortString(String data)
    {
        this(data == null ? EMPTY_CHAR_ARRAY : data.toCharArray());
        if(data != null) _hashCode = data.hashCode();
    }

    public AMQShortString(char[] data)
    {
        if(data == null)
        {
            throw new NullPointerException("Cannot create AMQShortString with null char[]");
        }
        final int length = data.length;
        final byte[] stringBytes = new byte[length];
        for(int i = 0; i < length; i++)
        {
            stringBytes[i] = (byte) (0xFF & data[i]);
        }

        _data = ByteBuffer.wrap(stringBytes);
        _data.rewind();

    }

    public AMQShortString(CharSequence charSequence)
    {
        final int length = charSequence.length();
        final byte[] stringBytes = new byte[length];
        int hash = 0;
        for(int i = 0 ; i < length; i++)
        {
            stringBytes[i] = ((byte) (0xFF & charSequence.charAt(i)));
            hash = (31 * hash) + stringBytes[i];

        }
        _data = ByteBuffer.wrap(stringBytes);
        _data.rewind();
        _hashCode = hash;

    }

    private AMQShortString(ByteBuffer data)
    {
        _data = data;
        
    }


    /**
     * Get the length of the short string
     * @return length of the underlying byte array
     */
    public int length()
    {
        return _data.limit();
    }

    public char charAt(int index)
    {

        return (char) _data.get(index);

    }

    public CharSequence subSequence(int start, int end)
    {
        return new CharSubSequence(start,end);
    }

    public int writeToByteArray(byte[] encoding, int pos)
    {
        final int size = length();
        encoding[pos++] = (byte) length();
        for(int i = 0; i < size; i++)
        {
            encoding[pos++] = _data.get(i);
        }
        return pos;
    }

    public static AMQShortString readFromByteArray(byte[] byteEncodedDestination, int pos)
    {

        final byte len = byteEncodedDestination[pos];
        if(len == 0)
        {
            return null;
        }
        ByteBuffer data = ByteBuffer.wrap(byteEncodedDestination,pos+1,len).slice();
        

        return new AMQShortString(data);
    }

    public static AMQShortString readFromBuffer(ByteBuffer buffer)
    {
        final short length = buffer.getUnsigned();
        if (length == 0)
        {
            return null;
        }
        else
        {
            ByteBuffer data = buffer.slice();
            data.limit(length);
            data.rewind();
            buffer.skip(length);

            return new AMQShortString(data);
        }
    }

    public void writeToBuffer(ByteBuffer buffer)
    {


        final int size = length();
        if (size != 0)
        {

            buffer.put((byte)size);
            if(_data.buf().hasArray())
            {
                buffer.put(_data.array(),_data.arrayOffset(),length());
            }
            else
            {

                for(int i = 0; i < size; i++)
                {

                    buffer.put(_data.get(i));
                }
            }
        }
        else
        {
            // really writing out unsigned byte
            buffer.put((byte) 0);
        }

    }

    private final class CharSubSequence implements CharSequence
    {
        private final int _offset;
        private final int _end;


        public CharSubSequence(final int offset, final int end)
        {
            _offset = offset;
            _end = end;
        }


        public int length()
        {
            return _end - _offset;
        }

        public char charAt(int index)
        {
            return AMQShortString.this.charAt(index + _offset);
        }

        public CharSequence subSequence(int start, int end)
        {
            return new CharSubSequence(start+_offset,end+_offset);
        }
    }



    public char[] asChars()
    {
        final int size = length();
        final char[] chars = new char[size];




        for(int i = 0 ; i < size; i++)
        {
            chars[i] = (char) _data.get(i);
        }
        return chars;
    }



    public String asString()
    {
        return new String(asChars());
    }

    public boolean equals(Object o)
    {
        if(o == null)
        {
            return false;
        }
        if(o == this)
        {
            return true;
        }
        if(o instanceof AMQShortString)
        {

            final AMQShortString otherString = (AMQShortString) o;

            if(otherString.length() != length())
            {
                return false;
            }
            if((_hashCode != 0) && (otherString._hashCode != 0) && (_hashCode != otherString._hashCode))
            {
                return false;
            }
            final int size = length();
            for(int i = 0; i < size; i++)
            {
                if(_data.get(i) != otherString._data.get(i))
                {
                    return false;
                }
            }

            return true;


        }
        return (o instanceof CharSequence) && equals((CharSequence)o);

    }

    public boolean equals(CharSequence s)
    {
        if(s == null)
        {
            return false;
        }
        if(s.length() != length())
        {
            return false;
        }
        for(int i = 0; i < length(); i++)
        {
            if(charAt(i)!= s.charAt(i))
            {
                return false;
            }
        }
        return true;
    }

    public int hashCode()
    {
        int hash = _hashCode;
        if(hash == 0)
        {
            final int size = length();


            for(int i = 0; i < size; i++)
            {
                hash = (31 * hash) + _data.get(i);
            }
            _hashCode = hash;
        }

        return hash;
    }

    public void setDirty()
    {
        _hashCode = 0;
    }
    
    public String toString()
    {
        return asString();
    }


    public int compareTo(AMQShortString name)
    {
        if(name == null)
        {
            return 1;
        }
        else
        {

            if(name.length() < length())
            {
                return - name.compareTo(this);
            }



            for(int i = 0; i < length() ; i++)
            {
                final byte d = _data.get(i);
                final byte n = name._data.get(i);
                if(d < n) return -1;
                if(d > n) return 1;
            }

            return length() == name.length() ? 0 : -1;
        }
    }
}
