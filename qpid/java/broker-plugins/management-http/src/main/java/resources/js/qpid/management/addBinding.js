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
define(["dojo/_base/connect",
        "dojo/_base/xhr",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/_base/window",
        "dijit/registry",
        "dojo/parser",
        "dojo/_base/array",
        "dojo/_base/event",
        'dojo/_base/json',
        "dojo/_base/lang",
        "dojo/_base/declare",
        "dojo/store/Memory",
        "dijit/form/FilteringSelect",
        "qpid/common/util",
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
        "dojox/grid/EnhancedGrid",
        "dojo/data/ObjectStore",
        "dojo/domReady!"],
    function (connect, xhr, dom, construct, win, registry, parser, array, event, json, lang, declare, Memory, FilteringSelect, util) {

        var noLocalValues = new Memory({
            data: [
                {name:"true", id:true},
                {name:"false", id:false}
            ]
        });

        var xMatchValues  = new Memory({
             data: [
                 {name:"all", id:"all"},
                 {name:"any", id:"any"}
             ]
        });

        var defaultBindingArguments = [
                                          {id: 0, name:"x-filter-jms-selector", value: null},
                                          {id: 1, name:"x-qpid-no-local", value: null}
                                      ];

        var GridWidgetProxy = declare("qpid.dojox.grid.cells.GridWidgetProxy", dojox.grid.cells._Widget, {
            createWidget: function(inNode, inDatum, inRowIndex)
            {
                var WidgetClass = this.widgetClass;
                var widgetProperties = this.getWidgetProps(inDatum);
                var getWidgetProperties = widgetProperties.getWidgetProperties;
                if (typeof getWidgetProperties == "function")
                {
                    var item = this.grid.getItem(inRowIndex);
                    if (item)
                    {
                        var additionalWidgetProperties = getWidgetProperties(inDatum, inRowIndex, item);
                        if (additionalWidgetProperties)
                        {
                            WidgetClass = additionalWidgetProperties.widgetClass;
                            for(var prop in additionalWidgetProperties)
                            {
                                if(additionalWidgetProperties.hasOwnProperty(prop) && !widgetProperties[prop])
                                {
                                    widgetProperties[prop] = additionalWidgetProperties[ prop ];
                                }
                            }
                        }
                    }
                }
                var widget = new WidgetClass(widgetProperties, inNode);
                return widget;
            },
            getValue: function(inRowIndex)
            {
                if (this.widget)
                {
                    return this.widget.get('value');
                }
                return null;
            },
            _finish: function(inRowIndex)
            {
                if (this.widget)
                {
                    this.inherited(arguments);
                    this.widget.destroyRecursive();
                    this.widget = null;
                }
            }
        });

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

                addBinding.bindingArgumentsGrid.store.fetch({
                      onComplete:function(items,request)
                      {
                          if(items.length)
                          {
                              array.forEach(items, function(item)
                              {
                                 if (item && item.name && item.value)
                                 {
                                    var bindingArguments = newBinding.arguments;
                                    if (!bindingArguments)
                                    {
                                        bindingArguments = {};
                                        newBinding.arguments = bindingArguments;
                                    }
                                    bindingArguments[item.name]=item.value;
                                 }
                              });
                          }
                      }
                });
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

                            var argumentsGridNode = dom.byId("formAddbinding.bindingArguments");
                            var objectStore = new dojo.data.ObjectStore({objectStore: new Memory({data:lang.clone(defaultBindingArguments), idProperty: "id"})});

                            var layout = [[
                                  { name: "Argument Name", field: "name", width: "50%", editable: true },
                                  { name: 'Argument Value', field: 'value', width: '50%', editable: true, type: GridWidgetProxy,
                                    widgetProps: {
                                       getWidgetProperties: function(inDatum, inRowIndex, item)
                                       {
                                            if (item.name == "x-qpid-no-local")
                                            {
                                                return {
                                                   labelAttr: "name",
                                                   searchAttr: "id",
                                                   selectOnClick: false,
                                                   query: { id: "*"},
                                                   required: false,
                                                   store: noLocalValues,
                                                   widgetClass: dijit.form.FilteringSelect
                                                };
                                            }
                                            else if (item.name && item.name.toLowerCase() == "x-match")
                                            {
                                                 return {
                                                    labelAttr: "name",
                                                    searchAttr: "id",
                                                    selectOnClick: false,
                                                    query: { id: "*"},
                                                    required: false,
                                                    store: xMatchValues,
                                                    widgetClass: dijit.form.FilteringSelect
                                                 };
                                            }
                                            return {widgetClass: dijit.form.TextBox };
                                       }
                                    }
                                  }
                                ]];

                            var grid = new dojox.grid.EnhancedGrid({
                                    selectionMode: "multiple",
                                    store: objectStore,
                                    singleClickEdit: true,
                                    structure: layout,
                                    autoHeight: true,
                                    plugins: {indirectSelection: true}
                                    }, argumentsGridNode);
                            grid.startup();

                            addBinding.bindingArgumentsGrid = grid;
                            addBinding.idGenerator = 1;
                            var addArgumentButton = registry.byId("formAddbinding.addArgumentButton");
                            var deleteArgumentButton = registry.byId("formAddbinding.deleteArgumentButton");

                            var toggleGridButtons =  function(index)
                            {
                                var data = grid.selection.getSelected();
                                deleteArgumentButton.set("disabled", !data || data.length==0);
                            };
                            connect.connect(grid.selection, 'onSelected', toggleGridButtons);
                            connect.connect(grid.selection, 'onDeselected', toggleGridButtons);
                            deleteArgumentButton.set("disabled", true);

                            addArgumentButton.on("click",
                                function(event)
                                {
                                    addBinding.idGenerator = addBinding.idGenerator + 1;
                                    var newItem = {id:addBinding.idGenerator, name: "", value: ""};
                                    grid.store.newItem(newItem);
                                    grid.store.save();
                                    grid.store.fetch(
                                    {
                                          onComplete:function(items,request)
                                          {
                                              var rowIndex = items.length - 1;
                                              window.setTimeout(function()
                                              {
                                                  grid.focus.setFocusIndex(rowIndex, 1 );
                                              },10);
                                          }
                                    });
                                }
                            );

                            deleteArgumentButton.on("click",
                                function(event)
                                {
                                    var data = grid.selection.getSelected();
                                    if(data.length)
                                    {
                                        array.forEach(data, function(selectedItem) {
                                            if (selectedItem !== null)
                                            {
                                                grid.store.deleteItem(selectedItem);
                                            }
                                        });
                                        grid.store.save();
                                    }
                                }
                            );

                            theForm.on("submit", function(e) {

                                event.stop(e);
                                if(theForm.validate()){

                                    var newBinding = convertToBinding(theForm.getValues());
                                    var that = this;

                                    xhr.put({url: "api/latest/binding/"+encodeURIComponent(addBinding.vhostnode)
                                                      +"/"+encodeURIComponent(addBinding.vhost)
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
                                        util.xhrErrorHandler(this.failureReason);
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

            addBinding.vhostnode = obj.virtualhostnode;
            addBinding.vhost = obj.virtualhost;
            addBinding.queue = obj.queue;
            addBinding.exchange = obj.exchange;
            registry.byId("formAddBinding").reset();

            var grid = addBinding.bindingArgumentsGrid;
            grid.store.fetch({
                  onComplete:function(items,request)
                  {
                      if(items.length)
                      {
                          array.forEach(items, function(item)
                          {
                             if (item !== null)
                             {
                                 grid.store.deleteItem(item);
                             }
                          });
                      }
                  }
            });
            array.forEach(lang.clone(defaultBindingArguments), function(item) {grid.store.newItem(item); });
            grid.store.save();

            xhr.get({url: "api/latest/queue/" + encodeURIComponent(obj.virtualhostnode) + "/" + encodeURIComponent(obj.virtualhost) + "?depth=0",
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
                                                              searchAttr: "name",
                                                              promptMessage: "Name of the queue",
                                                              title: "Select the name of the queue"}, input);

                    if(obj.queue)
                    {
                        that.queueChooser.set("value", obj.queue);
                        that.queueChooser.set("disabled", true);
                    }

                    xhr.get({url: "api/latest/exchange/" + encodeURIComponent(obj.virtualhostnode) + "/" + encodeURIComponent(obj.virtualhost) + "?depth=0",
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
                                                                      searchAttr: "name",
                                                                      promptMessage: "Name of the exchange",
                                                                      title: "Select the name of the exchange"}, input);

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
