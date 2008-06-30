package org.apache.qpid.testkit;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

public class MessageFactory
{
    public static Message createBytesMessage(Session ssn, int size) throws JMSException
    {
        BytesMessage msg = ssn.createBytesMessage();
        msg.writeBytes(createMessagePayload(size).getBytes());
        return msg;
    }

    public static Message createTextMessage(Session ssn, int size) throws JMSException
    {
        TextMessage msg = ssn.createTextMessage();
        msg.setText(createMessagePayload(size));
        return msg;
    }

    public static String createMessagePayload(int size)
    {
        String msgData = "Qpid Test Message";

        StringBuffer buf = new StringBuffer(size);
        int count = 0;
        while (count <= (size - msgData.length()))
        {
            buf.append(msgData);
            count += msgData.length();
        }
        if (count < size)
        {
            buf.append(msgData, 0, size - count);
        }

        return buf.toString();
    }
}
