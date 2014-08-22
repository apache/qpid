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
        "qpid/management/addPort",
        "qpid/common/metadata",
        "dojo/domReady!"],
       function (dom, xhr, parser, query, connect, registry, entities, properties, updater, util, formatter, addPort, metadata) {

           function Port(name, parent, controller, type) {
               this.name = name;
               this.controller = controller;
               this.type = type;
               this.modelObj = { type: "port", name: name, parent: parent};
           }

           Port.prototype.getTitle = function() {
               return "Port: " + this.name;
           };

           Port.prototype.open = function(contentPane) {
               var that = this;
               this.contentPane = contentPane;
               xhr.get({url: "showPort.html",
                        sync: true,
                        load:  function(data) {
                            contentPane.containerNode.innerHTML = data;
                            parser.parse(contentPane.containerNode);

                            that.portUpdater = new PortUpdater(contentPane.containerNode, that.modelObj, that.controller, "api/latest/port/" + encodeURIComponent(that.name));

                            updater.add( that.portUpdater );

                            that.portUpdater.update();

                            var deletePortButton = query(".deletePortButton", contentPane.containerNode)[0];
                            var node = registry.byNode(deletePortButton);
                            connect.connect(node, "onClick",
                                function(evt){
                                    that.deletePort();
                                });

                            var editPortButton = query(".editPortButton", contentPane.containerNode)[0];
                            var node = registry.byNode(editPortButton);
                            connect.connect(node, "onClick",
                                function(evt){
                                  that.showEditDialog();
                                });
                        }});
           };

           Port.prototype.close = function() {
               updater.remove( this.portUpdater );
           };


           Port.prototype.deletePort = function() {
               if(confirm("Are you sure you want to delete port '" +this.name+"'?")) {
                   var query = "api/latest/port/" + encodeURIComponent(this.name);
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
                       util.xhrErrorHandler(this.failureReason);
                   }
               }
           }

           Port.prototype.showEditDialog = function() {
               var that = this;
               xhr.get({url: "api/latest/broker", sync: properties.useSyncGet, handleAs: "json"})
               .then(function(data)
                     {
                         var brokerData= data[0];
                         addPort.show(that.name, that.type, brokerData.authenticationproviders, brokerData.keystores, brokerData.truststores);
                     }
               );
           }

           function PortUpdater(containerNode, portObj, controller, url)
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

               storeNodes(["nameValue",
                           "stateValue",
                           "typeValue",
                           "portValue",
                           "authenticationProviderValue",
                           "protocolsValue",
                           "transportsValue",
                           "bindingAddressValue",
                           "keyStoreValue",
                           "needClientAuthValue",
                           "wantClientAuthValue",
                           "trustStoresValue",
                           "bindingAddress",
                           "keyStore",
                           "needClientAuth",
                           "wantClientAuth",
                           "trustStores"
                           ]);

               this.query = url;

               xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                               {
                                  that.keyStoreData = data[0];
                                  that.updateHeader();
                               });

           }

           PortUpdater.prototype.updateHeader = function()
           {
               function printArray(fieldName, object)
               {
                   var array = object[fieldName];
                   var data = "<div>";
                   if (array) {
                       for(var i = 0; i < array.length; i++) {
                           data+= "<div>" + entities.encode(array[i]) + "</div>";
                       }
                   }
                   return data + "</div>";
               }

              this.nameValue.innerHTML = entities.encode(String(this.keyStoreData[ "name" ]));
              this.stateValue.innerHTML = entities.encode(String(this.keyStoreData[ "state" ]));
              this.typeValue.innerHTML = entities.encode(String(this.keyStoreData[ "type" ]));
              this.portValue.innerHTML = entities.encode(String(this.keyStoreData[ "port" ]));
              this.authenticationProviderValue.innerHTML = this.keyStoreData[ "authenticationProvider" ] ? entities.encode(String(this.keyStoreData[ "authenticationProvider" ])) : "";
              this.protocolsValue.innerHTML = printArray( "protocols", this.keyStoreData);
              this.transportsValue.innerHTML = printArray( "transports", this.keyStoreData);
              this.bindingAddressValue.innerHTML = this.keyStoreData[ "bindingAddress" ] ? entities.encode(String(this.keyStoreData[ "bindingAddress" ])) : "" ;
              this.keyStoreValue.innerHTML = this.keyStoreData[ "keyStore" ] ? entities.encode(String(this.keyStoreData[ "keyStore" ])) : "";
              this.needClientAuthValue.innerHTML = "<input type='checkbox' disabled='disabled' "+(this.keyStoreData[ "needClientAuth" ] ? "checked='checked'": "")+" />" ;
              this.wantClientAuthValue.innerHTML = "<input type='checkbox' disabled='disabled' "+(this.keyStoreData[ "wantClientAuth" ] ? "checked='checked'": "")+" />" ;
              this.trustStoresValue.innerHTML = printArray( "trustStores", this.keyStoreData);

              var typeMetaData = metadata.getMetaData("Port", this.keyStoreData["type"]);

              this.bindingAddress.style.display = "bindingAddress" in typeMetaData.attributes ? "block" : "none";
              this.keyStore.style.display = "keyStore" in typeMetaData.attributes ? "block" : "none";
              this.needClientAuth.style.display = "needClientAuth" in typeMetaData.attributes ? "block" : "none";
              this.wantClientAuth.style.display = "wantClientAuth" in typeMetaData.attributes ? "block" : "none";
              this.trustStores.style.display = "trustStores" in typeMetaData.attributes ? "block" : "none";
           };

           PortUpdater.prototype.update = function()
           {

              var thisObj = this;

              xhr.get({url: this.query, sync: properties.useSyncGet, handleAs: "json"}).then(function(data)
                   {
                      thisObj.keyStoreData = data[0];
                      thisObj.updateHeader();
                   });
           };

           return Port;
       });
