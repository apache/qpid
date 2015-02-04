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
        "dojo/store/JsonRest",
        "dojox/grid/EnhancedGrid",
        "dojo/data/ObjectStore",
        "qpid/management/group/addGroupMember",
        "dojox/html/entities",
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojo/domReady!"],
       function (xhr, parser, query, registry, connect, event, json, properties, updater, util, formatter,
                 UpdatableStore, JsonRest, EnhancedGrid, ObjectStore, addGroupMember, entities) {

           function Group(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "group", name: name };

               if(parent) {
                   this.modelObj.parent = {};
                   this.modelObj.parent[ parent.type] = parent;
               }
           }

           Group.prototype.getGroupName = function()
           {
               return this.name;
           };


           Group.prototype.getGroupProviderName = function()
           {
               return this.modelObj.parent.groupprovider.name;
           };

           Group.prototype.getTitle = function()
           {
               return "Group: " + this.name;
           };

           Group.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;

               xhr.get({url: "group/showGroup.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.groupUpdater = new GroupUpdater(contentPane.containerNode, that, that.controller);
                            that.groupUpdater.update();
                            updater.add( that.groupUpdater );
                            
                            var addGroupMemberButton = query(".addGroupMemberButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(addGroupMemberButton), "onClick",
                                    function(evt){
                                      addGroupMember.show(that.getGroupProviderName(), that.getGroupName())
                               }
                            );

                            var removeGroupMemberButton = query(".removeGroupMemberButton", contentPane.containerNode)[0];
                            connect.connect(registry.byNode(removeGroupMemberButton), "onClick",
                                    function(evt){
                                        util.deleteGridSelections(
                                                that.groupUpdater,
                                                that.groupUpdater.groupMembersUpdatableStore.grid,
                                                "api/latest/groupmember/"+ encodeURIComponent(that.getGroupProviderName()) +
                                                 "/" + encodeURIComponent(that.getGroupName()),
                                                "Are you sure you want to remove group member");
                                }
                            );
                        }});
           };

           Group.prototype.close = function() {
               updater.remove( this.groupUpdater );
           };

           function GroupUpdater(containerNode, groupObj, controller)
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
                           "type"]);
               this.name.innerHTML = entities.encode(String(groupObj.getGroupName()));
               this.query = "api/latest/groupmember/"+ encodeURIComponent(groupObj.getGroupProviderName()) + "/" + encodeURIComponent(groupObj.getGroupName());

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                               {
                                   that.groupMemberData = data;

                                   util.flattenStatistics( that.groupMemberData );

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

                                   that.groupMembersUpdatableStore = new UpdatableStore(that.groupMemberData, findNode("groupMembers"),
                                                            [ { name: "Group Member Name",    field: "name",      width: "100%" }],
                                                         function(obj)
                                                        {
                                                            connect.connect(obj.grid, "onRowDblClick", obj.grid,
                                                                         function(evt){

                                                                         });
                                                        } , gridProperties, EnhancedGrid);

                               });

           }

           GroupUpdater.prototype.update = function()
           {

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                  .then(function(data) {
                      that.groupMemberData = data;

                      util.flattenStatistics( that.groupMemberData );

                      that.groupMembersUpdatableStore.update(that.groupMemberData);
                  });
           };

           Group.prototype.deleteGroupMember = function() {
               if(confirm("Are you sure you want to delete group member'" +this.name+"'?")) {
                   var query = "api/latest/groupmember/"+ encodeURIComponent(this.getGroupProviderName()) + "/" + encodeURIComponent(this.name);
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

           return Group;
       });
