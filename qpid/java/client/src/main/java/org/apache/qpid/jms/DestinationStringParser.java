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
package org.apache.qpid.jms;

import java.net.URISyntaxException;
import java.util.Collections;

import org.apache.qpid.jms.QpidDestination.Type;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.address.AddressException;
import org.apache.qpid.messaging.address.Node;
import org.apache.qpid.messaging.address.Node.AddressPolicy;
import org.apache.qpid.messaging.util.AddressHelper;
import org.apache.qpid.url.AMQBindingURL;

public class DestinationStringParser
{
   public static Address parseAddressString(String str, Type type) throws AddressException
   {
       Address addr = Address.parse(str);
       AddressHelper helper = new AddressHelper(addr);
       Node node = new Node();
       node.setAssertPolicy(AddressPolicy.getAddressPolicy(helper.getAssert()));
       node.setCreatePolicy(AddressPolicy.getAddressPolicy(helper.getCreate()));
       node.setDeletePolicy(AddressPolicy.getAddressPolicy(helper.getDelete()));

       return addr;
   }

   public static Address parseBURLString(String str, Type type) throws AddressException
   {
	   AMQBindingURL burl;
	   try
	   {
           burl = new AMQBindingURL(str);
	   }
	   catch(URISyntaxException e)
	   {
	       AddressException ex = new AddressException("Error parsing BURL : " + e.getMessage());
	       ex.initCause(e);
	       throw ex;
	   }

	   Address addr;
	   if (type == Type.TOPIC)
	   {
	       addr = new Address(burl.getExchangeName().asString(),
                              burl.getRoutingKey().asString(),
                              Collections.EMPTY_MAP);

	      // use the queue name to add x-subscribe props.
	   }
	   else
	   {
		   addr = new Address(burl.getQueueName().asString(),
                              burl.getRoutingKey().asString(),
                              Collections.EMPTY_MAP);

		   // use the exchange and binding key to add a binding
	   }

	   Node node = new Node();
       node.setAssertPolicy(AddressPolicy.NEVER);
       node.setCreatePolicy(AddressPolicy.RECEIVER);
       node.setDeletePolicy(AddressPolicy.NEVER);

	   return addr;
   }
}
