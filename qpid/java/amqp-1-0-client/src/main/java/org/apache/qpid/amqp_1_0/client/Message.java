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
package org.apache.qpid.amqp_1_0.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.qpid.amqp_1_0.type.Binary;
import org.apache.qpid.amqp_1_0.type.DeliveryState;
import org.apache.qpid.amqp_1_0.type.Section;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpSequence;
import org.apache.qpid.amqp_1_0.type.messaging.AmqpValue;
import org.apache.qpid.amqp_1_0.type.messaging.ApplicationProperties;
import org.apache.qpid.amqp_1_0.type.messaging.Data;
import org.apache.qpid.amqp_1_0.type.messaging.DeliveryAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Footer;
import org.apache.qpid.amqp_1_0.type.messaging.Header;
import org.apache.qpid.amqp_1_0.type.messaging.MessageAnnotations;
import org.apache.qpid.amqp_1_0.type.messaging.Properties;

public class Message
{

    private static final Map<Class<? extends Section>, Collection<Class<? extends Section>>> VALID_NEXT_SECTIONS = new HashMap<>();

    static
    {
        VALID_NEXT_SECTIONS.put(null, Arrays.asList(Header.class,
                                                    DeliveryAnnotations.class,
                                                    MessageAnnotations.class,
                                                    Properties.class,
                                                    ApplicationProperties.class,
                                                    AmqpValue.class,
                                                    AmqpSequence.class,
                                                    Data.class));

        VALID_NEXT_SECTIONS.put(Header.class, Arrays.asList(DeliveryAnnotations.class,
                                                            MessageAnnotations.class,
                                                            Properties.class,
                                                            ApplicationProperties.class,
                                                            AmqpValue.class,
                                                            AmqpSequence.class,
                                                            Data.class));

        VALID_NEXT_SECTIONS.put(DeliveryAnnotations.class, Arrays.asList(MessageAnnotations.class,
                                                                         Properties.class,
                                                                         ApplicationProperties.class,
                                                                         AmqpValue.class,
                                                                         AmqpSequence.class,
                                                                         Data.class));

        VALID_NEXT_SECTIONS.put(MessageAnnotations.class, Arrays.asList(Properties.class,
                                                                        ApplicationProperties.class,
                                                                        AmqpValue.class,
                                                                        AmqpSequence.class,
                                                                        Data.class));

        VALID_NEXT_SECTIONS.put(Properties.class, Arrays.asList(ApplicationProperties.class,
                                                                AmqpValue.class,
                                                                AmqpSequence.class,
                                                                Data.class));


        VALID_NEXT_SECTIONS.put(ApplicationProperties.class, Arrays.asList(AmqpValue.class,
                                                                           AmqpSequence.class,
                                                                           Data.class));

        VALID_NEXT_SECTIONS.put(AmqpValue.class, Arrays.<Class<? extends Section>>asList(Footer.class, null));

        VALID_NEXT_SECTIONS.put(AmqpSequence.class, Arrays.asList(AmqpSequence.class,
                                                                  Footer.class, null));

        VALID_NEXT_SECTIONS.put(Data.class, Arrays.asList(Data.class, Footer.class, null));

        VALID_NEXT_SECTIONS.put(Footer.class, Collections.<Class<? extends Section>>singletonList(null));


    }


    private Binary _deliveryTag;
    private List<Section> _payload = new ArrayList<Section>();
    private Boolean _resume;
    private boolean _settled;
    private DeliveryState _deliveryState;
    private Receiver _receiver;


    public Message()
    {
    }

    public Message(Collection<Section> sections)
    {
        _payload.addAll(validateOrReorder(sections));
    }

    public Message(Section section)
    {
        this(Collections.singletonList(section));
    }

    public Message(String message)
    {
        this(new AmqpValue(message));
    }


    private static Collection<Section>  validateOrReorder(final Collection<Section> providedSections)
    {
        Collection<Section> validatedSections;
        if(providedSections == null)
        {
            validatedSections = Collections.emptyList();
        }
        else if(isValidOrder(providedSections))
        {
            validatedSections = providedSections;
        }
        else
        {
            validatedSections = reorderSections(providedSections);
        }
        return validatedSections;
    }

