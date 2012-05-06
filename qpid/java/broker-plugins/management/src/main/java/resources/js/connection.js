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

            var urlQuery = dojo.queryToObject(dojo.doc.location.search.substr((dojo.doc.location.search[0] === "?" ? 1 : 0)));
            this.query = "/rest/connection/"+ urlQuery.vhost + "/" + urlQuery.connection;


            var that = this;

            xhr.get({url: this.query, sync: useSyncGet, handleAs: "json"}).then(function(data)
                             {
                                that.connectionData = data[0];

                                flattenStatistics( that.connectionData );

                                that.updateHeader();
                                that.sessionsGrid = new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                                          that.connectionData.sessions, "sessions",
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

            var that = this;

            xhr.get({url: this.query, sync: useSyncGet, handleAs: "json"}).then(function(data)
                 {
                    that.connectionData = data[0];

                    flattenStatistics( that.connectionData );

                    var sessions = that.connectionData[ "sessions" ];

                    that.updateHeader();

                    var sampleTime = new Date();
                    var messageIn = that.connectionData["messagesIn"];
                    var bytesIn = that.connectionData["bytesIn"];
                    var messageOut = that.connectionData["messagesOut"];
                    var bytesOut = that.connectionData["bytesOut"];

                    if(that.sampleTime)
                    {
                        var samplePeriod = sampleTime.getTime() - that.sampleTime.getTime();

                        var msgInRate = (1000 * (messageIn - that.messageIn)) / samplePeriod;
                        var msgOutRate = (1000 * (messageOut - that.messageOut)) / samplePeriod;
                        var bytesInRate = (1000 * (bytesIn - that.bytesIn)) / samplePeriod;
                        var bytesOutRate = (1000 * (bytesOut - that.bytesOut)) / samplePeriod;

                        dom.byId("msgInRate").innerHTML = msgInRate.toFixed(0);
                        var bytesInFormat = new formatBytes( bytesInRate );
                        dom.byId("bytesInRate").innerHTML = "(" + bytesInFormat.value;
                        dom.byId("bytesInRateUnits").innerHTML = bytesInFormat.units + "/s)"

                        dom.byId("msgOutRate").innerHTML = msgOutRate.toFixed(0);
                        var bytesOutFormat = new formatBytes( bytesOutRate );
                        dom.byId("bytesOutRate").innerHTML = "(" + bytesOutFormat.value;
                        dom.byId("bytesOutRateUnits").innerHTML = bytesOutFormat.units + "/s)"

                        if(sessions && that.sessions)
                        {
                            for(var i=0; i < sessions.length; i++)
                            {
                                var session = sessions[i];
                                for(var j = 0; j < that.sessions.length; j++)
                                {
                                    var oldSession = that.session[j];
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

                    that.sampleTime = sampleTime;
                    that.messageIn = messageIn;
                    that.bytesIn = bytesIn;
                    that.messageOut = messageOut;
                    that.bytesOut = bytesOut;
                    that.sessions = sessions;


                    // update sessions
                    that.sessionsGrid.update(that.connectionData.sessions)


                 });
         };

         var connectionUpdater = new ConnectionUpdater();

         updateList.push( connectionUpdater );

         connectionUpdater.update();

     });

