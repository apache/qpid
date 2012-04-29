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
var bindingsTuple;


require(["dojo/store/JsonRest",
				"dojo/store/Memory",
				"dojo/store/Cache",
				"dojox/grid/DataGrid",
				"dojo/data/ObjectStore",
				"dojo/query",
				"dojo/store/Observable",
                "dojo/_base/xhr",
                "dojo/dom",
				"dojo/domReady!"],
	     function(JsonRest, Memory, Cache, DataGrid, ObjectStore, query, Observable, xhr, dom)
	     {

         function Updater()
         {
            this.name = dom.byId("name");
            this.state = dom.byId("state");
            this.durable = dom.byId("durable");
            this.lifetimePolicy = dom.byId("lifetimePolicy");

            urlQuery = dojo.queryToObject(dojo.doc.location.search.substr((dojo.doc.location.search[0] === "?" ? 1 : 0)));
            this.query = "/rest/virtualhost/"+ urlQuery.vhost ;


            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                             {
                                thisObj.vhostData = data[0];

                                // flatten statistics into attributes
                                flattenStatistics( thisObj.vhostData );

                                thisObj.updateHeader();
                                thisObj.queuesGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                        thisObj.vhostData.queues, "queues",
                                                         [ { name: "Name",    field: "name",      width: "90px"},
                                                           { name: "Messages", field: "queueDepthMessages", width: "90px"},
                                                           { name: "Arguments",   field: "arguments",     width: "100%"}
                                                         ],
                                                         function(obj)
                                                         {
                                                             dojo.connect(obj.grid, "onRowDblClick", obj.grid,
                                                             function(evt){
                                                                    var idx = evt.rowIndex,
                                                                    item = this.getItem(idx);

                                                                    url = "/queue?vhost="
                                                                     + thisObj.vhostData.name + "&queue=" +
                                                                    obj.dataStore.getValue(item,"name");

                                                                    window.location = url;

                                                            });
                                                         } );

                                thisObj.exchangesGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                           thisObj.vhostData.exchanges, "exchanges",
                                                         [ { name: "Name",    field: "name",      width: "120px"},
                                                           { name: "Type", field: "type", width: "120px"},
                                                           { name: "Binding Count", field: "bindingCount",
                                                           width: "100%"}
                                                         ],
                                                           function(obj)
                                                           {
                                                               dojo.connect(obj.grid, "onRowDblClick", obj.grid,
                                                               function(evt){
                                                                      var idx = evt.rowIndex,
                                                                      item = this.getItem(idx);

                                                                      url = "/exchange?vhost="
                                                                       + encodeURIComponent(thisObj.vhostData.name) + "&exchange=" +
                                                                      encodeURIComponent(obj.dataStore.getValue(item,"name"));

                                                                      window.location = url;

                                                              });
                                                           } );


                                thisObj.connectionsGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                             thisObj.vhostData.connections,
                                                         "connections",
                                                         [ { name: "Name",    field: "name",      width: "150px"},
                                                           { name: "Sessions", field: "sessionCount", width: "70px"},
                                                           { name: "Msgs In", field: "msgInRate",
                                                           width: "80px"},
                                                           { name: "Bytes In", field: "bytesInRate",
                                                              width: "80px"},
                                                           { name: "Msgs Out", field: "msgOutRate",
                                                           width: "80px"},
                                                           { name: "Bytes Out", field: "bytesOutRate",
                                                              width: "100%"}
                                                         ],
                                                         function(obj)
                                                         {
                                                             dojo.connect(obj.grid, "onRowDblClick", obj.grid,
                                                             function(evt){
                                                                    var idx = evt.rowIndex,
                                                                    item = this.getItem(idx);

                                                                    url = "/connection?vhost="
                                                                     + encodeURIComponent(thisObj.vhostData.name) + "&connection=" +
                                                                    encodeURIComponent(obj.dataStore.getValue(item,"name"));

                                                                    window.location = url;

                                                            });
                                                         } );



                             });

         }

         Updater.prototype.updateHeader = function()
         {
            this.name.innerHTML = this.vhostData[ "name" ];
            this.state.innerHTML = this.vhostData[ "state" ];
            this.durable.innerHTML = this.vhostData[ "durable" ];
            this.lifetimePolicy.innerHTML = this.vhostData[ "lifetimePolicy" ];


         }

         Updater.prototype.update = function()
         {

            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                 {
                    thisObj.vhostData = data[0];
                    flattenStatistics( thisObj.vhostData );
                    var connections = thisObj.vhostData[ "connections" ];
                    var queues = thisObj.vhostData[ "queues" ];
                    var exchanges = thisObj.vhostData[ "exchanges" ];

                    thisObj.updateHeader();


                    // update alerting info
                    alertRepeatGap = new formatTime( thisObj.vhostData["alertRepeatGap"] );

                    dom.byId("alertRepeatGap").innerHTML = alertRepeatGap.value;
                    dom.byId("alertRepeatGapUnits").innerHTML = alertRepeatGap.units;


                    alertMsgAge = new formatTime( thisObj.vhostData["alertThresholdMessageAge"] );

                    dom.byId("alertThresholdMessageAge").innerHTML = alertMsgAge.value;
                    dom.byId("alertThresholdMessageAgeUnits").innerHTML = alertMsgAge.units;

                    alertMsgSize = new formatBytes( thisObj.vhostData["alertThresholdMessageSize"] );

                    dom.byId("alertThresholdMessageSize").innerHTML = alertMsgSize.value;
                    dom.byId("alertThresholdMessageSizeUnits").innerHTML = alertMsgSize.units;

                    alertQueueDepth = new formatBytes( thisObj.vhostData["alertThresholdQueueDepthBytes"] );

                    dom.byId("alertThresholdQueueDepthBytes").innerHTML = alertQueueDepth.value;
                    dom.byId("alertThresholdQueueDepthBytesUnits").innerHTML = alertQueueDepth.units;

                    dom.byId("alertThresholdQueueDepthMessages").innerHTML = thisObj.vhostData["alertThresholdQueueDepthMessages"];

                    stats = thisObj.vhostData[ "statistics" ];

                    var sampleTime = new Date();
                    var messageIn = stats["messagesIn"];
                    var bytesIn = stats["bytesIn"];
                    var messageOut = stats["messagesOut"];
                    var bytesOut = stats["bytesOut"];

                    if(thisObj.sampleTime)
                    {
                        var samplePeriod = sampleTime.getTime() - thisObj.sampleTime.getTime();

                        var msgInRate = (1000 * (messageIn - thisObj.messageIn)) / samplePeriod;
                        var msgOutRate = (1000 * (messageOut - thisObj.messageOut)) / samplePeriod;
                        var bytesInRate = (1000 * (bytesIn - thisObj.bytesIn)) / samplePeriod;
                        var bytesOutRate = (1000 * (bytesOut - thisObj.bytesOut)) / samplePeriod;

                        dom.byId("msgInRate").innerHTML = msgInRate.toFixed(0);
                        bytesInFormat = new formatBytes( bytesInRate );
                        dom.byId("bytesInRate").innerHTML = "(" + bytesInFormat.value;
                        dom.byId("bytesInRateUnits").innerHTML = bytesInFormat.units + "/s)"

                        dom.byId("msgOutRate").innerHTML = msgOutRate.toFixed(0);
                        bytesOutFormat = new formatBytes( bytesOutRate );
                        dom.byId("bytesOutRate").innerHTML = "(" + bytesOutFormat.value;
                        dom.byId("bytesOutRateUnits").innerHTML = bytesOutFormat.units + "/s)"

                        if(connections && thisObj.connections)
                        {
                            for(var i=0; i < connections.length; i++)
                            {
                                var connection = connections[i];
                                for(var j = 0; j < thisObj.connections.length; j++)
                                {
                                    var oldConnection = thisObj.connections[j];
                                    if(oldConnection.id == connection.id)
                                    {
                                        var msgOutRate = (1000 * (connection.messagesOut - oldConnection.messagesOut)) /
                                                        samplePeriod;
                                        connection.msgOutRate = msgOutRate.toFixed(0) + "msg/s";

                                        var bytesOutRate = (1000 * (connection.bytesOut - oldConnection.bytesOut)) /
                                                        samplePeriod
                                        var bytesOutRateFormat = new formatBytes( bytesOutRate );
                                        connection.bytesOutRate = bytesOutRateFormat.value + bytesOutRateFormat.units + "/s";


                                        var msgInRate = (1000 * (connection.messagesIn - oldConnection.messagesIn)) /
                                                                                                samplePeriod;
                                        connection.msgInRate = msgInRate.toFixed(0) + "msg/s";

                                        var bytesInRate = (1000 * (connection.bytesIn - oldConnection.bytesIn)) /
                                                        samplePeriod
                                        var bytesInRateFormat = new formatBytes( bytesInRate );
                                        connection.bytesInRate = bytesInRateFormat.value + bytesInRateFormat.units + "/s";
                                    }


                                }

                            }
                        }
                    }

                    thisObj.sampleTime = sampleTime;
                    thisObj.messageIn = messageIn;
                    thisObj.bytesIn = bytesIn;
                    thisObj.messageOut = messageOut;
                    thisObj.bytesOut = bytesOut;
                    thisObj.connections = connections;

                    // update queues
                    thisObj.queuesGrid.update(thisObj.vhostData.queues)

                    // update exchanges
                    thisObj.exchangesGrid.update(thisObj.vhostData.exchanges)

                    // update connections
                    thisObj.connectionsGrid.update(thisObj.vhostData.connections)


                 });
         };

         updater = new Updater();

         updateList.push( updater );

         updater.update();

     });

