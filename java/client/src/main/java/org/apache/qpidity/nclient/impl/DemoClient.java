package org.apache.qpidity.nclient.impl;

import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.nclient.Client;
import org.apache.qpidity.nclient.Connection;
import org.apache.qpidity.nclient.ClosedListener;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.nclient.util.MessageListener;
import org.apache.qpidity.nclient.util.MessagePartListenerAdapter;
import org.apache.qpidity.transport.DeliveryProperties;
import org.apache.qpidity.transport.MessageAcceptMode;
import org.apache.qpidity.transport.MessageAcquireMode;
import org.apache.qpidity.transport.MessageProperties;

import java.util.UUID;

public class DemoClient
{
    public static MessagePartListenerAdapter createAdapter()
    {
        return new MessagePartListenerAdapter(new MessageListener()
        {
            public void onMessage(Message m)
            {
                System.out.println("\n================== Received Msg ==================");
                System.out.println("Message Id : " + m.getMessageProperties().getMessageId());
                System.out.println(m.toString());
                System.out.println("================== End Msg ==================\n");
            }

        });
    }

    public static final void main(String[] args)
    {
        Connection conn = Client.createConnection();
        try{
            conn.connect("0.0.0.0", 5672, "test", "guest", "guest");
        }catch(Exception e){
            e.printStackTrace();
        }

        Session ssn = conn.createSession(50000);
        ssn.setClosedListener(new ClosedListener()
                {
                     public void onClosed(ErrorCode errorCode, String reason, Throwable t)
                     {
                         System.out.println("ErrorCode : " + errorCode + " reason : " + reason);
                     }
                });
        ssn.queueDeclare("queue1", null, null);
        ssn.exchangeBind("queue1", "amq.direct", "queue1",null);
        ssn.sync();

        ssn.messageSubscribe("queue1", "myDest", (short)0, (short)0,createAdapter(), null);

        // queue
        ssn.messageTransfer("amq.direct", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED);
        ssn.header(new DeliveryProperties().setRoutingKey("queue1"),
                   new MessageProperties().setMessageId(UUID.randomUUID()));
        ssn.data("this is the data");
        ssn.endData();

        //reject
        ssn.messageTransfer("amq.direct", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED);
        ssn.data("this should be rejected");
        ssn.header(new DeliveryProperties().setRoutingKey("stocks"));
        ssn.endData();
        ssn.sync();

        // topic subs
        ssn.messageSubscribe("topic1", "myDest2", (short)0, (short)0,createAdapter(), null);
        ssn.messageSubscribe("topic2", "myDest3", (short)0, (short)0,createAdapter(), null);
        ssn.messageSubscribe("topic3", "myDest4", (short)0, (short)0,createAdapter(), null);
        ssn.sync();

        ssn.queueDeclare("topic1", null, null);
        ssn.exchangeBind("topic1", "amq.topic", "stock.*",null);
        ssn.queueDeclare("topic2", null, null);
        ssn.exchangeBind("topic2", "amq.topic", "stock.us.*",null);
        ssn.queueDeclare("topic3", null, null);
        ssn.exchangeBind("topic3", "amq.topic", "stock.us.rh",null);
        ssn.sync();

        // topic
        ssn.messageTransfer("amq.topic", MessageAcceptMode.NONE, MessageAcquireMode.PRE_ACQUIRED);
        ssn.data("Topic message");
        ssn.header(new DeliveryProperties().setRoutingKey("stock.us.ibm"),
                   new MessageProperties().setMessageId(UUID.randomUUID()));
        ssn.endData();
    }

}
