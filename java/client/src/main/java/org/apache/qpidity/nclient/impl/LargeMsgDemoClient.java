package org.apache.qpidity.nclient.impl;

import java.io.FileInputStream;

import org.apache.qpidity.ErrorCode;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.nclient.Client;
import org.apache.qpidity.nclient.Connection;
import org.apache.qpidity.nclient.ClosedListener;
import org.apache.qpidity.nclient.Session;
import org.apache.qpidity.nclient.util.FileMessage;
import org.apache.qpidity.nclient.util.MessageListener;
import org.apache.qpidity.nclient.util.MessagePartListenerAdapter;
import org.apache.qpidity.transport.DeliveryProperties;
import org.apache.qpidity.transport.MessageProperties;

import java.util.UUID;

public class LargeMsgDemoClient
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

        try
        {
           FileMessage msg = new FileMessage(new FileInputStream("/home/rajith/TestFile"),
                                             1024,
                                             new DeliveryProperties().setRoutingKey("queue1"),
                                             new MessageProperties().setMessageId(UUID.randomUUID()));

           // queue
           ssn.messageStream("amq.direct",msg, (short) 0, (short) 1);
           ssn.sync();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

}
