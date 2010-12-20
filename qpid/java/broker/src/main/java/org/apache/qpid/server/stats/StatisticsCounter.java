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
package org.apache.qpid.server.stats;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class collects statistics and counts the total, rate per second and
 * peak rate per second values for the events that are registered with it. 
 */
public class StatisticsCounter
{
    private static final Logger _log = LoggerFactory.getLogger(StatisticsCounter.class);
    
    public static final long DEFAULT_SAMPLE_PERIOD = Long.getLong("qpid.statistics.samplePeriod", 2000L); // 2s
    public static final boolean DISABLE_STATISTICS = Boolean.getBoolean("qpid.statistics.disable");
    
    private static final String COUNTER = "counter";
    private static final AtomicLong _counterIds = new AtomicLong(0L);
    
    private long _peak = 0L;
    private long _total = 0L;
    private long _temp = 0L;
    private long _last = 0L;
    private long _rate = 0L;

    private long _start;
    
    private final long _period;
    private final String _name;

    public StatisticsCounter()
    {
        this(COUNTER);
    }
    
    public StatisticsCounter(String name)
    {
        this(name, DEFAULT_SAMPLE_PERIOD);
    }

    public StatisticsCounter(String name, long period)
    {
        _period = period;
        _name = name + "-" + + _counterIds.incrementAndGet();
        reset();
    }
    
    public void registerEvent()
    {
        registerEvent(1L);
    }

    public void registerEvent(long value)
    {
        registerEvent(value, System.currentTimeMillis());
    }

    public void registerEvent(long value, long timestamp)
    {
        if (DISABLE_STATISTICS)
        {
            return;
        }
        
        long thisSample = (timestamp / _period);
        synchronized (this)
        {
            if (thisSample > _last)
            {
                _last = thisSample;
                _rate = _temp;
                _temp = 0L;
                if (_rate > _peak)
                {
                    _peak = _rate;
                }
            }
            
            _total += value;
            _temp += value;
        }
    }
    
    /**
     * Update the current rate and peak - may reset rate to zero if a new
     * sample period has started.
     */
    private void update()
    {
        registerEvent(0L, System.currentTimeMillis());
    }

    /**
     * Reset 
     */
    public void reset()
    {
        _log.info("Resetting statistics for counter: " + _name);
        _peak = 0L;
        _rate = 0L;
        _total = 0L;
        _start = System.currentTimeMillis();
        _last = _start / _period;
    }

    public double getPeak()
    {
        update();
        return (double) _peak / ((double) _period / 1000.0d);
    }

    public double getRate()
    {
        update();
        return (double) _rate / ((double) _period / 1000.0d);
    }

    public long getTotal()
    {
        return _total;
    }

    public long getStart()
    {
        return _start;
    }

    public Date getStartTime()
    {
        return new Date(_start);
    }
    
    public String getName()
    {
        return _name;
    }
    
    public long getPeriod()
    {
        return _period;
    }
}
