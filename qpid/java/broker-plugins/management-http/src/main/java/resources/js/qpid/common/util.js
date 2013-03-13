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
define(["dojo/_base/xhr"],
       function (xhr) {
           var util = {};
           if (Array.isArray) {
               util.isArray = function (object) {
                   return Array.isArray(object);
               };
           } else {
               util.isArray = function (object) {
                   return object instanceof Array;
               };
           }

           util.flattenStatistics = function (data) {
               var attrName, stats, propName, theList;
               for(attrName in data) {
                   if(data.hasOwnProperty(attrName)) {
                       if(attrName == "statistics") {
                           stats = data.statistics;
                           for(propName in stats) {
                               if(stats.hasOwnProperty( propName )) {
                                   data[ propName ] = stats[ propName ];
                               }
                           }
                       } else if(data[ attrName ] instanceof Array) {
                           theList = data[ attrName ];

                           for(var i=0; i < theList.length; i++) {
                               util.flattenStatistics( theList[i] );
                           }
                       }
                   }
               }
           };

           util.isReservedExchangeName = function(exchangeName)
           {
               return exchangeName == null || exchangeName == "" || "<<default>>" == exchangeName || exchangeName.indexOf("amq.") == 0 || exchangeName.indexOf("qpid.") == 0;
           };

           util.deleteGridSelections = function(updater, grid, url, confirmationMessageStart)
           {
               var data = grid.selection.getSelected();

               if(data.length)
               {
                   var confirmationMessage = null;
                   if (data.length == 1)
                   {
                       confirmationMessage = confirmationMessageStart + " '" + data[0].name + "'?";
                   }
                   else
                   {
                       var names = '';
                       for(var i = 0; i<data.length; i++)
                       {
                           if (names)
                           {
                               names += ', ';
                           }
                           names += "\""+ data[i].name + "\"";
                       }
                       confirmationMessage = confirmationMessageStart + "s " + names + "?";
                   }
                   if(confirm(confirmationMessage))
                   {
                       var i, queryParam;
                       for(i = 0; i<data.length; i++)
                       {
                           if(queryParam)
                           {
                               queryParam += "&";
                           }
                           else
                           {
                               queryParam = "?";
                           }
                           queryParam += "id=" + data[i].id;
                       }
                       var query = url + queryParam;
                       var success = true
                       var failureReason = "";
                       xhr.del({url: query, sync: true, handleAs: "json"}).then(
                           function(data)
                           {
                               // TODO why query *??
                               //grid.setQuery({id: "*"});
                               grid.selection.deselectAll();
                               updater.update();
                           },
                           function(error) {success = false; failureReason = error;});
                       if(!success )
                       {
                           alert("Error:" + failureReason);
                       }
                   }
               }
           }

           util.isProviderManagingUsers = function(type)
           {
               return (type === "PlainPasswordFile" || type === "Base64MD5PasswordFile");
           };

           return util;
       });