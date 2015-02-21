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

package org.apache.qpid.amqp_1_0.jms.impl;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.qpid.amqp_1_0.client.Message;
import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.Symbol;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpSequence;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

class MessageFactory
{
    private final SessionImpl _session;


    MessageFactory(final SessionImpl session)
    {
        _session = session;
    }

    public MessageImpl createMessage(final DestinationImpl destination, final Message msg)
    {
        MessageImpl message;
        List<Section> payload = msg.getPayload();
        Header header = null;
        MessageAnnotations messageAnnotations = null;
        DeliveryAnnotations deliveryAnnotations = null;

        Properties properties = null;
        ApplicationProperties appProperties = null;
        Footer footer;

        Iterator<Section> iter = payload.iterator();
        List<Section> body = new ArrayList<Section>();

        Section section = iter.hasNext() ? iter.next() : null;

        if(section instanceof Header)
        {
            header = (Header) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        if(section instanceof DeliveryAnnotations)
        {
            deliveryAnnotations = (DeliveryAnnotations) section;
            section = iter.hasNext() ? iter.next() : null;
        }
        
        if(section instanceof MessageAnnotations)
        {
            messageAnnotations = (MessageAnnotations) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        if(section instanceof Properties)
        {
            properties = (Properties) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        if(section instanceof ApplicationProperties)
        {
            appProperties = (ApplicationProperties) section;
            section = iter.hasNext() ? iter.next() : null;
        }

        while(section != null && !(section instanceof Footer))
        {
            body.add(section);
            section = iter.hasNext() ? iter.next() : null;
        }

        footer = (Footer) section;

        if(body.size() == 1)
        {
            Section bodySection = body.get(0);
            if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof Map)
            {
                message = new MapMessageImpl(header, deliveryAnnotations, messageAnnotations, properties, appProperties, (Map) ((AmqpValue)bodySection).getValue(), footer, _session);
            }
            else if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof List)
            {
                message = new StreamMessageImpl(header,
                                                deliveryAnnotations,
                                                messageAnnotations, properties, appProperties,
                                                (List) ((AmqpValue)bodySection).getValue(), footer, _session
                );
            }
            else if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof String)
            {
                message = new TextMessageImpl(header, deliveryAnnotations, messageAnnotations, properties, appProperties,
                                                (String) ((AmqpValue)bodySection).getValue(), footer, _session);
            }
            else if(bodySection instanceof AmqpValue && ((AmqpValue)bodySection).getValue() instanceof Binary)
            {

                Binary value = (Binary) ((AmqpValue) bodySection).getValue();
                message = new BytesMessageImpl(header,
                                               deliveryAnnotations,
                                               messageAnnotations, properties, appProperties,
                                               new Data(value), footer, _session);
            }
            else if(bodySection instanceof Data)
            {
                Data dataSection = (Data) bodySection;

                Symbol contentType = properties == null ? null : properties.getContentType();

                if(ObjectMessageImpl.CONTENT_TYPE.equals(contentType))
                {


                    message = new ObjectMessageImpl(header,
                                                    deliveryAnnotations,
                                                    messageAnnotations, properties, appProperties,
                                                    dataSection,
                                                    footer,
                                                    _session);
                }
                else if(contentType != null)
                {
                    ContentType contentTypeObj = parseContentType(contentType.toString());
                    // todo : content encoding - e.g. gzip
                    String contentTypeType = contentTypeObj.getType();
                    String contentTypeSubType = contentTypeObj.getSubType();
                    if ("text".equals(contentTypeType)
                        || ("application".equals(contentTypeType)
                            && contentTypeSubType != null
                            && ("xml".equals(contentTypeSubType)
                                || "json".equals(contentTypeSubType)
                                || "xml-dtd".equals(contentTypeSubType)
                                || "javascript".equals(contentTypeSubType)
                                || contentTypeSubType.endsWith("+xml")
                                || contentTypeSubType.endsWith("+json"))))
                    {
                        Charset charset;
                        if (contentTypeObj.getParameterNames().contains("charset"))
                        {
                            charset = Charset.forName(contentTypeObj.getParameter("charset").toUpperCase());
                        }
                        else
                        {
                            charset = StandardCharsets.US_ASCII;
                        }
                        Binary binary = dataSection.getValue();
                        String data =
                                new String(binary.getArray(), binary.getArrayOffset(), binary.getLength(), charset);
                        message = new TextMessageImpl(header, deliveryAnnotations, messageAnnotations, properties,
                                                      appProperties, data, footer, _session);
                    }
                    else
                    {
                        message = new BytesMessageImpl(header,
                                                       deliveryAnnotations,
                                                       messageAnnotations,
                                                       properties,
                                                       appProperties,
                                                       dataSection,
                                                       footer,
                                                       _session);
                    }
                }
                else
                {
                    message = new BytesMessageImpl(header,
                                                   deliveryAnnotations,
                                                   messageAnnotations,
                                                   properties,
                                                   appProperties,
                                                   dataSection,
                                                   footer,
                                                   _session);
                }

            }
            else if(bodySection instanceof AmqpSequence)
            {
                message = new StreamMessageImpl(header,
                                                deliveryAnnotations,
                                                messageAnnotations, properties, appProperties, ((AmqpSequence) bodySection).getValue(), footer, _session
                );
            }
            else
            {
                message = new AmqpMessageImpl(header,
                                              deliveryAnnotations,
                                              messageAnnotations, properties,appProperties,body,footer, _session);
            }
        }
        else if(body.size() == 0)
        {
            message = new AmqpMessageImpl(header,
                                          deliveryAnnotations,
                                          messageAnnotations, properties,appProperties,
                                          Collections.<Section>singletonList(new AmqpValue(null)),footer, _session);
        }
        else
        {
            message = new AmqpMessageImpl(header,
                                          deliveryAnnotations,
                                          messageAnnotations, properties,appProperties,body,footer, _session);
        }

        message.setReadOnly();

        return message;
    }


