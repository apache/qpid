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
        "dijit/registry",
        "dojox/html/entities",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/formatter",
        "qpid/common/UpdatableStore",
        "qpid/management/addQueue",
        "qpid/management/addExchange",
        "dojox/grid/EnhancedGrid",
        "dijit/form/ToggleButton",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, registry, entities, properties, updater, util, formatter, UpdatableStore, addQueue, addExchange, EnhancedGrid) {

           function VirtualHost(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "virtualhost", name: name};
               if(parent) {
                   this.modelObj.parent = {};
                   this.modelObj.parent[ parent.type] = parent;
               }
           }

           VirtualHost.prototype.getTitle = function()
           {
               return "VirtualHost: " + this.name;
           };

           VirtualHost.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showVirtualHost.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.vhostUpdater = new Updater(contentPane.containerNode, that.modelObj, that.controller);

                            updater.add( that.vhostUpdater );

                            var addQueueButton = query(".addQueueButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addQueueButton), "onClick", function(evt){ addQueue.show(that.name) });

                            var deleteQueueButton = query(".deleteQueueButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteQueueButton), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.vhostUpdater,
                                                that.vhostUpdater.queuesGrid.grid,
                                                "rest/queue/"+ encodeURIComponent(that.name),
                                                "Are you sure you want to delete queue");
                                }
                            );

                            var addExchangeButton = query(".addExchangeButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addExchangeButton), "onClick", function(evt){ addExchange.show(that.name) });

                            var deleteExchangeButton = query(".deleteExchangeButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteExchangeButton), "onClick",
                                    function(evt)
                                    {
                                        util.deleteGridSelections(
                                                that.vhostUpdater,
                                                that.vhostUpdater.exchangesGrid.grid,
                                                "rest/exchange/"+ encodeURIComponent(that.name),
                                                "Are you sure you want to delete exchange");
                                    }
                            );
                        }});

               var vhostData = this.vhostUpdater.vhostData;
               var virtualHostDetailsDiv = query(".virtualHostDetails", contentPane.containerNode)[0];
               require(["qpid/management/virtualhost/" + vhostData.type.toLowerCase() + "/show"],
                   function(VirtualHostDetails) {
                     try
                     {
                         that.vhostUpdater.details = new VirtualHostDetails(virtualHostDetailsDiv);
                         that.vhostUpdater.details.update(vhostData);
                     }
                     catch(e)
                     {
                       if (console && console.error)
                       {
                         console.error(e);
                       }
                     }
                 });
           };

           VirtualHost.prototype.close = function() {
               updater.remove( this.vhostUpdater );
           };

           function Updater(node, vhost, controller)
           {

               var that = this;

               function findNode(name) {
                   return query("." + name, node)[0];
               }

               function storeNodes(names)
               {
                   for(var i = 0; i < names.length; i++) {
                       that[names[i]] = findNode(names[i]);
                   }
               }

               storeNodes(["name",
                           "type",
                           "state",
                           "durable",
                           "lifetimePolicy",
                           "alertRepeatGap",
                           "alertRepeatGapUnits",
                           "alertThresholdMessageAge",
                           "alertThresholdMessageAgeUnits",
                           "alertThresholdMessageSize",
                           "alertThresholdMessageSizeUnits",
                           "alertThresholdQueueDepthBytes",
                           "alertThresholdQueueDepthBytesUnits",
                           "alertThresholdQueueDepthMessages",
                           "msgInRate",
                           "bytesInRate",
                           "bytesInRateUnits",
                           "msgOutRate",
                           "bytesOutRate",
                           "bytesOutRateUnits",
                           "configPath"]);

               this.query = "rest/virtualhost/"+ encodeURIComponent(vhost.name);

               var that = this;

               xhr.get({url: this.query, sync: true, handleAs: "json"}).then(function(data) {
                   that.vhostData = data[0];

                   if (!that.vhostData.hasOwnProperty("configPath"))
                   {
                       var node = findNode("configPathDiv");
                       node.style.display = "none";
                   }
                       // flatten statistics into attributes
                       util.flattenStatistics( that.vhostData );

                       var gridProperties = {
                               keepSelection: true,
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

                   that.updateHeader();
                   that.queuesGrid = new UpdatableStore(that.vhostData.queues || [], findNode("queues"),
                                                        [ { name: "Name",    field: "name",      width: "90px"},
                                                            { name: "Messages", field: "queueDepthMessages", width: "90px"},
                                                            { name: "Arguments",   field: "arguments",     width: "100%"}
                                                        ],
                                                        function(obj)
                                                        {
                                                            connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                                         function(evt){
                                                                             var idx = evt.rowIndex,
                                                                                 theItem = this.getItem(idx);
                                                                             var queueName = obj.dataStore.getValue(theItem,"name");
                                                                             controller.show("queue", queueName, vhost, theItem.id);
                                                                         });
                                                        } , gridProperties, EnhancedGrid);

                   that.exchangesGrid = new UpdatableStore(that.vhostData.exchanges || [], findNode("exchanges"),
                                                           [
                                                             { name: "Name",    field: "name", width: "120px"},
                                                             { name: "Type", field: "type", width: "120px"},
                                                             { name: "Binding Count", field: "bindingCount", width: "100%"}
                                                           ],
                                                           function(obj)
                                                           {
                                                               connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                                            function(evt){
                                                                                var idx = evt.rowIndex,
                                                                                theItem = this.getItem(idx);
                                                                                var exchangeName = obj.dataStore.getValue(theItem,"name");
                                                                                controller.show("exchange", exchangeName, vhost, theItem.id);
                                                                            });
                                                           } , gridProperties, EnhancedGrid);


                   that.connectionsGrid = new UpdatableStore(that.vhostData.connections || [],
                                                             findNode("connections"),
                                                             [ { name: "Name",    field: "name",      width: "150px"},
                                                                 { name: "User", field: "principal", width: "120px"},
                                                                 { name: "Port",    field: "port",      width: "70px"},
                                                                 { name: "Transport",    field: "transport",      width: "70px"},
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
                                                                 connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                                              function(evt){
                                                                                  var idx = evt.rowIndex,
                                                                                      theItem = this.getItem(idx);
                                                                                  var connectionName = obj.dataStore.getValue(theItem,"name");
                                                                                  controller.show("connection", connectionName, vhost, theItem.id);
                                                                              });
                                                             } );
               });

           }

           Updater.prototype.updateHeader = function()
           {
               this.name.innerHTML = entities.encode(String(this.vhostData[ "name" ]));
               this.type.innerHTML = entities.encode(String(this.vhostData[ "type" ]));
               this.state.innerHTML = entities.encode(String(this.vhostData[ "state" ]));
               this.durable.innerHTML = entities.encode(String(this.vhostData[ "durable" ]));
               this.lifetimePolicy.innerHTML = entities.encode(String(this.vhostData[ "lifetimePolicy" ]));
               this.configPath.innerHTML = entities.encode(String(this.vhostData[ "configPath" ]));
           };

           Updater.prototype.update = function()
           {
               var thisObj = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data) {
                     try
                     {
                       thisObj.performUpdate(data[0]);
                     }
                     catch(e)
                     {
                       if (console && console.error)
                       {
                         console.error(e);
                       }
                     }
                   });
           };

           Updater.prototype.performUpdate = function(vhostData)
           {
             this.vhostData = vhostData;
             util.flattenStatistics( this.vhostData );
             var connections = this.vhostData[ "connections" ];
             var queues = this.vhostData[ "queues" ];
             var exchanges = this.vhostData[ "exchanges" ];

             this.updateHeader();

             // update alerting info
             var alertRepeatGap = formatter.formatTime( this.vhostData["queue.alertRepeatGap"] );

             this.alertRepeatGap.innerHTML = alertRepeatGap.value;
             this.alertRepeatGapUnits.innerHTML = alertRepeatGap.units;


             var alertMsgAge = formatter.formatTime( this.vhostData["queue.alertThresholdMessageAge"] );

             this.alertThresholdMessageAge.innerHTML = alertMsgAge.value;
             this.alertThresholdMessageAgeUnits.innerHTML = alertMsgAge.units;

             var alertMsgSize = formatter.formatBytes( this.vhostData["queue.alertThresholdMessageSize"] );

             this.alertThresholdMessageSize.innerHTML = alertMsgSize.value;
             this.alertThresholdMessageSizeUnits.innerHTML = alertMsgSize.units;

             var alertQueueDepth = formatter.formatBytes( this.vhostData["queue.alertThresholdQueueDepthBytes"] );

             this.alertThresholdQueueDepthBytes.innerHTML = alertQueueDepth.value;
             this.alertThresholdQueueDepthBytesUnits.innerHTML = alertQueueDepth.units;

             this.alertThresholdQueueDepthMessages.innerHTML = entities.encode(String(this.vhostData["queue.alertThresholdQueueDepthMessages"]));

             var stats = this.vhostData[ "statistics" ] || {};

             var sampleTime = new Date();
             var messageIn = stats["messagesIn"];
             var bytesIn = stats["bytesIn"];
             var messageOut = stats["messagesOut"];
             var bytesOut = stats["bytesOut"];

             if(this.sampleTime)
             {
                 var samplePeriod = sampleTime.getTime() - this.sampleTime.getTime();

                 var msgInRate = (1000 * (messageIn - this.messageIn)) / samplePeriod;
                 var msgOutRate = (1000 * (messageOut - this.messageOut)) / samplePeriod;
                 var bytesInRate = (1000 * (bytesIn - this.bytesIn)) / samplePeriod;
                 var bytesOutRate = (1000 * (bytesOut - this.bytesOut)) / samplePeriod;

                 this.msgInRate.innerHTML = msgInRate.toFixed(0);
                 var bytesInFormat = formatter.formatBytes( bytesInRate );
                 this.bytesInRate.innerHTML = "(" + bytesInFormat.value;
                 this.bytesInRateUnits.innerHTML = bytesInFormat.units + "/s)";

                 this.msgOutRate.innerHTML = msgOutRate.toFixed(0);
                 var bytesOutFormat = formatter.formatBytes( bytesOutRate );
                 this.bytesOutRate.innerHTML = "(" + bytesOutFormat.value;
                 this.bytesOutRateUnits.innerHTML = bytesOutFormat.units + "/s)";

                 if(connections && this.connections)
                 {
                     for(var i=0; i < connections.length; i++)
                     {
                         var connection = connections[i];
                         for(var j = 0; j < this.connections.length; j++)
                         {
                             var oldConnection = this.connections[j];
                             if(oldConnection.id == connection.id)
                             {
                                 msgOutRate = (1000 * (connection.messagesOut - oldConnection.messagesOut)) /
                                              samplePeriod;
                                 connection.msgOutRate = msgOutRate.toFixed(0) + "msg/s";

                                 bytesOutRate = (1000 * (connection.bytesOut - oldConnection.bytesOut)) /
                                                samplePeriod;
                                 var bytesOutRateFormat = formatter.formatBytes( bytesOutRate );
                                 connection.bytesOutRate = bytesOutRateFormat.value + bytesOutRateFormat.units + "/s";


                                 msgInRate = (1000 * (connection.messagesIn - oldConnection.messagesIn)) /
                                             samplePeriod;
                                 connection.msgInRate = msgInRate.toFixed(0) + "msg/s";

                                 bytesInRate = (1000 * (connection.bytesIn - oldConnection.bytesIn)) /
                                               samplePeriod;
                                 var bytesInRateFormat = formatter.formatBytes( bytesInRate );
                                 connection.bytesInRate = bytesInRateFormat.value + bytesInRateFormat.units + "/s";
                             }


                         }

                     }
                 }
             }

             this.sampleTime = sampleTime;
             this.messageIn = messageIn;
             this.bytesIn = bytesIn;
             this.messageOut = messageOut;
             this.bytesOut = bytesOut;
             this.connections = connections;

             // update queues
             if (this.vhostData.queues)
             {
               this.queuesGrid.update(this.vhostData.queues);
             }

             // update exchanges
             if (this.vhostData.exchanges)
             {
               this.exchangesGrid.update(this.vhostData.exchanges);
               var exchangesGrid = this.exchangesGrid.grid;
               for(var i=0; i< this.vhostData.exchanges.length; i++)
               {
                   var data = exchangesGrid.getItem(i);
                   var isStandard = false;
                   if (data && data.name)
                   {
                       isStandard = util.isReservedExchangeName(data.name);
                   }
                   exchangesGrid.rowSelectCell.setDisabled(i, isStandard);
               }
             }

             // update connections
             if (this.vhostData.connections)
             {
               this.connectionsGrid.update(this.vhostData.connections);
             }

             if (this.details)
             {
               this.details.update(this.vhostData);
             }
           }

           return VirtualHost;
       });
