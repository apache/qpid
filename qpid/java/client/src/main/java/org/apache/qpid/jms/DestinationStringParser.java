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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jms.JMSException;

import org.apache.qpid.configuration.ClientProperties;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.jms.QpidDestination.DestinationType;
import org.apache.qpid.messaging.Address;
import org.apache.qpid.messaging.address.AddressException;
import org.apache.qpid.messaging.address.Link;
import org.apache.qpid.messaging.address.Link.Reliability;
import org.apache.qpid.messaging.address.Node;
import org.apache.qpid.messaging.address.Node.AddressPolicy;
import org.apache.qpid.messaging.address.Node.NodeType;
import org.apache.qpid.messaging.util.AddressHelper;
import org.apache.qpid.url.AMQBindingURL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DestinationStringParser
{
    private static final Logger _logger = LoggerFactory.getLogger(DestinationStringParser.class);

    private static final String BURL_STR = "BURL";
    private static final String ADDR_STR = "ADDR";

    public enum DestSyntax
    {
        BURL,ADDR;

        public static DestSyntax getSyntaxType(String s)
        {
            if ((BURL_STR).equals(s))
            {
                return BURL;
            }
            else if ((ADDR_STR).equals(s))
            {
                return ADDR;
            }
            else
            {
                throw new IllegalArgumentException("Invalid Destination Syntax Type" +
                " should be one of {BURL|ADDR}");
            }
        }
    }

    private static final DestSyntax _defaultDestSyntax;
    static
    {
        _defaultDestSyntax = DestSyntax.getSyntaxType(
                System.getProperty(ClientProperties.DEST_SYNTAX,
                        DestSyntax.ADDR.toString()));
    }

    public static DestSyntax getDestType(String str)
    {
        if (str.startsWith(ADDR_STR))
        {
            return DestSyntax.ADDR;
        }
        else if (str.startsWith(BURL_STR))
        {
            return DestSyntax.BURL;
        }
        else
        {
            return _defaultDestSyntax;
        }
    }

    public static String stripSyntaxPrefix(String str)
    {
        if (str.startsWith(BURL_STR) || str.startsWith(ADDR_STR))
        {
            return str.substring(5,str.length());
        }
        else
        {
            return str;
        }
    }

    public static Address parseDestinationString(String str, DestinationType type) throws JMSException
    {
        DestSyntax destSyntax = getDestType(str);
        str = stripSyntaxPrefix(str);

        if (_logger.isDebugEnabled())
        {
            _logger.debug("Based on " + str + " the selected destination syntax is " + destSyntax);
        }

        try
        {
            if (destSyntax == DestSyntax.BURL)
            {
                return DestinationStringParser.parseAddressString(str,type);
            }
            else
            {
                return DestinationStringParser.parseBURLString(str,type);
            }
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
        Address addr = Address.parse(str);
        AddressHelper helper = new AddressHelper(addr);

        Node node = new Node();
        node.setName(addr.getName());
        node.setAssertPolicy(AddressPolicy.getAddressPolicy(helper.getAssert()));
        node.setCreatePolicy(AddressPolicy.getAddressPolicy(helper.getCreate()));
        node.setDeletePolicy(AddressPolicy.getAddressPolicy(helper.getDelete()));
        node.setDurable(helper.isNodeDurable());

        if (DestinationType.TOPIC == type)
        {
            if (helper.getNodeType() == NodeType.QUEUE)
            {
                throw new AddressException("Destination is marked as a Topic, but address is defined as a Queue");
            }
            node.setType(NodeType.TOPIC);
        }
        else
        {
            if (helper.getNodeType() == NodeType.TOPIC)
            {
                throw new AddressException("Destination is marked as a Queue, but address is defined as a Topic");
            }
            node.setType(NodeType.QUEUE);
        }

        node.setDeclareProps(helper.getNodeDeclareArgs());
        node.setBindingProps(helper.getNodeBindings());
        addr.setNode(node);

        Link link =  new Link();
        link.setName(helper.getLinkName());
        link.setDurable(helper.isLinkDurable());
        link.setReliability(Reliability.getReliability(helper.getLinkReliability()));
        link.setProducerCapacity(helper.getProducerCapacity());
        link.setConsumerCapacity(helper.getConsumeCapacity());
        link.setDeclareProps(helper.getLinkDeclareArgs());
        link.setBindingProps(helper.getLinkBindings());
        link.setSubscribeProps(helper.getLinkSubscribeArgs());
        addr.setLink(link);

        return addr;
    }

    public static Address parseBURLString(String str, DestinationType type) throws AddressException
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
        Node node = new Node();
        Link link = new Link();

        if (type == DestinationType.TOPIC)
        {
            addr = new Address(burl.getExchangeName().asString(),
                    burl.getRoutingKey().asString(),
                    Collections.emptyMap());

            link.setName(burl.getQueueName().asString());
            node.setBindingProps(Collections.emptyList());
        }
        else
        {
            addr = new Address(burl.getQueueName().asString(),
                    burl.getRoutingKey().asString(),
                    Collections.emptyMap());

            List<Object> bindings = new ArrayList<Object>();
            Map<String,Object> binding = new HashMap<String,Object>();
            binding.put(AddressHelper.EXCHANGE, burl.getExchangeName().asString());
            binding.put(AddressHelper.KEY, burl.getRoutingKey());
            bindings.add(binding);
            node.setBindingProps(bindings);
        }

        List<Object> bindings = node.getBindingProperties();
        for (AMQShortString key: burl.getBindingKeys())
        {
            Map<String,Object> binding = new HashMap<String,Object>();
            binding.put(AddressHelper.EXCHANGE, burl.getExchangeName().asString());
            binding.put(AddressHelper.KEY, key.asString());
            bindings.add(binding);
        }

        node.setAssertPolicy(AddressPolicy.NEVER);
        node.setCreatePolicy(AddressPolicy.RECEIVER);
        node.setDeletePolicy(AddressPolicy.NEVER);

        return addr;
    }
}
