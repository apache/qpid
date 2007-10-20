/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.    
 *
 * 
 */
package org.apache.qpid.server.filter;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.server.filter.jms.selector.SelectorParser;
import org.apache.qpid.server.queue.AMQMessage;



public class JMSSelectorFilter implements MessageFilter
{
    private final static Logger _logger = org.apache.log4j.Logger.getLogger(JMSSelectorFilter.class);

    private String _selector;
    private BooleanExpression _matcher;

    public JMSSelectorFilter(String selector) throws AMQException
    {
        _selector = selector;
        _logger.info("Created JMSSelectorFilter with selector:" + _selector);


        _matcher = new SelectorParser().parse(selector);


    }

    public boolean matches(AMQMessage message)
    {
        try
        {
            boolean match = _matcher.matches(message);
            _logger.info(message + " match(" + match + ") selector(" + System.identityHashCode(_selector) + "):" + _selector);
            return match;
        }
        catch (AMQException e)
        {
            //fixme this needs to be sorted.. it shouldn't happen
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return false;
    }

    public String getSelector()
    {
        return _selector;
    }
}
