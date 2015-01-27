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
        "dojo/json",
        "dojo/_base/connect",
        "dojo/store/Memory",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/UpdatableStore",
        "dojox/grid/EnhancedGrid",
        "dijit/registry",
        "dojox/html/entities",
        "qpid/management/addAuthenticationProvider",
        "qpid/management/addVirtualHostNodeAndVirtualHost",
        "qpid/management/addPort",
        "qpid/management/addStore",
        "qpid/management/addGroupProvider",
        "qpid/management/addAccessControlProvider",
        "qpid/management/editBroker",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dijit/layout/AccordionContainer",
        "dijit/layout/AccordionPane",
        "dijit/form/FilteringSelect",
        "dijit/form/NumberSpinner",
        "dijit/form/ValidationTextBox",
        "dijit/form/CheckBox",
        "dojo/store/Memory",
        "dijit/form/DropDownButton",
        "dijit/Menu",
        "dijit/MenuItem",
        "dojo/domReady!"],
       function (xhr, parser, query, json, connect, memory, properties, updater, util, UpdatableStore, EnhancedGrid, registry, entities,
        addAuthenticationProvider, addVirtualHostNodeAndVirtualHost, addPort, addStore, addGroupProvider, addAccessControlProvider, editBroker) {

           var brokerAttributeNames = ["name", "operatingSystem", "platform", "productVersion", "modelVersion",
                                        "defaultVirtualHost", "statisticsReportingPeriod", "statisticsReportingResetEnabled",
                                        "connection.sessionCountLimit", "connection.heartBeatDelay"];

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
                                                "api/latest/authenticationprovider",
                                                warning + "Are you sure you want to delete authentication provider");
                                }
                            );

                            var addVHNAndVHButton = query(".addVirtualHostNodeAndVirtualHostButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addVHNAndVHButton), "onClick", function(evt){ addVirtualHostNodeAndVirtualHost.show(); });

                            var addPortButton = query(".addPort", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addPortButton), "onClick", function(evt){
                              addPort.show(null, "AMQP", that.brokerUpdater.brokerData.authenticationproviders,
                                  that.brokerUpdater.brokerData.keystores, that.brokerUpdater.brokerData.truststores);
                            });

                            var deletePort = query(".deletePort", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deletePort), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.portsGrid.grid,
                                                "api/latest/port",
                                                "Are you sure you want to delete port");
                                }
                            );

                            var editButton = query(".editBroker", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(editButton), "onClick",
                                function(evt)
                                {
                                  editBroker.show(that.brokerUpdater.brokerData);
                                }
                            );

                            var addKeystoreButton = query(".addKeystore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addKeystoreButton), "onClick",
                                function(evt)
                                {
                                    addStore.setupTypeStore("KeyStore");
                                    addStore.show();
                                });

                            var deleteKeystore = query(".deleteKeystore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteKeystore), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.keyStoresGrid.grid,
                                                "api/latest/keystore",
                                                "Are you sure you want to delete key store");
                                }
                            );

                            var addTruststoreButton = query(".addTruststore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addTruststoreButton), "onClick",
                                function(evt)
                                {
                                    addStore.setupTypeStore("TrustStore");
                                    addStore.show();
                                });

                            var deleteTruststore = query(".deleteTruststore", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(deleteTruststore), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.brokerUpdater,
                                                that.brokerUpdater.trustStoresGrid.grid,
                                                "api/latest/truststore",
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
                                                "api/latest/groupprovider",
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
                                                "api/latest/accesscontrolprovider",
                                                "Are you sure you want to delete access control provider");
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
               this.query = "api/latest/broker?depth=2";
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
                                     selectionMode: "single",
                                     plugins: {
                                              pagination: {
                                                  pageSizes: [10, 25, 50, 100],
                                                  description: true,
                                                  sizeSwitch: true,
                                                  pageStepper: true,
                                                  gotoButton: true,
                                                  maxPageStep: 4,
                                                  position: "bottom"
                                              }
                                     }};

                             function isActiveVH(item)
                             {
                                return item && item.virtualhosts &&  item.virtualhosts[0].state=="ACTIVE";
                             }

                             that.vhostsGrid =
                                new UpdatableStore(that.brokerData.virtualhostnodes, query(".broker-virtualhosts")[0],
                                                [
                                                  { name: "Node Name", field: "name", width: "10%"},
                                                  { name: "Node State", field: "state", width: "10%"},
                                                  { name: "Node Type", field: "type", width: "10%"},
                                                  { name: "Host Name", field: "_item", width: "10%",
                                                    formatter: function(item){
                                                      return item && item.virtualhosts? item.virtualhosts[0].name: "N/A";
                                                    }
                                                  },
                                                  { name: "Host State", field: "_item", width: "15%",
                                                    formatter: function(item){
                                                      return item && item.virtualhosts? item.virtualhosts[0].state: "N/A";
                                                    }
                                                  },
                                                  { name: "Host Type", field: "_item", width: "15%",
                                                      formatter: function(item){
                                                        return item && item.virtualhosts? item.virtualhosts[0].type: "N/A";
                                                      }
                                                  },
                                                  { name: "Connections", field: "_item", width: "8%",
                                                    formatter: function(item){
                                                        return isActiveVH(item)? item.virtualhosts[0].statistics.connectionCount: "N/A";
                                                    }
                                                  },
                                                  { name: "Queues",    field: "_item", width: "8%",
                                                    formatter: function(item){
                                                        return isActiveVH(item)? item.virtualhosts[0].statistics.queueCount: "N/A";
                                                    }
                                                  },
                                                  { name: "Exchanges", field: "_item", width: "8%",
                                                    formatter: function(item){
                                                        return isActiveVH(item)? item.virtualhosts[0].statistics.exchangeCount: "N/A";
                                                    }
                                                  },
                                                  {
                                                    name: "Default", field: "_item", width: "6%",
                                                    formatter: function(item){
                                                        var val = item && item.virtualhosts? item.virtualhosts[0].name: null;
                                                        return "<input type='radio' disabled='disabled' "+(val == that.brokerData.defaultVirtualHost ? "checked='checked'": "")+" />";
                                                    }
                                                  }
                                                ], function(obj) {
                                                        connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                        function(evt){
                                                            var idx = evt.rowIndex,
                                                                theItem = this.getItem(idx);
                                                            if (theItem.virtualhosts)
                                                            {
                                                                that.showVirtualHost(theItem, brokerObj);
                                                            }
                                                        });
                                                }, gridProperties, EnhancedGrid, true);

                             that.virtualHostNodeMenuButton = registry.byNode(query(".virtualHostNodeMenuButton", node)[0]);
                             that.virtualHostMenuButton = registry.byNode(query(".virtualHostMenuButton", node)[0]);

                             var hostMenuItems = that.virtualHostMenuButton.dropDown.getChildren();
                             var viewVirtualHostItem = hostMenuItems[0];
                             var startVirtualHostItem = hostMenuItems[1];
                             var stopVirtualHostItem = hostMenuItems[2];

                             var nodeMenuItems = that.virtualHostNodeMenuButton.dropDown.getChildren();
                             var viewNodeItem = nodeMenuItems[0];
                             var deleteNodeItem = nodeMenuItems[1];
                             var startNodeItem = nodeMenuItems[2];
                             var stopNodeItem = nodeMenuItems[3];

                             var toggler =  function(index){ that.toggleVirtualHostNodeNodeMenus(index);}
                             connect.connect(that.vhostsGrid.grid.selection, 'onSelected', toggler);
                             connect.connect(that.vhostsGrid.grid.selection, 'onDeselected', toggler);

                             viewVirtualHostItem.on("click", function(){
                               var data = that.vhostsGrid.grid.selection.getSelected();
                               if (data.length == 1)
                               {
                                 that.showVirtualHost(data[0], brokerObj);
                                 that.vhostsGrid.grid.selection.clear();
                               }
                             });

                             viewNodeItem.on("click",
                                     function(evt){
                                       var data = that.vhostsGrid.grid.selection.getSelected();
                                       if (data.length == 1)
                                       {
                                         var item = data[0];
                                         that.controller.show("virtualhostnode", item.name, brokerObj, item.id);
                                         that.vhostsGrid.grid.selection.clear();
                                       }
                                 }
                             );

                             deleteNodeItem.on("click",
                                     function(evt){
                                         util.deleteGridSelections(
                                                 that,
                                                 that.vhostsGrid.grid,
                                                 "api/latest/virtualhostnode",
                                                 "Deletion of virtual host node will delete both configuration and message data.\n\n Are you sure you want to delete virtual host node");
                                 }
                             );

                             startNodeItem.on("click",
                               function(event)
                               {
                                 var data = that.vhostsGrid.grid.selection.getSelected();
                                 if (data.length == 1)
                                 {
                                   var item = data[0];
                                   util.sendRequest("api/latest/virtualhostnode/" + encodeURIComponent(item.name),
                                           "PUT", {desiredState: "ACTIVE"});
                                   that.vhostsGrid.grid.selection.clear();
                                 }
                               });

                             stopNodeItem.on("click",
                               function(event)
                               {
                                 var data = that.vhostsGrid.grid.selection.getSelected();
                                 if (data.length == 1)
                                 {
                                   var item = data[0];
                                   if (confirm("Stopping the node will also shutdown the virtual host. "
                                           + "Are you sure you want to stop virtual host node '"
                                           + entities.encode(String(item.name)) +"'?"))
                                   {
                                       util.sendRequest("api/latest/virtualhostnode/" + encodeURIComponent(item.name),
                                               "PUT", {desiredState: "STOPPED"});
                                       that.vhostsGrid.grid.selection.clear();
                                   }
                                 }
                               });

                             startVirtualHostItem.on("click", function(event)
                               {
                                 var data = that.vhostsGrid.grid.selection.getSelected();
                                 if (data.length == 1 && data[0].virtualhosts)
                                 {
                                   var item = data[0];
                                   var host = item.virtualhosts[0];
                                   util.sendRequest("api/latest/virtualhost/" + encodeURIComponent(item.name) + "/" + encodeURIComponent(host.name),
                                           "PUT", {desiredState: "ACTIVE"});
                                   that.vhostsGrid.grid.selection.clear();
                                 }
                               });

                             stopVirtualHostItem.on("click", function(event)
                               {
                                 var data = that.vhostsGrid.grid.selection.getSelected();
                                 if (data.length == 1 && data[0].virtualhosts)
                                 {
                                   var item = data[0];
                                   var host = item.virtualhosts[0];
                                   if (confirm("Are you sure you want to stop virtual host '"
                                           + entities.encode(String(host.name)) +"'?"))
                                   {
                                       util.sendRequest("api/latest/virtualhost/" + encodeURIComponent(item.name) + "/" + encodeURIComponent(host.name),
                                               "PUT", {desiredState: "STOPPED"});
                                       that.vhostsGrid.grid.selection.clear();
                                   }
                                 }
                               });
                             gridProperties.selectionMode = "extended";
                             gridProperties.plugins.indirectSelection = true;

                             that.portsGrid =
                                new UpdatableStore(that.brokerData.ports, query(".broker-ports")[0],
                                                [   { name: "Name", field: "name", width: "15%"},
                                                    { name: "State", field: "state", width: "15%"},
                                                    { name: "Auth Provider", field: "authenticationProvider", width: "15%"},
                                                    { name: "Address",    field: "bindingAddress",      width: "15%"},
                                                    { name: "Port", field: "port", width: "10%"},
                                                    { name: "Transports", field: "transports", width: "15%"},
                                                    { name: "Protocols", field: "protocols", width: "15%"}
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
                                                 { name: "State",    field: "state",      width: "10%"},
                                                 { name: "Type", field: "type", width: "10%"},
                                                 { name: "Path", field: "path", width: "60%"}
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
                                                 { name: "State",    field: "state",      width: "10%"},
                                                 { name: "Type", field: "type", width: "10%"},
                                                 { name: "Path", field: "path", width: "50%"},
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

           BrokerUpdater.prototype.showVirtualHost=function(item, brokerObj)
           {
             var nodeName = item.name;
             var host = item.virtualhosts? item.virtualhosts[0]: null;
             var nodeObject = { type: "virtualhostnode", name: nodeName, parent: brokerObj};
             this.controller.show("virtualhost", host?host.name:nodeName, nodeObject, host?host.id:null);
           }

           BrokerUpdater.prototype.updateHeader = function()
           {
               var brokerData = this.brokerData;
               window.document.title = "Qpid: " + brokerData.name + " Management";

               for(var i in brokerAttributeNames)
               {
                 var propertyName = brokerAttributeNames[i];
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

                       if (that.vhostsGrid.update(that.brokerData.virtualhostnodes))
                       {
                           that.vhostsGrid.grid._refresh();
                           that.toggleVirtualHostNodeNodeMenus();
                       }

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

           BrokerUpdater.prototype.toggleVirtualHostNodeNodeMenus = function(rowIndex)
           {
             var data = this.vhostsGrid.grid.selection.getSelected();
             var selected = data.length==1;
             this.virtualHostNodeMenuButton.set("disabled", !selected);
             this.virtualHostMenuButton.set("disabled", !selected || !data[0].virtualhosts);
             if (selected)
             {
                 var nodeMenuItems = this.virtualHostNodeMenuButton.dropDown.getChildren();
                 var hostMenuItems = this.virtualHostMenuButton.dropDown.getChildren();

                 var startNodeItem = nodeMenuItems[2];
                 var stopNodeItem = nodeMenuItems[3];

                 var viewVirtualHostItem = hostMenuItems[0];
                 var startVirtualHostItem = hostMenuItems[1];
                 var stopVirtualHostItem = hostMenuItems[2];

                 var node = data[0];
                 startNodeItem.set("disabled", node.state != "STOPPED");
                 stopNodeItem.set("disabled", node.state != "ACTIVE");

                 if (node.virtualhosts)
                 {
                     viewVirtualHostItem.set("disabled", false);

                     var host = node.virtualhosts[0];
                     startVirtualHostItem.set("disabled", host.state != "STOPPED");
                     stopVirtualHostItem.set("disabled", host.state != "ACTIVE");
                 }
                 else
                 {
                     viewVirtualHostItem.set("disabled", true);
                     startVirtualHostItem.set("disabled", true);
                     stopVirtualHostItem.set("disabled", true);
                 }
             }
           };
           return Broker;
       });
