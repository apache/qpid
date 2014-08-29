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

package org.apache.qpid.amqp_1_0.type.codec;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.qpid.amqp_1_0.codec.*;
import org.apache.qpid.amqp_1_0.type.RestrictedType;
import org.apache.qpid.amqp_1_0.type.messaging.StdDistMode;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusDurability;
import org.apache.qpid.amqp_1_0.type.messaging.TerminusExpiryPolicy;
import org.apache.qpid.amqp_1_0.type.messaging.codec.*;
import org.apache.qpid.amqp_1_0.type.security.SaslCode;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslChallengeConstructor;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslChallengeWriter;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslInitConstructor;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslInitWriter;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslMechanismsConstructor;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslMechanismsWriter;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslOutcomeConstructor;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslOutcomeWriter;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslResponseConstructor;
import org.apache.qpid.amqp_1_0.type.security.codec.SaslResponseWriter;
import org.apache.qpid.amqp_1_0.type.transaction.TransactionErrors;
import org.apache.qpid.amqp_1_0.type.transaction.TxnCapability;
import org.apache.qpid.amqp_1_0.type.transaction.codec.CoordinatorConstructor;
import org.apache.qpid.amqp_1_0.type.transaction.codec.CoordinatorWriter;
import org.apache.qpid.amqp_1_0.type.transaction.codec.DeclareConstructor;
import org.apache.qpid.amqp_1_0.type.transaction.codec.DeclareWriter;
import org.apache.qpid.amqp_1_0.type.transaction.codec.DeclaredConstructor;
import org.apache.qpid.amqp_1_0.type.transaction.codec.DeclaredWriter;
import org.apache.qpid.amqp_1_0.type.transaction.codec.DischargeConstructor;
import org.apache.qpid.amqp_1_0.type.transaction.codec.DischargeWriter;
import org.apache.qpid.amqp_1_0.type.transaction.codec.TransactionalStateConstructor;
import org.apache.qpid.amqp_1_0.type.transaction.codec.TransactionalStateWriter;
import org.apache.qpid.amqp_1_0.type.transport.AmqpError;
import org.apache.qpid.amqp_1_0.type.transport.ConnectionError;
import org.apache.qpid.amqp_1_0.type.transport.LinkError;
import org.apache.qpid.amqp_1_0.type.transport.ReceiverSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.Role;
import org.apache.qpid.amqp_1_0.type.transport.SenderSettleMode;
import org.apache.qpid.amqp_1_0.type.transport.SessionError;
import org.apache.qpid.amqp_1_0.type.transport.codec.*;

public class AMQPDescribedTypeRegistry implements DescribedTypeConstructorRegistry, ValueWriter.Registry
{

    private final Map<Object, DescribedTypeConstructor> _constructorRegistry = new HashMap<Object, DescribedTypeConstructor>();

    public void register(Object descriptor, DescribedTypeConstructor constructor)
    {
        _constructorRegistry.put(descriptor, constructor);
    }

    public void register(Object descriptor, DescribedTypeConstructor constructor, TypeConstructor describedConstructor)
    {
        _constructorRegistry.put(descriptor, constructor);
    }

    public DescribedTypeConstructor getConstructor(Object descriptor)
    {
        return _constructorRegistry.get(descriptor);
    }

    private AMQPDescribedTypeRegistry()
    {
    }

    public AMQPDescribedTypeRegistry registerTransportLayer()
    {
        registerTransportConstructors(this);
        registerTransportWriters(this);
        return this;
    }

    public AMQPDescribedTypeRegistry registerMessagingLayer()
    {
        registerMessagingConstructors(this);
        registerMessagingWriters(this);
        return this;
    }

    public AMQPDescribedTypeRegistry registerTransactionLayer()
    {
        registerTransactionsConstructors(this);
        registerTransactionsWriters(this);
        return this;
    }

    public AMQPDescribedTypeRegistry registerSecurityLayer()
    {
        registerSecurityConstructors(this);
        registerSecurityWriters(this);
        return this;
    }

