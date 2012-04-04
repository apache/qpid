package org.apache.qpid.amqp_1_0.codec;


final class BinaryString
{

    private byte[] _data;
    private int _offset;
    private int _size;
    private int _hashCode;

    BinaryString(final byte[] data, final int offset, final int size)
    {

        setData(data, offset, size);
    }

    BinaryString()
    {
    }

    void setData(byte[] data, int offset, int size)
    {
        _data = data;
        _offset = offset;
        _size = size;
        int hc = 0;
        for (int i = 0; i < size; i++)
        {
            hc = 31*hc + (0xFF & data[offset + i]);
        }
        _hashCode = hc;
    }


    public final int hashCode()
    {
        return _hashCode;
    }

    public final boolean equals(Object o)
    {
        BinaryString buf = (BinaryString) o;
        final int size = _size;
        if (size != buf._size)
        {
            return false;
        }

        final byte[] myData = _data;
        final byte[] theirData = buf._data;
        int myOffset = _offset;
        int theirOffset = buf._offset;
        final int myLimit = myOffset + size;

        while(myOffset < myLimit)
        {
            if (myData[myOffset++] != theirData[theirOffset++])
            {
                return false;
            }
        }

        return true;
    }


}
