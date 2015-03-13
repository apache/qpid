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
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/_base/json',
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "dijit/form/FilteringSelect",
        "dojo/dom-style",
        "dojo/_base/lang",
        "qpid/common/util",
        "qpid/common/metadata",
        /* dojox/ validate resources */
        "dojox/validate/us",
        "dojox/validate/web",
        /* basic dijit classes */
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/Textarea",
        "dijit/form/TextBox",
        "dijit/form/ValidationTextBox",
        "dijit/form/DateTextBox",
        "dijit/form/TimeTextBox",
        "dijit/form/Button",
        "dijit/form/RadioButton",
        "dijit/form/Form",
        "dijit/form/DateTextBox",
        "dijit/form/MultiSelect",
        "dijit/form/Select",
        "dijit/form/NumberSpinner",
        /* basic dojox classes */
        "dojox/grid/EnhancedGrid",
        "dojox/grid/enhanced/plugins/IndirectSelection",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, array, event, json, Memory, ObjectStore, FilteringSelect, domStyle, lang, util, metadata) {

        var addPort = {};

        var node = construct.create("div", null, win.body(), "last");

        addPort._typeChanged = function (newValue)
        {
            var typeMetaData = metadata.getMetaData("Port", newValue);

            //protocols
            var protocolsMultiSelect = dom.byId("formAddPort.protocols");
            var protocolValidValues = typeMetaData.attributes.protocols.validValues;
            var protocolValues = metadata.extractUniqueListOfValues(protocolValidValues);
            util.setMultiSelectOptions(protocolsMultiSelect, protocolValues.sort());

            //authenticationProvider
            registry.byId("formAddPort.authenticationProvider").set("disabled", ! ("authenticationProvider" in typeMetaData.attributes));
            dom.byId("formAddPort:fieldsAuthenticationProvider").style.display = "authenticationProvider" in typeMetaData.attributes ? "block" : "none";

            //bindingAddress
            registry.byId("formAddPort.bindingAddress").set("disabled", ! ("bindingAddress" in typeMetaData.attributes));
            dom.byId("formAddPort:fieldsBindingAddress").style.display = "bindingAddress" in typeMetaData.attributes ? "block" : "none";

            //maxOpenConnections
            registry.byId("formAddPort.maxOpenConnections").set("disabled", ! ("maxOpenConnections" in typeMetaData.attributes));
            dom.byId("formAddPort:maxOpenConnections").style.display = "maxOpenConnections" in typeMetaData.attributes ? "block" : "none";

            //transports
            var transportsMultiSelect = dom.byId("formAddPort.transports");
            var transportsValidValues = typeMetaData.attributes.transports.validValues;
            var transportsValues = metadata.extractUniqueListOfValues(transportsValidValues);
            util.setMultiSelectOptions(transportsMultiSelect, transportsValues.sort());

            addPort._toggleSslWidgets(newValue, transportsMultiSelect.value);
            util.applyMetadataToWidgets(registry.byId("addPort").domNode, "Port", newValue);

        };

        addPort._isSecure = function(currentTransport)
        {
          return currentTransport == "SSL" || (lang.isArray(currentTransport) && array.indexOf(currentTransport, "SSL")>=0)
            || currentTransport == "WSS" || (lang.isArray(currentTransport) && array.indexOf(currentTransport, "WSS")>=0);
        }

        addPort._convertToPort = function(formValues)
            {
                var newPort = {};
                newPort.name = dijit.byId("formAddPort.name").value;
                var id = dojo.byId("formAddPort.id").value;
                if (id)
                {
                    newPort.id = id;
                }
                for(var propName in formValues)
                {
                    if(formValues.hasOwnProperty(propName) && formValues[propName])
                    {
                        if (propName == "needClientAuth" || propName == "wantClientAuth")
                        {
                            continue;
                        }
                        else if (propName === "protocols")
                        {
                            var val = formValues[propName];

                            if(val === "" || (lang.isArray(val) && val.length == 0) )
                            {
                                continue;
                            }

                            if (!lang.isArray(val))
                            {
                                val = [ val ];
                            }
                            newPort[ propName ] = val;
                        }
                        else if (propName === "transports")
                        {
                            var val = formValues[propName];

                            if(val === "" || (lang.isArray(val) && val.length == 0) )
                            {
                                continue;
                            }

                            if (!lang.isArray(val))
                            {
                                val = [ val ];
                            }
                            newPort[ propName ] = val;
                        }
                        else if(formValues[ propName ] !== "")
                        {
                            newPort[ propName ] = formValues[propName];
                        }

                    }
                }

                var type = dijit.byId("formAddPort.type").value;
                if (type == "AMQP" || type == "HTTP")
                {
                    var transportWidget = registry.byId("formAddPort.transports");
                    var needClientAuth = dijit.byId("formAddPort.needClientAuth");
                    var wantClientAuth = dijit.byId("formAddPort.wantClientAuth");
                    var trustStoreWidget = dijit.byId("formAddPort.trustStores");

                    var initialTransport = transportWidget.initialValue;
                    var currentTransport = transportWidget.value;
                    if (addPort._isSecure(currentTransport))
                    {
                      newPort.needClientAuth = needClientAuth.checked;
                      newPort.wantClientAuth = wantClientAuth.checked

                      var items = trustStoreWidget.selection.getSelected();
                      var trustStores = [];
                      if(items.length > 0){
                        for(var i in items)
                        {
                          var item = items[i];
                          trustStores.push(trustStoreWidget.store.getValue(item, "name"));
                        }
                        newPort.trustStores = trustStores;
                      }
                      else if (trustStoreWidget.initialValue && trustStoreWidget.initialValue.length > 0)
                      {
                        newPort.trustStores = null;
                      }
                    }
                    else if (initialTransport && currentTransport != initialTransport)
                    {
                      newPort.needClientAuth = false;
                      newPort.wantClientAuth = false;
                      newPort.trustStores = null;
                    }
                }

                return newPort;
            };

        addPort._toggleSslWidgets = function(portType, transportType)
            {
                var clientAuthPanel = dojo.byId("formAddPort:fieldsClientAuth");
                var transportSSLPanelNode = dom.byId("formAddPort:fieldsTransportSSL");

                if (addPort._isSecure(transportType))
                {
                    var typeMetaData = metadata.getMetaData("Port", portType);
                    var clientAuth = "needClientAuth" in typeMetaData.attributes || "wantClientAuth" in typeMetaData.attributes;
                    clientAuthPanel.style.display = clientAuth ? "block" : "none";
                    if (clientAuth)
                    {
                        registry.byId("formAddPort.needClientAuth").set("disabled", !("needClientAuth" in typeMetaData.attributes));
                        registry.byId("formAddPort.wantClientAuth").set("disabled", !("wantClientAuth" in typeMetaData.attributes));
                        registry.byId("formAddPort.trustStores").resize();
                    }

                    transportSSLPanelNode.style.display = "block";
                    registry.byId("formAddPort.keyStore").set("disabled", false);
                }
                else
                {
                    clientAuthPanel.style.display = "none";
                    registry.byId("formAddPort.needClientAuth").set("disabled", true);
                    registry.byId("formAddPort.wantClientAuth").set("disabled", true);

                    transportSSLPanelNode.style.display = "none";
                    registry.byId("formAddPort.keyStore").set("disabled", true);
                }

            };

        addPort._init = function()
        {
          xhr.get({url: "addPort.html", sync: true, load: function (data)
          {
            var theForm;
            node.innerHTML = data;
            addPort.dialogNode = dom.byId("addPort");
          }});
        }

        addPort._prepareForm = function()
        {
          //add the port types to formAddPort.type
          var portTypeSelect = registry.byId("formAddPort.type");
          var supportedPortTypes = metadata.getTypesForCategory("Port");
          var portTypeSelectStore = util.makeTypeStore(supportedPortTypes);
          portTypeSelect.set("store", portTypeSelectStore);

          //add handler for transports change
          registry.byId("formAddPort.transports").on("change", function (newValue)
          {
            var portType = portTypeSelect.get("value");
            addPort._toggleSslWidgets(portType, newValue);
          });

          theForm = registry.byId("formAddPort");
          theForm.on("submit", function (e)
          {

            event.stop(e);
            if (theForm.validate())
            {

              var newPort = addPort._convertToPort(theForm.getValues());
              if ((newPort.needClientAuth || newPort.wantClientAuth) && (!newPort.hasOwnProperty("trustStores") || newPort.trustStores.length == 0))
              {
                alert("A trust store must be selected when requesting client certificates.");
                return false;
              }
              var url = "api/latest/port";
              if (registry.byId("formAddPort.name").get("disabled"))
              {
                // update request
                url += "/" + encodeURIComponent(newPort.name);
              }
              util.post(url, newPort, function(x){registry.byId("addPort").hide()});
              return false;
            } else
            {
              alert('Form contains invalid data.  Please correct first');
              return false;
            }

          });
        }

        addPort.show = function(portName, portType, providers, keystores, truststores)
        {

            if (!this.formPrepared)
            {
              this._prepareForm();
              this.formPrepared = true;
            }

            registry.byId("formAddPort").reset();
            dojo.byId("formAddPort.id").value = "";

            var nameWidget = registry.byId("formAddPort.name");
            var typeWidget = registry.byId("formAddPort.type");
            var portWidget = registry.byId("formAddPort.port");
            var maxOpenConnectionsWidget = registry.byId("formAddPort.maxOpenConnections");
            var editWarning = dojo.byId("portEditWarning");

            var providerWidget = registry.byId("formAddPort.authenticationProvider");
            if (providers)
            {
                var data = [];
                for (var i=0; i< providers.length; i++)
                {
                    data.push( {id: providers[i].name, name: providers[i].name} );
                }
                var providersStore = new Memory({ data: data });
                providerWidget.set("store", providersStore);
                providerWidget.startup();
            }

            var keystoreWidget = registry.byId("formAddPort.keyStore");
            if (keystores)
            {
                var data = [];
                for (var i=0; i< keystores.length; i++)
                {
                    data.push( {id: keystores[i].name, name: keystores[i].name} );
                }
                var keystoresStore = new Memory({ data: data });
                keystoreWidget.set("store", keystoresStore);
                keystoreWidget.startup();
            }

            var truststoreWidget = registry.byId("formAddPort.trustStores");
            if (truststores)
            {
                var layout = [[{name: "Name", field: "name", width: "80%"},
                               {name: "Peers only", field: "peersOnly", width: "20%",
                                 formatter: function(val){
                                   return "<input type='radio' disabled='disabled' "+(val?"checked='checked'": "")+" />"
                                 }
                             }]];

                var mem = new Memory({ data: truststores, idProperty: "id"});
                truststoreWidget.set("store", new ObjectStore({objectStore: mem}));
                truststoreWidget.set("structure", layout);
                truststoreWidget.rowSelectCell.toggleAllSelection(false);
                truststoreWidget.startup();
            }

            // Editing existing port, de-register existing on change handler if set
            if (this.typeChangeHandler)
            {
                this.typeChangeHandler.remove();
            }

            if (portName)
            {
                editWarning.style.display = "block";

                xhr.get({
                    url: "api/latest/port/" + encodeURIComponent(portName),
                    content: { actuals: true },
                    handleAs: "json"
                }).then(
                   function(data){
                       var port = data[0];
                       nameWidget.set("value", port.name);
                       nameWidget.set("disabled", true);

                       dom.byId("formAddPort.id").value=port.id;

                       //type
                       typeWidget.set("value", portType);
                       typeWidget.set("disabled", true);
                       var typeMetaData = metadata.getMetaData("Port", portType);

                       //port number
                       portWidget.set("value", port.port);
                       portWidget.set("regExpGen", util.numericOrContextVarRegexp);

                       //protocols
                       var protocolsMultiSelect = dom.byId("formAddPort.protocols");
                       var protocolValidValues = typeMetaData.attributes.protocols.validValues;
                       var protocolValues = metadata.extractUniqueListOfValues(protocolValidValues);
                       util.setMultiSelectOptions(protocolsMultiSelect, protocolValues.sort());

                       var protocolsMultiSelectWidget = registry.byId("formAddPort.protocols");
                       protocolsMultiSelectWidget.set("value", port.protocols);

                       //authenticationProvider
                       providerWidget.set("value", port.authenticationProvider ? port.authenticationProvider : "");
                       providerWidget.set("disabled", ! ("authenticationProvider" in typeMetaData.attributes));
                       dom.byId("formAddPort:fieldsAuthenticationProvider").style.display = "authenticationProvider" in typeMetaData.attributes ? "block" : "none";

                       //transports
                       var transportsMultiSelect = dom.byId("formAddPort.transports");
                       var transportsValidValues = typeMetaData.attributes.transports.validValues;
                       var transportsValues = metadata.extractUniqueListOfValues(transportsValidValues);
                       util.setMultiSelectOptions(transportsMultiSelect, transportsValues.sort());
                       var transportWidget = registry.byId("formAddPort.transports");
                       transportWidget.set("value", port.transports);

                       //binding address
                       var bindAddressWidget = registry.byId("formAddPort.bindingAddress");
                       bindAddressWidget.set("value", port.bindingAddress ? port.bindingAddress : "");
                       bindAddressWidget.set("disabled", ! ("bindingAddress" in typeMetaData.attributes));
                       dom.byId("formAddPort:fieldsBindingAddress").style.display = "bindingAddress" in typeMetaData.attributes ? "block" : "none";

                       //maxOpenConnections
                       var maxOpenConnectionsWidget = registry.byId("formAddPort.maxOpenConnections");
                       maxOpenConnectionsWidget.set("regExpGen", util.signedOrContextVarRegexp);
                       maxOpenConnectionsWidget.set("value", port.maxOpenConnections ? port.maxOpenConnections : "");
                       maxOpenConnectionsWidget.set("disabled", ! ("maxOpenConnections" in typeMetaData.attributes));
                       dom.byId("formAddPort:maxOpenConnections").style.display = "maxOpenConnections" in typeMetaData.attributes ? "block" : "none";

                       //ssl
                       keystoreWidget.set("value", port.keyStore ? port.keyStore : "");
                       if (port.trustStores)
                       {
                         var items = truststoreWidget.store.objectStore.data;
                         for (var j=0; j< items.length; j++)
                         {
                           var selected = false;
                           for (var i=0; i< port.trustStores.length; i++)
                           {
                             var trustStore = port.trustStores[i];
                             if (items[j].name == trustStore)
                             {
                               selected = true;
                               break;
                             }
                           }
                           truststoreWidget.selection.setSelected(j,selected);
                         }
                       }

                       // want/need client auth
                       registry.byId("formAddPort.needClientAuth").set("checked", port.needClientAuth);
                       registry.byId("formAddPort.wantClientAuth").set("checked", port.wantClientAuth);

                       keystoreWidget.initialValue = port.keyStore;
                       truststoreWidget.initialValue = port.trustStores;
                       transportWidget.initialValue = transportWidget.value;
                       providerWidget.initialValue = providerWidget.value;
                       maxOpenConnectionsWidget.initialValue = maxOpenConnectionsWidget.value;

                       registry.byId("addPort").show();
                       util.applyMetadataToWidgets(registry.byId("addPort").domNode, "Port", portType);
                   });
            }
            else
            {
                // Adding new port, register the on change handler
                this.typeChangeHandler = typeWidget.on("change", addPort._typeChanged);

                if (typeWidget.get("disabled"))
                {
                  typeWidget.set("disabled", false);
                }
                typeWidget.set("value", portType);

                nameWidget.set("disabled", false);
                nameWidget.set("regExpGen", util.nameOrContextVarRegexp);
                portWidget.set("regExpGen", util.numericOrContextVarRegexp);
                maxOpenConnectionsWidget.set("regExpGen", util.signedOrContextVarRegexp);

                editWarning.style.display = "none";
                registry.byId("addPort").show();

                util.applyMetadataToWidgets(registry.byId("addPort").domNode, "Port", portType);
            }

        };

        addPort._init();

        return addPort;
    });
