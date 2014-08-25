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
define(["dojo/_base/xhr",
        "dojo/_base/array",
        "dojox/lang/functional/object",
        "qpid/common/properties",
        "dojo/domReady!"
        ],
  function (xhr, array, fobject, properties)
  {
   var metadata =
   {
     _init: function ()
     {
       var that = this;
       xhr.get({sync: true, handleAs: "json", url: "service/metadata", load: function(metadata){that._onMetadata(metadata)}});
     },
     _onMetadata: function (metadata)
     {
       this.metadata = metadata;
     },
     getMetaData: function (category, type)
     {
       return this.metadata[category][type];
     },
     getDefaultValueForAttribute: function (category, type, attributeName)
     {
       var metaDataForInstance = this.getMetaData(category, type);
       var attributesForType =  metaDataForInstance["attributes"];
       var attributesForName = attributesForType[attributeName];
       return attributesForName ? attributesForName["defaultValue"] : undefined;
     },
     getTypesForCategory: function (category)
     {
        return fobject.keys(this.metadata[category]);
     },
     extractUniqueListOfValues : function(data)
     {
        var values = [];
        for (i = 0; i < data.length; i++)
        {
           for (j = 0; j < data[i].length; j++)
           {
               var current = data[i][j];
               if (array.indexOf(values, current) == -1)
               {
                   values.push(current);
               }
           }
        }
        return values;
     }
   };

   metadata._init();

   return metadata;
  });
