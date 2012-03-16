package org.apache.qpid.server.model.adapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.qpid.server.model.Statistics;
import org.apache.qpid.server.stats.StatisticsCounter;
import org.apache.qpid.server.stats.StatisticsGatherer;

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
class StatisticsAdapter implements Statistics
{

    private final Map<String, StatisticsCounter> _statistics =
            new HashMap<String, StatisticsCounter>();


    private static final String BYTES_IN = "bytesIn";
    private static final String BYTES_OUT = "bytesOut";
    private static final String MESSAGES_IN = "messagesIn";
    private static final String MESSAGES_OUT = "messagesOut";

    private static final Collection<String> STATISTIC_NAMES =
            Collections.unmodifiableCollection(Arrays.asList(BYTES_IN, BYTES_OUT, MESSAGES_IN, MESSAGES_OUT));

    

    public StatisticsAdapter(StatisticsGatherer applicationRegistry)
    {
        _statistics.put(BYTES_OUT, applicationRegistry.getDataDeliveryStatistics());
        _statistics.put(BYTES_IN, applicationRegistry.getDataReceiptStatistics());
        _statistics.put(MESSAGES_OUT, applicationRegistry.getMessageDeliveryStatistics());
        _statistics.put(MESSAGES_IN, applicationRegistry.getMessageReceiptStatistics());
    }

    
    public Collection<String> getStatisticNames()
    {
        return STATISTIC_NAMES;
    }

    public Number getStatistic(String name)
    {
        StatisticsCounter counter = _statistics.get(name);
        return counter == null ? null : counter.getTotal();

    }
    
    
}
