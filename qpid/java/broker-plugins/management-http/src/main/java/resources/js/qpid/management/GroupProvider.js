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
        "dojo/_base/array",
        "dojo/_base/event",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/metadata",
        "qpid/common/UpdatableStore",
        "dojox/grid/EnhancedGrid",
        "dijit/registry",
        "dojox/html/entities",
        "dojo/text!showGroupProvider.html",
        "qpid/management/addGroupProvider",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, array, event, properties, updater, util, metadata, UpdatableStore,
                 EnhancedGrid, registry, entities, template, addGroupProvider)
       {

           function GroupProvider(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "groupprovider", name: name, parent: parent};
           }

           GroupProvider.prototype.getTitle = function() {
               return "GroupProvider: " + this.name ;
           };

           GroupProvider.prototype.open = function(contentPane)
           {
               var that = this;
               this.contentPane = contentPane;
               contentPane.containerNode.innerHTML = template;
               parser.parse(contentPane.containerNode).then(function(instances) { that.onOpen(); });
           };

           GroupProvider.prototype.onOpen = function()
           {
               var that = this;
               var contentPane = this.contentPane;
               this.groupProviderUpdater = new GroupProviderUpdater(contentPane.containerNode, this.modelObj, this.controller);

               // load data
               this.groupProviderUpdater.update();

               this.deleteButton = registry.byNode(query(".deleteGroupProviderButton", contentPane.containerNode)[0]);
               this.deleteButton.on("click", function(evt){ event.stop(evt); that.deleteGroupProvider(); });

               this.editButton = registry.byNode(query(".editGroupProviderButton", contentPane.containerNode)[0]);
               this.editButton.on("click", function(evt){ event.stop(evt); that.editGroupProvider(); });

               var type = this.groupProviderUpdater.groupProviderData.type;
               var providerDetailsNode = query(".providerDetails", contentPane.containerNode)[0];

               require(["qpid/management/groupprovider/"+ encodeURIComponent(type.toLowerCase()) + "/show"],
                        function(DetailsUI)
                        {
                            that.groupProviderUpdater.details = new DetailsUI({containerNode: providerDetailsNode, parent: that});
                            that.groupProviderUpdater.details.update(that.groupProviderUpdater.groupProviderData);
                        });

               var managedInterfaces = metadata.getMetaData("GroupProvider", type).managedInterfaces;
               if (managedInterfaces)
               {

                    var managedInterfaceUI = this.groupProviderUpdater.managedInterfaces;

                    array.forEach(managedInterfaces,
                                  function(managedInterface)
                                  {
                                      require(["qpid/management/groupprovider/" + encodeURIComponent(managedInterface)],
                                              function(ManagedInterface)
                                              {
                                                  managedInterfaceUI[ManagedInterface] = new ManagedInterface(providerDetailsNode, that.modelObj, that.controller);
                                                  managedInterfaceUI[ManagedInterface].update(that.groupProviderUpdater.groupProviderData);
                                              });
                                  });
               }

               updater.add( this.groupProviderUpdater );
           };


           GroupProvider.prototype.close = function() {
               updater.remove( this.groupProviderUpdater );
           };

           GroupProvider.prototype.deleteGroupProvider = function() {
             var warnMessage = "";
             if (this.groupProviderUpdater.groupProviderData && this.groupProviderUpdater.groupProviderData.type.indexOf("File") != -1)
             {
               warnMessage = "NOTE: provider deletion will also remove the group file on disk.\n\n";
             }
             if(confirm(warnMessage + "Are you sure you want to delete group provider '" + this.name + "'?")) {
                 var query = "api/latest/groupprovider/" +encodeURIComponent(this.name);
                 this.success = true
                 var that = this;
                 xhr.del({url: query, sync: true, handleAs: "json"}).then(
                     function(data) {
                         that.close();
                         that.contentPane.onClose()
                         that.controller.tabContainer.removeChild(that.contentPane);
                         that.contentPane.destroyRecursive();
                     },
                     function(error) {that.success = false; that.failureReason = error;});
                 if(!this.success ) {
                     util.xhrErrorHandler(this.failureReason);
                 }
             }
           };

           GroupProvider.prototype.editGroupProvider = function()
           {
                xhr.get(
                        {
                          url: this.groupProviderUpdater.query,
                          sync: true,
                          content: { actuals: true },
                          handleAs: "json",
                          load: function(actualData)
                          {
                            addGroupProvider.show(actualData[0]);
                          }
                        }
                );
           }

           function GroupProviderUpdater(node, groupProviderObj, controller)
           {
               this.controller = controller;
               this.name = query(".name", node)[0];
               this.type = query(".type", node)[0];
               this.state = query(".state", node)[0];
               this.query = "api/latest/groupprovider/"+encodeURIComponent(groupProviderObj.name);
               this.managedInterfaces = {};
               this.details = null;
           }

           GroupProviderUpdater.prototype.updateHeader = function()
           {
               this.name.innerHTML = entities.encode(String(this.groupProviderData[ "name" ]));
               this.type.innerHTML = entities.encode(String(this.groupProviderData[ "type" ]));
               this.state.innerHTML = entities.encode(String(this.groupProviderData[ "state" ]));
           };

           GroupProviderUpdater.prototype.update = function()
           {
               var that = this;
               xhr.get({url: this.query, sync: true, handleAs: "json"}).then(function(data) {that._update(data[0]);});
           };

           GroupProviderUpdater.prototype._update = function(data)
           {
               this.groupProviderData = data;
               util.flattenStatistics( this.groupProviderData );
               this.updateHeader();

               if (this.details)
               {
                    this.details.update(this.groupProviderData);
               }

               for(var managedInterface in this.managedInterfaces)
               {
                    var managedInterfaceUI = this.managedInterfaces[managedInterface];
                    if (managedInterfaceUI)
                    {
                        managedInterfaceUI.update(this.groupProviderData);
                    }
               }
           };

           return GroupProvider;
       });