    private static Collection<Section> reorderSections(final Collection<Section> providedSections)
    {
        Collection<Section> validSections = new ArrayList<>();
        List<Section> originalSection = new ArrayList<>(providedSections);
        validSections.addAll(getAndRemoveSections(Header.class, originalSection, false));
        validSections.addAll(getAndRemoveSections(DeliveryAnnotations.class, originalSection, false));
        validSections.addAll(getAndRemoveSections(MessageAnnotations.class, originalSection, false));
        validSections.addAll(getAndRemoveSections(Properties.class, originalSection, false));
        validSections.addAll(getAndRemoveSections(ApplicationProperties.class, originalSection, false));

        final List<AmqpValue> valueSections = getAndRemoveSections(AmqpValue.class, originalSection, false);
        final List<AmqpSequence> sequenceSections = getAndRemoveSections(AmqpSequence.class, originalSection, true);
        final List<Data> dataSections = getAndRemoveSections(Data.class, originalSection, true);

        if(valueSections.isEmpty() && sequenceSections.isEmpty() && dataSections.isEmpty())
        {
            throw new IllegalArgumentException("Message must contain one of Data, AmqpValue or AmqpSequence");
        }
        if((!valueSections.isEmpty() && (!sequenceSections.isEmpty() || !dataSections.isEmpty()))
                || (!sequenceSections.isEmpty() && !dataSections.isEmpty()))
        {
            throw new IllegalArgumentException("Only one type of content Data, AmqpValue or AmqpSequence can be used");
        }
        validSections.addAll(valueSections);
        validSections.addAll(sequenceSections);
        validSections.addAll(dataSections);

        validSections.addAll(getAndRemoveSections(Footer.class, originalSection, false));

        if(!originalSection.isEmpty())
        {
            throw new IllegalArgumentException("Invalid section type: " + originalSection.get(0).getClass().getName());
        }
        return validSections;
    }

    private static <T extends Section> List<T> getAndRemoveSections(Class<T> clazz,
                                                                    List<Section> sections,
                                                                    boolean allowMultiple)
    {
        List<T> desiredSections = new ArrayList<>();
        ListIterator<Section> iterator = sections.listIterator();
        while(iterator.hasNext())
        {
            Section s = iterator.next();
            if(s.getClass() == clazz)
            {
                desiredSections.add((T)s);
                iterator.remove();
            }
        }
        if(desiredSections.size() > 1 && !allowMultiple)
        {
            throw new IllegalArgumentException("Multiple " + clazz.getSimpleName() + " sections are not allowed");
        }
        return desiredSections;
    }

    private static boolean isValidOrder(final Collection<Section> providedSections)
    {
        Class<? extends Section> previousSection = null;
        final Iterator<? extends Section> it = providedSections.iterator();
        while(it.hasNext())
        {
            Collection<Class<? extends Section>> validSections = VALID_NEXT_SECTIONS.get(previousSection);
            Class<? extends Section> sectionClass = it.next().getClass();
            if(validSections == null || !validSections.contains(sectionClass))
            {
                return false;
            }
            else
            {
                previousSection = sectionClass;
            }
        }
        Collection<Class<? extends Section>> validSections = VALID_NEXT_SECTIONS.get(previousSection);
        return validSections != null && validSections.contains(null);
    }



    public Binary getDeliveryTag()
    {
        return _deliveryTag;
    }

    public void setDeliveryTag(Binary deliveryTag)
    {
        _deliveryTag = deliveryTag;
    }

    public List<Section> getPayload()
    {
        return Collections.unmodifiableList(_payload);
    }

    private <T extends Section> T getSection(Class<T> clazz)
    {
        for(Section s : _payload)
        {
            if(clazz.isAssignableFrom(s.getClass()))
            {
                return (T) s;
            }
        }
        return null;
    }

    public ApplicationProperties getApplicationProperties()
    {
        return getSection(ApplicationProperties.class);
    }

    public Properties getProperties()
    {
        return getSection(Properties.class);
    }

    public Header getHeader()
    {
        return getSection(Header.class);
    }


    public void setResume(final Boolean resume)
    {
        _resume = resume;
    }

    public boolean isResume()
    {
        return Boolean.TRUE.equals(_resume);
    }

    public void setDeliveryState(DeliveryState state)
    {
        _deliveryState = state;
    }

    public DeliveryState getDeliveryState()
    {
        return _deliveryState;
    }

    public void setSettled(boolean settled)
    {
        _settled = settled;
    }

    public boolean getSettled()
    {
        return _settled;
    }

    public void setReceiver(final Receiver receiver)
    {
        _receiver = receiver;
    }

    public Receiver getReceiver()
    {
        return _receiver;
    }
}
