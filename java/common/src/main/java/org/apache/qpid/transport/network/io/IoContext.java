package org.apache.qpid.transport.network.io;

import java.net.Socket;
import java.nio.ByteBuffer;

import org.apache.qpid.transport.Sender;

public interface IoContext
{
    Sender<ByteBuffer> getSender();
    
    IoReceiver getReceiver();

    Socket getSocket();
}
