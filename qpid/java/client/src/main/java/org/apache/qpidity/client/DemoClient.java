package org.apache.qpidity.client;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.api.Message;
import org.apache.qpidity.client.util.MessagePartListenerAdapter;

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

        // queue
        ssn.messageTransfer("amq.direct", (short) 0, (short) 1);
        ssn.headers(new DeliveryProperties().setRoutingKey("queue1"),new MessageProperties().setMessageId("123"));
        ssn.data("this is the data");
        ssn.endData();

        //reject
        ssn.messageTransfer("amq.direct", (short) 0, (short) 1);
        ssn.data("this should be rejected");
        ssn.headers(new DeliveryProperties().setRoutingKey("stocks"));
        ssn.endData();
        ssn.sync();
        
        // topic subs
        ssn.messageSubscribe("topic1", "myDest2", (short)0, (short)0,createAdapter(), null);
        ssn.messageSubscribe("topic2", "myDest3", (short)0, (short)0,createAdapter(), null);
        ssn.messageSubscribe("topic3", "myDest4", (short)0, (short)0,createAdapter(), null);
        ssn.sync();
        
        ssn.queueDeclare("topic1", null, null);
        ssn.queueBind("topic1", "amq.topic", "stock.*",null);        
        ssn.queueDeclare("topic2", null, null);
        ssn.queueBind("topic2", "amq.topic", "stock.us.*",null);
        ssn.queueDeclare("topic3", null, null);
        ssn.queueBind("topic3", "amq.topic", "stock.us.rh",null);
        ssn.sync();
        
        // topic
        ssn.messageTransfer("amq.topic", (short) 0, (short) 1);
        ssn.data("Topic message");
        ssn.headers(new DeliveryProperties().setRoutingKey("stock.us.ibm"),new MessageProperties().setMessageId("456"));
        ssn.endData();
        ssn.sync();
    }
    
}
