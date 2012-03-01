package org.apache.qpid.codec;

import org.apache.qpid.framing.AMQShortString;

import java.io.DataInput;
import java.io.IOException;

public interface MarkableDataInput extends DataInput
{
    public void mark(int pos);
    public void reset() throws IOException;

    int available() throws IOException;

    long skip(long i) throws IOException;

    int read(byte[] b) throws IOException;

    public AMQShortString readAMQShortString() throws IOException;

}
