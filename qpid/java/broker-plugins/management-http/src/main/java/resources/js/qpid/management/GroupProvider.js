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
        "dojox/grid/enhanced/plugins/Pagination",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, properties, updater, util, UpdatableStore, EnhancedGrid) {

           function GroupProvider(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "groupprovider", name: name };
               if(parent) {
                    this.modelObj.parent = {};
                    this.modelObj.parent[ parent.type] = parent;
                }
           }

           GroupProvider.prototype.getTitle = function() {
               return "GroupProvider";
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

                        }});
           };

           GroupProvider.prototype.close = function() {
               updater.remove( this.groupProviderAdapter );
           };

           function GroupProviderUpdater(node, groupProviderObj, controller)
           {
               this.controller = controller;
               this.name = query(".name", node)[0];
               this.query = "rest/groupprovider/"+encodeURIComponent(groupProviderObj.name);

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.groupProviderData = data[0];

                             util.flattenStatistics( that.groupProviderData );

                             that.updateHeader();

                             require(["qpid/management/groupprovider/"+that.groupProviderData.type],
                                 function(SpecificProvider) {
                                 that.details = new SpecificProvider(node, groupProviderObj, controller);
                                 that.details.update();
                             });

                         });

           }

           GroupProviderUpdater.prototype.updateHeader = function()
           {
               this.name.innerHTML = this.groupProviderData[ "name" ];
           };

           GroupProviderUpdater.prototype.update = function()
           {
               var that = this;
           };

           return GroupProvider;
       });
