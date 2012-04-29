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
         "dojo/json",
				"dojo/store/Memory",
				"dojo/store/Cache",
				"dojox/grid/DataGrid",
				"dojo/data/ObjectStore",
				"dojo/query",
				"dojo/store/Observable",
                "dojo/_base/xhr",
                "dojo/dom",
				"dojo/domReady!"],
	     function(JsonRest, json, Memory, Cache, DataGrid, ObjectStore, query, Observable, xhr, dom)
	     {


         function ConnectionUpdater()
         {
            this.name = dom.byId("name");
            this.state = dom.byId("state");
            this.durable = dom.byId("durable");
            this.lifetimePolicy = dom.byId("lifetimePolicy");
            this.queueDepthMessages = dom.byId("queueDepthMessages");
            this.queueDepthBytes = dom.byId("queueDepthBytes");
            this.queueDepthBytesUnits = dom.byId("queueDepthBytesUnits");
            this.unacknowledgedMessages = dom.byId("unacknowledgedMessages");
            this.unacknowledgedBytes = dom.byId("unacknowledgedBytes");
            this.unacknowledgedBytesUnits = dom.byId("unacknowledgedBytesUnits");

            urlQuery = dojo.queryToObject(dojo.doc.location.search.substr((dojo.doc.location.search[0] === "?" ? 1 : 0)));
            this.query = "/rest/connection/"+ urlQuery.vhost + "/" + urlQuery.connection;


            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                             {
                                thisObj.connectionData = data[0];

                                flattenStatistics( thisObj.connectionData );

                                thisObj.updateHeader();
                                thisObj.sessionsGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                          thisObj.connectionData.sessions, "sessions",
                                                         [ { name: "Name",    field: "name",      width: "70px"},
                                                           { name: "Mode", field: "distributionMode", width: "70px"},
                                                           { name: "Msgs Rate", field: "msgRate",
                                                           width: "150px"},
                                                           { name: "Bytes Rate", field: "bytesRate",
                                                              width: "100%"}
                                                         ]);



                             });

         }

         ConnectionUpdater.prototype.updateHeader = function()
         {
            this.name.innerHTML = this.connectionData[ "name" ];
            this.state.innerHTML = this.connectionData[ "state" ];
            this.durable.innerHTML = this.connectionData[ "durable" ];
            this.lifetimePolicy.innerHTML = this.connectionData[ "lifetimePolicy" ];



         }

         ConnectionUpdater.prototype.update = function()
         {

            var thisObj = this;

            xhr.get({url: this.query, handleAs: "json"}).then(function(data)
                 {
                    thisObj.connectionData = data[0];

                    flattenStatistics( thisObj.connectionData );

                    var sessions = thisObj.connectionData[ "sessions" ];

                    thisObj.updateHeader();

                    var sampleTime = new Date();
                    var messageIn = thisObj.connectionData["messagesIn"];
                    var bytesIn = thisObj.connectionData["bytesIn"];
                    var messageOut = thisObj.connectionData["messagesOut"];
                    var bytesOut = thisObj.connectionData["bytesOut"];

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

                        if(sessions && thisObj.sessions)
                        {
                            for(var i=0; i < sessions.length; i++)
                            {
                                var session = sessions[i];
                                for(var j = 0; j < thisObj.sessions.length; j++)
                                {
                                    var oldSession = thisObj.session[j];
                                    if(oldSession.id == session.id)
                                    {
                                        var msgRate = (1000 * (session.messagesOut - oldSession.messagesOut)) /
                                                        samplePeriod;
                                        session.msgRate = msgRate.toFixed(0) + "msg/s";

                                        var bytesRate = (1000 * (session.bytesOut - oldSession.bytesOut)) /
                                                        samplePeriod
                                        var bytesRateFormat = new formatBytes( bytesRate );
                                        session.bytesRate = bytesRateFormat.value + bytesRateFormat.units + "/s";
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
                    thisObj.sessions = sessions;


                    // update sessions
                    thisObj.sessionsGrid.update(thisObj.connectionData.sessions)


                 });
         };

         connectionUpdater = new ConnectionUpdater();

         updateList.push( connectionUpdater );

         connectionUpdater.update();

     });