    public static AMQPDescribedTypeRegistry newInstance()
    {
        AMQPDescribedTypeRegistry registry = new AMQPDescribedTypeRegistry();

        NullWriter.register(registry);
        BooleanWriter.register(registry);
        ByteWriter.register(registry);
        UnsignedByteWriter.register(registry);
        ShortWriter.register(registry);
        UnsignedShortWriter.register(registry);
        IntegerWriter.register(registry);
        UnsignedIntegerWriter.register(registry);
        CharWriter.register(registry);
        FloatWriter.register(registry);
        LongWriter.register(registry);
        UnsignedLongWriter.register(registry);
        DoubleWriter.register(registry);
        TimestampWriter.register(registry);
        UUIDWriter.register(registry);
        StringWriter.register(registry);
        SymbolWriter.register(registry);
        BinaryWriter.register(registry);
        ByteArrayWriter.register(registry);
        ListWriter.register(registry);
        MapWriter.register(registry);

        SymbolArrayWriter.register(registry);

        return registry;
    }


        
    private static void registerTransportWriters(final AMQPDescribedTypeRegistry registry)
    {
    
        OpenWriter.register(registry);
        BeginWriter.register(registry);
        AttachWriter.register(registry);
        FlowWriter.register(registry);
        TransferWriter.register(registry);
        DispositionWriter.register(registry);
        DetachWriter.register(registry);
        EndWriter.register(registry);
        CloseWriter.register(registry);
        RestrictedTypeValueWriter.register(registry,Role.class);
        RestrictedTypeValueWriter.register(registry,SenderSettleMode.class);
        RestrictedTypeValueWriter.register(registry,ReceiverSettleMode.class);
        ErrorWriter.register(registry);
        RestrictedTypeValueWriter.register(registry,AmqpError.class);
        RestrictedTypeValueWriter.register(registry,ConnectionError.class);
        RestrictedTypeValueWriter.register(registry,SessionError.class);
        RestrictedTypeValueWriter.register(registry,LinkError.class);
    }

    private static void registerMessagingWriters(final AMQPDescribedTypeRegistry registry)
    {
    
        HeaderWriter.register(registry);
        DeliveryAnnotationsWriter.register(registry);
        MessageAnnotationsWriter.register(registry);
        PropertiesWriter.register(registry);
        ApplicationPropertiesWriter.register(registry);
        DataWriter.register(registry);
        AmqpSequenceWriter.register(registry);
        AmqpValueWriter.register(registry);
        FooterWriter.register(registry);
        ReceivedWriter.register(registry);
        AcceptedWriter.register(registry);
        RejectedWriter.register(registry);
        ReleasedWriter.register(registry);
        ModifiedWriter.register(registry);
        SourceWriter.register(registry);
        TargetWriter.register(registry);
        RestrictedTypeValueWriter.register(registry,TerminusDurability.class);
        RestrictedTypeValueWriter.register(registry,TerminusExpiryPolicy.class);
        RestrictedTypeValueWriter.register(registry,StdDistMode.class);
        DeleteOnCloseWriter.register(registry);
        DeleteOnNoLinksWriter.register(registry);
        DeleteOnNoMessagesWriter.register(registry);
        DeleteOnNoLinksOrMessagesWriter.register(registry);


        ExactSubjectFilterWriter.register(registry);
        MatchingSubjectFilterWriter.register(registry);
        JMSSelectorFilterWriter.register(registry);
        NoLocalFilterWriter.register(registry);
    }

    private static void registerTransactionsWriters(final AMQPDescribedTypeRegistry registry)
    {
    
        CoordinatorWriter.register(registry);
        DeclareWriter.register(registry);
        DischargeWriter.register(registry);
        DeclaredWriter.register(registry);
        TransactionalStateWriter.register(registry);
        RestrictedTypeValueWriter.register(registry,TxnCapability.class);
        RestrictedTypeValueWriter.register(registry,TransactionErrors.class);
    }

    private static void registerSecurityWriters(final AMQPDescribedTypeRegistry registry)
    {
    
        SaslMechanismsWriter.register(registry);
        SaslInitWriter.register(registry);
        SaslChallengeWriter.register(registry);
        SaslResponseWriter.register(registry);
        SaslOutcomeWriter.register(registry);
        RestrictedTypeValueWriter.register(registry,SaslCode.class);
    }

    private static void registerTransportConstructors(final AMQPDescribedTypeRegistry registry)
    {
    
        OpenConstructor.register(registry);
        BeginConstructor.register(registry);
        AttachConstructor.register(registry);
        FlowConstructor.register(registry);
        TransferConstructor.register(registry);
        DispositionConstructor.register(registry);
        DetachConstructor.register(registry);
        EndConstructor.register(registry);
        CloseConstructor.register(registry);
        ErrorConstructor.register(registry);
    }

