package org.apache.qpidity;

import java.util.*;
import java.lang.annotation.*;

import java.nio.ByteBuffer;

import static org.apache.qpidity.Option.*;


public class Stub {

    private static Connection conn = new Connection(new ConsoleOutput());

    static
    {
        conn.init(new ProtocolHeader((byte) 1, (byte) 0, (byte) 10));
    }

    private static void frame(byte track, byte type, boolean first, boolean last) {
        frame(track, type, first, last, null);
    }

    private static void frame(byte track, byte type, boolean first, boolean last, Method m) {
        SizeEncoder sizer = new SizeEncoder();
        if (m != null) {
            sizer.writeLong(m.getEncodedType());
            m.write(sizer);
            sizer.flush();
        }
        ByteBuffer buf = ByteBuffer.allocate(sizer.getSize());
        if (m != null) {
            Encoder enc = new BBEncoder(buf);
            enc.writeLong(m.getEncodedType());
            m.write(enc);
            enc.flush();
        }
        buf.flip();
        byte flags = 0;
        if (first) { flags |= Frame.FIRST_FRAME; }
        if (last) { flags |= Frame.LAST_FRAME; }
        Frame frame = new Frame(flags, type, track, 0);
        frame.addFragment(buf);
        conn.frame(frame);
    }

    public static final void main(String[] args) {
        StructFactory f = new StructFactory_v0_10();
        frame(Frame.L2, Frame.METHOD, true, true, f.newSessionOpen(0));
        frame(Frame.L4, Frame.METHOD, true, false,
              f.newQueueDeclare("asdf", "alternate", null, DURABLE));
        frame(Frame.L4, Frame.METHOD, false, false);
        frame(Frame.L3, Frame.METHOD, true, true,
              f.newExchangeDeclare("exchange", "type", "alternate", null));
        frame(Frame.L4, Frame.METHOD, false, true);
        frame(Frame.L4, Frame.HEADER, true, false);
        frame(Frame.L4, Frame.HEADER, false, false);
        frame(Frame.L4, Frame.HEADER, false, true);
        frame(Frame.L4, Frame.BODY, true, false);
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L1, Frame.METHOD, true, true,
              f.newExchangeDeclare("exchange", "type", "alternate", null));
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L4, Frame.BODY, false, true);
    }

}

//====: Channel and Session Delegates :=======================================//

class ChannelDelegate extends Delegate<Channel> {

    public @Override void sessionOpen(Channel channel, SessionOpen open) {
        Session ssn = new Session();
        ssn.attach(channel);
        long lifetime = open.getDetachedLifetime();
        System.out.println("Session Opened lifetime = " + lifetime);
        try
        {
            ssn.sessionAttached(UUID.randomUUID(), lifetime);
        }
        catch (QpidException e)
        {
            throw new RuntimeException(e);
        }
    }

}

class SessionDelegate extends Delegate<Session> {

    public @Override void queueDeclare(Session session, QueueDeclare qd) {
        System.out.println("got a queue declare: " + qd.getQueue());
    }

    public @Override void exchangeDeclare(Session session, ExchangeDeclare ed) {
        System.out.println("got an exchange declare: " + ed.getExchange() + ", " + ed.getType());
        try
        {
            session.queueDeclare("asdf", "alternate", null);
        }
        catch(QpidException e)
        {
            throw new RuntimeException(e);
        }
    }

    /*
    public @Override void executionResult(Session session, ExecutionResult result) {
        Handler<Struct> handler = session.handlers.get(result.getCommandId());
        if (handler != null) {
            handler.handle(result.getData());
        }
        }
    */

}
