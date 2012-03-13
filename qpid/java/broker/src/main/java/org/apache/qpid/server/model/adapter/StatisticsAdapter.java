package org.apache.qpid.server.model.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
                  

    private static final Collection<String> STATISTIC_NAMES;
    
    static 
    {
        List<String> names = new ArrayList<String>(16);
        for(String stat : new String[] {"bytes-in", "bytes-out", "msgs-in", "msgs-out"})
        {
            for(String type : new String[] {"total","rate","peak"})
            {
                names.add(stat + "-" + type);
            }
        }
        STATISTIC_NAMES = Collections.unmodifiableCollection(names);
    }
    
    public StatisticsAdapter(StatisticsGatherer applicationRegistry)
    {
        _statistics.put("bytes-out", applicationRegistry.getDataDeliveryStatistics());
        _statistics.put("bytes-in", applicationRegistry.getDataReceiptStatistics());
        _statistics.put("msgs-out", applicationRegistry.getMessageDeliveryStatistics());
        _statistics.put("msgs-in", applicationRegistry.getMessageReceiptStatistics());
    }

    
    public Collection<String> getStatisticNames()
    {
        return STATISTIC_NAMES;
    }

    public Number getStatistic(String name)
    {
        if(name.endsWith("-total"))
        {
            StatisticsCounter counter = _statistics.get(name.substring(0,name.length()-6));
            return counter == null ? null : counter.getTotal();
        }
        else if(name.endsWith("-rate"))
        {
            StatisticsCounter counter = _statistics.get(name.substring(0,name.length()-5));
            return counter == null ? null : counter.getRate();
        }
        else if(name.endsWith("-peak"))
        {
            StatisticsCounter counter = _statistics.get(name.substring(0,name.length()-5));
            return counter == null ? null : counter.getPeak();
        }

        return null;
    }
    
    
}
