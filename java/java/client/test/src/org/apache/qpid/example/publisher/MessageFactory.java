/**
 * Class that creates messages from file payload
 * Author: Marnie McCormack
 * Date: 18-Jul-2006
 * Time: 10:48:38
 * Copyright JPMorgan Chase 2006
 */

package org.apache.qpid.example.publisher;

import org.apache.qpid.example.shared.FileUtils;
import org.apache.qpid.example.shared.Statics;

import java.io.*;
import javax.jms.*;

public class MessageFactory
{
    private final Session _session;
    private final String _payload;
    private final String _filename;

    public MessageFactory(Session session, String filename) throws MessageFactoryException
    {
        try
        {
            _filename = filename;
            _payload = FileUtils.getFileContent(filename);
            _session = session;
        }
        catch (IOException e)
        {
            throw new MessageFactoryException(e.toString());
        }
    }

    /*
    * Creates message and sets filename property on it
    */
    public Message createEventMessage() throws JMSException
    {
        TextMessage msg = _session.createTextMessage();
        msg.setText(_payload);
        msg.setStringProperty(Statics.FILENAME_PROPERTY,new File(_filename).getName());
        return msg;
    }

    /*
    * Creates message from a string for use by the monitor
    */
    public static Message createSimpleEventMessage(Session session, String textMsg) throws JMSException
    {
        TextMessage msg = session.createTextMessage();
        msg.setText(textMsg);
        return msg;
    }

    public Message createShutdownMessage() throws JMSException
    {
        return _session.createTextMessage("SHUTDOWN");
    }

    public Message createReportRequestMessage() throws JMSException
    {
        return _session.createTextMessage("REPORT");
    }

    public Message createReportResponseMessage(String msg) throws JMSException
    {
        return _session.createTextMessage(msg);
    }

    public boolean isShutdown(Message m)
    {
        return checkText(m, "SHUTDOWN");
    }

    public boolean isReport(Message m)
    {
        return checkText(m, "REPORT");
    }

    public Object getReport(Message m)
    {
        try
        {
            return ((TextMessage) m).getText();
        }
        catch (JMSException e)
        {
            e.printStackTrace(System.out);
            return e.toString();
        }
    }

    private static boolean checkText(Message m, String s)
    {
        try
        {
            return m instanceof TextMessage && ((TextMessage) m).getText().equals(s);
        }
        catch (JMSException e)
        {
            e.printStackTrace(System.out);
            return false;
        }
    }
}