    static interface ContentType
    {
        String getType();
        String getSubType();
        Set<String> getParameterNames();
        String getParameter(String name);
    }

    static ContentType parseContentType(String contentType)
    {
        int subTypeSeparator = contentType.indexOf("/");
        final String type = contentType.substring(0, subTypeSeparator).toLowerCase().trim();
        String subTypePart = contentType.substring(subTypeSeparator +1).toLowerCase().trim();
        if(subTypePart.contains(";"))
        {
            subTypePart = subTypePart.substring(0,subTypePart.indexOf(";")).trim();
        }
        if(subTypePart.contains("("))
        {
            subTypePart = subTypePart.substring(0,subTypePart.indexOf("(")).trim();
        }
        final String subType = subTypePart;
        final Map<String,String> parameters = new HashMap<>();

        if(contentType.substring(subTypeSeparator +1).contains(";"))
        {
            parseContentTypeParameters(contentType.substring(contentType.indexOf(";",subTypeSeparator +1)+1), parameters);
        }
        return  new ContentType()
                {
                    @Override
                    public String getType()
                    {
                        return type;
                    }

                    @Override
                    public String getSubType()
                    {
                        return subType;
                    }

                    @Override
                    public Set<String> getParameterNames()
                    {
                        return Collections.unmodifiableMap(parameters).keySet();
                    }

                    @Override
                    public String getParameter(final String name)
                    {
                        return parameters.get(name);
                    }
                };
    }

    private static void parseContentTypeParameters(final String parameterString, final Map<String, String> parameters)
    {
        int equalsIndex = parameterString.indexOf("=");
        if(equalsIndex != -1)
        {
            String paramName = parameterString.substring(0,equalsIndex).trim();
            String valuePart = equalsIndex == parameterString.length() - 1 ? "" : parameterString.substring(equalsIndex+1).trim();
            String remainder;
            if(valuePart.startsWith("\""))
            {
                int closeQuoteIndex = valuePart.indexOf("\"", 1);
                if(closeQuoteIndex != -1)
                {
                    parameters.put(paramName, valuePart.substring(1, closeQuoteIndex));
                    remainder = (closeQuoteIndex == valuePart.length()-1) ? "" : valuePart.substring(closeQuoteIndex+1);
                }
                else
                {
                    remainder = "";
                }
            }
            else
            {
                Pattern pattern = Pattern.compile("\\s|;|\\(");
                Matcher matcher = pattern.matcher(valuePart);
                if(matcher.matches())
                {
                    parameters.put(paramName, valuePart.substring(0,matcher.start()));
                    remainder = valuePart.substring(matcher.start());
                }
                else
                {
                    parameters.put(paramName, valuePart);
                    remainder = "";
                }
            }

            int paramSep = remainder.indexOf(";");
            if(paramSep != -1 && paramSep != remainder.length()-1)
            {
                parseContentTypeParameters(remainder.substring(paramSep+1),parameters);
            }
        }

    }

}
