/*
 *
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
 *
 */
using System;
using System.Collections;
using Qpid.Framing;
using Qpid.Buffer;

namespace Qpid.Client.Message
{
    public class QpidTextMessageFactory : AbstractQmsMessageFactory
    {

    //    protected override AbstractQmsMessage CreateMessageWithBody(long messageNbr, ContentHeaderBody contentHeader,
    //                                                                IList bodies)
    //    {
    //        byte[] data;

    //        // we optimise the non-fragmented case to avoid copying
    //        if (bodies != null && bodies.Count == 1)
    //        {
    //            data = ((ContentBody)bodies[0]).Payload;
    //        }
    //        else
    //        {
    //            data = new byte[(int)contentHeader.BodySize];
    //            int currentPosition = 0;
    //            foreach (ContentBody cb in bodies)
    //            {                
    //                Array.Copy(cb.Payload, 0, data, currentPosition, cb.Payload.Length);
    //                currentPosition += cb.Payload.Length;
    //            }
    //        }

    //        return new QpidTextMessage(messageNbr, data, (BasicContentHeaderProperties)contentHeader.Properties);
    //    }
        
     
    //    public override AbstractQmsMessage CreateMessage()
    //    {
    //        return new QpidTextMessage();
    //    }      


        
        public override AbstractQmsMessage CreateMessage()
        {
            return new QpidTextMessage();
        }

        protected override AbstractQmsMessage CreateMessage(long deliveryTag, ByteBuffer data, ContentHeaderBody contentHeader)
        {
            return new QpidTextMessage(deliveryTag, (BasicContentHeaderProperties) contentHeader.Properties, data);
        }

    }
}

