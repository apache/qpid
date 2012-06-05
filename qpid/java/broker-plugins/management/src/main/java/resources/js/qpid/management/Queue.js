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
        "dojo/json",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/formatter",
        "qpid/common/UpdatableStore",
        "dojo/store/JsonRest",
        "dojox/grid/EnhancedGrid",
        "dojo/data/ObjectStore",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, json, properties, updater, util, formatter,
                 UpdatableStore, JsonRest, EnhancedGrid, ObjectStore, Pagination) {

           function Queue(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "queue", name: name };
               if(parent) {
                   this.modelObj.parent = {};
                   this.modelObj.parent[ parent.type] = parent;
               }
           }

           Queue.prototype.getTitle = function()
           {
               return "Queue: " + this.name;
           };

           Queue.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showQueue.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.queueUpdater = new QueueUpdater(contentPane.containerNode, that.modelObj, that.controller);

                            updater.add( that.queueUpdater );

                            that.queueUpdater.update();

                            var myStore = new JsonRest({target:"/rest/message/"+ encodeURIComponent(that.modelObj.parent.virtualhost.name) +
                                                                               "/" + encodeURIComponent(that.name)});
                            var messageGridDiv = query(".messages",contentPane.containerNode)[0];
                            that.grid = new EnhancedGrid({
                                store: new ObjectStore({objectStore: myStore}),
                                autoHeight: 10,
                                structure: [
                                    {name:"Size", field:"size", width: "60px"},
                                    {name:"State", field:"state", width: "120px"},

                                    {name:"Arrival", field:"arrivalTime", width: "100%",
                                        formatter: function(val) {
                                            var d = new Date(0);
                                            d.setUTCSeconds(val/1000);

                                            return d.toLocaleString();
                                        } }
                                ],
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
                                }
                            }, messageGridDiv);


                        }});



           };

           Queue.prototype.startup = function() {
               this.grid.startup();
           };

           Queue.prototype.close = function() {
               updater.remove( this.queueUpdater );
           };

           function QueueUpdater(containerNode, queueObj, controller)
           {
               var that = this;

               function findNode(name) {
                   return query("." + name, containerNode)[0];
               }

               function storeNodes(names)
               {
                  for(var i = 0; i < names.length; i++) {
                      that[names[i]] = findNode(names[i]);
                  }
               }

               storeNodes(["name",
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
                           "queueDepthMessages",
                           "queueDepthBytes",
                           "queueDepthBytesUnits",
                           "unacknowledgedMessages",
                           "unacknowledgedBytes",
                           "unacknowledgedBytesUnits",
                           "msgInRate",
                           "bytesInRate",
                           "bytesInRateUnits",
                           "msgOutRate",
                           "bytesOutRate",
                           "bytesOutRateUnits"]);



               this.query = "/rest/queue/"+ encodeURIComponent(queueObj.parent.virtualhost.name) + "/" + encodeURIComponent(queueObj.name);

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                               {
                                   that.queueData = data[0];

                                   util.flattenStatistics( that.queueData );

                                   that.updateHeader();
                                   that.bindingsGrid = new UpdatableStore(that.queueData.bindings, findNode("bindings"),
                                                            [ { name: "Exchange",    field: "exchange",      width: "90px"},
                                                              { name: "Binding Key", field: "name",          width: "120px"},
                                                              { name: "Arguments",   field: "argumentString",     width: "100%"}
                                                            ]);

                                   that.consumersGrid = new UpdatableStore(that.queueData.consumers, findNode("consumers"),
                                                            [ { name: "Name",    field: "name",      width: "70px"},
                                                              { name: "Mode", field: "distributionMode", width: "70px"},
                                                              { name: "Msgs Rate", field: "msgRate",
                                                              width: "150px"},
                                                              { name: "Bytes Rate", field: "bytesRate",
                                                                 width: "100%"}
                                                            ]);




                               });

           }

           QueueUpdater.prototype.updateHeader = function()
           {

               var bytesDepth;
               this.name.innerHTML = this.queueData[ "name" ];
               this.state.innerHTML = this.queueData[ "state" ];
               this.durable.innerHTML = this.queueData[ "durable" ];
               this.lifetimePolicy.innerHTML = this.queueData[ "lifetimePolicy" ];

               this.queueDepthMessages.innerHTML = this.queueData["queueDepthMessages"];
               bytesDepth = formatter.formatBytes( this.queueData["queueDepthBytes"] );
               this.queueDepthBytes.innerHTML = "(" + bytesDepth.value;
               this.queueDepthBytesUnits.innerHTML = bytesDepth.units + ")";

               this.unacknowledgedMessages.innerHTML = this.queueData["unacknowledgedMessages"];
               bytesDepth = formatter.formatBytes( this.queueData["unacknowledgedBytes"] );
               this.unacknowledgedBytes.innerHTML = "(" + bytesDepth.value;
               this.unacknowledgedBytesUnits.innerHTML = bytesDepth.units + ")"


           };

           QueueUpdater.prototype.update = function()
           {

               var thisObj = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data) {
                       var i,j;
                       thisObj.queueData = data[0];
                       util.flattenStatistics( thisObj.queueData );

                       var bindings = thisObj.queueData[ "bindings" ];
                       var consumers = thisObj.queueData[ "consumers" ];

                       for(i=0; i < bindings.length; i++) {
                           bindings[i].argumentString = json.stringify(bindings[i].arguments);
                       }

                       thisObj.updateHeader();


                       // update alerting info
                       var alertRepeatGap = formatter.formatTime( thisObj.queueData["alertRepeatGap"] );

                       thisObj.alertRepeatGap.innerHTML = alertRepeatGap.value;
                       thisObj.alertRepeatGapUnits.innerHTML = alertRepeatGap.units;


                       var alertMsgAge = formatter.formatTime( thisObj.queueData["alertThresholdMessageAge"] );

                       thisObj.alertThresholdMessageAge.innerHTML = alertMsgAge.value;
                       thisObj.alertThresholdMessageAgeUnits.innerHTML = alertMsgAge.units;

                       var alertMsgSize = formatter.formatBytes( thisObj.queueData["alertThresholdMessageSize"] );

                       thisObj.alertThresholdMessageSize.innerHTML = alertMsgSize.value;
                       thisObj.alertThresholdMessageSizeUnits.innerHTML = alertMsgSize.units;

                       var alertQueueDepth = formatter.formatBytes( thisObj.queueData["alertThresholdQueueDepthBytes"] );

                       thisObj.alertThresholdQueueDepthBytes.innerHTML = alertQueueDepth.value;
                       thisObj.alertThresholdQueueDepthBytesUnits.innerHTML = alertQueueDepth.units;

                       thisObj.alertThresholdQueueDepthMessages.innerHTML = thisObj.queueData["alertThresholdQueueDepthMessages"];

                       var sampleTime = new Date();
                       var messageIn = thisObj.queueData["totalEnqueuedMessages"];
                       var bytesIn = thisObj.queueData["totalEnqueuedBytes"];
                       var messageOut = thisObj.queueData["totalDequeuedMessages"];
                       var bytesOut = thisObj.queueData["totalDequeuedBytes"];

                       if(thisObj.sampleTime) {
                           var samplePeriod = sampleTime.getTime() - thisObj.sampleTime.getTime();

                           var msgInRate = (1000 * (messageIn - thisObj.messageIn)) / samplePeriod;
                           var msgOutRate = (1000 * (messageOut - thisObj.messageOut)) / samplePeriod;
                           var bytesInRate = (1000 * (bytesIn - thisObj.bytesIn)) / samplePeriod;
                           var bytesOutRate = (1000 * (bytesOut - thisObj.bytesOut)) / samplePeriod;

                           thisObj.msgInRate.innerHTML = msgInRate.toFixed(0);
                           var bytesInFormat = formatter.formatBytes( bytesInRate );
                           thisObj.bytesInRate.innerHTML = "(" + bytesInFormat.value;
                           thisObj.bytesInRateUnits.innerHTML = bytesInFormat.units + "/s)";

                           thisObj.msgOutRate.innerHTML = msgOutRate.toFixed(0);
                           var bytesOutFormat = formatter.formatBytes( bytesOutRate );
                           thisObj.bytesOutRate.innerHTML = "(" + bytesOutFormat.value;
                           thisObj.bytesOutRateUnits.innerHTML = bytesOutFormat.units + "/s)";

                           if(consumers && thisObj.consumers) {
                               for(i=0; i < consumers.length; i++) {
                                   var consumer = consumers[i];
                                   for(j = 0; j < thisObj.consumers.length; j++) {
                                       var oldConsumer = thisObj.consumers[j];
                                       if(oldConsumer.id == consumer.id) {
                                           var msgRate = (1000 * (consumer.messagesOut - oldConsumer.messagesOut)) /
                                                           samplePeriod;
                                           consumer.msgRate = msgRate.toFixed(0) + "msg/s";

                                           var bytesRate = (1000 * (consumer.bytesOut - oldConsumer.bytesOut)) /
                                                           samplePeriod;
                                           var bytesRateFormat = formatter.formatBytes( bytesRate );
                                           consumer.bytesRate = bytesRateFormat.value + bytesRateFormat.units + "/s";
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
                      thisObj.consumers = consumers;

                      // update bindings
                      thisObj.bindingsGrid.update(thisObj.queueData.bindings);

                      // update consumers
                      thisObj.consumersGrid.update(thisObj.queueData.consumers)

                   });
           };


           return Queue;
       });