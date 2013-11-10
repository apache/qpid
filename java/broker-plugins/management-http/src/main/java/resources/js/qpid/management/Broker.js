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
        "dojox/html/entities",
        "qpid/management/addAuthenticationProvider",
        "qpid/management/addVirtualHost",
        "qpid/management/addPort",
        "qpid/management/addKeystore",
        "qpid/management/addGroupProvider",
        "qpid/management/addAccessControlProvider",
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
       function (xhr, parser, query, connect, properties, updater, util, UpdatableStore, EnhancedGrid, registry, entities, addAuthenticationProvider, addVirtualHost, addPort, addKeystore, addGroupProvider, addAccessControlProvider) {

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
                          label: "Name*:",
                          name: "name"})
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
                       name: "statisticsReportingPeriod",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData.statisticsReportingPeriod,
                           placeholder: "Time in ms",
                           label: "Statistics reporting period (ms):",
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
                       name: "queue.alertThresholdQueueDepthMessages",
                       groupName: "Global Queue Defaults",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["queue.alertThresholdQueueDepthMessages"],
                           placeholder: "Number of messages",
                           label: "Depth alert threshold (messages):",
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
                         label: "Depth alert threshold (bytes):",
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
                           label: "Message age alert threshold (ms):",
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
                           label: "Message size alert threshold (bytes):",
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
                           label: "Alert repeat gap (ms):",
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
                           placeholder: "Number of messages",
                           label: "Maximum delivery retries (messages):",
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
                           name: "queue.deadLetterQueueEnabled"
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
                           label: "Flow control threshold (bytes):",
                           name: "queue.flowControlSizeBytes"
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
                           label: "Flow resume threshold (bytes):",
                           name: "queue.flowResumeSizeBytes"
                         });
                       }
               }, {
                       name: "connection.sessionCountLimit",
                       groupName: "Global Connection Defaults",
                       createWidget: function(brokerData)
                       {
                         return new dijit.form.NumberSpinner({
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["connection.sessionCountLimit"],
                           smallDelta: 1,
                           constraints: {min:1,max:65535,places:0, pattern: "#####"},
                           label: "Maximum number of sessions:",
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
                           label: "Heart beat delay (ms):",
                           name: "connection.heartBeatDelay"
                         });
                       }
               }, {
                       name: "virtualhost.housekeepingCheckPeriod",
                       groupName: "Global Virtual Host defaults",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["virtualhost.housekeepingCheckPeriod"],
                           placeholder: "Time in ms",
                           label: "House keeping check period (ms):",
                           name: "virtualhost.housekeepingCheckPeriod"
                         });
                       }
               }, {
                       name: "virtualhost.storeTransactionIdleTimeoutClose",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["virtualhost.storeTransactionIdleTimeoutClose"],
                           placeholder: "Time in ms",
                           label: "Idle store transaction close timeout (ms):",
                           name: "virtualhost.storeTransactionIdleTimeoutClose"
                         });
                       }
               }, {
                       name: "virtualhost.storeTransactionIdleTimeoutWarn",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["virtualhost.storeTransactionIdleTimeoutWarn"],
                           placeholder: "Time in ms",
                           label: "Idle store transaction warn timeout (ms):",
                           name: "virtualhost.storeTransactionIdleTimeoutWarn"
                         });
                       }
               }, {
                       name: "virtualhost.storeTransactionOpenTimeoutClose",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["virtualhost.storeTransactionOpenTimeoutClose"],
                           placeholder: "Time in ms",
                           label: "Open store transaction close timeout (ms):",
                           name: "virtualhost.storeTransactionOpenTimeoutClose"
                         });
                       }
               }, {
                       name: "virtualhost.storeTransactionOpenTimeoutWarn",
                       createWidget: function(brokerData) {
                         return new dijit.form.ValidationTextBox({
                           trim: "true",
                           regexp: "[0-9]+",
                           invalidMessage: "Invalid value",
                           required: false,
                           value: brokerData["virtualhost.storeTransactionOpenTimeoutWarn"],
                           placeholder: "Time in ms",
                           label: "Open store transaction warn timeout (ms):",
                           name: "virtualhost.storeTransactionOpenTimeoutWarn"
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

                            var logViewerButton = query(".logViewer", contentPane.containerNode)[0];
                            registry.byNode(logViewerButton).on("click", function(evt){
                              that.controller.show("logViewer", "");
                            });


                            var addProviderButton = query(".addAuthenticationProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addProviderButton), "onClick", function(evt){ addAuthenticationProvider.show(); });

                            var deleteProviderButton = query(".deleteAuthenticationProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteProviderButton), "onClick",
                                    function(evt){
                                        var warning = "";
                                        var data = that.brokerUpdater.authenticationProvidersGrid.grid.selection.getSelected();
                                        if(data.length && data.length > 0)
                                        {
                                          for(var i = 0; i<data.length; i++)
                                          {
                                              if (data[i].type.indexOf("File") != -1)
                                              {
                                                warning = "NOTE: provider deletion will also remove the password file on disk.\n\n"
                                                break;
                                              }
                                          }
                                        }

                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.authenticationProvidersGrid.grid,
                                                "rest/authenticationprovider",
                                                warning + "Are you sure you want to delete authentication provider");
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
                                                "Deletion of virtual host will delete the message store data.\n\n Are you sure you want to delete virtual host");
                                }
                            );

                            var addPortButton = query(".addPort", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addPortButton), "onClick", function(evt){
                              addPort.show(null, that.brokerUpdater.brokerData.authenticationproviders,
                                  that.brokerUpdater.brokerData.keystores, that.brokerUpdater.brokerData.truststores);
                            });

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

                            var addKeystoreButton = query(".addKeystore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addKeystoreButton), "onClick",
                                function(evt){ addKeystore.showKeystoreDialog() });

                            var deleteKeystore = query(".deleteKeystore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteKeystore), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.keyStoresGrid.grid,
                                                "rest/keystore",
                                                "Are you sure you want to delete key store");
                                }
                            );

                            var addTruststoreButton = query(".addTruststore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addTruststoreButton), "onClick",
                                function(evt){ addKeystore.showTruststoreDialog() });

                            var deleteTruststore = query(".deleteTruststore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteTruststore), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.trustStoresGrid.grid,
                                                "rest/truststore",
                                                "Are you sure you want to delete trust store");
                                }
                            );

                            var addGroupProviderButton = query(".addGroupProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addGroupProviderButton), "onClick",
                                function(evt){addGroupProvider.show();});

                            var deleteGroupProvider = query(".deleteGroupProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteGroupProvider), "onClick",
                                    function(evt){
                                        var warning = "";
                                        var data = that.brokerUpdater.groupProvidersGrid.grid.selection.getSelected();
                                        if(data.length && data.length > 0)
                                        {
                                          for(var i = 0; i<data.length; i++)
                                          {
                                              if (data[i].type.indexOf("File") != -1)
                                              {
                                                warning = "NOTE: provider deletion will also remove the group file on disk.\n\n"
                                                break;
                                              }
                                          }
                                        }

                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.groupProvidersGrid.grid,
                                                "rest/groupprovider",
                                                warning + "Are you sure you want to delete group provider");
                                }
                            );

                            var addAccessControlButton = query(".addAccessControlProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addAccessControlButton), "onClick",
                                function(evt){addAccessControlProvider.show();});

                            var deleteAccessControlProviderButton = query(".deleteAccessControlProvider", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteAccessControlProviderButton), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.accessControlProvidersGrid.grid,
                                                "rest/accesscontrolprovider",
                                                "Are you sure you want to delete access control provider");
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
               this.accessControlProvidersWarn = query(".broker-access-control-providers-warning", node)[0]
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
                                                  { name: "State",    field: "state",      width: "70px"},
                                                    { name: "Connections",    field: "connectionCount",      width: "80px"},
                                                    { name: "Queues",    field: "queueCount",      width: "80px"},
                                                    { name: "Exchanges",    field: "exchangeCount",      width: "100%"}
                                                ], function(obj) {
                                                        connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                        function(evt){
                                                            var idx = evt.rowIndex,
                                                                theItem = this.getItem(idx);
                                                            var name = obj.dataStore.getValue(theItem,"name");
                                                            that.controller.show("virtualhost", name, brokerObj, theItem.id);
                                                        });
                                                }, gridProperties, EnhancedGrid);

                             that.portsGrid =
                                new UpdatableStore(that.brokerData.ports, query(".broker-ports")[0],
                                                [   { name: "Name", field: "name", width: "150px"},
                                                    { name: "State", field: "state", width: "60px"},
                                                    { name: "Auth Provider", field: "authenticationProvider", width: "100px"},
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
                                                            that.controller.show("port", name, brokerObj, theItem.id);
                                                        });
                                                }, gridProperties, EnhancedGrid);

                             gridProperties = {
                                     keepSelection: true,
                                     plugins: {
                                               indirectSelection: true
                                      }};

                             that.authenticationProvidersGrid =
                                 new UpdatableStore(that.brokerData.authenticationproviders, query(".broker-authentication-providers")[0],
                                                 [ { name: "Name",    field: "name",      width: "40%"},
                                                   { name: "State",    field: "state",      width: "20%"},
                                                     { name: "Type", field: "type", width: "20%"},
                                                     { name: "User Management", field: "type", width: "20%",
                                                             formatter: function(val){
                                                                 return "<input type='radio' disabled='disabled' "+(util.isProviderManagingUsers(val)?"checked='checked'": "")+" />";
                                                             }
                                                     }
                                                 ], function(obj) {
                                                         connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                         function(evt){
                                                             var idx = evt.rowIndex,
                                                                 theItem = this.getItem(idx);
                                                             var name = obj.dataStore.getValue(theItem,"name");
                                                             that.controller.show("authenticationprovider", name, brokerObj, theItem.id);
                                                         });
                                                 }, gridProperties, EnhancedGrid);

                             that.keyStoresGrid =
                               new UpdatableStore(that.brokerData.keystores, query(".broker-key-stores")[0],
                                               [ { name: "Name",    field: "name",      width: "20%"},
                                                 { name: "Path", field: "path", width: "40%"},
                                                 { name: "Type", field: "type", width: "5%"},
                                                 { name: "Key Manager Algorithm", field: "keyManagerFactoryAlgorithm", width: "20%"},
                                                 { name: "Alias", field: "certificateAlias", width: "15%"}
                                               ], function(obj) {
                                                       connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                       function(evt){
                                                           var idx = evt.rowIndex,
                                                               theItem = this.getItem(idx);
                                                           var name = obj.dataStore.getValue(theItem,"name");
                                                           that.controller.show("keystore", name, brokerObj, theItem.id);
                                                       });
                                               }, gridProperties, EnhancedGrid);

                             that.trustStoresGrid =
                               new UpdatableStore(that.brokerData.truststores, query(".broker-trust-stores")[0],
                                               [ { name: "Name",    field: "name",      width: "20%"},
                                                 { name: "Path", field: "path", width: "40%"},
                                                 { name: "Type", field: "type", width: "5%"},
                                                 { name: "Trust Manager Algorithm", field: "trustManagerFactoryAlgorithm", width: "25%"},
                                                 { name: "Peers only", field: "peersOnly", width: "10%",
                                                   formatter: function(val){
                                                     return "<input type='radio' disabled='disabled' "+(val ? "checked='checked'": "")+" />";
                                                   }
                                                 }
                                               ], function(obj) {
                                                       connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                       function(evt){
                                                           var idx = evt.rowIndex,
                                                               theItem = this.getItem(idx);
                                                           var name = obj.dataStore.getValue(theItem,"name");
                                                           that.controller.show("truststore", name, brokerObj, theItem.id);
                                                       });
                                               }, gridProperties, EnhancedGrid);
                             that.groupProvidersGrid =
                               new UpdatableStore(that.brokerData.groupproviders, query(".broker-group-providers")[0],
                                               [ { name: "Name",    field: "name",      width: "40%"},
                                                 { name: "State", field: "state", width: "30%"},
                                                 { name: "Type", field: "type", width: "30%"}
                                               ], function(obj) {
                                                       connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                       function(evt){
                                                           var idx = evt.rowIndex,
                                                               theItem = this.getItem(idx);
                                                           var name = obj.dataStore.getValue(theItem,"name");
                                                           that.controller.show("groupprovider", name, brokerObj, theItem.id);
                                                       });
                                               }, gridProperties, EnhancedGrid);
                             var aclData = that.brokerData.accesscontrolproviders ? that.brokerData.accesscontrolproviders :[];
                             that.accessControlProvidersGrid =
                               new UpdatableStore(aclData, query(".broker-access-control-providers")[0],
                                               [ { name: "Name",  field: "name",  width: "40%"},
                                                 { name: "State", field: "state", width: "30%"},
                                                 { name: "Type",  field: "type", width: "30%"}
                                               ], function(obj) {
                                                       connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                       function(evt){
                                                           var idx = evt.rowIndex,
                                                               theItem = this.getItem(idx);
                                                           var name = obj.dataStore.getValue(theItem,"name");
                                                           that.controller.show("accesscontrolprovider", name, brokerObj, theItem.id);
                                                       });
                                               }, gridProperties, EnhancedGrid);
                             that.displayACLWarnMessage(aclData);
                         });
           }

           BrokerUpdater.prototype.updateHeader = function()
           {
               this.showReadOnlyAttributes();
               var brokerData = this.brokerData;
               window.document.title = "Qpid: " + brokerData.name + " Management";
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
                     element.innerHTML = entities.encode(String(brokerData [propertyName]));
                   }
                   else
                   {
                     element.innerHTML = "";
                   }
                 }
               }
           };

           BrokerUpdater.prototype.displayACLWarnMessage = function(aclProviderData)
           {
             var message = "";
             if (aclProviderData.length > 1)
             {
               var aclProviders = {};
               var theSameTypeFound = false;
               for(var d=0; d<aclProviderData.length; d++)
               {
                 var acl = aclProviderData[d];
                 var aclType = acl.type;
                 if (aclProviders[aclType])
                 {
                   aclProviders[aclType].push(acl.name);
                   theSameTypeFound = true;
                 }
                 else
                 {
                   aclProviders[aclType] = [acl.name];
                 }
               }

               if (theSameTypeFound)
               {
                 message = "Only one instance of a given type will be used. Please remove an instance of type(s):";
                 for(var aclType in aclProviders)
                 {
                     if(aclProviders[aclType].length>1)
                     {
                       message +=  " " + aclType;
                     }
                 }
               }
             }
             this.accessControlProvidersWarn.innerHTML = message;
           }

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

                                                                                       if (that.keyStoresGrid)
                                                                                       {
                                                                                         that.keyStoresGrid.update(that.brokerData.keystores);
                                                                                       }
                                                                                       if (that.trustStoresGrid)
                                                                                       {
                                                                                         that.trustStoresGrid.update(that.brokerData.truststores);
                                                                                       }
                                                                                       if (that.groupProvidersGrid)
                                                                                       {
                                                                                         that.groupProvidersGrid.update(that.brokerData.groupproviders);
                                                                                       }
                                                                                       if (that.accessControlProvidersGrid)
                                                                                       {
                                                                                         var data = that.brokerData.accesscontrolproviders ? that.brokerData.accesscontrolproviders :[];
                                                                                         that.accessControlProvidersGrid.update(data);
                                                                                         that.displayACLWarnMessage(data);
                                                                                       }
                                                                                   });
           };

           BrokerUpdater.prototype.showReadOnlyAttributes = function()
           {
               var brokerData = this.brokerData;
               dojo.byId("brokerAttribute.name").innerHTML = entities.encode(String(brokerData.name));
               dojo.byId("brokerAttribute.operatingSystem").innerHTML = entities.encode(String(brokerData.operatingSystem));
               dojo.byId("brokerAttribute.platform").innerHTML = entities.encode(String(brokerData.platform));
               dojo.byId("brokerAttribute.productVersion").innerHTML = entities.encode(String(brokerData.productVersion));
               dojo.byId("brokerAttribute.modelVersion").innerHTML = entities.encode(String(brokerData.modelVersion));
               dojo.byId("brokerAttribute.storeType").innerHTML = entities.encode(String(brokerData.storeType));
               dojo.byId("brokerAttribute.storeVersion").innerHTML = entities.encode(String(brokerData.storeVersion));
               dojo.byId("brokerAttribute.storePath").innerHTML = entities.encode(String(brokerData.storePath));
           }

           return Broker;
       });
