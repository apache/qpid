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
define(["dojo/dom",
        "dojo/_base/xhr",
        "dojo/parser",
        "dojo/query",
        "dojo/_base/connect",
        "dijit/registry",
        "dojox/html/entities",
        "qpid/common/properties",
        "qpid/common/updater",
        "qpid/common/util",
        "qpid/common/formatter",
        "qpid/management/addKeystore",
        "dojo/domReady!"],
       function (dom, xhr, parser, query, connect, registry, entities, properties, updater, util, formatter, addKeystore) {

           function KeyStore(name, parent, controller, objectType) {
               this.keyStoreName = name;
               this.controller = controller;
               this.modelObj = { type: "keystore", name: name, parent: parent};
               this.url = "api/latest/keystore/" + encodeURIComponent(name);
               this.dialog =  addKeystore.showKeystoreDialog;
           }

           KeyStore.prototype.getTitle = function() {
               return "KeyStore: " + this.keyStoreName;
           };

           KeyStore.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showKeyStore.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.keyStoreUpdater = new KeyStoreUpdater(contentPane.containerNode, that.modelObj, that.controller, that.url);

                            updater.add( that.keyStoreUpdater );

                            that.keyStoreUpdater.update();

                            var deleteKeyStoreButton = query(".deleteKeyStoreButton", contentPane.containerNode)[0];
                            var node = registry.byNode(deleteKeyStoreButton);
                            connect.connect(node, "onClick",
                                function(evt){
                                    that.deleteKeyStore();
                                });

                            var editKeyStoreButton = query(".editKeyStoreButton", contentPane.containerNode)[0];
                            var node = registry.byNode(editKeyStoreButton);
                            connect.connect(node, "onClick",
                                function(evt){
                                  xhr.get({url: that.url, sync: properties.useSyncGet, handleAs: "json", content: { actuals: true }})
                                    .then(function(data)
                                    {
                                      that.dialog(data[0], that.url);
                                    });
                                });
                        }});
           };

           KeyStore.prototype.close = function() {
               updater.remove( this.keyStoreUpdater );
           };

           function KeyStoreUpdater(containerNode, keyStoreObj, controller, url)
           {
               var that = this;

               function findNode(name) {
                   return query("." + name + "Value", containerNode)[0];
               }

               function storeNodes(names)
               {
                  for(var i = 0; i < names.length; i++) {
                      that[names[i]] = findNode(names[i]);
                  }
               }

               storeNodes(["name",
                           "path",
                           "keyStoreType",
                           "keyManagerFactoryAlgorithm",
                           "certificateAlias",
                           "peersOnly"
                           ]);

               this.query = url;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                               {
                                  that.keyStoreData = data[0];
                                  that.updateHeader();
                               });

           }

           KeyStoreUpdater.prototype.updateHeader = function()
           {
              this.name.innerHTML = entities.encode(String(this.keyStoreData[ "name" ]));
              this.path.innerHTML = entities.encode(String(this.keyStoreData[ "path" ]));
              this.keyStoreType.innerHTML = entities.encode(String(this.keyStoreData[ "keyStoreType" ]));
              this.keyManagerFactoryAlgorithm.innerHTML = entities.encode(String(this.keyStoreData[ "keyManagerFactoryAlgorithm" ]));
              this.certificateAlias.innerHTML = this.keyStoreData[ "certificateAlias" ] ? entities.encode(String( this.keyStoreData[ "certificateAlias" ])) : "";
           };

           KeyStoreUpdater.prototype.update = function()
           {

              var thisObj = this;

              xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                   {
                      thisObj.keyStoreData = data[0];
                      thisObj.updateHeader();
                   });
           };

           KeyStore.prototype.deleteKeyStore = function() {
               if(confirm("Are you sure you want to delete key store '" +this.keyStoreName+"'?")) {
                   var query = this.url;
                   this.success = true
                   var that = this;
                   xhr.del({url: query, sync: true, handleAs: "json"}).then(
                       function(data) {
                           that.contentPane.onClose()
                           that.controller.tabContainer.removeChild(that.contentPane);
                           that.contentPane.destroyRecursive();
                           that.close();
                       },
                       function(error) {that.success = false; that.failureReason = error;});
                   if(!this.success ) {
                       alert("Error:" + this.failureReason);
                   }
               }
           }

           return KeyStore;
       });
