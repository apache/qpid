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

         function BrokerUpdater()
         {
            this.name = dom.byId("name");
            this.state = dom.byId("state");
            this.durable = dom.byId("durable");
            this.lifetimePolicy = dom.byId("lifetimePolicy");

            this.query = "/rest/broker";

            var thisObj = this;

            xhr.get({url: this.query, sync: useSyncGet, handleAs: "json"})
                .then(function(data)
                      {
                        thisObj.brokerData = data[0];

                        flattenStatistics( thisObj.brokerData );

                        thisObj.updateHeader();
                        thisObj.vhostsGrid =
                            new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                               thisObj.brokerData.vhosts, "virtualhosts",
                                                         [ { name: "Virtual Host",    field: "name",      width: "100%"}
                                                         ]);

                        thisObj.portsGrid =
                            new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                               thisObj.brokerData.ports, "ports",
                                                         [ { name: "Address",    field: "bindingAddress",      width: "70px"},
                                                           { name: "Port", field: "port", width: "70px"},
                                                           { name: "Transports", field: "transports", width: "150px"},
                                                           { name: "Protocols", field: "protocols", width: "100%"}
                                                         ]);



                             });

             xhr.get({url: "/rest/logrecords", sync: useSyncGet, handleAs: "json"})
                 .then(function(data)
                       {
                            this.logData = data;

                            thisObj.logfileGrid =
                                         new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                            thisObj.logData, "logfile",
                                                                     [   { name: "ID", field: "id", width: "30px"},
                                                                         { name: "Level", field: "level", width: "60px"},
                                                                         { name: "Logger", field: "logger", width: "100px"},
                                                                         { name: "Thread", field: "thread", width: "60px"},
                                                                         { name: "Log Message", field: "message", width: "100%"}

                                                                     ]);
                       });
         }

         BrokerUpdater.prototype.updateHeader = function()
         {
            this.name.innerHTML = this.brokerData[ "name" ];
            this.state.innerHTML = this.brokerData[ "state" ];
            this.durable.innerHTML = this.brokerData[ "durable" ];
            this.lifetimePolicy.innerHTML = this.brokerData[ "lifetimePolicy" ];

         }

         BrokerUpdater.prototype.update = function()
         {

            var thisObj = this;

            xhr.get({url: this.query, sync: useSyncGet, handleAs: "json"}).then(function(data)
                 {
                    thisObj.brokerData = data[0];
                    flattenStatistics( thisObj.brokerData )

                    var virtualhosts = thisObj.brokerData[ "virtualhosts" ];
                    var ports = thisObj.brokerData[ "ports" ];


                    thisObj.updateHeader();


                    // update alerting info
                    var sampleTime = new Date();

                    thisObj.vhostsGrid.update(thisObj.brokerData.virtualhosts);

                    thisObj.portsGrid.update(thisObj.brokerData.ports);


                 });


             xhr.get({url: "/rest/logrecords", sync: useSyncGet, handleAs: "json"})
                 .then(function(data)
                       {
                           this.logData = data;
                           thisObj.logfileGrid.update(this.logData);
                       });
         };

         brokerUpdater = new BrokerUpdater();

         updateList.push( brokerUpdater );

         brokerUpdater.update();

     });
