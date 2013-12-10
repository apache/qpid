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
define(["qpid/management/UserPreferences"], function (UserPreferences) {
    var updateList = new Array();

    function invokeUpdates()
    {
      for(var i = 0; i < updateList.length; i++)
      {
        var obj = updateList[i];
        obj.update();
      }
    }

    var updatePeriod = UserPreferences.updatePeriod ? UserPreferences.updatePeriod: 5;

    var timer = setInterval(invokeUpdates, updatePeriod * 1000);

    var updateIntervalListener = {
        onPreferencesChange: function(preferences)
        {
          if (preferences.updatePeriod && preferences.updatePeriod != updatePeriod)
          {
            updatePeriod = preferences.updatePeriod;
            clearInterval(timer);
            timer = setInterval(invokeUpdates, updatePeriod * 1000);
          }
        }
    };

    UserPreferences.addListener(updateIntervalListener);

    return {
            add: function(obj) {
                updateList.push(obj);
            },

            remove: function(obj) {
                for(var i = 0; i < updateList.length; i++) {
                    if(updateList[i] === obj) {
                        updateList.splice(i,1);
                        return;
                    }
                }
            }
        };
});