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

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.common.AMQPFilterTypes;
import org.apache.qpid.filter.SelectorParsingException;
import org.apache.qpid.filter.selector.ParseException;
import org.apache.qpid.filter.selector.TokenMgrError;


public class FilterManagerFactory
{

    private final static Logger _logger = LoggerFactory.getLogger(FilterManagerFactory.class);

    private FilterManagerFactory()
    {
    }

    //TODO move to a common class so it can be referred to from client code.

    public static FilterManager createManager(Map<String,Object> filters) throws AMQInvalidArgumentException
    {
        FilterManager manager = null;

        if (filters != null)
        {

            if(filters.containsKey(AMQPFilterTypes.JMS_SELECTOR.toString()))
            {
                Object selector = filters.get(AMQPFilterTypes.JMS_SELECTOR.toString());

                if (selector instanceof String && !selector.equals(""))
                {
                    manager = new FilterManager();
                    try
                    {
                        MessageFilter filter = new JMSSelectorFilter((String)selector);
                        manager.add(filter.getName(), filter);
                    }
                    catch (ParseException | SelectorParsingException | TokenMgrError e)
                    {
                        throw new AMQInvalidArgumentException("Cannot parse JMS selector \"" + selector + "\"", e);
                    }
                }

            }


        }
        else
        {
            _logger.debug("No Filters found.");
        }


        return manager;

    }

}
