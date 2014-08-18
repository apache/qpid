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
        "dijit/registry",
        "dojo/_base/connect",
        "dojo/_base/event",
        "dojo/json",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/formatter",
        "qpid/common/UpdatableStore",
        "qpid/management/addBinding",
        "qpid/management/moveCopyMessages",
        "qpid/management/showMessage",
        "qpid/management/UserPreferences",
        "dojo/store/JsonRest",
        "dojox/grid/EnhancedGrid",
        "dojo/data/ObjectStore",
        "dojox/html/entities",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojo/domReady!"],
       function (xhr, parser, query, registry, connect, event, json, properties, updater, util, formatter,
                 UpdatableStore, addBinding, moveMessages, showMessage, UserPreferences, JsonRest, EnhancedGrid, ObjectStore, entities) {

           function Queue(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "queue", name: name, parent: parent };
           }

           Queue.prototype.getQueueName = function()
           {
               return this.name;
           };


           Queue.prototype.getVirtualHostName = function()
           {
               return this.modelObj.parent.name;
           };

           Queue.prototype.getVirtualHostNodeName = function()
           {
               return this.modelObj.parent.parent.name;
           };

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

                            that.queueUpdater = new QueueUpdater(contentPane.containerNode, that, that.controller);

                            updater.add( that.queueUpdater );

                            that.queueUpdater.update();

                            var myStore = new JsonRest({target:"service/message/"+ encodeURIComponent(that.getVirtualHostName()) +
                                                                               "/" + encodeURIComponent(that.getQueueName())});
                            var messageGridDiv = query(".messages",contentPane.containerNode)[0];
                            that.dataStore = new ObjectStore({objectStore: myStore});
                            that.grid = new EnhancedGrid({
                                store: that.dataStore,
                                autoHeight: 10,
                                keepSelection: true,
                                structure: [
                                    {name:"Size", field:"size", width: "40%"},
                                    {name:"State", field:"state", width: "30%"},

                                    {name:"Arrival", field:"arrivalTime", width: "30%",
                                        formatter: function(val) {
                                            return UserPreferences.formatDateTime(val, {addOffset: true, appendTimeZone: true});
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

                            connect.connect(that.grid, "onRowDblClick", that.grid,
                                             function(evt){
                                                 var idx = evt.rowIndex,
                                                     theItem = this.getItem(idx);
                                                 var id = that.dataStore.getValue(theItem,"id");
                                                 showMessage.show({ messageNumber: id,
                                                                    queue: that.getQueueName(),
                                                                    virtualhost: that.getVirtualHostName(),
                                                                    virtualhostnode: that.getVirtualHostNodeName()});
                                             });

                            var deleteMessagesButton = query(".deleteMessagesButton", contentPane.containerNode)[0];
                            var deleteWidget = registry.byNode(deleteMessagesButton);
                            connect.connect(deleteWidget, "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                that.deleteMessages();
                                            });
                            var clearQueueButton = query(".clearQueueButton", contentPane.containerNode)[0];
                            var clearQueueWidget = registry.byNode(clearQueueButton);
                            connect.connect(clearQueueWidget, "onClick",
                                function(evt){
                                    event.stop(evt);
                                    that.clearQueue();
                                });
                            var moveMessagesButton = query(".moveMessagesButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(moveMessagesButton), "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                that.moveOrCopyMessages({move: true});
                                            });


                            var copyMessagesButton = query(".copyMessagesButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(copyMessagesButton), "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                that.moveOrCopyMessages({move: false});
                                            });

                            var addBindingButton = query(".addBindingButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addBindingButton), "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                addBinding.show({ virtualhost: that.getVirtualHostName(),
                                                                  queue: that.getQueueName(),
                                                                  virtualhostnode: that.getVirtualHostNodeName()});
                                            });

                            var deleteQueueButton = query(".deleteQueueButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteQueueButton), "onClick",
                                    function(evt){
                                        event.stop(evt);
                                        that.deleteQueue();
                                    });
                            UserPreferences.addListener(that);
                        }});



           };

           Queue.prototype.deleteMessages = function() {
               var data = this.grid.selection.getSelected();
               if(data.length) {
                   var that = this;
                   if(confirm("Delete " + data.length + " messages?")) {
                       var i, queryParam;
                       for(i = 0; i<data.length; i++) {
                           if(queryParam) {
                               queryParam += "&";
                           } else {
                               queryParam = "?";
                           }

                           queryParam += "id=" + data[i].id;
                       }
                       var query = "service/message/"+ encodeURIComponent(that.getVirtualHostName())
                           + "/" + encodeURIComponent(that.getQueueName()) + queryParam;
                       that.success = true
                       xhr.del({url: query, sync: true, handleAs: "json"}).then(
                           function(data) {
                               that.grid.setQuery({id: "*"});
                               that.grid.selection.deselectAll();
                               that.queueUpdater.update();
                           },
                           function(error) {that.success = false; that.failureReason = error;});
                        if(!that.success ) {
                            alert("Error:" + this.failureReason);
                        }
                   }
               }
           };
           Queue.prototype.clearQueue = function() {
               var that = this;
               if(confirm("Clear all messages from queue?")) {
                   var query = "service/message/"+ encodeURIComponent(that.getVirtualHostName())
                       + "/" + encodeURIComponent(that.getQueueName()) + "?clear=true";
                   that.success = true
                   xhr.del({url: query, sync: true, handleAs: "json"}).then(
                       function(data) {
                           that.grid.setQuery({id: "*"});
                           that.grid.selection.deselectAll();
                           that.queueUpdater.update();
                       },
                       function(error) {that.success = false; that.failureReason = error;});
                   if(!that.success ) {
                       alert("Error:" + this.failureReason);
                   }
               }
           };
           Queue.prototype.moveOrCopyMessages = function(obj) {
               var that = this;
               var move = obj.move;
               var data = this.grid.selection.getSelected();
               if(data.length) {
                   var that = this;
                   var i, putData = { messages:[] };
                   if(move) {
                       putData.move = true;
                   }
                   for(i = 0; i<data.length; i++) {
                       putData.messages.push(data[i].id);
                   }
                   moveMessages.show({ virtualhost: this.getVirtualHostName(),
                                       queue: this.getQueueName(),
                                       data: putData}, function() {
                                         if(move)
                                         {
                                            that.grid.setQuery({id: "*"});
                                            that.grid.selection.deselectAll();
                                         }
                                     });

               }



           };

           Queue.prototype.startup = function() {
               this.grid.startup();
           };

           Queue.prototype.close = function() {
               updater.remove( this.queueUpdater );
               UserPreferences.removeListener(this);
           };

           Queue.prototype.onPreferencesChange = function(data)
           {
             this.grid._refresh();
           };

           var queueTypeKeys = {
                   priority: "priorities",
                   lvq: "lvqKey",
                   sorted: "sortKey"
               };

           var queueTypeKeyNames = {
                   priority: "Number of priorities",
                   lvq: "LVQ key",
                   sorted: "Sort key"
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
                           "messageDurability",
                           "maximumMessageTtl",
                           "minimumMessageTtl",
                           "exclusive",
                           "owner",
                           "lifetimePolicy",
                           "type",
                           "typeQualifier",
                           "alertRepeatGap",
                           "alertRepeatGapUnits",
                           "alertThresholdMessageAge",
                           "alertThresholdMessageAgeUnits",
                           "alertThresholdMessageSize",
                           "alertThresholdMessageSizeUnits",
                           "alertThresholdQueueDepthBytes",
                           "alertThresholdQueueDepthBytesUnits",
                           "alertThresholdQueueDepthMessages",
                           "alternateExchange",
                           "messageGroups",
                           "messageGroupKey",
                           "messageGroupSharedGroups",
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
                           "bytesOutRateUnits",
                           "queueFlowResumeSizeBytes",
                           "queueFlowControlSizeBytes",
                           "maximumDeliveryAttempts",
                           "oldestMessageAge"]);



               this.query = "api/latest/queue/" + encodeURIComponent(queueObj.getVirtualHostNodeName()) + "/"  + encodeURIComponent(queueObj.getVirtualHostName()) + "/" + encodeURIComponent(queueObj.getQueueName());

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                               {
                                   that.queueData = data[0];

                                   util.flattenStatistics( that.queueData );

                                   that.updateHeader();
                                   that.bindingsGrid = new UpdatableStore(that.queueData.bindings, findNode("bindings"),
                                                            [ { name: "Exchange",    field: "exchange",      width: "40%"},
                                                              { name: "Binding Key", field: "name",          width: "30%"},
                                                              { name: "Arguments",   field: "argumentString",     width: "30%"}
                                                            ]);

                                   that.consumersGrid = new UpdatableStore(that.queueData.consumers, findNode("consumers"),
                                                            [ { name: "Name",    field: "name",      width: "40%"},
                                                              { name: "Mode", field: "distributionMode", width: "20%"},
                                                              { name: "Msgs Rate", field: "msgRate",
                                                              width: "20%"},
                                                              { name: "Bytes Rate", field: "bytesRate",
                                                                 width: "20%"}
                                                            ]);




                               });

           }

           QueueUpdater.prototype.updateHeader = function()
           {

               var bytesDepth;
               this.name.innerHTML = entities.encode(String(this.queueData[ "name" ]));
               this.state.innerHTML = entities.encode(String(this.queueData[ "state" ]));
               this.durable.innerHTML = entities.encode(String(this.queueData[ "durable" ]));
               this.exclusive.innerHTML = entities.encode(String(this.queueData[ "exclusive" ]));
               this.owner.innerHTML = this.queueData[ "owner" ] ? entities.encode(String(this.queueData[ "owner" ])) : "" ;
               this.lifetimePolicy.innerHTML = entities.encode(String(this.queueData[ "lifetimePolicy" ]));
               this.messageDurability.innerHTML = entities.encode(String(this.queueData[ "messageDurability" ]));
               this.minimumMessageTtl.innerHTML = entities.encode(String(this.queueData[ "minimumMessageTtl" ]));
               this.maximumMessageTtl.innerHTML = entities.encode(String(this.queueData[ "maximumMessageTtl" ]));

               this.alternateExchange.innerHTML = this.queueData[ "alternateExchange" ] ? entities.encode(String(this.queueData[ "alternateExchange" ])) : "" ;

               this.queueDepthMessages.innerHTML = entities.encode(String(this.queueData["queueDepthMessages"]));
               bytesDepth = formatter.formatBytes( this.queueData["queueDepthBytes"] );
               this.queueDepthBytes.innerHTML = "(" + bytesDepth.value;
               this.queueDepthBytesUnits.innerHTML = bytesDepth.units + ")";

               this.unacknowledgedMessages.innerHTML = entities.encode(String(this.queueData["unacknowledgedMessages"]));
               bytesDepth = formatter.formatBytes( this.queueData["unacknowledgedBytes"] );
               this.unacknowledgedBytes.innerHTML = "(" + bytesDepth.value;
               this.unacknowledgedBytesUnits.innerHTML = bytesDepth.units + ")";
               this["type" ].innerHTML = entities.encode(this.queueData[ "type" ]);
               if (this.queueData["type"] == "standard")
               {
                   this.typeQualifier.style.display = "none";
               }
               else
               {
                   this.typeQualifier.innerHTML = entities.encode("(" + queueTypeKeyNames[this.queueData[ "type" ]] + ": " + this.queueData[queueTypeKeys[this.queueData[ "type" ]]] + ")");
               }

               if(this.queueData["messageGroupKey"])
               {
                   this.messageGroupKey.innerHTML = entities.encode(String(this.queueData["messageGroupKey"]));
                   this.messageGroupSharedGroups.innerHTML = entities.encode(String(this.queueData["messageGroupSharedGroups"]));
                   this.messageGroups.style.display = "block";
               }
               else
               {
                   this.messageGroups.style.display = "none";
               }

               this.queueFlowControlSizeBytes.innerHTML = entities.encode(String(this.queueData[ "queueFlowControlSizeBytes" ]));
               this.queueFlowResumeSizeBytes.innerHTML = entities.encode(String(this.queueData[ "queueFlowResumeSizeBytes" ]));

               this.oldestMessageAge.innerHTML = entities.encode(String(this.queueData[ "oldestMessageAge" ] / 1000));
               var maximumDeliveryAttempts = this.queueData[ "maximumDeliveryAttempts" ];
               this.maximumDeliveryAttempts.innerHTML = entities.encode(String( maximumDeliveryAttempts == 0 ? "" : maximumDeliveryAttempts));
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

                       if (bindings)
                       {
                         for(i=0; i < bindings.length; i++) {
                           bindings[i].argumentString = json.stringify(bindings[i].arguments);
                         }
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

                       thisObj.alertThresholdQueueDepthMessages.innerHTML = entities.encode(String(thisObj.queueData["alertThresholdQueueDepthMessages"]));

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

           Queue.prototype.deleteQueue = function() {
               if(confirm("Are you sure you want to delete queue '" +this.name+"'?")) {
                   var query = "api/latest/queue/" + encodeURIComponent(this.getVirtualHostNodeName())
                                   + "/" + encodeURIComponent(this.getVirtualHostName()) + "/" + encodeURIComponent(this.name);
                   this.success = true
                   var that = this;
                   xhr.del({url: query, sync: true, handleAs: "json"}).then(
                       function(data) {
                           that.contentPane.onClose()
                           that.controller.tabContainer.removeChild(that.contentPane);
                           that.contentPane.destroyRecursive();
                       },
                       function(error) {that.success = false; that.failureReason = error;});
                   if(!this.success ) {
                       util.xhrErrorHandler(this.failureReason);
                   }
               }
           }

           return Queue;
       });
