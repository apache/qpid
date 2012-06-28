/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        "dijit/form/NumberSpinner", // required by the form
        /* dojox/ validate resources */
        "dojox/validate/us", "dojox/validate/web",
        /* basic dijit classes */
        "dijit/Dialog",
        "dijit/form/CheckBox", "dijit/form/Textarea",
        "dijit/form/FilteringSelect", "dijit/form/TextBox",
        "dijit/form/ValidationTextBox", "dijit/form/DateTextBox",
        "dijit/form/TimeTextBox", "dijit/form/Button",
        "dijit/form/RadioButton", "dijit/form/Form",
        "dijit/form/DateTextBox",
        /* basic dojox classes */
        "dojox/form/BusyButton", "dojox/form/CheckedMultiSelect",
        "dojo/domReady!"],
    function (xhr, dom, construct, win, registry, parser, array, event, json, Memory, FilteringSelect) {

        var addBinding = {};

        var node = construct.create("div", null, win.body(), "last");

        var convertToBinding = function convertToBinding(formValues)
            {
                var newBinding = {};

                newBinding.name = formValues.name;
                for(var propName in formValues)
                {
                    if(formValues.hasOwnProperty(propName))
                    {
                        if(propName === "durable")
                        {
                            if (formValues.durable[0] && formValues.durable[0] == "durable") {
                                newBinding.durable = true;
                            }
                        } else {
                            if(formValues[ propName ] !== "") {
                                newBinding[ propName ] = formValues[propName];
                            }
                        }

                    }
                }
                if(addBinding.queue) {
                    newBinding.queue = addBinding.queue;
                }
                if(addBinding.exchange) {
                    newBinding.exchange = addBinding.exchange;
                }
                return newBinding;
            };


        xhr.get({url: "addBinding.html",
                 sync: true,
                 load:  function(data) {
                            var theForm;
                            node.innerHTML = data;
                            addBinding.dialogNode = dom.byId("addBinding");
                            parser.instantiate([addBinding.dialogNode]);

                            theForm = registry.byId("formAddBinding");
                            array.forEach(theForm.getDescendants(), function(widget)
                                {
                                    if(widget.name === "type") {
                                        widget.on("change", function(isChecked) {

                                            var obj = registry.byId(widget.id + ":fields");
                                            if(obj) {
                                                if(isChecked) {
                                                    obj.domNode.style.display = "block";
                                                    obj.resize();
                                                } else {
                                                    obj.domNode.style.display = "none";
                                                    obj.resize();
                                                }
                                            }
                                        })
                                    }

                                });

                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newBinding = convertToBinding(theForm.getValues());
                                    var that = this;

                                    xhr.put({url: "rest/binding/"+encodeURIComponent(addBinding.vhost)
                                                      +"/"+encodeURIComponent(newBinding.exchange)
                                                      +"/"+encodeURIComponent(newBinding.queue)
                                                      +"/"+encodeURIComponent(newBinding.name),
                                             sync: true, handleAs: "json",
                                             headers: { "Content-Type": "application/json"},
                                             putData: json.toJson(newBinding),
                                             load: function(x) {that.success = true; },
                                             error: function(error) {that.success = false; that.failureReason = error;}});

                                    if(this.success === true)
                                    {
                                        registry.byId("addBinding").hide();
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

        addBinding.show = function(obj) {
            var that = this;

            addBinding.vhost = obj.virtualhost;
            addBinding.queue = obj.queue;
            addBinding.exchange = obj.exchange;
            registry.byId("formAddBinding").reset();



            xhr.get({url: "rest/queue/" + encodeURIComponent(obj.virtualhost) + "?depth=0",
                     handleAs: "json"}).then(
                function(data) {
                    var queues =  [];
                    for(var i=0; i < data.length; i++) {
                      queues[i] = {id: data[i].name, name: data[i].name};
                    }
                    var queueStore = new Memory({ data: queues });


                    if(that.queueChooser) {
                        that.queueChooser.destroy( false );
                    }
                    var queueDiv = dom.byId("addBinding.selectQueueDiv");
                    var input = construct.create("input", {id: "addBindingSelectQueue"}, queueDiv);

                    that.queueChooser = new FilteringSelect({ id: "addBindingSelectQueue",
                                                              name: "queue",
                                                              store: queueStore,
                                                              searchAttr: "name"}, input);

                    if(obj.queue)
                    {
                        that.queueChooser.set("value", obj.queue);
                        that.queueChooser.set("disabled", true);
                    }

                    xhr.get({url: "rest/exchange/" + encodeURIComponent(obj.virtualhost) + "?depth=0",
                                         handleAs: "json"}).then(
                        function(data) {

                            var exchanges =  [];
                            for(var i=0; i < data.length; i++) {
                              exchanges[i] = {id: data[i].name, name: data[i].name};
                            }
                            var exchangeStore = new Memory({ data: exchanges });


                            if(that.exchangeChooser) {
                                that.exchangeChooser.destroy( false );
                            }
                            var exchangeDiv = dom.byId("addBinding.selectExchangeDiv");
                            var input = construct.create("input", {id: "addBindingSelectExchange"}, exchangeDiv);

                            that.exchangeChooser = new FilteringSelect({ id: "addBindingSelectExchange",
                                                                      name: "exchange",
                                                                      store: exchangeStore,
                                                                      searchAttr: "name"}, input);

                            if(obj.exchange)
                            {
                                that.exchangeChooser.set("value", obj.exchange);
                                that.exchangeChooser.set("disabled", true);
                            }


                            registry.byId("addBinding").show();
                        });


                });


        };

        return addBinding;
    });
