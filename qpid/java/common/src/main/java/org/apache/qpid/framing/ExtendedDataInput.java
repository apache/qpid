package org.apache.qpid.framing;

import java.io.DataInput;

public interface ExtendedDataInput extends DataInput
{
    AMQShortString readAMQShortString();

    int available();

    int position();

    void position(int position);
}
