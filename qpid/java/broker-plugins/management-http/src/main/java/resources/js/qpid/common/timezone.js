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

define(["dojo/_base/xhr"], function (xhr) {

    var timezones = {};

    var timeZoneSortFunction = function(a, b){
      if (a.region == b.region)
      {
          if (a.city == b.city)
          {
            return 0;
          }
          return a.city < b.city ? -1 : 1;
      }
      return a.region < b.region ? -1 : 1;
    }

    function loadTimezones()
    {
      xhr.get({
        url: "rest/helper?action=ListTimeZones",
        sync: true,
        handleAs: "json",
        load: function(zones)
        {
          zones.sort(timeZoneSortFunction);
          timezones.data = zones;
        },
        error: function(error)
        {
          if (console && console.error)
          {
            console.error(error);
          }
        }
      });
    }

    return {
      getAllTimeZones: function()
      {
        if (!timezones.data)
        {
          loadTimezones();
        }
        return timezones.data;
      },
      getTimeZoneInfo: function(timeZone) {
        if (timeZone == "UTC")
        {
          return {
            "id" : "UTC",
            "name" : "UTC",
            "offset" : 0
          }
        }
        var tzi = timezones[timeZone];
        if (!tzi)
        {
          var data = this.getAllTimeZones();
          for(var i = 0; i < data.length; i++)
          {
            var zone = data[i];
            if (zone.id == timeZone)
            {
              tzi = zone;
              timezones[timeZone] = zone;
              break;
            }
          }
        }
        return tzi;
      }
    };
});