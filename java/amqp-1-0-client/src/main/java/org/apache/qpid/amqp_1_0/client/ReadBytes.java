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

import org.apache.qpid.amqp_1_0.codec.ValueHandler;
import org.apache.qpid.amqp_1_0.type.AmqpErrorException;
import org.apache.qpid.amqp_1_0.type.codec.AMQPDescribedTypeRegistry;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ReadBytes
{

    public static void main(String[] args) throws IOException, AmqpErrorException
    {

        if(args.length == 0)
        {
            readBytes(System.in);
        }
        else
        {
            for(String fileName : args)
            {
                System.out.println("=========================== " + fileName + " ===========================");
                final FileInputStream fis = new FileInputStream(fileName);
                readBytes(fis);
                fis.close();
            }
        }

    }

    private static void readBytes(final InputStream inputStream) throws IOException, AmqpErrorException
    {
        byte[] bytes = new byte[4096];

        ValueHandler valueHandler = new ValueHandler(AMQPDescribedTypeRegistry.newInstance());

        int count;

        while((count = inputStream.read(bytes))!=-1)
        {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            buf.limit(count);
            while(buf.hasRemaining())
            {

                    final Object value = valueHandler.parse(buf);
                    System.out.print((value == null ? "" : value.getClass().getName() + ":") +value +"\n");

            }
        }

    }


}
