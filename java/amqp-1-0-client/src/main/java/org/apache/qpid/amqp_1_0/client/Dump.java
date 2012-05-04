package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.commons.cli.Options;

public class Dump extends Util
{
    private static final String USAGE_STRING = "dump [options] <address>\n\nOptions:";


    protected Dump(String[] args)
    {
        super(args);
    }

    public static void main(String[] args)
    {
        new Dump(args).run();
    }

    @Override
    protected boolean hasLinkDurableOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasLinkNameOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasResponseQueueOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasSizeOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasBlockOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasStdInOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasTxnOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasModeOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected boolean hasCountOption()
    {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void printUsage(Options options)
    {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    protected void run()
    {
        final String queue = getArgs()[0];

        try
        {
            Connection conn = newConnection();

            Session session = conn.createSession();


            Sender s = session.createSender(queue, 10);

            Message message = new Message("dump me");
            message.setDeliveryTag(new Binary("dump".getBytes()));

            s.send(message);

            s.close();
            session.close();
            conn.close();

        } catch (Connection.ConnectionException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        catch (Sender.SenderCreationException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (Sender.SenderClosingException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }
}
