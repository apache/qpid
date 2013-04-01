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
        "dijit/form/FilteringSelect",
        "dojo/dom-style",
        "dojo/_base/lang",
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
        "dojox/form/BusyButton",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json, Memory, FilteringSelect, domStyle, lang) {

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
                    if(formValues.hasOwnProperty(propName))
                    {
                        if (propName === "type" || propName === "protocolsDefault")
                        {
                            continue;
                        }
                        else if (propName === "protocols")
                        {
                            var val = formValues[propName];
                            if (!lang.isArray(val))
                            {
                                val = [ val ];
                            }
                            newPort[ propName ] = val;
                        }
                        else if (propName === "transports")
                        {
                            var val = formValues[propName];

                            if(val === "")
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

                var needClientAuth = dijit.byId("formAddPort.needClientAuth");
                var wantClientAuth = dijit.byId("formAddPort.wantClientAuth");
                if(!needClientAuth.disabled)
                {
                    newPort.needClientAuth = needClientAuth.checked;
                }
                if(!wantClientAuth.disabled)
                {
                    newPort.wantClientAuth = wantClientAuth.checked;
                }

                return newPort;
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

                            registry.byId("formAddPort.type").on("change", function(newValue) {
                                var typeWidget = registry.byId("formAddPort.type");
                                var store = typeWidget.store;
                                store.data.forEach(function(option){
                                    registry.byId("formAddPort.protocols" + option.value).set("disabled", true);
                                    registry.byId("formAddPort:fields" + option.value).domNode.style.display = "none";
                                });

                                if ("AMQP" == newValue)
                                {
                                    registry.byId("formAddPort:fieldsClientAuth").domNode.style.display = "block";
                                    registry.byId("formAddPort.needClientAuth").set("disabled", false);
                                    registry.byId("formAddPort.wantClientAuth").set("disabled", false);
                                }
                                else
                                {
                                    registry.byId("formAddPort:fieldsClientAuth").domNode.style.display = "none";
                                    registry.byId("formAddPort.needClientAuth").set("checked", false);
                                    registry.byId("formAddPort.wantClientAuth").set("checked", false);
                                    registry.byId("formAddPort.needClientAuth").set("disabled", true);
                                    registry.byId("formAddPort.wantClientAuth").set("disabled", true);
                                }

                                registry.byId("formAddPort:fields" + newValue).domNode.style.display = "block";
                                var defaultsAMQPProtocols = registry.byId("formAddPort.protocolsDefault");
                                defaultsAMQPProtocols.set("disabled", "AMQP" != newValue)
                                var protocolsWidget = registry.byId("formAddPort.protocols" + newValue);
                                if (protocolsWidget)
                                {
                                    if ("AMQP" == newValue && defaultsAMQPProtocols.checked)
                                    {
                                        protocolsWidget.set("disabled", true);
                                    }
                                    else
                                    {
                                        protocolsWidget.set("disabled", false);
                                    }
                                }
                                var transportsWidget = registry.byId("formAddPort.transports");
                                if (transportsWidget)
                                {
                                    transportsWidget.startup();
                                }
                            });
                            theForm = registry.byId("formAddPort");

                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newPort = convertToPort(theForm.getValues());
                                    var that = this;

                                    xhr.put({url: "rest/port/"+encodeURIComponent(newPort.name), sync: true, handleAs: "json",
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

        addPort.show = function(portName, providers) {

            if (!addPort.fields)
            {
                var labelWidthValue = "300";
                addPort.fields = new dojox.layout.TableContainer( {
                    cols: 1,
                    labelWidth: labelWidthValue,
                    showLabels: true,
                    orientation: "horiz",
                    customClass: "formLabel"
                }, dom.byId("formAddPort:fields"));
                addPort.fields.startup();
                addPort.fieldsClientAuth = new dojox.layout.TableContainer( {
                    cols: 1,
                    labelWidth: labelWidthValue,
                    showLabels: true,
                    orientation: "horiz",
                    customClass: "formLabel"
                }, dom.byId("formAddPort:fieldsClientAuth"));
                addPort.fieldsClientAuth.startup();
                addPort.fieldsAMQP = new dojox.layout.TableContainer( {
                    cols: 1,
                    labelWidth: labelWidthValue,
                    showLabels: true,
                    orientation: "horiz",
                    customClass: "formLabel"
                }, dom.byId("formAddPort:fieldsAMQP"));
                addPort.fieldsAMQP.startup();
                addPort.fieldsJMX = new dojox.layout.TableContainer( {
                  cols: 1,
                  labelWidth: labelWidthValue,
                  showLabels: true,
                  orientation: "horiz",
                  customClass: "formLabel"
                }, dom.byId("formAddPort:fieldsJMX"));
                addPort.fieldsJMX.startup();
                addPort.fieldsHTTP = new dojox.layout.TableContainer( {
                  cols: 1,
                  labelWidth: labelWidthValue,
                  showLabels: true,
                  orientation: "horiz",
                  customClass: "formLabel"
                }, dom.byId("formAddPort:fieldsHTTP"));
                addPort.fieldsHTTP.startup();
            }
            registry.byId("formAddPort").reset();
            dojo.byId("formAddPort.id").value = "";

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

            if (portName)
            {
                xhr.get({
                    url: "rest/port/" + encodeURIComponent(portName),
                    handleAs: "json"
                }).then(
                   function(data){
                       var port = data[0];
                       var nameField = registry.byId("formAddPort.name");
                       nameField.set("value", port.name);
                       nameField.set("disabled", true);
                       dom.byId("formAddPort.id").value=port.id;
                       providerWidget.set("value", port.authenticationProvider ? port.authenticationProvider : "");
                       registry.byId("formAddPort.transports").set("value", port.transports ? port.transports[0] : "");
                       registry.byId("formAddPort.port").set("value", port.port);
                       var protocols = port.protocols;
                       var typeWidget = registry.byId("formAddPort.type");
                       var store = typeWidget.store;
                       store.data.forEach(function(option){
                           registry.byId("formAddPort.protocols" + option.value).set("disabled", true);
                           registry.byId("formAddPort:fields" + option.value).domNode.style.display = "none";
                       });

                       registry.byId("formAddPort.needClientAuth").set("checked", false);
                       registry.byId("formAddPort.wantClientAuth").set("checked", false);
                       registry.byId("formAddPort.needClientAuth").set("disabled", true);
                       registry.byId("formAddPort.wantClientAuth").set("disabled", true);
                       registry.byId("formAddPort:fieldsClientAuth").domNode.style.display = "none";

                       // identify the type of port using first protocol specified in protocol field if provided
                       if ( !protocols || protocols.length == 0 || protocols[0].indexOf("AMQP") == 0)
                       {
                           typeWidget.set("value", "AMQP");
                           var amqpProtocolsWidget = registry.byId("formAddPort.protocolsAMQP");
                           var defaultProtocolsWidget = registry.byId("formAddPort.protocolsDefault");
                           var addressWidget = registry.byId("formAddPort.bindingAddress");
                           addressWidget.set("value", port.bindingAddress);
                           amqpProtocolsWidget.set("disabled", false);
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

                           registry.byId("formAddPort.needClientAuth").set("disabled", false);
                           registry.byId("formAddPort.wantClientAuth").set("disabled", false);
                           registry.byId("formAddPort.needClientAuth").set("checked", port.needClientAuth);
                           registry.byId("formAddPort.wantClientAuth").set("checked", port.wantClientAuth);
                           registry.byId("formAddPort:fieldsClientAuth").domNode.style.display = "block";
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
                           httpProtocolsWidget.set("value", protocols[0])
                           typeWidget.set("value", "HTTP");
                       }
                       registry.byId("formAddPort:fields" + typeWidget.value).domNode.style.display = "block";
                       typeWidget.set("disabled", true);
                       registry.byId("addPort").show();
               });
            }
            else
            {
                var typeWidget = registry.byId("formAddPort.type");
                typeWidget.set("disabled", false);
                typeWidget.set("value", "AMQP");
                registry.byId("formAddPort.name").set("disabled", false);
                registry.byId("addPort").show();
            }
        };

        return addPort;
    });