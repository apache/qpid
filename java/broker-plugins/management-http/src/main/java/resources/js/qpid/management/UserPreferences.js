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
define(["dojo/_base/xhr",
        "dojo/json",
        "dojo/date",
        "dojo/date/locale",
        "dojo/number",
        "qpid/common/timezone"], function (xhr, json, date, locale, number, timezone) {

  var listeners = [];

  var UserPreferences = {

    loadPreferences : function(callbackSuccessFunction, callbackErrorFunction)
    {
      var that = this;
      xhr.get({
        url: "rest/preferences",
        sync: true,
        handleAs: "json",
        load: function(data)
        {
          for(var name in data)
          {
            that[name] = data[name];
          }
          if (callbackSuccessFunction)
          {
            callbackSuccessFunction();
          }
       },
       error: function(error)
       {
         if (callbackErrorFunction)
         {
           callbackErrorFunction(error);
         }
       }
      });
    },

    setPreferences : function(preferences, callbackSuccessFunction, callbackErrorFunction)
    {
      var that = this;
      xhr.post({
        url: "rest/preferences",
        sync: true,
        handleAs: "json",
        headers: { "Content-Type": "application/json"},
        postData: json.stringify(preferences),
        load: function(x)
        {
          for(var name in preferences)
          {
            if (preferences.hasOwnProperty(name))
            that[name] = preferences[name];
          }
          that._notifyListeners(preferences);
          if (callbackSuccessFunction)
          {
            callbackSuccessFunction(preferences);
          }
        },
        error: function(error)
        {
          if (callbackErrorFunction)
          {
            callbackErrorFunction(error);
          }
        }
      });
    },

    resetPreferences : function()
    {
      var preferences = {};
      for(var name in this)
      {
        if (this.hasOwnProperty(name) && typeof this[name] != "function")
        {
          if (name == "preferencesError")
          {
            continue;
          }
          this[name] = null;
          preferences[name] = undefined;
          delete preferences[name];
        }
      }
      this._notifyListeners(preferences);
    },

    addListener : function(obj)
    {
      listeners.push(obj);
    },

    removeListener : function(obj)
    {
      for(var i = 0; i < listeners.length; i++)
      {
        if(listeners[i] === obj)
        {
          listeners.splice(i,1);
          return;
        }
      }
    },

    _notifyListeners : function(preferences)
    {
      for(var i = 0; i < listeners.length; i++)
      {
        try
        {
          listeners[i].onPreferencesChange(preferences);
        }
        catch(e)
        {
          if (console && console.warn)
          {
            console.warn(e);
          }
        }
      }
    },

    getTimeZoneInfo : function(timeZoneName)
    {
      if (!timeZoneName && this.timeZone)
      {
        timeZoneName = this.timeZone;
      }

      if (!timeZoneName)
      {
        return null;
      }

      return timezone.getTimeZoneInfo(timeZoneName);
    },

    addTimeZoneOffsetToUTC : function(utcTimeInMilliseconds, timeZone)
    {
      var tzi = null;
      if (timeZone && timeZone.hasOwnProperty("offset"))
      {
        tzi = timeZone;
      }
      else
      {
        tzi = this.getTimeZoneInfo(timeZone);
      }

      if (tzi)
      {
        var browserTimeZoneOffsetInMinutes = -new Date().getTimezoneOffset();
        return utcTimeInMilliseconds + ( tzi.offset - browserTimeZoneOffsetInMinutes ) * 60000;
      }
      return utcTimeInMilliseconds;
    },

    getTimeZoneDescription : function(timeZone)
    {
      var tzi = null;
      if (timeZone && timeZone.hasOwnProperty("offset"))
      {
        tzi = timeZone;
      }
      else
      {
        tzi = this.getTimeZoneInfo(timeZone);
      }

      if (tzi)
      {
        var timeZoneOfsetInMinutes = tzi.offset;
        return (timeZoneOfsetInMinutes>0? "+" : "")
          + number.format(timeZoneOfsetInMinutes/60, {pattern: "00"})
          + ":" + number.format(timeZoneOfsetInMinutes%60, {pattern: "00"})
          + " " + tzi.name;
      }
      return date.getTimezoneName(new Date());
    },

    formatDateTime : function(utcTimeInMilliseconds, options)
    {
      var dateTimeOptions = options || {};
      var tzi = this.getTimeZoneInfo(dateTimeOptions.timeZoneName);
      var timeInMilliseconds = utcTimeInMilliseconds;

      if (tzi && dateTimeOptions.addOffset)
      {
        timeInMilliseconds = this.addTimeZoneOffsetToUTC(utcTimeInMilliseconds, tzi);
      }

      var d = new Date(timeInMilliseconds);

      var formatOptions = {
          datePattern: dateTimeOptions.datePattern || "yyyy-MM-dd",
          timePattern: dateTimeOptions.timePattern || "HH:mm:ss.SSS"
      };

      if ("date" == dateTimeOptions.selector)
      {
        formatOptions.selector = "date";
      }
      else if ("time" == dateTimeOptions.selector)
      {
        formatOptions.selector = "time";
      }

      var result = locale.format(d, formatOptions);
      if(dateTimeOptions.appendTimeZone)
      {
        result += " (" + this.getTimeZoneDescription(tzi) + ")";
      }
      return result;
    }

  };

  UserPreferences.loadPreferences(null,
      function(error)
      {
        UserPreferences.preferencesError = error;
      }
  );

  return UserPreferences;
});