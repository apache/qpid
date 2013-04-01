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
        "qpid/management/addPort",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dijit/layout/AccordionContainer",
        "dijit/layout/AccordionPane",
        "dijit/form/FilteringSelect",
        "dijit/form/NumberSpinner",
        "dijit/form/ValidationTextBox",
        "dijit/form/CheckBox",
        "dojo/store/Memory",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, properties, updater, util, UpdatableStore, EnhancedGrid, registry, addAuthenticationProvider, addVirtualHost, addPort) {

           function Broker(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "broker", name: name };
               if(parent) {
                    this.modelObj.parent = {};
                    this.modelObj.parent[ parent.type] = parent;
               }
               this.attributeWidgetFactories = [{
                     name: "name",
                     createWidget: function(brokerData) {
                        return new dijit.form.ValidationTextBox({
                          required: true,
                          value: brokerData.name,
                          disabled: true,
                          label: "Name*:",
                          name: "name"})
                    }
               }, {
                      name: "defaultAuthenticationProvider",
                      createWidget: function(brokerData) {
                        var providers = brokerData.authenticationproviders;
                        var data = [];
                        if (providers) {
                           for (var i=0; i< providers.length; i++) {
                               data.push({id: providers[i].name, name: providers[i].name});
                           }
                        }
                        var providersStore = new dojo.store.Memory({ data: data });
                        return new dijit.form.FilteringSelect({
                           required: true,
                           store: providersStore,
                           value: brokerData.defaultAuthenticationProvider,
                           label: "Default Authentication Provider*:",
                           name: "defaultAuthenticationProvider"})
                      }
               }, {
                       name: "defaultVirtualHost",
                       createWidget: function(brokerData) {
                         var hosts = brokerData.virtualhosts;
                         var data = [];
                         if (hosts) {
                           for (var i=0; i< hosts.length; i++) {
                               data.push({id: hosts[i].name, name: hosts[i].name});
                           }
                         }
                         var hostsStore = new dojo.store.Memory({ data: data });
                         return new dijit.form.FilteringSelect({
                           required: true, store: hostsStore,
                           value: brokerData.defaultVirtualHost,
                           label: "Default Virtual Host*:",
                           name: "defaultVirtualHost"})
                       }
               }, {
                       name: "aclFile",
                       createWidget: function(brokerData) {
                          return new dijit.form.ValidationTextBox({
                            required: false,
                            value: brokerData.aclFile,
                            label: "ACL file location:",
                            name: "aclFile"})
                       }
               }, {
                       name: "groupFile",
                       createWidget: function(brokerData)
                       {
                           return new dijit.form.ValidationTextBox({
                             required: false,
                             value: brokerData.groupFile,
                             label: "Group file location:",
                             name: "groupFile"});
                       }
               }, {
                       name: "keyStorePath",
                       createWidget: function(brokerData) {
                           return new dijit.form.ValidationTextBox({
                             required: false,
                             value: brokerData.keyStorePath,
                             label: "Path to keystore:",
                             name: "keyStorePath"});
                       }
               }, {
                       name: "keyStoreCertAlias",
                       createWidget: function(brokerData) {
                           return new dijit.form.ValidationTextBox({
                             required: false,
                             value: brokerData.keyStoreCertAlias,
                             label: "Keystore certificate alias:",
                             name: "keyStoreCertAlias"});
                       }
               }, {
                       name: "keyStorePassword",
                       requiredFor: "keyStorePath",
                       createWidget: function(brokerData) {
                           return new dijit.form.ValidationTextBox({
                             required: false,
                             label: "Keystore password:",
                             invalidMessage: "Missed keystore password",
                             name: "keyStorePassword"});
                       }
               }, {
                       name: "trustStorePath",
                       createWidget: function(brokerData)
                       {
                         return new dijit.form.ValidationTextBox({
                             required: false,
                             value: brokerData.trustStorePath,
                             label: "Path to truststore:",
                             name: "trustStorePath"});
                       }
               }, {
                       name: "trustStorePassword",
                       requiredFor: "trustStorePath",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           required: false,
                           label: "Truststore password:",
                           invalidMessage: "Missed trustore password",
                           name: "trustStorePassword"});
                       }
               }, {
                       name: "peerStorePath",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           required: false,
                           value: brokerData.peerStorePath,
                           label: "Path to peerstore:",
                           name: "peerStorePath"});
                       }
               }, {
                       name: "peerStorePassword",
                       requiredFor: "peerStorePath",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           required: false,
                           label: "Peerstore password:",
                           invalidMessage: "Missed peerstore password",
                           name: "peerStorePassword"});
                       }
               }, {
                       name: "queue.alertThresholdQueueDepthMessages",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.alertThresholdQueueDepthMessages"],
                           placeholder: "Count of messages",
                           label: "Queue depth messages alert threshold:",
                           name: "queue.alertThresholdQueueDepthMessages"
                         });
                       }
               }, {
                     name: "queue.alertThresholdQueueDepthBytes",
                     createWidget: function(brokerData) {
                       return new dijit.form.ValidationTextBox({
                         trim: "true",
                         regexp: "[0-9]+",
                         invalidMessage: "Invalid value",
                         required: false,
                         value: brokerData["queue.alertThresholdQueueDepthBytes"],
                         placeholder: "Number of bytes",
                         label: "Queue depth bytes alert threshold:",
                         name: "queue.alertThresholdQueueDepthBytes"
                       });
                     }
               }, {
                       name: "queue.alertThresholdMessageAge",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.alertThresholdMessageAge"],
                           placeholder: "Time in ms",
                           label: "Queue message age alert threshold:",
                           name: "queue.alertThresholdMessageAge"
                         });
                       }
               }, {
                       name: "queue.alertThresholdMessageSize",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.alertThresholdMessageSize"],
                           placeholder: "Size in bytes",
                           label: "Queue message size alert threshold:",
                           name: "queue.alertThresholdMessageSize"
                         });
                       }
               }, {
                       name: "queue.alertRepeatGap",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.alertRepeatGap"],
                           placeholder: "Time in ms",
                           label: "Queue alert repeat gap:",
                           name: "queue.alertRepeatGap"
                         });
                       }
               }, {
                       name: "queue.maximumDeliveryAttempts",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.maximumDeliveryAttempts"],
                           placeholder: "Count of messages",
                           label: "Queue maximum delivery retries:",
                           name: "queue.maximumDeliveryAttempts"
                         });
                       }
               }, {
                       name: "queue.deadLetterQueueEnabled",
                       createWidget: function(brokerData) {
                         return new dijit.form.CheckBox({
                           required: false,
                           checked: brokerData["queue.deadLetterQueueEnabled"],
                           value: "true",
                           label: "Dead letter queue enabled:",
                           name: "queue.deadLetterQueueEnabled",
                         });
                       }
               }, {
                       name: "queue.flowControlSizeBytes",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.flowControlSizeBytes"],
                           placeholder: "Size in bytes",
                           label: "Queue flow capacity:",
                           name: "queue.flowControlSizeBytes",
                         });
                       }
               }, {
                       name: "queue.flowResumeSizeBytes",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.flowResumeSizeBytes"],
                           placeholder: "Size in bytes",
                           label: "Queue flow resume capacity:",
                           name: "queue.flowResumeSizeBytes",
                         });
                       }
               }, {
                       name: "connection.sessionCountLimit",
                       createWidget: function(brokerData)
                       {
                         return new dijit.form.NumberSpinner({
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["connection.sessionCountLimit"],
                           smallDelta: 1,
                           constraints: {min:1,max:65535,places:0, pattern: "#####"},
                           label: "Connection session limit:",
                           name: "connection.sessionCountLimit"
                         });
                       }
               }, {
                       name: "connection.heartBeatDelay",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["connection.heartBeatDelay"],
                           placeholder: "Time in ms",
                           label: "Heart beat delay:",
                           name: "connection.heartBeatDelay"
                         });
                       }
               }, {
                       name: "statisticsReportingPeriod",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData.statisticsReportingPeriod,
                           placeholder: "Time in ms",
                           label: "Statistics reporting period:",
                           name: "statisticsReportingPeriod"
                         });
                       }
               }, {
                       name: "statisticsReportingResetEnabled",
                       createWidget: function(brokerData)
                       {
                         return new dijit.form.CheckBox({
                           required: false, checked: brokerData.statisticsReportingResetEnabled, value: "true",
                           label: "Statistics reporting period enabled:",
                           name: "statisticsReportingResetEnabled"
                         });
                       }
               }, {
                       name: "virtualhost.housekeepingCheckPeriod",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["virtualhost.housekeepingCheckPeriod"],
                           placeholder: "Time in ms",
                           label: "House keeping check period:",
                           name: "virtualhost.housekeepingCheckPeriod"
                         });
                       }
               } ];
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

                            that.brokerUpdater = new BrokerUpdater(contentPane.containerNode, that.modelObj, that.controller, that.attributeWidgetFactories);

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

                            var addPortButton = query(".addPort", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addPortButton), "onClick", function(evt){ addPort.show(null, that.brokerUpdater.brokerData.authenticationproviders); });

                            var deletePort = query(".deletePort", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deletePort), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.portsGrid.grid,
                                                "rest/port",
                                                "Are you sure you want to delete port");
                                }
                            );

                            var editButton = query(".editBroker", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(editButton), "onClick",
                                function(evt){
                                    util.showSetAttributesDialog(
                                            that.attributeWidgetFactories,
                                            that.brokerUpdater.brokerData,
                                            "rest/broker",
                                            "Set broker attributes");
                                }
                            );

                        }});
           };

           Broker.prototype.close = function() {
               updater.remove( this.brokerUpdater );
           };

           function BrokerUpdater(node, brokerObj, controller, attributes)
           {
               this.controller = controller;
               this.query = "rest/broker";
               this.attributes = attributes;
               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.brokerData= data[0];

                             util.flattenStatistics( that.brokerData);

                             that.showReadOnlyAttributes();
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
                                                [   { name: "Name", field: "name", width: "150px"},
                                                    { name: "State", field: "state", width: "60px"},
                                                    { name: "Authentication", field: "authenticationProvider", width: "100px"},
                                                    { name: "Address",    field: "bindingAddress",      width: "70px"},
                                                    { name: "Port", field: "port", width: "50px"},
                                                    { name: "Transports", field: "transports", width: "100px"},
                                                    { name: "Protocols", field: "protocols", width: "100%"}
                                                ], function(obj) {
                                                        connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                        function(evt){
                                                            var idx = evt.rowIndex,
                                                                theItem = this.getItem(idx);
                                                            var name = obj.dataStore.getValue(theItem,"name");
                                                            addPort.show(name, that.brokerData.authenticationproviders);
                                                        });
                                                }, gridProperties, EnhancedGrid);

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
               var brokerData = this.brokerData;
               for(var i in this.attributes)
               {
                 var propertyName = this.attributes[i].name;
                 var element = dojo.byId("brokerAttribute." + propertyName);
                 if (element)
                 {
                   if (brokerData.hasOwnProperty(propertyName))
                   {
                     var container = dojo.byId("brokerAttribute." + propertyName + ".container");
                     if (container)
                     {
                       container.style.display = "block";
                     }
                     element.innerHTML = brokerData [propertyName];
                   }
                   else
                   {
                     element.innerHTML = "";
                   }
                 }
               }
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

           BrokerUpdater.prototype.showReadOnlyAttributes = function()
           {
               var brokerData = this.brokerData;
               dojo.byId("brokerAttribute.name").innerHTML = brokerData.name;
               dojo.byId("brokerAttribute.operatingSystem").innerHTML = brokerData.operatingSystem;
               dojo.byId("brokerAttribute.platform").innerHTML = brokerData.platform;
               dojo.byId("brokerAttribute.productVersion").innerHTML = brokerData.productVersion;
               dojo.byId("brokerAttribute.managementVersion").innerHTML = brokerData.managementVersion;
               dojo.byId("brokerAttribute.storeType").innerHTML = brokerData.storeType;
               dojo.byId("brokerAttribute.storeVersion").innerHTML = brokerData.storeVersion;
               dojo.byId("brokerAttribute.storePath").innerHTML = brokerData.storePath;
           }

           return Broker;
       });
