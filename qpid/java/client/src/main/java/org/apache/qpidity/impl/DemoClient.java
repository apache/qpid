package org.apache.qpidity.impl;

import org.apache.qpidity.DeliveryProperties;
import org.apache.qpidity.MessageProperties;
import org.apache.qpidity.QpidException;
import org.apache.qpidity.client.ExceptionListener;
import org.apache.qpidity.client.Session;
import org.apache.qpidity.client.Connection;

public class DemoClient
{

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
        ssn.queueDeclare("Queue1", null, null);
        ssn.sync();

        ssn.messageTransfer("Queue1", (short) 0, (short) 1);
        ssn.headers(new DeliveryProperties(),
                    new MessageProperties());
        ssn.data("this is the data");
        ssn.endData();

        ssn.messageTransfer("Queue2", (short) 0, (short) 1);
        ssn.data("this should be rejected");
        ssn.endData();
        ssn.sync();
    }
    
}
