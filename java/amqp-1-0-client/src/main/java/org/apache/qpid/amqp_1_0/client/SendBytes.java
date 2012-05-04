/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.qpid.amqp_1_0.client;

import org.apache.qpid.amqp_1_0.codec.FrameWriter;
import org.apache.qpid.amqp_1_0.codec.ValueWriter;
import org.apache.qpid.amqp_1_0.framing.AMQFrame;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.FrameBody;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.UnsignedByte;
import org.apache.qpid.amqp_1_0.type.UnsignedInteger;
import org.apache.qpid.amqp_1_0.type.UnsignedLong;
import org.apache.qpid.amqp_1_0.type.UnsignedShort;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;
import org.apache.qpid.amqp_1_0.type.transport.Flow;

import org.apache.qpid.amqp_1_0.type.transport.Transfer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SendBytes
{

    public static void main(String[] args) throws
                                           Sender.SenderCreationException,
                                           Sender.SenderClosingException,
                                           Connection.ConnectionException,
                                           IOException, ParseException
    {
        Transfer xfr = new Transfer();
        Flow fs = new Flow();
        fs.setIncomingWindow(UnsignedInteger.valueOf(1024));
        fs.setDeliveryCount(UnsignedInteger.valueOf(2));
        fs.setLinkCredit(UnsignedInteger.valueOf(18));
        fs.setAvailable(UnsignedInteger.valueOf(0));
        fs.setDrain(false);

        xfr.setHandle(UnsignedInteger.valueOf(0));
        xfr.setDeliveryTag(new Binary("\"queue\"<-6ec024a7-d98e-4196-9348-15f6026c32ca:0".getBytes()));
        //xfr.setDeliveryTag(new Binary(new byte[] {0}));
        xfr.setDeliveryId(UnsignedInteger.valueOf(0));
        xfr.setSettled(true);


        Header h = new Header();
        Properties p = new Properties();
        p.setTo("queue");
        //p.setMessageId(new Binary(UUID.randomUUID().toString().getBytes()));

        Footer f = new Footer(Collections.EMPTY_MAP);

        Section[] sections = new Section[] { h,p,f};
        //Section[] sections = new Section[] { b };
        //Section[] sections = { h,p, b};
/*
        Fragment[] fragments = new Fragment[5];

        final AMQPDescribedTypeRegistry typeRegistry = AMQPDescribedTypeRegistry.newInstance().registerTransportLayer().registerMessagingLayer();

        SectionEncoderImpl encoder = new SectionEncoderImpl(typeRegistry);

        int num = 0;
        int i = 0;
        for(Section s : sections)
        {
            Fragment frag = new Fragment();

            frag.setPayload(s.encode(encoder));
            frag.setFirst(true);
            frag.setLast(true);
            frag.setSectionCode(s.getSectionCode());
            frag.setSectionNumber(UnsignedInteger.valueOf(num++));
            frag.setSectionOffset(UnsignedLong.valueOf(0L));
            fragments[i++] =frag;
        }

        xfr.setFragments(fragments);
*/

        encodeTypes("xfr",xfr);

        final byte[] result;
        final Object input = xfr;
/*
        result = encode(1024, input);

        boolean ok = true;

        for(int j = 10; ok && j < 400; j++)
        {

            byte[] result2 = encode(j,input);

            for(int i = 0; i <400; i++)
            {
                if(result[i] != result2[i])
                {
                    System.out.println("result differs at " + i + " Splitting at " + j+ " [" + result[i] + " - " + result2[i] + "]");
                    //break;
                    //ok = false;

                }
            }
        }*/
        //System.out.println(Arrays.equals(result, result2));

        //doEncodes();
        /*OutputStream out = System.out;
        if(args.length > 0)
        {
            out = new FileOutputStream(args[0]);
        }

        Transfer xfr = new Transfer();
        fs.setSessionCredit(UnsignedInteger.valueOf(1024));
        fs.setTransferCount(UnsignedInteger.valueOf(2));
        fs.setLinkCredit(UnsignedInteger.valueOf(18));
        fs.setAvailable(UnsignedInteger.valueOf(0));
        fs.setDrain(false);

        xfr.setHandle(UnsignedInteger.valueOf(0));
        //xfr.setDeliveryTag(new Binary("\"queue\"<-6ec024a7-d98e-4196-9348-15f6026c32ca:0".getBytes()));
        xfr.setDeliveryTag(new Binary(new byte[] {0}));
        xfr.setTransferId(UnsignedInteger.valueOf(0));
        xfr.setSettled(true);
        xfr.setFlowState(fs);

        Header h = new Header();
        h.setTransmitTime(new Date(System.currentTimeMillis()));
        Properties p = new Properties();
        p.setTo(new Address("queue"));
        //p.setMessageId(new Binary(UUID.randomUUID().toString().getBytes()));
        AmqpMapSection m = new AmqpMapSection();
        DataSection b = new DataSection("Hello World!".getBytes());

        Footer f = new Footer();

        Section[] sections = new Section[] { h,p,m,b,f};
        //Section[] sections = new Section[] { b };
        //Section[] sections = { h,p, b};
        List<Fragment> fragments = new ArrayList<Fragment>(5);

        final AMQPDescribedTypeRegistry typeRegistry = AMQPDescribedTypeRegistry.newInstance();

        SectionEncoderImpl encoder = new SectionEncoderImpl(typeRegistry);

        for(Section s : sections)
        {
            Fragment frag = new Fragment();

            frag.setPayload(s.encode(encoder));
            frag.setFirst(true);
            frag.setLast(true);
            frag.setFormatCode(s.getSectionCode());
            frag.setFragmentOffset(null);
            fragments.add(frag);
        }

        xfr.setFragments(fragments);


        Object[] objectsToWrite = new Object[] { xfr };
        ByteBuffer buf = ByteBuffer.allocate(4096);


        for(Object obj : objectsToWrite)
        {
            ValueWriter writer = typeRegistry.getValueWriter(obj);

            int count;


            do
            {
                count = writer.writeToBuffer(buf);
                out.write(buf.array(), buf.arrayOffset(), count);
                buf.clear();
            } while (!writer.isComplete());

        }

        out.flush();
        out.close();*/

    }

    public static void doEncodes() throws IOException, ParseException
    {
        encodeTypes("boolean",  Boolean.TRUE,  Boolean.FALSE);
        encodeTypes("ubyte",  UnsignedByte.valueOf((byte)0), UnsignedByte.valueOf((byte)1 ),UnsignedByte.valueOf((byte)3), UnsignedByte.valueOf((byte)42), UnsignedByte.valueOf("255"));
        encodeTypes("byte",  Byte.valueOf((byte)0), Byte.valueOf( (byte)1), Byte.valueOf((byte) 3), Byte.valueOf((byte) 42), Byte.valueOf((byte) 127), Byte.valueOf((byte) -1), Byte.valueOf((byte) -3), Byte.valueOf((byte) -42), Byte.valueOf( (byte)-128));
        encodeTypes("ushort",  UnsignedShort.valueOf((short)0), UnsignedShort.valueOf((short)1), UnsignedShort.valueOf((short)3), UnsignedShort.valueOf((short)42), UnsignedShort.valueOf("65535"));
        encodeTypes("short",  Short.valueOf((short)0), Short.valueOf((short)1), Short.valueOf((short)3), Short.valueOf((short)42), Short.valueOf((short)32767), Short.valueOf((short)-1), Short.valueOf((short)-3), Short.valueOf((short)-42), Short.valueOf((short)-32768));
        encodeTypes("uint",UnsignedInteger.valueOf(0), UnsignedInteger.valueOf(1), UnsignedInteger.valueOf(3), UnsignedInteger.valueOf(42), UnsignedInteger.valueOf("4294967295"));
        encodeTypes("int",  0, 1, 3, 42, 2147483647, -1, -3, -42, -2147483648);
        encodeTypes("ulong", UnsignedLong.valueOf(0), UnsignedLong.valueOf(1), UnsignedLong.valueOf(3), UnsignedLong.valueOf(42), UnsignedLong.valueOf("18446744073709551615"));
        encodeTypes("long",  0l, 1l, 3l, 42l, 9223372036854775807l, -1l, -3l, -42l, -9223372036854775808l);
        encodeTypes("float",  3.14159);
        encodeTypes("double",  Double.valueOf(3.14159265359));
        encodeTypes("char",  '?');

        SimpleDateFormat df = new SimpleDateFormat("HHa z MMM d yyyy");

        encodeTypes("timestamp",  df.parse("9AM PST Dec 6 2010"),   df.parse("9AM PST Dec 6 1910"));
        encodeTypes("uuid",  UUID.fromString("f275ea5e-0c57-4ad7-b11a-b20c563d3b71"));
        encodeTypes("binary",  new Binary( new byte[] {(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF}), new Binary(new byte[] { (byte)0xCA,(byte)0xFE, (byte)0xBA, (byte)0xBE}));
        encodeTypes("string",  "The quick brown fox jumped over the lazy cow.");
        encodeTypes("symbol",  Symbol.valueOf("connectathon"));
        encodeTypes("list",  Arrays.asList(new Object[] {Long.valueOf(1), "two", Double.valueOf(3.14159265359), null, Boolean.FALSE}));
        Map map = new HashMap();
        map.put("one", Long.valueOf(1));
        map.put("two", Long.valueOf(2));
        map.put("pi", Double.valueOf(3.14159265359));
        map.put("list:", Arrays.asList(new Object[] {Long.valueOf(1), "two", Double.valueOf(3.14159265359), null, Boolean.FALSE}));
        map.put(null, Boolean.TRUE);
        encodeTypes("map",  map);
        encodeTypes("null",  null);

    }

    static void encodeTypes(String name, Object... vals ) throws IOException
    {
        FileOutputStream out = new FileOutputStream("/home/rob/"+name+".out");
        ByteBuffer buf = ByteBuffer.allocate(4096);
        final AMQPDescribedTypeRegistry typeRegistry = AMQPDescribedTypeRegistry.newInstance();

        if(vals != null)
        {
            for(Object obj : vals)
            {
                ValueWriter writer = typeRegistry.getValueWriter(obj);

                int count;


                do
                {
                    count = writer.writeToBuffer(buf);
                    out.write(buf.array(), buf.arrayOffset(), count);
                    buf.clear();
                } while (!writer.isComplete());

            }
        }
        else
        {
            ValueWriter writer = typeRegistry.getValueWriter(null);

            int count;


            do
            {
                count = writer.writeToBuffer(buf);
                out.write(buf.array(), buf.arrayOffset(), count);
                buf.clear();
            } while (!writer.isComplete());

        }
        out.flush();
        out.close();

    }

    static byte[] encode(int size, Object... vals)
    {
        byte[] result = new byte[10000];
        int pos = 0;

        final AMQPDescribedTypeRegistry typeRegistry = AMQPDescribedTypeRegistry.newInstance();
        AMQFrame frame = AMQFrame.createAMQFrame((short) 0, (FrameBody) vals[0]);
        FrameWriter writer =  new FrameWriter(typeRegistry);
        /*for(Object obj : vals)
        {
            final AMQPDescribedTypeRegistry typeRegistry = AMQPDescribedTypeRegistry.newInstance();
            ValueWriter writer = typeRegistry.getValueWriter(obj);
*/
            int count;

            ByteBuffer buf = ByteBuffer.wrap(result, pos, size);

            do
            {

                writer.writeToBuffer(buf);
                pos = buf.position();
                buf = ByteBuffer.wrap(result, pos, size);
                if(!writer.isComplete())
                {
                    count = 3;
                }

            } while (!writer.isComplete());
/*

        }
*/

        return result;

    }


}
