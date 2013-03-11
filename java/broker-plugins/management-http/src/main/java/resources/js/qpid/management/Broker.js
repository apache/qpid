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
        "dojox/grid/EnhancedGrid",
        "dijit/registry",
        "qpid/management/addAuthenticationProvider",
        "qpid/management/addVirtualHost",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dijit/layout/AccordionContainer",
        "dijit/layout/AccordionPane",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, properties, updater, util, UpdatableStore, EnhancedGrid, registry, addAuthenticationProvider, addVirtualHost) {

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

                            var addProviderButton = query(".addAuthenticationProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addProviderButton), "onClick", function(evt){ addAuthenticationProvider.show(); });

                            var deleteProviderButton = query(".deleteAuthenticationProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteProviderButton), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.authenticationProvidersGrid.grid,
                                                "rest/authenticationprovider",
                                                "Are you sure you want to delete authentication provider");
                                }
                            );

                            var addHostButton = query(".addVirtualHost", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addHostButton), "onClick", function(evt){ addVirtualHost.show(); });

                            var deleteHostButton = query(".deleteVirtualHost", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteHostButton), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.vhostsGrid.grid,
                                                "rest/virtualhost",
                                                "Deletion of virtual will delete the message store data.\n\n Are you sure you want to delete virtual host");
                                }
                            );
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
               this.query = "rest/broker";

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.brokerData= data[0];

                             util.flattenStatistics( that.brokerData);

                             that.updateHeader();

                             var gridProperties = {
                                     height: 400,
                                     plugins: {
                                              pagination: {
                                                  pageSizes: ["10", "25", "50", "100"],
                                                  description: true,
                                                  sizeSwitch: true,
                                                  pageStepper: true,
                                                  gotoButton: true,
                                                  maxPageStep: 4,
                                                  position: "bottom"
                                              },
                                              indirectSelection: true
                                     }};

                             that.vhostsGrid =
                                new UpdatableStore(that.brokerData.vhosts, query(".broker-virtualhosts")[0],
                                                [ { name: "Virtual Host",    field: "name",      width: "120px"},
                                                    { name: "Connections",    field: "connectionCount",      width: "80px"},
                                                    { name: "Queues",    field: "queueCount",      width: "80px"},
                                                    { name: "Exchanges",    field: "exchangeCount",      width: "100%"}
                                                ], function(obj) {
                                                        connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                        function(evt){
                                                            var idx = evt.rowIndex,
                                                                theItem = this.getItem(idx);
                                                            var name = obj.dataStore.getValue(theItem,"name");
                                                            that.controller.show("virtualhost", name, brokerObj);
                                                        });
                                                }, gridProperties, EnhancedGrid);

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

                             gridProperties = {
                                     keepSelection: true,
                                     plugins: {
                                               indirectSelection: true
                                      }};

                             that.authenticationProvidersGrid =
                                 new UpdatableStore(that.brokerData.authenticationproviders, query(".broker-authentication-providers")[0],
                                                 [ { name: "Name",    field: "name",      width: "100%"},
                                                     { name: "Type", field: "type", width: "300px"},
                                                     { name: "User Management", field: "type", width: "200px",
                                                             formatter: function(val){
                                                                 return "<input type='radio' disabled='disabled' "+(util.isProviderManagingUsers(val)?"checked='checked'": "")+" />";
                                                             }
                                                     },
                                                     { name: "Default", field: "name", width: "100px",
                                                         formatter: function(val){
                                                             return "<input type='radio' disabled='disabled' "+(val == that.brokerData.defaultAuthenticationProvider ? "checked='checked'": "")+" />";
                                                         }
                                                 }
                                                 ], function(obj) {
                                                         connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                         function(evt){
                                                             var idx = evt.rowIndex,
                                                                 theItem = this.getItem(idx);
                                                             var name = obj.dataStore.getValue(theItem,"name");
                                                             that.controller.show("authenticationprovider", name, brokerObj);
                                                         });
                                                 }, gridProperties, EnhancedGrid);

                         });

               xhr.get({url: "rest/logrecords", sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.logData = data;

                             var gridProperties = {
                                 height: 400,
                                 plugins: {
                                          pagination: {
                                              pageSizes: ["10", "25", "50", "100"],
                                              description: true,
                                              sizeSwitch: true,
                                              pageStepper: true,
                                              gotoButton: true,
                                              maxPageStep: 4,
                                              position: "bottom"
                                          }
                                 }};


                             that.logfileGrid =
                                new UpdatableStore(that.logData, query(".broker-logfile")[0],
                                                [   { name: "Timestamp", field: "timestamp", width: "200px",
                                                        formatter: function(val) {
                                                        var d = new Date(0);
                                                        d.setUTCSeconds(val/1000);

                                                        return d.toLocaleString();
                                                    }},
                                                    { name: "Level", field: "level", width: "60px"},
                                                    { name: "Logger", field: "logger", width: "280px"},
                                                    { name: "Thread", field: "thread", width: "120px"},
                                                    { name: "Log Message", field: "message", width: "100%"}

                                                ], null, gridProperties, EnhancedGrid);
                         });
           }

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

                                                                                       that.updateHeader();

                                                                                       that.vhostsGrid.update(that.brokerData.virtualhosts);

                                                                                       that.portsGrid.update(that.brokerData.ports);

                                                                                       that.authenticationProvidersGrid.update(that.brokerData.authenticationproviders);
                                                                                   });


               xhr.get({url: "rest/logrecords", sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.logData = data;
                             that.logfileGrid.update(that.logData);
                         });

           };



           return Broker;
       });
