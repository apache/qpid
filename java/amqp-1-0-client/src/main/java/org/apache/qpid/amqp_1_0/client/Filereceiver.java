package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.type.*;
import org.apache.qpid.amqp_1_0.type.messaging.*;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

public class Filereceiver extends Util
{
    private static final String USAGE_STRING = "filereceiver [options] <address> <directory>\n\nOptions:";

    protected Filereceiver(String[] args)
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
        return true;
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
            Connection conn = newConnection();

            Session session = conn.createSession();

            final File directory = new File(directoryName);
            if(directory.isDirectory() && directory.canWrite())
            {
                File tmpDirectory = new File(directoryName, ".tmp");
                if(!tmpDirectory.exists())
                {
                    tmpDirectory.mkdir();
                }

                String[] unsettledFiles = tmpDirectory.list();

                Map<Binary, Outcome> unsettled = new HashMap<Binary, Outcome>();
                final Map<Binary, String> unsettledFileNames = new HashMap<Binary, String>();

                Accepted accepted = new Accepted();

                for(String fileName : unsettledFiles)
                {
                    File theFile  = new File(tmpDirectory, fileName);
                    if(theFile.isFile())
                    {
                        if(fileName.startsWith("~") && fileName.endsWith("~"))
                        {
                            theFile.delete();
                        }
                        else
                        {
                            int splitPoint = fileName.indexOf(".");
                            String deliveryTagStr = fileName.substring(0,splitPoint);
                            String actualFileName = fileName.substring(splitPoint+1);

                            byte[] bytes = new byte[deliveryTagStr.length()/2];


                            for(int i = 0; i < bytes.length; i++)
                            {
                                char c = deliveryTagStr.charAt(2*i);
                                char d = deliveryTagStr.charAt(1+(2*i));

                                bytes[i] = (byte) (((c <= '9' ? c - '0' : c - 'W') << 4)
                                                  | (d <= '9' ? d - '0' : d - 'W'));

                            }
                            Binary deliveryTag = new Binary(bytes);
                            unsettled.put(deliveryTag, accepted);
                            unsettledFileNames.put(deliveryTag, fileName);
                        }
                    }

                }

                Receiver r = session.createReceiver(queue, AcknowledgeMode.EO, getLinkName(), isDurableLink(),
                                                    unsettled);

                Map<Binary, Outcome> remoteUnsettled = r.getRemoteUnsettled();

                for(Map.Entry<Binary, String> entry : unsettledFileNames.entrySet())
                {
                    if(remoteUnsettled == null || !remoteUnsettled.containsKey(entry.getKey()))
                    {

                        File tmpFile = new File(tmpDirectory, entry.getValue());
                        final File dest = new File(directory,
                                entry.getValue().substring(entry.getValue().indexOf(".") + 1));
                        if(dest.exists())
                        {
                            System.err.println("Duplicate detected - filename " + dest.getName());
                        }

                        tmpFile.renameTo(dest);
                    }
                }


                int credit = 10;

                r.setCredit(UnsignedInteger.valueOf(credit), true);


                int received = 0;
                Message m = null;
                do
                {
                    m = isBlock() && received == 0 ? r.receive() : r.receive(10000);
                    if(m != null)
                    {
                        if(m.isResume() && unsettled.containsKey(m.getDeliveryTag()))
                        {
                            final String tmpFileName = unsettledFileNames.get(m.getDeliveryTag());
                            final File unsettledFile = new File(tmpDirectory,
                                    tmpFileName);
                            r.acknowledge(m, new Receiver.SettledAction()
                                {
                                    public void onSettled(final Binary deliveryTag)
                                    {
                                        int splitPoint = tmpFileName.indexOf(".");

                                        String fileName = tmpFileName.substring(splitPoint+1);

                                        final File dest = new File(directory, fileName);
                                        if(dest.exists())
                                        {
                                            System.err.println("Duplicate detected - filename " + dest.getName());
                                        }
                                        unsettledFile.renameTo(dest);
                                        unsettledFileNames.remove(deliveryTag);
                                    }
                                });
                        }
                        else
                        {
                            received++;
                            List<Section> sections = m.getPayload();
                            Binary deliveryTag = m.getDeliveryTag();
                            StringBuilder tagNameBuilder = new StringBuilder();

                            ByteBuffer dtbuf = deliveryTag.asByteBuffer();
                            while(dtbuf.hasRemaining())
                            {
                                tagNameBuilder.append(String.format("%02x", dtbuf.get()));
                            }


                            ApplicationProperties properties = null;
                            List<Binary> data = new ArrayList<Binary>();
                            int totalSize = 0;
                            for(Section section : sections)
                            {
                                if(section instanceof ApplicationProperties)
                                {
                                    properties = (ApplicationProperties) section;
                                }
                                else if(section instanceof AmqpValue)
                                {
                                    AmqpValue value = (AmqpValue) section;
                                    if(value.getValue() instanceof Binary)
                                    {
                                        Binary binary = (Binary) value.getValue();
                                        data.add(binary);
                                        totalSize += binary.getLength();

                                    }
                                    else
                                    {
                                        // TODO exception
                                    }
                                }
                                else if(section instanceof Data)
                                {
                                    Data value = (Data) section;
                                    Binary binary = value.getValue();
                                    data.add(binary);
                                    totalSize += binary.getLength();

                                }
                            }
                            if(properties != null)
                            {
                                final String fileName = (String) properties.getValue().get("filename");
                                byte[] fileData = new byte[totalSize];
                                ByteBuffer buf = ByteBuffer.wrap(fileData);
                                int offset = 0;
                                for(Binary bin : data)
                                {
                                    buf.put(bin.asByteBuffer());
                                }
                                File outputFile = new File(tmpDirectory, "~"+fileName+"~");
                                if(outputFile.exists())
                                {
                                    outputFile.delete();
                                }
                                FileOutputStream fos = new FileOutputStream(outputFile);
                                fos.write(fileData);
                                fos.flush();
                                fos.close();

                                final File unsettledFile = new File(tmpDirectory, tagNameBuilder.toString() + "." +
                                                                   fileName);
                                outputFile.renameTo(unsettledFile);
                                r.acknowledge(m, new Receiver.SettledAction()
                                {
                                    public void onSettled(final Binary deliveryTag)
                                    {
                                        final File dest = new File(directory, fileName);
                                        if(dest.exists())
                                        {
                                            System.err.println("Duplicate detected - filename " + dest.getName());
                                        }
                                        unsettledFile.renameTo(dest);

                                    }
                                });

                            }
                        }
                    }
                }
                while(m != null);


                r.close();
            }
            else
            {
                System.err.println("No such directory: " + directoryName);
            }
            session.close();
            conn.close();
        }
        catch (Connection.ConnectionException e)
        {
            e.printStackTrace();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (IOException e)
        {
            e.printStackTrace();  //TODO.
        }
        catch (AmqpErrorException e)
        {
            e.printStackTrace();  //TODO.
        }

    }

    public static void main(String[] args)
    {
        new Filereceiver(args).run();
    }
}
