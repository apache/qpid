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
package org.apache.qpid.disttest.results.aggregation;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public class SeriesStatistics
{
    private long _minValue;
    private long _maxValue;
    private double _mean;
    private double _standardDeviation;
    private Collection<Long> _series = new CopyOnWriteArrayList<Long>();

    public SeriesStatistics()
    {
        super();
    }

    public SeriesStatistics(Collection<Long> messageLatencies)
    {
        setMessageLatencies(messageLatencies);
    }

    public void addMessageLatencies(Collection<Long> messageLatencies)
    {
        if (messageLatencies != null)
        {
            _series.addAll(messageLatencies);
        }
    }

    public void setMessageLatencies(Collection<Long> messageLatencies)
    {
        _series = messageLatencies;
        aggregate();
    }

    public void aggregate()
    {
        if (_series != null && _series.size() > 0)
        {
            long minLatency = Long.MAX_VALUE;
            long maxLatency = Long.MIN_VALUE;
            long totalLatency = 0;
            for (Long latency : _series)
            {
                totalLatency += latency;
                minLatency = Math.min(minLatency, latency);
                maxLatency = Math.max(maxLatency, latency);
            }
            _mean = ((double) totalLatency) / (double) _series.size();
            _minValue = minLatency;
            _maxValue = maxLatency;
            double sum = 0;
            for (Long latency : _series)
            {
                double diff = latency - _mean;
                sum += diff * diff;
            }
            long size = _series.size() == 1 ? 1: _series.size() - 1;
            _standardDeviation = Math.sqrt(sum / (double) size);
        }
        else
        {
            _mean = 0;
            _minValue = 0;
            _maxValue = 0;
            _standardDeviation = 0;
        }
    }

    public long getMinimum()
    {
        return _minValue;
    }

    public long getMaximum()
    {
        return _maxValue;
    }

    public double getAverage()
    {
        return _mean;
    }

    public double getStandardDeviation()
    {
        return _standardDeviation;
    }
}
