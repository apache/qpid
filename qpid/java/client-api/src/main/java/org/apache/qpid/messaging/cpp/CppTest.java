package org.apache.qpid.messaging.cpp;

import org.apache.qpid.messaging.Connection;
import org.apache.qpid.messaging.ConnectionFactory;
import org.apache.qpid.messaging.Message;
import org.apache.qpid.messaging.Receiver;
import org.apache.qpid.messaging.Session;
import org.apache.qpid.messaging.Sender;

public class CppTest
{

    public static void main(String[] args)
    {
        System.out.println(System.getProperty("sys.path"));
        
        Connection con = ConnectionFactory.get().createConnection("localhost:5672");
        con.open();
        Session ssn = con.createSession("hello");
        System.out.println("Got a session object "  + ssn);
        
        Sender sender = ssn.createSender("amq.topic/test");
        System.out.println("Got a Sender object "  + sender);

        Receiver receiver = ssn.createReceiver("amq.topic/test");
        System.out.println("Got a Receiver object "  + receiver);

        Message msg = new TextMessage("Hello World");
        sender.send(msg, false);
        TextMessage m = (TextMessage) receiver.fetch(0);
        System.out.println("Received message "  + m + " with content type : " + m.getContentType() + " and content : " + m.getContent());
        
        ssn.close();
        con.close();
    }

}
