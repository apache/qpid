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


if (Array.isArray)
{
    isArray = function (object)
              {
                  return Array.isArray(object);
              };
}
else
{
    isArray = function (object)
              {
                  return object instanceof Array;
              };
}

var updateList = new Array();

var useSyncGet = false;

require(["dojo/has", "dojo/_base/sniff"], function(has)
{
  if(has("ie") <= 8)
  {
    useSyncGet = true;
  }
});

setInterval(function()
{
  for(var i = 0; i < updateList.length; i++)
  {
      var obj = updateList[i];
      obj.update();


  }
}, 5000);



var formatBytes = function formatBytes(amount)
{
    var returnVal = { units: "B",
                      value: "0"};


    if(amount < 1000)
    {
        returnVal.value = amount;
    }
    else if(amount < 1000 * 1024)
    {
        returnVal.units = "KB";
        returnVal.value = (amount / 1024).toPrecision(3);
    }
    else if(amount < 1000 * 1024 * 1024)
    {
        returnVal.units = "MB";
        returnVal.value = (amount / (1024 * 1024)).toPrecision(3);
    }
    else if(amount < 1000 * 1024 * 1024 * 1024)
    {
        returnVal.units = "GB";
        returnVal.value = (amount / (1024 * 1024 * 1024)).toPrecision(3);
    }

    return returnVal;

};

var formatTime = function formatTime(amount)
{
    var returnVal = { units: "ms",
                      value: "0"};

    if(amount < 1000)
    {
        returnVal.units = "ms";
        returnVal.value = amount.toString();
    }
    else if(amount < 1000 * 60)
    {
        returnVal.units = "s";
        returnVal.value = (amount / 1000).toPrecision(3);
    }
    else if(amount < 1000 * 60 * 60)
    {
        returnVal.units = "min";
        returnVal.value = (amount / (1000 * 60)).toPrecision(3);
    }
    else if(amount < 1000 * 60 * 60 * 24)
    {
        returnVal.units = "hr";
        returnVal.value = (amount / (1000 * 60 * 60)).toPrecision(3);
    }
    else if(amount < 1000 * 60 * 60 * 24 * 7)
    {
        returnVal.units = "d";
        returnVal.value = (amount / (1000 * 60 * 60 * 24)).toPrecision(3);
    }
    else if(amount < 1000 * 60 * 60 * 24 * 365)
    {
        returnVal.units = "wk";
        returnVal.value = (amount / (1000 * 60 * 60 * 24 * 7)).toPrecision(3);
    }
    else
    {
        returnVal.units = "yr";
        returnVal.value = (amount / (1000 * 60 * 60 * 24 * 365)).toPrecision(3);
    }

    return returnVal;
}

function flattenStatistics(data)
{
    for(var attrName in data)
    {
        if(attrName == "statistics")
        {
            var stats = data.statistics;
            for(var propName in stats)
            {
                data[ propName ] = stats[ propName ];
            }
        }
        else if(data[ attrName ] instanceof Array)
        {
            var theList = data[ attrName ];

            for(var i=0; i < theList.length; i++)
            {
                flattenStatistics( theList[i] );
            }
        }
    }
}

function UpdatableStore( Observable, Memory, ObjectStore, DataGrid, data, divName, structure, func )
{

    var thisObj = this;

    thisObj.store = Observable(Memory({data: data, idProperty: "id"}));
    thisObj.dataStore = ObjectStore({objectStore: thisObj.store});
    thisObj.grid =
        new DataGrid({  store: thisObj.dataStore,
                        structure: structure,
                        autoHeight: true
                     }, divName);

    // since we created this grid programmatically, call startup to render it
    thisObj.grid.startup();

    if( func )
    {
        func(thisObj);
    }

}

UpdatableStore.prototype.update = function(bindingData)
{
    data = bindingData;
    var store = this.store;


    // handle deletes
    // iterate over existing store... if not in new data then remove
    store.query({ }).forEach(function(object)
                             {
                                 if(data)
                                 {
                                     for(var i=0; i < data.length; i++)
                                     {
                                         if(data[i].id == object.id)
                                         {
                                             return;
                                         }
                                     }
                                 }
                                 store.remove(object.id);

                             });

    // iterate over data...
    if(data)
    {
        for(var i=0; i < data.length; i++)
        {
            if(theItem = store.get(data[i].id))
            {
                var modified;
                for(var propName in data[i])
                {
                    if(theItem[ propName ] != data[i][ propName ])
                    {
                        theItem[ propName ] = data[i][ propName ];
                        modified = true;
                    }
                }
                if(modified)
                {
                    // ... check attributes for updates
                    store.notify(theItem, data[i].id);
                }
            }
            else
            {
                // ,,, if not in the store then add
                store.put(data[i]);
            }
        }
    }

};

