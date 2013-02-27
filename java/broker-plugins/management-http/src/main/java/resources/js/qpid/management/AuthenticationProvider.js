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

           function AuthenticationProvider(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "authenticationprovider", name: name };
               if(parent) {
                    this.modelObj.parent = {};
                    this.modelObj.parent[ parent.type] = parent;
                }
           }

           AuthenticationProvider.prototype.getTitle = function() {
               return "AuthenticationProvider";
           };

           AuthenticationProvider.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showAuthProvider.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.authProviderAdapter = new AuthProviderUpdater(contentPane.containerNode, that.modelObj, that.controller);

                            updater.add( that.authProviderAdapter );

                            that.authProviderAdapter.update();

                        }});
           };

           AuthenticationProvider.prototype.close = function() {
               updater.remove( this.authProviderAdapter );
           };

           function AuthProviderUpdater(node, authProviderObj, controller)
           {
               this.controller = controller;
               this.name = query(".name", node)[0];
               this.type = query(".type", node)[0];
               /*this.state = dom.byId("state");
               this.durable = dom.byId("durable");
               this.lifetimePolicy = dom.byId("lifetimePolicy");
               */
               this.query = "rest/authenticationprovider/"+encodeURIComponent(authProviderObj.name);

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.authProviderData = data[0];

                             util.flattenStatistics( that.authProviderData );

                             that.updateHeader();

                             require(["qpid/management/authenticationprovider/"+that.authProviderData.category],
                                 function(SpecificProvider) {
                                 that.details = new SpecificProvider(node, authProviderObj, controller);
                                 that.details.update();
                             });

                         });

           }

           AuthProviderUpdater.prototype.updateHeader = function()
           {
               this.name.innerHTML = this.authProviderData[ "name" ];
               this.type.innerHTML = this.authProviderData[ "authenticationProviderType" ];
    /*           this.state.innerHTML = this.brokerData[ "state" ];
               this.durable.innerHTML = this.brokerData[ "durable" ];
               this.lifetimePolicy.innerHTML = this.brokerData[ "lifetimePolicy" ];
*/
           };

           AuthProviderUpdater.prototype.update = function()
           {

               var that = this;


           };



           return AuthenticationProvider;
       });
