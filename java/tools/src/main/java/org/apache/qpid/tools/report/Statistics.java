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
import java.text.DecimalFormat;

import javax.jms.Message;

public interface Statistics
{
    public void message(Message msg);
    public void report(PrintStream out);
    public void header(PrintStream out);
    public void clear();

    static class Throughput implements Statistics 
    {
        DecimalFormat df = new DecimalFormat("###.##");
        int messages = 0;
        long start = 0;
        boolean started = false;

        @Override
        public void message(Message msg)
        {
            ++messages;
            if (!started) 
            {
                start = System.currentTimeMillis();
                started = true;
            }
        }

        @Override
        public void report(PrintStream out)
        {
            long elapsed = System.currentTimeMillis() - start;
            out.print(df.format((double)messages/(double)elapsed));            
        }

        @Override
        public void header(PrintStream out)
        {
            out.print("tp(m/s)");            
        }        

        public void clear()
        {
            messages = 0;
            start = 0;
            started = false;
        }
    }

    static class ThroughputAndLatency extends Throughput 
    {
        long minLatency = Long.MAX_VALUE;
        long maxLatency = Long.MIN_VALUE;
        double totalLatency = 0;
        int sampleCount = 0;

        @Override
        public void message(Message msg)
        {
            super.message(msg);
            try
            {
                long ts = msg.getLongProperty("ts");
                long latency = System.currentTimeMillis() - ts;
                minLatency = Math.min(latency, minLatency);
                maxLatency = Math.min(latency, maxLatency);
                totalLatency = totalLatency + latency;
                sampleCount++;
            }
            catch(Exception e)
            {
                System.out.println("Error calculating latency");
            }
        }

        @Override
        public void report(PrintStream out)
        {
            super.report(out);
            double avgLatency = totalLatency/(double)sampleCount;
            out.append('\t')
            .append(String.valueOf(minLatency))
            .append('\t')
            .append(String.valueOf(maxLatency))
            .append('\t')
            .append(df.format(avgLatency));   

            out.flush();
        }

        @Override
        public void header(PrintStream out)
        {
            super.header(out);
            out.append('\t')
            .append("l-min")
            .append('\t')
            .append("l-max")
            .append('\t')
            .append("l-avg");

            out.flush();
        }       

        public void clear()
        {
            super.clear();
            minLatency = 0;
            maxLatency = 0;
            totalLatency = 0;
            sampleCount = 0;
        }
    }

}