    private static void registerMessagingConstructors(final AMQPDescribedTypeRegistry registry)
    {
    
        HeaderConstructor.register(registry);
        DeliveryAnnotationsConstructor.register(registry);
        MessageAnnotationsConstructor.register(registry);
        PropertiesConstructor.register(registry);
        ApplicationPropertiesConstructor.register(registry);
        DataConstructor.register(registry);
        AmqpSequenceConstructor.register(registry);
        AmqpValueConstructor.register(registry);
        FooterConstructor.register(registry);
        ReceivedConstructor.register(registry);
        AcceptedConstructor.register(registry);
        RejectedConstructor.register(registry);
        ReleasedConstructor.register(registry);
        ModifiedConstructor.register(registry);
        SourceConstructor.register(registry);
        TargetConstructor.register(registry);
        DeleteOnCloseConstructor.register(registry);
        DeleteOnNoLinksConstructor.register(registry);
        DeleteOnNoMessagesConstructor.register(registry);
        DeleteOnNoLinksOrMessagesConstructor.register(registry);

        ExactSubjectFilterConstructor.register(registry);
        MatchingSubjectFilterConstructor.register(registry);
        JMSSelectorFilterConstructor.register(registry);
        NoLocalFilterConstructor.register(registry);
    }

    private static void registerTransactionsConstructors(final AMQPDescribedTypeRegistry registry)
    {
    
        CoordinatorConstructor.register(registry);
        DeclareConstructor.register(registry);
        DischargeConstructor.register(registry);
        DeclaredConstructor.register(registry);
        TransactionalStateConstructor.register(registry);
    }

    private static void registerSecurityConstructors(final AMQPDescribedTypeRegistry registry)
    {
    
        SaslMechanismsConstructor.register(registry);
        SaslInitConstructor.register(registry);
        SaslChallengeConstructor.register(registry);
        SaslResponseConstructor.register(registry);
        SaslOutcomeConstructor.register(registry);
    }


    private final Map<Class, ValueWriter.Factory> _writerMap = new HashMap<Class, ValueWriter.Factory>();

    public <V extends Object> ValueWriter<V> getValueWriter(V value, Map<Class, ValueWriter> localCache)
    {
        return getValueWriter(value);

    }


    public <V extends Object> ValueWriter<V> getValueWriter(V value)
    {
        ValueWriter writer;

        Class<? extends Object> clazz = value == null ? Void.TYPE : value.getClass();

        ValueWriter.Factory<V> factory = (ValueWriter.Factory<V>) (_writerMap.get(clazz));

        if(factory == null)
        {
            if(value instanceof List)
            {
                factory = _writerMap.get(List.class);
                _writerMap.put(value.getClass(), factory);
                writer = factory.newInstance(this);
                writer.setValue(value);

            }
            else if(value instanceof Map)
            {
                factory = _writerMap.get(Map.class);
                _writerMap.put(value.getClass(), factory);
                writer = factory.newInstance(this);
                writer.setValue(value);

            }
            else if(value.getClass().isArray())
            {
                if(RestrictedType.class.isAssignableFrom(value.getClass().getComponentType()))
                {
                    RestrictedType[] restrictedTypes = (RestrictedType[]) value;
                    Object[] newVals = (Object[]) Array.newInstance(restrictedTypes[0].getValue().getClass(),
                                                                    restrictedTypes.length);
                    for(int i = 0; i < restrictedTypes.length; i++)
                    {
                        newVals[i] = restrictedTypes[i].getValue();
                    }
                    return (ValueWriter<V>) getValueWriter(newVals);
                }
                // TODO primitive array types
                factory = _writerMap.get(List.class);
                writer = factory.newInstance(this);
                writer.setValue(Arrays.asList((Object[])value));

            }
            else
            {
                return null;
            }
        }
        else
        {
            writer = factory.newInstance(this);
            writer.setValue(value);
        }


        return writer;

    }

    public <V extends Object> ValueWriter<V> register(Class<V> clazz, ValueWriter.Factory<V> writer)
    {
        return (ValueWriter<V>) _writerMap.put(clazz, writer);
    }

}

