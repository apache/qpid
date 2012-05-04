package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Outcome;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Filesender extends Util
{
    private static final String USAGE_STRING = "filesender [options] <address> <directory>\n\nOptions:";

    protected Filesender(String[] args)
    {
        super(args);
    }

    @Override
    protected boolean hasLinkDurableOption()
    {
        return true;
    }

    @Override
    protected boolean hasLinkNameOption()
    {
        return true;
    }

    @Override
    protected boolean hasResponseQueueOption()
    {
        return false;
    }

    @Override
    protected boolean hasSizeOption()
    {
        return false;
    }

    @Override
    protected boolean hasBlockOption()
    {
        return false;
    }

    @Override
    protected boolean hasStdInOption()
    {
        return false;
    }

    @Override
    protected boolean hasTxnOption()
    {
        return false;
    }

    @Override
    protected boolean hasModeOption()
    {
        return false;
    }

    @Override
    protected boolean hasCountOption()
    {
        return false;
    }

    @Override
    protected void printUsage(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(USAGE_STRING, options );

    }

    @Override
    protected void run()
    {
        final String queue = getArgs()[0];
        final String directoryName = getArgs()[1];

        try
        {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            Connection conn = newConnection();

            Session session = conn.createSession();

            File directory = new File(directoryName);
            if(directory.isDirectory() && directory.canWrite())
            {

                File tmpDirectory = new File(directoryName, ".tmp");
                if(!tmpDirectory.exists())
                {
                    tmpDirectory.mkdir();
                }

                String[] unsettledFiles = tmpDirectory.list();



                Map<Binary, Outcome> unsettled = new HashMap<Binary, Outcome>();
                Map<Binary, String> unsettledFileNames = new HashMap<Binary, String>();
                for(String fileName : unsettledFiles)
                {
                    File aFile = new File(tmpDirectory, fileName);
                    if(aFile.canRead() && aFile.canWrite())
                    {
                        Binary deliveryTag = new Binary(md5.digest(fileName.getBytes()));
                        unsettled.put(deliveryTag, null);
                        unsettledFileNames.put(deliveryTag, fileName);
                    }
                }


                Sender s = session.createSender(queue, 10, AcknowledgeMode.EO, getLinkName(), isDurableLink(),
                                                unsettled);

                Map<Binary, DeliveryState> remoteUnsettled = s.getRemoteUnsettled();

                for(Map.Entry<Binary, String> entry: unsettledFileNames.entrySet())
                {
                    if(remoteUnsettled == null || !remoteUnsettled.containsKey(entry.getKey()))
                    {
                        (new File(tmpDirectory, entry.getValue())).renameTo(new File(directory, entry.getValue()));
                    }
                }

                if(remoteUnsettled != null)
                {
                    for(Map.Entry<Binary, DeliveryState> entry : remoteUnsettled.entrySet())
                    {
                        if(entry.getValue() instanceof Accepted)
                        {
                            final String fileName = unsettledFileNames.get(entry.getKey());
                            if(fileName != null)
                            {

                                Message resumed = new Message();
                                resumed.setDeliveryTag(entry.getKey());
                                resumed.setDeliveryState(entry.getValue());
                                resumed.setResume(Boolean.TRUE);
                                resumed.setSettled(Boolean.TRUE);



                                final File unsettledFile = new File(tmpDirectory, fileName);
                                unsettledFile.delete();

                                s.send(resumed);

                            }

                        }
                        else if(entry.getValue() instanceof Received || entry.getValue() == null)
                        {
                            final File unsettledFile = new File(tmpDirectory, unsettledFileNames.get(entry.getKey()));
                            Message resumed = createMessageFromFile(md5, unsettledFileNames.get(entry.getKey()), unsettledFile);
                            resumed.setResume(Boolean.TRUE);
                            Sender.OutcomeAction action = new Sender.OutcomeAction()
                            {
                                public void onOutcome(Binary deliveryTag, Outcome outcome)
                                {
                                    if(outcome instanceof Accepted)
                                    {
                                        unsettledFile.delete();
                                    }
                                }
                            };
                            s.send(resumed, action);

                        }
                    }
                }



                String[] files = directory.list();

                for(String fileName : files)
                {
                    final File file = new File(directory, fileName);

                    if(file.canRead() && file.canWrite() && !file.isDirectory())
                    {
                        Message message = createMessageFromFile(md5, fileName, file);

                        final File unsettledFile = new File(tmpDirectory, fileName);

                        Sender.OutcomeAction action = new Sender.OutcomeAction()
                        {
                            public void onOutcome(Binary deliveryTag, Outcome outcome)
                            {
                                if(outcome instanceof Accepted)
                                {
                                    unsettledFile.delete();
                                }
                            }
                        };

                        file.renameTo(unsettledFile);

                        s.send(message, action);
                    }
                }

                s.close();
            }
            else
            {
                System.err.println("No such directory: " + directory);
            }
            session.close();
            conn.close();
        }
        catch (Connection.ConnectionException e)
        {
            e.printStackTrace();
        }
        catch (Sender.SenderCreationException e)
        {
            e.printStackTrace();
        } catch (FileNotFoundException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (NoSuchAlgorithmException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (Sender.SenderClosingException e)
        {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }

    }

    private Message createMessageFromFile(MessageDigest md5, String fileName, File file) throws IOException
    {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];

        int read = fis.read(data);

        fis.close();

        Section applicationProperties = new ApplicationProperties(Collections.singletonMap("filename", fileName));
        Section amqpValue = new Data(new Binary(data));
        Message message = new Message(Arrays.asList(applicationProperties, amqpValue));
        Binary deliveryTag = new Binary(md5.digest(fileName.getBytes()));
        message.setDeliveryTag(deliveryTag);
        md5.reset();
        return message;
    }

    public static void main(String[] args)
    {
        new Filesender(args).run();
    }
}
