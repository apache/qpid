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
        "dojo/_base/event",
        "dojox/html/entities",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, properties, updater, util, UpdatableStore, EnhancedGrid, registry, event, entities) {

           function GroupProvider(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "groupprovider", name: name, parent: parent};
           }

           GroupProvider.prototype.getTitle = function() {
               return "GroupProvider: " + this.name ;
           };

           GroupProvider.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showGroupProvider.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.groupProviderAdapter = new GroupProviderUpdater(contentPane.containerNode, that.modelObj, that.controller);

                            updater.add( that.groupProviderAdapter );

                            that.groupProviderAdapter.update();

                            var deleteButton = query(".deleteGroupProviderButton", contentPane.containerNode)[0];
                            var deleteWidget = registry.byNode(deleteButton);
                            connect.connect(deleteWidget, "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                that.deleteGroupProvider();
                                            });
                        }});
           };

           GroupProvider.prototype.close = function() {
               updater.remove( this.groupProviderAdapter );
           };

           GroupProvider.prototype.deleteGroupProvider = function() {
             var warnMessage = "";
             if (this.groupProviderAdapter.groupProviderData && this.groupProviderAdapter.groupProviderData.type.indexOf("File") != -1)
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

           function GroupProviderUpdater(node, groupProviderObj, controller)
           {
               this.controller = controller;
               this.name = query(".name", node)[0];
               this.type = query(".type", node)[0];
               this.state = query(".state", node)[0];
               this.query = "api/latest/groupprovider/"+encodeURIComponent(groupProviderObj.name);
               this.typeUI ={"GroupFile": "FileGroupManager"};
               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.groupProviderData = data[0];

                             util.flattenStatistics( that.groupProviderData );

                             that.updateHeader();

                             var ui = that.typeUI[that.groupProviderData.type];
                             require(["qpid/management/groupprovider/"+ ui],
                                 function(SpecificProvider) {
                                 that.details = new SpecificProvider(query(".providerDetails", node)[0], groupProviderObj, controller);
                                 that.details.update();
                             });

                         });

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
           };

           return GroupProvider;
       });
