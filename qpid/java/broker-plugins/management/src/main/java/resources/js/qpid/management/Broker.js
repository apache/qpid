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
        "dojo/parser",
        "dojo/query",
        "dojo/_base/connect",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/UpdatableStore",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, properties, updater, util, UpdatableStore) {

           function Broker(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "broker", name: name };
               if(parent) {
                    this.modelObj.parent = {};
                    this.modelObj.parent[ parent.type] = parent;
                }
           }

           Broker.prototype.getTitle = function()
           {
               return "Broker";
           };

           Broker.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showBroker.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.brokerUpdater = new BrokerUpdater(contentPane.containerNode, that.modelObj, that.controller);

                            updater.add( that.brokerUpdater );

                            that.brokerUpdater.update();

                        }});
           };

           Broker.prototype.close = function() {
               updater.remove( this.brokerUpdater );
           };

           function BrokerUpdater(node, brokerObj, controller)
           {
               this.controller = controller;
               this.name = query(".broker-name", node)[0];
               /*this.state = dom.byId("state");
               this.durable = dom.byId("durable");
               this.lifetimePolicy = dom.byId("lifetimePolicy");
               */
               this.query = "/rest/broker";

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.brokerData = data[0];

                             util.flattenStatistics( that.brokerData );

                             that.updateHeader();
                             that.vhostsGrid =
                                new UpdatableStore(that.brokerData.vhosts, query(".broker-virtualhosts")[0],
                                                [ { name: "Virtual Host",    field: "name",      width: "100%"}
                                                ], function(obj) {
                                                        connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                        function(evt){
                                                            var idx = evt.rowIndex,
                                                                theItem = this.getItem(idx);
                                                            var name = obj.dataStore.getValue(theItem,"name");
                                                            that.controller.show("virtualhost", name, brokerObj);
                                                        });
                                                });

                             that.portsGrid =
                                new UpdatableStore(that.brokerData.ports, query(".broker-ports")[0],
                                                [ { name: "Address",    field: "bindingAddress",      width: "70px"},
                                                    { name: "Port", field: "port", width: "70px"},
                                                    { name: "Transports", field: "transports", width: "150px"},
                                                    { name: "Protocols", field: "protocols", width: "100%"}
                                                ], function(obj) {
                                                        connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                        function(evt){
                                                            var idx = evt.rowIndex,
                                                                theItem = this.getItem(idx);
                                                            var name = obj.dataStore.getValue(theItem,"name");
                                                            that.controller.show("port", name, brokerObj);
                                                        });
                                                });

                         });

  /*             xhr.get({url: "/rest/logrecords", sync: useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             this.logData = data;

                             that.logfileGrid =
                             new UpdatableStore(Observable, Memory, ObjectStore, DataGrid,
                                                that.logData, "logfile",
                                                [   { name: "ID", field: "id", width: "30px"},
                                                    { name: "Level", field: "level", width: "60px"},
                                                    { name: "Logger", field: "logger", width: "100px"},
                                                    { name: "Thread", field: "thread", width: "60px"},
                                                    { name: "Log Message", field: "message", width: "100%"}

                                                ]);
                         });
  */         }

           BrokerUpdater.prototype.updateHeader = function()
           {
               this.name.innerHTML = this.brokerData[ "name" ];
    /*           this.state.innerHTML = this.brokerData[ "state" ];
               this.durable.innerHTML = this.brokerData[ "durable" ];
               this.lifetimePolicy.innerHTML = this.brokerData[ "lifetimePolicy" ];
*/
           };

           BrokerUpdater.prototype.update = function()
           {

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                                                                                   {
                                                                                       that.brokerData = data[0];
                                                                                       util.flattenStatistics( that.brokerData );

                                                                                       var virtualhosts = that.brokerData[ "virtualhosts" ];
                                                                                       var ports = that.brokerData[ "ports" ];


                                                                                       that.updateHeader();


                                                                                       that.vhostsGrid.update(that.brokerData.virtualhosts);

                                                                                       that.portsGrid.update(that.brokerData.ports);


                                                                                   });


      /*         xhr.get({url: "/rest/logrecords", sync: useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             this.logData = data;
                             that.logfileGrid.update(this.logData);
                         });
      */
           };



           return Broker;
       });