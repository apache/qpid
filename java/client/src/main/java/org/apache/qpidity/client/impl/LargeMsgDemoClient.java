package org.apache.qpidity.client.impl;

import java.io.FileInputStream;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.client.Client;
import org.apache.qpidity.client.Connection;
import org.apache.qpidity.client.ExceptionListener;
import org.apache.qpidity.client.MessageListener;
import org.apache.qpidity.client.Session;
import org.apache.qpidity.client.util.FileMessage;
import org.apache.qpidity.client.util.MessagePartListenerAdapter;

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
        ssn.setExceptionListener(new ExceptionListener()
                {
                     public void onException(QpidException e)
                     {
                         System.out.println(e);
                     }
                });
        ssn.queueDeclare("queue1", null, null);
        ssn.queueBind("queue1", "amq.direct", "queue1",null);
        ssn.sync();
        
        ssn.messageSubscribe("queue1", "myDest", (short)0, (short)0,createAdapter(), null);

        try
        {
           FileMessage msg = new FileMessage(new FileInputStream("/home/rajith/TestFile"),
                                             1024,
                                             new DeliveryProperties().setRoutingKey("queue1"),
                                             new MessageProperties().setMessageId("123"));
        
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
