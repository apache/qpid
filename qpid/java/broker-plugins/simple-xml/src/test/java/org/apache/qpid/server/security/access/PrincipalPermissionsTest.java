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
package org.apache.qpid.server.security.access;

import org.apache.qpid.AMQException;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.security.Result;
import org.apache.qpid.server.security.access.config.PrincipalPermissions;
import org.apache.qpid.server.security.access.config.PrincipalPermissions.Permission;
import org.apache.qpid.test.utils.QpidTestCase;

public class PrincipalPermissionsTest extends QpidTestCase
{
    private String _user = "user";
    private PrincipalPermissions _perms;

    // Common things that are passed to frame constructors
    private AMQShortString _queueName = new AMQShortString(this.getClass().getName() + "queue");
    private AMQShortString _tempQueueName = new AMQShortString(this.getClass().getName() + "tempqueue");
    private AMQShortString _exchangeName = new AMQShortString("amq.direct");
    private AMQShortString _routingKey = new AMQShortString(this.getClass().getName() + "route");
    private boolean _autoDelete = false;
    private AMQShortString _exchangeType = new AMQShortString("direct");
    private Boolean _temporary = false;
    private Boolean _ownQueue = false;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _perms = new PrincipalPermissions(_user);
    }


    public void testPrincipalPermissions()
    {
        assertNotNull(_perms);
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.ACCESS, (String[]) null));
    }

    // FIXME: test has been disabled since the permissions assume that the user has tried to create
    // the queue first. QPID-1597
    public void disableTestBind() throws Exception
    {
        String[] args = new String[]{null, _exchangeName.asString(), _queueName.asString(), _routingKey.asString()};

        assertEquals(Result.DENIED, _perms.authorise(Permission.BIND, args));
        _perms.grant(Permission.BIND, (Object[]) null);
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.BIND, args));
    }

    public void testQueueCreate()
    {
        Object[] grantArgs = new Object[]{_temporary , _queueName, _exchangeName, _routingKey};
        String[] authArgs = new String[]{Boolean.toString(_autoDelete), _queueName.asString()};

        assertEquals(Result.DENIED, _perms.authorise(Permission.CREATEQUEUE, authArgs));
        _perms.grant(Permission.CREATEQUEUE, grantArgs);
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CREATEQUEUE, authArgs));
    }

     public void testQueueCreateWithNullRoutingKey()
    {
        Object[] grantArgs = new Object[]{_temporary , _queueName, _exchangeName, null};
        String[] authArgs = new String[]{Boolean.toString(_autoDelete), _queueName.asString()};
        
        assertEquals(Result.DENIED, _perms.authorise(Permission.CREATEQUEUE, authArgs));
        _perms.grant(Permission.CREATEQUEUE, grantArgs);
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CREATEQUEUE, authArgs));
    }

    // FIXME disabled, this fails due to grant putting the grant into the wrong map QPID-1598
    public void disableTestExchangeCreate()
    {
        String[] authArgs = new String[]{_exchangeName.asString()};
        Object[] grantArgs = new Object[]{_exchangeName, _exchangeType};

        assertEquals(Result.DENIED, _perms.authorise(Permission.CREATEEXCHANGE, authArgs));
        _perms.grant(Permission.CREATEEXCHANGE, grantArgs);
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CREATEEXCHANGE, authArgs));
    }

    public void testConsume()
    {
        String[] authArgs = new String[]{_queueName.asString(), Boolean.toString(_autoDelete), _user};
        Object[] grantArgs = new Object[]{_queueName, _ownQueue};

        // FIXME: This throws a null pointer exception QPID-1599
        // assertFalse(_perms.authorise(Permission.CONSUME, authArgs));
        _perms.grant(Permission.CONSUME, grantArgs);
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CONSUME, authArgs));
    }

    public void testPublish() throws AMQException
    {
        String[] authArgs = new String[]{_exchangeName.asString(), _routingKey.asString()};
        Object[] grantArgs = new Object[]{_exchangeName, _routingKey};

        assertEquals(Result.DENIED, _perms.authorise(Permission.PUBLISH, authArgs));
        _perms.grant(Permission.PUBLISH, grantArgs);
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.PUBLISH, authArgs));
    }

    public void testVhostAccess()
    {
        //Tests that granting a user Virtualhost level access allows all authorisation requests
        //where previously they would be denied

        //QPID-2133 createExchange rights currently allow all exchange creation unless rights for creating some
        //specific exchanges are granted. Grant a specific exchange creation to cause all others to be denied.
        Object[] createArgsCreateExchange = new Object[]{new AMQShortString("madeup"), _exchangeType};
        String[] authArgsCreateExchange = new String[]{_exchangeName.asString()};
        assertEquals("Exchange creation was not allowed", Result.ALLOWED, _perms.authorise(Permission.CREATEEXCHANGE, authArgsCreateExchange));
        _perms.grant(Permission.CREATEEXCHANGE, createArgsCreateExchange);

        String[] authArgsPublish = new String[]{_exchangeName.asString(), _routingKey.asString()};
        String[] authArgsConsume = new String[]{_queueName.asString(), Boolean.toString(_autoDelete), _user};
        String[] authArgsCreateQueue = new String[]{Boolean.toString(_autoDelete), _queueName.asString()};
//        QueueBindBodyImpl bind = new QueueBindBodyImpl(_ticket, _queueName, _exchangeName, _routingKey, _nowait, _arguments);
        String[] authArgsBind = new String[]{ null, _exchangeName.asString(), _queueName.asString(), _routingKey.asString()};

        assertEquals("Exchange creation was not denied", Result.DENIED, _perms.authorise(Permission.CREATEEXCHANGE, authArgsCreateExchange));
        assertEquals("Publish was not denied", Result.DENIED, _perms.authorise(Permission.PUBLISH, authArgsPublish));
        assertEquals("Consume creation was not denied", Result.DENIED, _perms.authorise(Permission.CONSUME, authArgsConsume));
        assertEquals("Queue creation was not denied", Result.DENIED, _perms.authorise(Permission.CREATEQUEUE, authArgsCreateQueue));
        //BIND pre-grant authorise check disabled due to QPID-1597
        //assertEquals("Binding creation was not denied", Result.DENIED, _perms.authorise(Permission.BIND, authArgsBind));

        _perms.grant(Permission.ACCESS);

        assertEquals("Exchange creation was not allowed", Result.ALLOWED, _perms.authorise(Permission.CREATEEXCHANGE, authArgsCreateExchange));
        assertEquals("Publish was not allowed", Result.ALLOWED, _perms.authorise(Permission.PUBLISH, authArgsPublish));
        assertEquals("Consume creation was not allowed", Result.ALLOWED, _perms.authorise(Permission.CONSUME, authArgsConsume));
        assertEquals("Queue creation was not allowed", Result.ALLOWED, _perms.authorise(Permission.CREATEQUEUE, authArgsCreateQueue));
        assertEquals("Binding creation was not allowed", Result.ALLOWED, _perms.authorise(Permission.BIND, authArgsBind));
    }

    /**
     * If the consume permission for temporary queues is for an unnamed queue then is should
     * be global for any temporary queue but not for any non-temporary queue
     */
    public void testTemporaryUnnamedQueueConsume()
    {
        String[] authNonTempQArgs = new String[]{_queueName.asString(), Boolean.toString(_autoDelete), _user};
        String[] authTempQArgs = new String[]{_tempQueueName.asString(), Boolean.TRUE.toString(), _user};
        Object[] grantArgs = new Object[]{true};

        _perms.grant(Permission.CONSUME, grantArgs);

        //Next line shows up bug - non temp queue should be denied
        assertEquals(Result.DENIED, _perms.authorise(Permission.CONSUME, authNonTempQArgs));
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CONSUME, authTempQArgs));
    }

    /**
     * Test that temporary queue permissions before queue perms in the ACL config work correctly
     */
    public void testTemporaryQueueFirstConsume()
    {
        String[] authNonTempQArgs = new String[]{_queueName.asString(), Boolean.toString(_autoDelete), _user};
        String[] authTempQArgs = new String[]{_tempQueueName.asString(), Boolean.TRUE.toString(), _user};
        Object[] grantArgs = new Object[]{true};
        Object[] grantNonTempQArgs = new Object[]{_queueName, _ownQueue};

        //should not matter if the temporary permission is processed first or last
        _perms.grant(Permission.CONSUME, grantNonTempQArgs);
        _perms.grant(Permission.CONSUME, grantArgs);

        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CONSUME, authNonTempQArgs));
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CONSUME, authTempQArgs));
    }

    /**
     * Test that temporary queue permissions after queue perms in the ACL config work correctly
     */
    public void testTemporaryQueueLastConsume()
    {
        String[] authNonTempQArgs = new String[]{_queueName.asString(), Boolean.toString(_autoDelete), _user};
        String[] authTempQArgs = new String[]{_tempQueueName.asString(), Boolean.TRUE.toString(), _user};
        Object[] grantArgs = new Object[]{true};
        Object[] grantNonTempQArgs = new Object[]{_queueName, _ownQueue};

        //should not matter if the temporary permission is processed first or last
        _perms.grant(Permission.CONSUME, grantArgs);
        _perms.grant(Permission.CONSUME, grantNonTempQArgs);

        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CONSUME, authNonTempQArgs));
        assertEquals(Result.ALLOWED, _perms.authorise(Permission.CONSUME, authTempQArgs));
    }
}
