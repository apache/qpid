package org.apache.qpid.nclient.interop;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.ErrorCode;
import org.apache.qpid.QpidException;
import org.apache.qpid.api.Message;
import org.apache.qpid.nclient.Client;
import org.apache.qpid.nclient.Connection;
import org.apache.qpid.nclient.ClosedListener;
import org.apache.qpid.nclient.Session;
import org.apache.qpid.nclient.util.MessageListener;
import org.apache.qpid.nclient.util.MessagePartListenerAdapter;
import org.apache.qpid.transport.DeliveryProperties;
import org.apache.qpid.transport.Header;
import org.apache.qpid.transport.MessageAcceptMode;
import org.apache.qpid.transport.MessageAcquireMode;
import org.apache.qpid.transport.MessageCreditUnit;
import org.apache.qpid.transport.MessageFlowMode;
import org.apache.qpid.transport.MessageProperties;
import org.apache.qpid.transport.RangeSet;

public class BasicInteropTest implements ClosedListener
{

    private Session session;
    private Connection conn;
    private String host;

    public BasicInteropTest(String host)
    {
        this.host = host;
    }

    public void close() throws QpidException
    {
        conn.close();
    }

    public void testCreateConnection(){
        System.out.println("------- Creating connection--------");
        conn = Client.createConnection();
        try{
            conn.connect(host, 5672, "test", "guest", "guest");
        }catch(Exception e){
            System.out.println("------- Error Creating connection--------");
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("------- Connection created Suscessfully --------");
    }

    public void testCreateSession(){
        System.out.println("------- Creating session --------");
        session = conn.createSession(0);
        System.out.println("------- Session created sucessfully --------");
    }

    public void testExchange(){
        System.out.println("------- Creating an exchange --------");
        session.exchangeDeclare("test", "direct", "", null);
        session.sync();
        System.out.println("------- Exchange created --------");
    }

    public void testQueue(){
        System.out.println("------- Creating a queue --------");
        session.queueDeclare("testQueue", "", null);
        session.sync();
        System.out.println("------- Queue created --------");

        System.out.println("------- Binding a queue --------");
        session.exchangeBind("testQueue", "test", "testKey", null);
        session.sync();
        System.out.println("------- Queue bound --------");
    }

    public void testSendMessage(){
        System.out.println("------- Sending a message --------");
        Map<String,Object> props = new HashMap<String,Object>();
        props.put("name", "rajith");
        props.put("age", 10);
        props.put("spf", 8.5);
        session.messageTransfer("test", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED,
                                new Header(new DeliveryProperties().setRoutingKey("testKey"),
                                           new MessageProperties().setApplicationHeaders(props)),
                                ByteBuffer.wrap("TestMessage".getBytes()));

        session.sync();
        System.out.println("------- Message sent --------");
    }

    public void testSubscribe()
    {
        System.out.println("------- Sending a subscribe --------");
        session.messageSubscribe("testQueue", "myDest",
                                 Session.TRANSFER_CONFIRM_MODE_REQUIRED,
                                 Session.TRANSFER_ACQUIRE_MODE_PRE_ACQUIRE,
                                 new MessagePartListenerAdapter(new MessageListener(){

                                    public void onMessage(Message message)
                                    {
                                        System.out.println("--------Message Received--------");
                                        System.out.println(message.toString());
                                        System.out.println("--------/Message Received--------");
                                        RangeSet ack = new RangeSet();
                                        ack.add(message.getMessageTransferId(),message.getMessageTransferId());
                                        session.messageAcknowledge(ack, true);
                                    }

                                 }),
                                 null);

        System.out.println("------- Setting Credit mode --------");
        session.messageSetFlowMode("myDest", MessageFlowMode.WINDOW);
        System.out.println("------- Setting Credit --------");
        session.messageFlow("myDest", MessageCreditUnit.MESSAGE, 1);
        session.messageFlow("myDest", MessageCreditUnit.BYTE, -1);
    }

    public void testMessageFlush()
    {
        session.messageFlush("myDest");
        session.sync();
    }

    public void onClosed(ErrorCode errorCode, String reason, Throwable t)
    {
        System.out.println("------- Broker Notified an error --------");
        System.out.println("------- " + errorCode + " --------");
        System.out.println("------- " + reason + " --------");
        System.out.println("------- /Broker Notified an error --------");
    }

    public static void main(String[] args) throws QpidException
    {
        String host = "0.0.0.0";
        if (args.length>0)
        {
            host = args[0];
        }

        BasicInteropTest t = new BasicInteropTest(host);
        t.testCreateConnection();
        t.testCreateSession();
        t.testExchange();
        t.testQueue();
        t.testSubscribe();
        t.testSendMessage();
        t.testMessageFlush();
        t.close();
    }
}
