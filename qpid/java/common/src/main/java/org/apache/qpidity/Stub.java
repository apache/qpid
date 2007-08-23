package org.apache.qpidity;

import java.util.*;
import java.lang.annotation.*;

import java.nio.ByteBuffer;

import org.apache.qpidity.codec.BBEncoder;
import org.apache.qpidity.codec.Encoder;
import org.apache.qpidity.codec.SizeEncoder;

import static org.apache.qpidity.Option.*;


public class Stub {

    private static final byte major = 0;
    private static final byte minor = 10;

    private static Connection conn = new Connection(new ConsoleOutput(),
                                                    SessionDelegateStub.source());

    static
    {
        conn.init(new ProtocolHeader((byte) 1, major, minor));
    }

    private static void frame(byte track, byte type, boolean first, boolean last) {
        frame(track, type, first, last, null);
    }

    private static void frame(byte track, byte type, boolean first, boolean last, Method m) {
        SizeEncoder sizer = new SizeEncoder(major, minor);
        if (m != null) {
            sizer.writeLong(m.getEncodedType());
            m.write(sizer, major, minor);
            sizer.flush();
        }
        ByteBuffer buf = ByteBuffer.allocate(sizer.getSize());
        if (m != null) {
            Encoder enc = new BBEncoder(major, minor, buf);
            enc.writeLong(m.getEncodedType());
            m.write(enc, major, minor);
            enc.flush();
        }
        buf.flip();
        byte flags = Frame.VERSION;
        if (first) { flags |= Frame.FIRST_FRAME; }
        if (last) { flags |= Frame.LAST_FRAME; }
        Frame frame = new Frame(flags, type, track, 0);
        frame.addFragment(buf);
        conn.frame(frame);
    }

    public static final void main(String[] args) {
        frame(Frame.L2, Frame.METHOD, true, true, new SessionOpen(0));
        frame(Frame.L4, Frame.METHOD, true, false,
              new QueueDeclare("asdf", "alternate", null, DURABLE));
        frame(Frame.L4, Frame.METHOD, false, false);
        frame(Frame.L3, Frame.METHOD, true, true,
              new ExchangeDeclare("exchange", "type", "alternate", null));
        frame(Frame.L4, Frame.METHOD, false, true);
        frame(Frame.L4, Frame.HEADER, true, false);
        frame(Frame.L4, Frame.HEADER, false, false);
        frame(Frame.L4, Frame.HEADER, false, true);
        frame(Frame.L4, Frame.BODY, true, false);
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L1, Frame.METHOD, true, true,
              new ExchangeDeclare("exchange", "type", "alternate", null));
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L4, Frame.BODY, false, true);
    }

}

class SessionDelegateStub extends SessionDelegate {

    public static final ConnectionDelegate source()
    {
        return new ConnectionDelegate()
        {
            public SessionDelegate getSessionDelegate()
            {
                return new SessionDelegateStub();
            }
        };
    }

    public @Override void queueDeclare(Session session, QueueDeclare qd) {
        System.out.println("got a queue declare: " + qd.getQueue());
    }

    public @Override void exchangeDeclare(Session session, ExchangeDeclare ed) {
        System.out.println("got an exchange declare: " + ed.getExchange() + ", " + ed.getType());
        session.queueDeclare("asdf", "alternate", null);
    }

    public void data(Session ssn, Frame frame)
    {
        System.out.println("got data: " + frame);
    }

    public void headers(Session ssn, Struct ... headers)
    {
        System.out.println("got headers: " + headers);
    }

}
