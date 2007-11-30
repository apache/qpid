package org.apache.qpidity.transport.network.nio;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpidity.transport.Connection;
import org.apache.qpidity.transport.ConnectionDelegate;
import org.apache.qpidity.transport.Receiver;
import org.apache.qpidity.transport.network.Assembler;
import org.apache.qpidity.transport.network.Disassembler;
import org.apache.qpidity.transport.network.InputHandler;
import org.apache.qpidity.transport.network.OutputHandler;

public class NioHandler implements Runnable
{
    private Receiver<ByteBuffer> _receiver;
    private SocketChannel _ch;
    private ByteBuffer _readBuf;
    private static Map<Integer,NioSender> _handlers = new ConcurrentHashMap<Integer,NioSender>();
    private AtomicInteger _count = new AtomicInteger();

    private NioHandler(){}

    public static final Connection connect(String host, int port,
            ConnectionDelegate delegate)
    {
        NioHandler handler = new NioHandler();
        return handler.connectInternal(host,port,delegate);
    }

    private Connection connectInternal(String host, int port,
            ConnectionDelegate delegate)
    {
        try
        {
            SocketAddress address = new InetSocketAddress(host,port);
            _ch = SocketChannel.open();
            _ch.socket().setReuseAddress(true);
            _ch.configureBlocking(true);
            _ch.socket().setTcpNoDelay(true);
            if (address != null)
            {
                _ch.socket().connect(address);
            }
            while (_ch.isConnectionPending())
            {

            }

        }
        catch (SocketException e)
        {

            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        NioSender sender = new NioSender(_ch);
        Connection con = new Connection
            (new Disassembler(new OutputHandler(sender), 64*1024 - 1),
             delegate);

        con.setConnectionId(_count.incrementAndGet());
        _handlers.put(con.getConnectionId(),sender);

        _receiver = new InputHandler(new Assembler(con), InputHandler.State.FRAME_HDR);

        Thread t = new Thread(this);
        t.start();

        return con;
    }

    public void run()
    {
        _readBuf = ByteBuffer.allocate(1024);
        long read = 0;
        while(_ch.isConnected() && _ch.isOpen())
        {
            try
            {
                read = _ch.read(_readBuf);
                if (read > 0)
                {
                    ByteBuffer b = _readBuf;
                    b.flip();
                    _receiver.received(b);
                    _readBuf.clear();
                }
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        //throw new EOFException("The underlying socket/channel has closed");
    }

    public static void startBatchingFrames(int connectionId)
    {
        NioSender sender = _handlers.get(connectionId);
        sender.setStartBatching();
    }


}
