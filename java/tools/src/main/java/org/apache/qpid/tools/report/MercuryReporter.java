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
package org.apache.qpid.tools.report;

import java.io.PrintStream;

import org.apache.qpid.tools.report.Statistics.Throughput;
import org.apache.qpid.tools.report.Statistics.ThroughputAndLatency;

public class MercuryReporter extends BasicReporter
{
    MercuryStatistics stats;

    public MercuryReporter(Class<? extends MercuryStatistics> clazz, PrintStream out,
            int batchSize, boolean printHeader) throws Exception
    {
        super(clazz, out, batchSize, printHeader);
        stats = (MercuryStatistics)overall;
    }

    public double getRate()
    {
        return stats.getRate();
    }

    public double getAvgLatency()
    {
        return stats.getAvgLatency();
    }

    public double getStdDev()
    {
        return stats.getStdDev();
    }

    public double getMinLatency()
    {
        return stats.getMinLatency();
    }

    public double getMaxLatency()
    {
        return stats.getMaxLatency();
    }

    public int getSampleSize()
    {
        return stats.getSampleSize();
    }

    public interface MercuryStatistics extends Statistics
    {
        public double getRate();
        public long getMinLatency();
        public long getMaxLatency();
        public double getAvgLatency();
        public double getStdDev();
        public int getSampleSize();
    }

    public static class MercuryThroughput extends Throughput implements MercuryStatistics
    {
        double rate = 0;

        @Override
        public void report(PrintStream out)
        {
            long elapsed = System.currentTimeMillis() - start;
            rate = (double)messages/(double)elapsed;
        }

        @Override
        public void clear()
        {
            super.clear();
            rate = 0;
        }

        public double getRate()
        {
            return rate;
        }

        public int getSampleSize()
        {
            return messages;
        }

        public long getMinLatency() { return 0; }
        public long getMaxLatency() { return 0; }
        public double getAvgLatency(){ return 0; }
        public double getStdDev(){ return 0; }

    }

    public static class MercuryThroughputAndLatency extends ThroughputAndLatency implements MercuryStatistics
    {
        double rate = 0;
        double avgLatency = 0;
        double stdDev;

        @Override
        public void report(PrintStream out)
        {
            long elapsed = System.currentTimeMillis() - start;
            rate = (double)messages/(double)elapsed;
            avgLatency = totalLatency/(double)sampleCount;
        }

        @Override
        public void clear()
        {
            super.clear();
            rate = 0;
            avgLatency = 0;
        }

        public double getRate()
        {
            return rate;
        }

        public long getMinLatency()
        { 
            return minLatency; 
        }

        public long getMaxLatency() 
        { 
            return maxLatency; 
        }

        public double getAvgLatency()
        { 
            return avgLatency; 
        }

        public double getStdDev()
        { 
            return stdDev; 
        }

        public int getSampleSize()
        {
            return messages;
        }
    }

}
