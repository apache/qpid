package org.apache.qpidity;

import java.nio.ByteBuffer;
import java.util.*;
import java.lang.annotation.*;

public class Stub {

    private static Connection conn = new Connection();

    private static void frame(short track, short type, boolean first, boolean last) {
        frame(track, type, first, last, null);
    }

    private static void frame(short track, short type, boolean first, boolean last, Method m) {
        ByteBuffer buf = ByteBuffer.allocate(512);
        if (m != null) {
            buf.putInt(m.getEncodingType());
            m.write(new BBEncoder(buf));
        }
        buf.flip();
        Frame frame = new Frame((short)0, track, type, true, true, first, last, buf);
        conn.handle(frame);
    }

    public static final void main(String[] args) {
        StructFactory f = new StructFactory_v0_10();
        frame(Frame.L2, Frame.METHOD, true, true, f.newSessionOpen(0));
        frame(Frame.L4, Frame.METHOD, true, false, f.newQueueDeclare((short) 0, "asdf", "alternate", false, false, false, false, false, null));
        frame(Frame.L4, Frame.METHOD, false, false);
        frame(Frame.L3, Frame.METHOD, true, true, f.newExchangeDeclare((short) 0, "exchange", "type", "alternate", false, false, false, null));
        frame(Frame.L4, Frame.METHOD, false, true);
        frame(Frame.L4, Frame.HEADER, true, false);
        frame(Frame.L4, Frame.HEADER, false, false);
        frame(Frame.L4, Frame.HEADER, false, true);
        frame(Frame.L4, Frame.BODY, true, false);
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L1, Frame.METHOD, true, true, f.newExchangeDeclare((short) 0, "exchange", "type", "alternate", false, false, false, null));
        frame(Frame.L4, Frame.BODY, false, false);
        frame(Frame.L4, Frame.BODY, false, true);
    }

}

//====: Channel and Session Delegates :=======================================//

class ChannelDelegate extends Delegate<Channel> {

    public @Override void sessionOpen(Channel channel, SessionOpen open) {
        Session ssn = new Session();
        ssn.attach(channel);
        System.out.println("Session Open");
    }

}

class SessionDelegate extends Delegate<Session> {

    public @Override void queueDeclare(Session session, QueueDeclare qd) {
        System.out.println("got a queue declare: " + qd.getQueue());
    }

    public @Override void exchangeDeclare(Session session, ExchangeDeclare ed) {
        System.out.println("got an exchange declare: " + ed.getExchange() + ", " + ed.getType());
        session.queueDeclare((short) 0, "asdf", "alternate", false, false, false, false, false, null);
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
