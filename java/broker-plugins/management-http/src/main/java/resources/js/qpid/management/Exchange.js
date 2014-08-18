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
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/formatter",
        "qpid/common/UpdatableStore",
        "qpid/management/addBinding",
        "dojox/grid/EnhancedGrid",
        "dojox/html/entities",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, registry, properties, updater, util, formatter, UpdatableStore, addBinding, EnhancedGrid, entities) {

           function Exchange(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "exchange", name: name, parent: parent};
           }


           Exchange.prototype.getExchangeName = function()
           {
               return this.name;
           };


           Exchange.prototype.getVirtualHostName = function()
           {
               return this.modelObj.parent.name;
           };

           Exchange.prototype.getVirtualHostNodeName = function()
           {
               return this.modelObj.parent.parent.name;
           };

           Exchange.prototype.getTitle = function()
           {
               return "Exchange: " + this.name;
           };

           Exchange.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showExchange.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.exchangeUpdater = new ExchangeUpdater(contentPane.containerNode, that.modelObj, that.controller);

                            updater.add( that.exchangeUpdater );

                            that.exchangeUpdater.update();


                            var addBindingButton = query(".addBindingButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addBindingButton), "onClick",
                                            function(evt){
                                                addBinding.show({ virtualhost: that.getVirtualHostName(),
                                                                  virtualhostnode: that.getVirtualHostNodeName(),
                                                                  exchange: that.getExchangeName()});
                                            });

                            var deleteBindingButton = query(".deleteBindingButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteBindingButton), "onClick",
                                            function(evt){
                                                that.deleteBindings();
                                            });

                            var isStandard = util.isReservedExchangeName(that.name);
                            var deleteExchangeButton = query(".deleteExchangeButton", contentPane.containerNode)[0];
                            var node = registry.byNode(deleteExchangeButton);
                            if(isStandard)
                            {
                                node.set('disabled', true);
                            }
                            else
                            {
                                connect.connect(node, "onClick",
                                        function(evt){
                                            that.deleteExchange();
                                        });
                            }

                        }});
           };

           Exchange.prototype.close = function() {
               updater.remove( this.exchangeUpdater );
           };

           Exchange.prototype.deleteBindings = function()
           {
               util.deleteGridSelections(
                       this.exchangeUpdater,
                       this.exchangeUpdater.bindingsGrid.grid,
                       "api/latest/binding/" + encodeURIComponent(this.getVirtualHostNodeName())
                           + "/" + encodeURIComponent(this.getVirtualHostName()) + "/" + encodeURIComponent(this.name),
                       "Are you sure you want to delete binding for queue");
           }

           function ExchangeUpdater(containerNode, exchangeObj, controller)
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
                           "msgInRate",
                           "bytesInRate",
                           "bytesInRateUnits",
                           "msgDropRate",
                           "bytesDropRate",
                           "bytesDropRateUnits"]);



               this.query = "api/latest/exchange/" + encodeURIComponent(exchangeObj.parent.parent.name)
                               + "/" + encodeURIComponent(exchangeObj.parent.name) + "/" + encodeURIComponent(exchangeObj.name);

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                               {
                                  that.exchangeData = data[0];
                                  util.flattenStatistics( that.exchangeData );

                                  that.updateHeader();
                                  that.bindingsGrid = new UpdatableStore(that.exchangeData.bindings, findNode("bindings"),
                                                           [ { name: "Queue",    field: "queue",      width: "40%"},
                                                             { name: "Binding Key", field: "name",          width: "30%"},
                                                             { name: "Arguments",   field: "argumentString",     width: "30%"}
                                                           ], null, {
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

                                                                }}, EnhancedGrid);

                               });

           }

           ExchangeUpdater.prototype.updateHeader = function()
           {
              this.name.innerHTML = entities.encode(String(this.exchangeData[ "name" ]));
              this.state.innerHTML = entities.encode(String(this.exchangeData[ "state" ]));
              this.durable.innerHTML = entities.encode(String(this.exchangeData[ "durable" ]));
              this.lifetimePolicy.innerHTML = entities.encode(String(this.exchangeData[ "lifetimePolicy" ]));

           };

           ExchangeUpdater.prototype.update = function()
           {

              var thisObj = this;

              xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                   {
                      thisObj.exchangeData = data[0];

                      util.flattenStatistics( thisObj.exchangeData );

                      var bindings = thisObj.exchangeData[ "bindings" ];

                      if(bindings)
                      {
                          for(var i=0; i < bindings.length; i++)
                          {
                              if(bindings[i].arguments)
                              {
                                  bindings[i].argumentString = dojo.toJson(bindings[i].arguments);
                              }
                              else
                              {
                                  bindings[i].argumentString = "";
                              }
                          }
                      }


                      var sampleTime = new Date();

                      thisObj.updateHeader();

                      var messageIn = thisObj.exchangeData["messagesIn"];
                      var bytesIn = thisObj.exchangeData["bytesIn"];
                      var messageDrop = thisObj.exchangeData["messagesDropped"];
                      var bytesDrop = thisObj.exchangeData["bytesDropped"];

                      if(thisObj.sampleTime)
                      {
                          var samplePeriod = sampleTime.getTime() - thisObj.sampleTime.getTime();

                          var msgInRate = (1000 * (messageIn - thisObj.messageIn)) / samplePeriod;
                          var msgDropRate = (1000 * (messageDrop - thisObj.messageDrop)) / samplePeriod;
                          var bytesInRate = (1000 * (bytesIn - thisObj.bytesIn)) / samplePeriod;
                          var bytesDropRate = (1000 * (bytesDrop - thisObj.bytesDrop)) / samplePeriod;

                          thisObj.msgInRate.innerHTML = msgInRate.toFixed(0);
                          var bytesInFormat = formatter.formatBytes( bytesInRate );
                          thisObj.bytesInRate.innerHTML = "(" + bytesInFormat.value;
                          thisObj.bytesInRateUnits.innerHTML = bytesInFormat.units + "/s)";

                          thisObj.msgDropRate.innerHTML = msgDropRate.toFixed(0);
                          var bytesDropFormat = formatter.formatBytes( bytesDropRate );
                          thisObj.bytesDropRate.innerHTML = "(" + bytesDropFormat.value;
                          thisObj.bytesDropRateUnits.innerHTML = bytesDropFormat.units + "/s)"

                      }

                      thisObj.sampleTime = sampleTime;
                      thisObj.messageIn = messageIn;
                      thisObj.bytesIn = bytesIn;
                      thisObj.messageDrop = messageDrop;
                      thisObj.bytesDrop = bytesDrop;

                      // update bindings
                      thisObj.bindingsGrid.update(thisObj.exchangeData.bindings)

                   });
           };

           Exchange.prototype.deleteExchange = function() {
               if(confirm("Are you sure you want to delete exchange '" +this.name+"'?")) {
                   var query = "api/latest/exchange/" + encodeURIComponent(this.getVirtualHostNodeName())
                                   + "/" + encodeURIComponent(this.getVirtualHostName()) +  "/" + encodeURIComponent(this.name);
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

           return Exchange;
       });
