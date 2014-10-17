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
        "qpid/management/addAuthenticationProvider",
        "dojo/_base/event",
        "dijit/registry",
        "dojo/dom-style",
        "dojox/html/entities",
        "dojo/dom",
        "qpid/management/addPreferencesProvider",
        "qpid/management/PreferencesProvider",
        "qpid/management/authenticationprovider/PrincipalDatabaseAuthenticationManager",
        "dojo/domReady!"],
       function (xhr, parser, query, connect, properties, updater, util, UpdatableStore, EnhancedGrid,
           addAuthenticationProvider, event, registry, domStyle, entities, dom, addPreferencesProvider, PreferencesProvider, PrincipalDatabaseAuthenticationManager) {

           function AuthenticationProvider(name, parent, controller) {
               this.name = name;
               this.controller = controller;
               this.modelObj = { type: "authenticationprovider", name: name, parent: parent};
           }

           AuthenticationProvider.prototype.getTitle = function() {
               return "AuthenticationProvider:" + this.name;
           };

           AuthenticationProvider.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showAuthProvider.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.authProviderUpdater = new AuthProviderUpdater(contentPane.containerNode, that.modelObj, that.controller, that);

                            that.authProviderUpdater.update();

                            var editButton = query(".editAuthenticationProviderButton", contentPane.containerNode)[0];
                            var editWidget = registry.byNode(editButton);
                            connect.connect(editWidget, "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                addAuthenticationProvider.show(that.name);
                                            });

                            var deleteButton = query(".deleteAuthenticationProviderButton", contentPane.containerNode)[0];
                            var deleteWidget = registry.byNode(deleteButton);
                            connect.connect(deleteWidget, "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                that.deleteAuthenticationProvider();
                                            });

                            var addPreferencesProviderButton = query(".addPreferencesProviderButton", contentPane.containerNode)[0];
                            var addPreferencesProviderWidget = registry.byNode(addPreferencesProviderButton);
                            connect.connect(addPreferencesProviderWidget, "onClick",
                                            function(evt){
                                                event.stop(evt);
                                                that.addPreferencesProvider();
                                            });
                            updater.add( that.authProviderUpdater );
                        }});
           };

           AuthenticationProvider.prototype.close = function() {
               updater.remove( this.authProviderUpdater);
               if (this.authProviderUpdater.details)
               {
                   updater.remove(this.authProviderUpdater.details.authDatabaseUpdater);
               }
           };

           AuthenticationProvider.prototype.deleteAuthenticationProvider = function() {
               if(confirm("Are you sure you want to delete authentication provider '" + this.name + "'?")) {
                   var query = "api/latest/authenticationprovider/" +encodeURIComponent(this.name);
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

           AuthenticationProvider.prototype.addPreferencesProvider = function() {
             if (this.authProviderUpdater && this.authProviderUpdater.authProviderData
                   && (!this.authProviderUpdater.authProviderData.preferencesproviders
                       || !this.authProviderUpdater.authProviderData.preferencesproviders[0])){
               addPreferencesProvider.show(this.name);
             }
           };

           function AuthProviderUpdater(node, authProviderObj, controller, authenticationProvider)
           {
               this.controller = controller;
               this.name = query(".name", node)[0];
               this.type = query(".type", node)[0];
               this.state = query(".state", node)[0];
               this.authenticationProvider = authenticationProvider;
               this.preferencesProviderType=dom.byId("preferencesProviderType");
               this.preferencesProviderName=dom.byId("preferencesProviderName");
               this.preferencesProviderState=dom.byId("preferencesProviderState");
               this.addPreferencesProviderButton = query(".addPreferencesProviderButton", node)[0];
               this.editPreferencesProviderButton = query(".editPreferencesProviderButton", node)[0];
               this.deletePreferencesProviderButton = query(".deletePreferencesProviderButton", node)[0];
               this.preferencesProviderAttributes = dom.byId("preferencesProviderAttributes")
               this.preferencesNode = query(".preferencesProviderDetails", node)[0];

               this.query = "api/latest/authenticationprovider/" + encodeURIComponent(authProviderObj.name);

               var that = this;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                   .then(function(data)
                         {
                             that.authProviderData = data[0];

                             util.flattenStatistics( that.authProviderData );

                             that.updateHeader();

                             var editButton = query(".editAuthenticationProviderButton", node)[0];
                             var editWidget = registry.byNode(editButton);
                             var hideEdit = (that.authProviderData.type === 'Anonymous' || that.authProviderData.type === 'External' || that.authProviderData.type === 'Kerberos')
                             domStyle.set(editWidget.domNode, "display", hideEdit ? "none": "");

                             if (util.isProviderManagingUsers(that.authProviderData.type))
                             {
                                     that.details = new PrincipalDatabaseAuthenticationManager(node, that.authProviderData, controller);
                                     that.details.update(that.authProviderData);
                             }
                             if (that.authProviderData.type == "Anonymous")
                             {
                               var authenticationProviderPanel =  registry.byNode( query(".preferencesPanel", node)[0]);
                               domStyle.set(authenticationProviderPanel.domNode, "display","none");
                             }
                             else
                             {
                               var preferencesProviderData = that.authProviderData.preferencesproviders? that.authProviderData.preferencesproviders[0]: null;

                               that.updatePreferencesProvider(preferencesProviderData);
                             }
                         });

           }

           AuthProviderUpdater.prototype.updatePreferencesProvider = function(preferencesProviderData)
           {
             if (preferencesProviderData)
             {
               this.addPreferencesProviderButton.style.display = 'none';
               if (!this.preferencesProvider)
               {
                 this.preferencesProvider=new PreferencesProvider(preferencesProviderData.name, this.authProviderData);
                 this.preferencesProvider.init(this.preferencesNode);
               }
               this.preferencesProvider.update(preferencesProviderData);
             }
             else
             {
               if (this.preferencesProvider)
               {
                 this.preferencesProvider.update(null);
               }
               this.addPreferencesProviderButton.style.display = 'inline';
             }
           };

           AuthProviderUpdater.prototype.updateHeader = function()
           {
               this.authenticationProvider.name = this.authProviderData[ "name" ]
               this.name.innerHTML = entities.encode(String(this.authProviderData[ "name" ]));
               this.type.innerHTML = entities.encode(String(this.authProviderData[ "type" ]));
               this.state.innerHTML = entities.encode(String(this.authProviderData[ "state" ]));
           };

           AuthProviderUpdater.prototype.update = function()
           {
             var that = this;

             xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"})
                 .then(function(data) {
                     that.authProviderData = data[0];
                     that.name = data[0].name
                     util.flattenStatistics( that.authProviderData );
                     that.updateHeader();
                     if (that.details)
                     {
                       try
                       {
                         that.details.update(that.authProviderData);
                       }
                       catch(e)
                       {
                         if (console)
                         {
                           console.error(e);
                         }
                       }
                     }
                     var preferencesProviderData = that.authProviderData.preferencesproviders? that.authProviderData.preferencesproviders[0]: null;
                     try
                     {
                       that.updatePreferencesProvider(preferencesProviderData);
                     }
                     catch(e)
                     {
                       if (console)
                       {
                         console.error(e);
                       }
                     }
                 });


           };



           return AuthenticationProvider;
       });
