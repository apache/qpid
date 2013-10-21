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

package org.apache.qpid.server.management.plugin.servlet.rest.action;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.apache.qpid.server.management.plugin.servlet.rest.Action;
import org.apache.qpid.server.model.Broker;

public class ListTimeZones implements Action
{

    private static final String[] TIMEZONE_REGIONS = { "Africa", "America", "Antarctica", "Arctic", "Asia", "Atlantic", "Australia",
            "Europe", "Indian", "Pacific" };

    @Override
    public String getName()
    {
        return ListTimeZones.class.getSimpleName();
    }

    @Override
    public Object perform(Map<String, Object> request, Broker broker)
    {
        List<TimeZoneDetails> timeZoneDetails = new ArrayList<TimeZoneDetails>();
        String[] ids = TimeZone.getAvailableIDs();
        long currentTime = System.currentTimeMillis();
        Date currentDate = new Date(currentTime);
        for (String id : ids)
        {
            int cityPos = id.indexOf("/");
            if (cityPos > 0 && cityPos < id.length() - 1)
            {
                String region = id.substring(0, cityPos);
                for (int i = 0; i < TIMEZONE_REGIONS.length; i++)
                {
                    if (region.equals(TIMEZONE_REGIONS[i]))
                    {
                        TimeZone tz = TimeZone.getTimeZone(id);
                        int offset = tz.getOffset(currentTime)/60000;
                        String city = id.substring(cityPos + 1).replace('_', ' ');
                        timeZoneDetails.add(new TimeZoneDetails(id, tz.getDisplayName(tz.inDaylightTime(currentDate), TimeZone.SHORT), offset, city, region));
                        break;
                    }
                }
            }
        }
        return timeZoneDetails;
    }

    public static class TimeZoneDetails
    {
        private String id;
        private String name;
        private int offset;
        private String city;
        private String region;

        public TimeZoneDetails(String id, String name, int offset, String city, String region)
        {
            super();
            this.id = id;
            this.name = name;
            this.offset = offset;
            this.city = city;
            this.region = region;
        }

        public String getId()
        {
            return id;
        }

        public String getName()
        {
            return name;
        }

        public int getOffset()
        {
            return offset;
        }

        public String getCity()
        {
            return city;
        }

        public String getRegion()
        {
            return region;
        }
    }
}
