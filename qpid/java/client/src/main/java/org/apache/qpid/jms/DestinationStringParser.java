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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jms.JMSException;
import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.QpidDestination.DestinationType;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.AddressRaw;
import org.apache.qpid.messaging.address.AddressException;
import org.apache.qpid.messaging.address.AddressPolicy;
import org.apache.qpid.messaging.address.Link;
import org.apache.qpid.messaging.address.Reliability;
import org.apache.qpid.messaging.address.Node;
import org.apache.qpid.messaging.address.NodeType;
import org.apache.qpid.messaging.util.AddressHelper;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DestinationStringParser
{
    private static final Logger _logger = LoggerFactory.getLogger(DestinationStringParser.class);

    public enum DestSyntax
    {
        BURL
        {
            public Address parseAddress(String addressString, DestinationType type) throws AddressException
            {
                return DestinationStringParser.parseBURLString(addressString, type);
            }
        },
        ADDR
        {
            public Address parseAddress(String addressString, DestinationType type) throws AddressException
            {
                return DestinationStringParser.parseAddressString(addressString,type);
            }
        };

        abstract Address parseAddress(String addressString, DestinationType type) throws AddressException;

        public static DestSyntax getDestSyntax(String name)
        {
            try
            {
                return DestSyntax.valueOf(name);
            }
            catch (IllegalArgumentException e)
            {
                throw new IllegalArgumentException("Invalid Destination Syntax Type '"
                        + name
                        + "' should be one of " + Arrays.asList(DestSyntax.values()));
            }
        }
        
    }

    private static final DestSyntax _defaultDestSyntax;

    static
    {
        _defaultDestSyntax =
                DestSyntax.getDestSyntax(System.getProperty(ClientProperties.DEST_SYNTAX, DestSyntax.ADDR.name()));

    }

    public static DestSyntax getDestType(String str)
    {
        DestSyntax chosenSyntax = _defaultDestSyntax;
        for(DestSyntax syntax : DestSyntax.values())
        {
            if(str.startsWith(syntax.name() +":"))
            {                
                chosenSyntax = syntax;
                break;
            }
        }
        
        if (_logger.isDebugEnabled())
        {
            _logger.debug("Based on " + str + " the selected destination syntax is " + chosenSyntax);
        }        
        
        return chosenSyntax;
        
    }


    public static Address parseDestinationString(String str, DestinationType type) throws JMSException
    {
        try
        {

            return getDestType(str).parseAddress(str, type);

        }
        catch (AddressException e)
        {
            JMSException ex = new JMSException("Error parsing destination string, due to : " + e.getMessage());
            ex.initCause(e);
            ex.setLinkedException(e);
            throw ex;
        }
    }


    public static Address parseAddressString(String str, DestinationType type) throws AddressException
    {
        if(str.startsWith(DestSyntax.ADDR.name() + ":"))
        {
            str = str.substring(DestSyntax.ADDR.name().length() + 1);
        }

        AddressRaw rawAddr = Address.parse(str);
        AddressHelper helper = new AddressHelper(rawAddr);

        
        if (DestinationType.TOPIC == type)
        {
            if (helper.getNodeType() == NodeType.QUEUE)
            {
                throw new AddressException("Destination is marked as a Topic, but address is defined as a Queue");
            }
        }
        else
        {
            if (helper.getNodeType() == NodeType.TOPIC)
            {
                throw new AddressException("Destination is marked as a Queue, but address is defined as a Topic");
            }
        }

        
        Node node = new Node(rawAddr.getName(), helper.getNodeType(), helper.isNodeDurable(),
                             AddressPolicy.getAddressPolicy(helper.getCreate()), 
                             AddressPolicy.getAddressPolicy(helper.getAssert()),
                             AddressPolicy.getAddressPolicy(helper.getDelete()),
                             helper.getNodeDeclareArgs(),
                             helper.getNodeBindings());

        Link link = new Link(helper.getLinkName(),
                             helper.isLinkDurable(),
                             Reliability.getReliability(helper.getLinkReliability()),
                             helper.getProducerCapacity(),
                             helper.getConsumeCapacity(),
                             helper.getLinkDeclareArgs(),
                             helper.getLinkBindings(),
                             helper.getLinkSubscribeArgs());

        return new Address(rawAddr.getName(),rawAddr.getSubject(),node,link);
    }

    public static Address parseBURLString(String str, DestinationType type) throws AddressException
    {
        if(str.startsWith(DestSyntax.BURL.name() + ":"))
        {
            str = str.substring(DestSyntax.BURL.name().length() + 1);
        }

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

        String name;
        String subject;
        String linkName;

        String exchangeName = burl.getExchangeName().asString();
        String queueName = burl.getQueueName().asString();
        String routingKey = burl.getRoutingKey().asString();
        boolean durable = Boolean.getBoolean(burl.getOption(BindingURL.OPTION_DURABLE));
        boolean autoDelete = Boolean.getBoolean(burl.getOption(BindingURL.OPTION_AUTODELETE));
        boolean exclusive = Boolean.getBoolean(burl.getOption(BindingURL.OPTION_EXCLUSIVE));
        boolean browse = Boolean.getBoolean(burl.getOption(BindingURL.OPTION_BROWSE)); // TODO

        List<Object> nodeBindings  = Collections.EMPTY_LIST;
        Map<String,Object> declareArgs = Collections.EMPTY_MAP;

        if (type == DestinationType.TOPIC)
        {
            name = exchangeName;
            subject = routingKey;
            linkName = queueName;
            nodeBindings = Collections.emptyList();
        }
        else
        {
            name = queueName;
            subject = routingKey;

            List<Object> bindings = new ArrayList<Object>();
            Map<String,Object> binding = new HashMap<String,Object>();
            binding.put(AddressHelper.EXCHANGE, exchangeName);
            binding.put(AddressHelper.KEY, routingKey);
            bindings.add(binding);
            nodeBindings = bindings;
            linkName = null; // ??? This doesn't seem right

            declareArgs = new HashMap<String,Object>();
            declareArgs.put(AddressHelper.AUTO_DELETE, autoDelete);
            declareArgs.put(AddressHelper.EXCLUSIVE, exclusive);
        }

        for (AMQShortString key: burl.getBindingKeys())
        {
            Map<String,Object> binding = new HashMap<String,Object>();
            binding.put(AddressHelper.EXCHANGE, exchangeName);
            binding.put(AddressHelper.KEY, key.asString());
            nodeBindings.add(binding);
        }

        Node node = 
                new Node(name,
                         type == DestinationType.TOPIC ? NodeType.TOPIC : NodeType.QUEUE,
                         durable,
                         AddressPolicy.NEVER, // ?? should this not be determined
                         AddressPolicy.NEVER, // ?? should this not be determined
                         AddressPolicy.NEVER, // ?? should this not be determined
                         declareArgs,
                         nodeBindings);

        Link link = new Link(linkName, 
                             durable,
                             Reliability.AT_LEAST_ONCE, // ?? should this not be determined
                             0, // ?? should this not be determined
                             0, // ?? should this not be determined
                             Collections.EMPTY_MAP, // ?? should this not be determined
                             Collections.EMPTY_LIST, // ?? should this not be determined
                             Collections.EMPTY_MAP); // ?? should this not be determined
        
         return new Address(name,subject,node,link);
    }
}
