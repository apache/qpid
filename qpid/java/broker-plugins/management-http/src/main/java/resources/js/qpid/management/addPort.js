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
        "dojo/parser",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/_base/json',
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "dijit/form/FilteringSelect",
        "dojo/dom-style",
        "dojo/_base/lang",
        "qpid/common/util",
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
    function (xhr, dom, construct, win, registry, parser, array, event, json, Memory, ObjectStore, FilteringSelect, domStyle, lang, util) {

        var addPort = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToPort = function convertToPort(formValues)
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
                    if (currentTransport == "SSL" || (lang.isArray(currentTransport) && array.indexOf(currentTransport, "SSL")>=0))
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

            var toggleSslWidgets = function toggleSslWidgets(protocolType, transportType)
            {
                var clientAuthPanel = dojo.byId("formAddPort:fieldsClientAuth");
                var display = clientAuthPanel.style.display;

                if ((transportType == "SSL" || (lang.isArray(transportType)  && array.indexOf(transportType, "SSL")>=0))
                    && (protocolType == "AMQP" || protocolType == "HTTP"))
                {
                    clientAuthPanel.style.display = "block";
                    registry.byId("formAddPort.needClientAuth").set("disabled", false);
                    registry.byId("formAddPort.wantClientAuth").set("disabled", false);
                }
                else
                {
                    clientAuthPanel.style.display = "none";
                    registry.byId("formAddPort.needClientAuth").set("disabled", true);
                    registry.byId("formAddPort.wantClientAuth").set("disabled", true);
                }

                var transportSSLPanel = registry.byId("formAddPort:fieldsTransportSSL");
                var transportSSLPanelDisplay = transportSSLPanel.domNode.style.display;
                if (transportType == "SSL" || (lang.isArray(transportType)  && array.indexOf(transportType, "SSL")>=0))
                {
                    transportSSLPanel.domNode.style.display = "block";
                    registry.byId("formAddPort.keyStore").set("disabled", false);
                }
                else
                {
                    transportSSLPanel.domNode.style.display = "none";
                    registry.byId("formAddPort.keyStore").set("disabled", true);
                }

                if (transportSSLPanel.domNode.style.display != transportSSLPanelDisplay && transportSSLPanel.domNode.style.display=="block")
                {
                    registry.byId("formAddPort.trustStores").resize();
                }
            };

        xhr.get({url: "addPort.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addPort.dialogNode = dom.byId("addPort");
                            parser.instantiate([addPort.dialogNode]);

                            registry.byId("formAddPort.protocolsDefault").on("change", function(isChecked) {
                                dijit.byId("formAddPort.protocolsAMQP").set("disabled", isChecked);
                            });

                            registry.byId("formAddPort.transports").on("change", function(newValue){
                                var protocolType = registry.byId("formAddPort.type").value;
                                if(lang.isArray(newValue) && newValue.length == 2 && protocolType == "JMX")
                                {
                                    registry.byId("formAddPort.transports").set("value", ["SSL"]);
                                    newValue = "SSL"
                                }
                                toggleSslWidgets(protocolType, newValue);
                            });

                            registry.byId("formAddPort.type").on("change", function(newValue) {
                                var typeWidget = registry.byId("formAddPort.type");
                                var store = typeWidget.store;
                                store.data.forEach(function(option){
                                    registry.byId("formAddPort.protocols" + option.value).set("disabled", true);
                                    registry.byId("formAddPort:fields" + option.value).domNode.style.display = "none";
                                });

                                var isAMQP = ("AMQP" == newValue);

                                var isHTTP = ("HTTP" == newValue);

                                registry.byId("formAddPort.needClientAuth").set("enabled", isAMQP || isHTTP);
                                registry.byId("formAddPort.wantClientAuth").set("enabled", isAMQP || isHTTP);

                                registry.byId("formAddPort:fields" + newValue).domNode.style.display = "block";
                                var defaultsAMQPProtocols = registry.byId("formAddPort.protocolsDefault");
                                defaultsAMQPProtocols.set("disabled", "AMQP" != newValue)
                                var protocolsWidget = registry.byId("formAddPort.protocols" + newValue);
                                if (protocolsWidget)
                                {
                                    protocolsWidget.set("disabled", (isAMQP && defaultsAMQPProtocols.checked));
                                }

                                var transportWidget = registry.byId("formAddPort.transports");
                                var disableTransportWidget = false;
                                var toggleSsl = true;
                                var isRMI = (newValue == "JMX" && registry.byId("formAddPort.protocolsJMX").value == "RMI");
                                if (isRMI)
                                {
                                    if  (transportWidget.value != "TCP")
                                    {
                                      transportWidget.set("value", ["TCP"]);

                                      // changing of transport widget value will cause the call to toggleSslWidgets
                                      toggleSsl = false;
                                    }
                                    disableTransportWidget = true;

                                }
                                else if(newValue == "JMX" )
                                {
                                    var transports = transportWidget.value;
                                    if(lang.isArray(transports) && transports.length == 2)
                                    {
                                        transportWidget.set("value", ["SSL"]);
                                    }
                                }


                                if (toggleSsl)
                                {
                                  toggleSslWidgets(newValue, transportWidget.value);
                                }
                                transportWidget.set("disabled", disableTransportWidget);
                                registry.byId("formAddPort.authenticationProvider").set("disabled", isRMI);
                                registry.byId("formAddPort:fieldsAuthenticationProvider").domNode.style.display = isRMI? "none" : "block";
                                registry.byId("formAddPort:fieldsBindingAddress").domNode.style.display = newValue == "JMX" ? "none" : "block";
                                registry.byId("formAddPort:transport").domNode.style.display = isRMI ? "none" : "block";



                            });

                            theForm = registry.byId("formAddPort");

                            var containers = ["formAddPort:fields", "formAddPort:fieldsTransportSSL", "formAddPort:fieldsAMQP",
                                              "formAddPort:fieldsJMX", "formAddPort:fieldsHTTP", "formAddPort:transport",
                                              "formAddPort:fieldsClientAuthCheckboxes", "formAddPort:fieldsAuthenticationProvider", "formAddPort:fieldsBindingAddress"];
                            var labelWidthValue = "200";
                            for(var i = 0; i < containers.length; i++)
                            {
                                var containerId = containers[i];
                                var fields = new dojox.layout.TableContainer( {
                                    cols: 1,
                                    labelWidth: labelWidthValue,
                                    showLabels: true,
                                    orientation: "horiz",
                                    customClass: "formLabel"
                                }, dom.byId(containerId));
                                fields.startup();
                            }

                            registry.byId("formAddPort.protocolsJMX").on("change", function(newValue){
                                var isRMI = newValue == "RMI";
                                var transportWidget = registry.byId("formAddPort.transports");
                                if (isRMI && transportWidget.value != "TCP")
                                {
                                    transportWidget.set("value", "TCP");
                                }
                                transportWidget.set("disabled", isRMI);
                                registry.byId("formAddPort:transport").domNode.style.display = isRMI ? "none" : "block";
                                registry.byId("formAddPort:fieldsAuthenticationProvider").domNode.style.display = isRMI? "none" : "block";
                                registry.byId("formAddPort.authenticationProvider").set("disabled", isRMI);
                            });

                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newPort = convertToPort(theForm.getValues());
                                    if ((newPort.needClientAuth || newPort.wantClientAuth) && (!newPort.hasOwnProperty("trustStores") || newPort.trustStores.length==0))
                                    {
                                      alert("A trust store must be selected when requesting client certificates.");
                                      return false;
                                    }
                                    var that = this;

                                    xhr.put({url: "api/latest/port/"+encodeURIComponent(newPort.name), sync: true, handleAs: "json",
                                             headers: { "Content-Type": "application/json"},
                                             putData: json.toJson(newPort),
                                             load: function(x) {that.success = true; },
                                             error: function(error) {that.success = false; that.failureReason = error;}});

                                    if(this.success === true)
                                    {
                                        registry.byId("addPort").hide();
                                    }
                                    else
                                    {
                                        alert("Error:" + this.failureReason);
                                    }

                                    return false;


                                }else{
                                    alert('Form contains invalid data.  Please correct first');
                                    return false;
                                }

                            });
                        }});

        addPort.show = function(portName, providers, keystores, truststores) {

            registry.byId("formAddPort").reset();
            dojo.byId("formAddPort.id").value = "";
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
                       var nameWidget = registry.byId("formAddPort.name");
                       nameWidget.set("value", port.name);
                       nameWidget.set("disabled", true);
                       nameWidget.set("regExpGen", util.nameOrContextVarRegexp);
                       dom.byId("formAddPort.id").value=port.id;
                       providerWidget.set("value", port.authenticationProvider ? port.authenticationProvider : "");
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

                       var transportWidget = registry.byId("formAddPort.transports");
                       transportWidget.set("value", port.transports);

                       var portWidget = registry.byId("formAddPort.port");
                       portWidget.set("value", port.port);
                       portWidget.set("regExpGen", util.numericOrContextVarRegexp);

                       var protocols = port.protocols;
                       var typeWidget = registry.byId("formAddPort.type");

                       var store = typeWidget.store;
                       store.data.forEach(function(option){
                           registry.byId("formAddPort.protocols" + option.value).set("disabled", true);
                           registry.byId("formAddPort:fields" + option.value).domNode.style.display = "none";
                       });

                       // identify the type of port using first protocol specified in protocol field if provided
                       if ( !protocols || protocols.length == 0 || protocols[0].indexOf("AMQP") == 0)
                       {
                           typeWidget.set("value", "AMQP");
                           var amqpProtocolsWidget = registry.byId("formAddPort.protocolsAMQP");
                           var defaultProtocolsWidget = registry.byId("formAddPort.protocolsDefault");
                           var addressWidget = registry.byId("formAddPort.bindingAddress");
                           addressWidget.set("value", port.bindingAddress);

                           if (protocols)
                           {
                               amqpProtocolsWidget.set("value", protocols)
                               amqpProtocolsWidget.set("disabled", false)
                               defaultProtocolsWidget.set("checked", false);
                           }
                           else
                           {
                               defaultProtocolsWidget.set("checked", true);
                               amqpProtocolsWidget.set("disabled", true)
                           }

                           registry.byId("formAddPort.needClientAuth").set("checked", port.needClientAuth);
                           registry.byId("formAddPort.wantClientAuth").set("checked", port.wantClientAuth);
                       }
                       else if (protocols[0].indexOf("RMI") != -1)
                       {
                           var jmxProtocolsWidget = registry.byId("formAddPort.protocolsJMX");
                           jmxProtocolsWidget.set("disabled", false);
                           jmxProtocolsWidget.set("value", protocols[0]);
                           typeWidget.set("value", "JMX");
                       }
                       else if (protocols[0].indexOf("HTTP") == 0)
                       {
                           var httpProtocolsWidget = registry.byId("formAddPort.protocolsHTTP");
                           httpProtocolsWidget.set("disabled", false);
                           httpProtocolsWidget.set("value", protocols[0]);
                           typeWidget.set("value", "HTTP");
                           var addressWidget = registry.byId("formAddPort.bindingAddress");
                           addressWidget.set("value", port.bindingAddress)
                       }
                       registry.byId("formAddPort:fields" + typeWidget.value).domNode.style.display = "block";
                       typeWidget.set("disabled", true);

                       keystoreWidget.initialValue = port.keyStore;
                       truststoreWidget.initialValue = port.trustStores;
                       transportWidget.initialValue = transportWidget.value;
                       providerWidget.initialValue = providerWidget.value;

                       registry.byId("addPort").show();
                       util.applyMetadataToWidgets(registry.byId("addPort").domNode, "Port", typeWidget.get("value"));

                   });
            }
            else
            {
                // Creating new port
                var typeWidget = registry.byId("formAddPort.type");
                if (typeWidget.get("disabled"))
                {
                  typeWidget.set("disabled", false);
                }
                typeWidget.set("value", "AMQP");
                var name = registry.byId("formAddPort.name");
                name.set("disabled", false);
                editWarning.style.display = "none";
                registry.byId("addPort").show();

                util.applyMetadataToWidgets(registry.byId("addPort").domNode, "Port", "AMQP");
            }

        };

        return addPort;
    });
