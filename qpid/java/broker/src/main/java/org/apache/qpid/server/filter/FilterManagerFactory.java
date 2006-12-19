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

import org.apache.qpid.framing.FieldTable;
import org.apache.qpid.AMQException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.util.Iterator;


public class FilterManagerFactory
{
    //private final static Logger _logger = LoggerFactory.getLogger(FilterManagerFactory.class);
    private final static org.apache.log4j.Logger _logger = org.apache.log4j.Logger.getLogger(FilterManagerFactory.class);

    //fixme move to a common class so it can be refered to from client code.
    private static String JMS_SELECTOR_FILTER = "x-filter-jms-selector";

    public static FilterManager createManager(FieldTable filters) throws AMQException
    {
        FilterManager manager = null;

        if (filters != null)
        {

            manager = new SimpleFilterManager();

            Iterator it = filters.keySet().iterator();
            _logger.info("Processing filters:");
            while (it.hasNext())
            {
                String key = (String) it.next();
                _logger.info("filter:" + key);
                if (key.equals(JMS_SELECTOR_FILTER))
                {
                    String selector = (String) filters.get(key);

                    if (selector != null && !selector.equals(""))
                    {
                        manager.add(new JMSSelectorFilter(selector));
                    }
                }

            }

            //If we added no filters don't bear the overhead of having an filter manager
            if (!manager.hasFilters())
            {
                manager = null;
            }
        }
        else
        {
            _logger.info("No Filters found.");
        }


        return manager;

    }
}
