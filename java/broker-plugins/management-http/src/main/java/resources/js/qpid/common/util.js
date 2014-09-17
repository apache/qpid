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
        "dojo/_base/array",
        "dojo/_base/event",
        "dojo/_base/lang",
        "dojo/json",
        "dojo/dom-construct",
        "dojo/dom-geometry",
        "dojo/dom-style",
        "dojo/_base/window",
        "dojo/query",
        "dojo/parser",
        "dojo/store/Memory",
        "dojox/html/entities",
        "qpid/common/metadata",
        "qpid/common/widgetconfigurer",
        "dijit/registry",
        "dijit/TitlePane",
        "dijit/Dialog",
        "dijit/form/Form",
        "dijit/form/Button",
        "dijit/form/RadioButton",
        "dijit/form/CheckBox",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dojox/layout/TableContainer",
        "dijit/layout/ContentPane",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"
        ],
       function (xhr, array, event, lang, json, dom, geometry, domStyle, win, query, parser, Memory, entities, metadata, widgetconfigurer, registry) {
           var util = {};
           if (Array.isArray) {
               util.isArray = function (object) {
                   return Array.isArray(object);
               };
           } else {
               util.isArray = function (object) {
                   return object instanceof Array;
               };
           }

           util.flattenStatistics = function (data) {
               var attrName, stats, propName, theList;
               for(attrName in data) {
                   if(data.hasOwnProperty(attrName)) {
                       if(attrName == "statistics") {
                           stats = data.statistics;
                           for(propName in stats) {
                               if(stats.hasOwnProperty( propName )) {
                                   data[ propName ] = stats[ propName ];
                               }
                           }
                       } else if(data[ attrName ] instanceof Array) {
                           theList = data[ attrName ];

                           for(var i=0; i < theList.length; i++) {
                               util.flattenStatistics( theList[i] );
                           }
                       }
                   }
               }
           };

           util.isReservedExchangeName = function(exchangeName)
           {
               return exchangeName == null || exchangeName == "" || "<<default>>" == exchangeName || exchangeName.indexOf("amq.") == 0 || exchangeName.indexOf("qpid.") == 0;
           };

           util.deleteGridSelections = function(updater, grid, url, confirmationMessageStart, idParam)
           {
               var data = grid.selection.getSelected();
               var success = false;
               if(data.length)
               {
                   var confirmationMessage = null;
                   if (data.length == 1)
                   {
                       confirmationMessage = confirmationMessageStart + " '" + data[0].name + "'?";
                   }
                   else
                   {
                       var names = '';
                       for(var i = 0; i<data.length; i++)
                       {
                           if (names)
                           {
                               names += ', ';
                           }
                           names += "\""+ data[i].name + "\"";
                       }
                       confirmationMessage = confirmationMessageStart + "s " + names + "?";
                   }
                   if(confirm(confirmationMessage))
                   {
                       var i, queryParam;
                       for(i = 0; i<data.length; i++)
                       {
                           if(queryParam)
                           {
                               queryParam += "&";
                           }
                           else
                           {
                               queryParam = "?";
                           }
                           queryParam += ( idParam || "id" ) + "=" + encodeURIComponent(data[i].id);
                       }
                       var query = url + queryParam;
                       var failureReason = "";
                       xhr.del({url: query, sync: true, handleAs: "json"}).then(
                           function(data)
                           {
                               success = true;
                               grid.selection.deselectAll();
                               if (updater)
                               {
                                 updater.update();
                               }
                           },
                           function(error) {success = false; failureReason = error;});
                       if(!success )
                       {
                           util.xhrErrorHandler(failureReason);
                       }
                   }
               }
               return success;
           }

           util.isProviderManagingUsers = function(type)
           {
               return (type === "PlainPasswordFile" || type === "Base64MD5PasswordFile" || type === "SCRAM-SHA-1" || type === "SCRAM-SHA-256");
           };

           util.showSetAttributesDialog = function(attributeWidgetFactories, data, putURL, dialogTitle, category, type, appendNameToUrl)
           {
              var layout = new dojox.layout.TableContainer({
                   cols: 1,
                   "labelWidth": "300",
                   showLabels: true,
                   orientation: "horiz",
                   customClass: "formLabel"
              });
              var submitButton = new dijit.form.Button({label: "Submit", type: "submit"});
              var form = new dijit.form.Form();

              var dialogContent = dom.create("div");
              var dialogContentArea = dom.create("div", {"style": {width: 600}});
              var dialogActionBar = dom.create("div", { "class": "dijitDialogPaneActionBar"} );
              dialogContent.appendChild(dialogContentArea);
              dialogContent.appendChild(dialogActionBar);
              dialogContentArea.appendChild(layout.domNode)
              dialogActionBar.appendChild(submitButton.domNode);
              form.domNode.appendChild(dialogContent);

              var widgets = {};
              var requiredFor ={};
              var groups = {};
              for(var i in attributeWidgetFactories)
              {
                 var attributeWidgetFactory = attributeWidgetFactories[i];
                 var widget = attributeWidgetFactory.createWidget(data);
                 var name = attributeWidgetFactory.name ? attributeWidgetFactory.name : widget.name;
                 widgets[name] = widget;
                 var dotPos = name.indexOf(".");
                 if (dotPos == -1)
                 {
                   if (widget instanceof dijit.layout.ContentPane)
                   {
                     dialogContentArea.appendChild(widget.domNode);
                   }
                   else
                   {
                     layout.addChild(widget);
                   }
                 }
                 else
                 {
                   var groupName = name.substring(0, dotPos);
                   var groupFieldContainer = null;
                   if (groups.hasOwnProperty(groupName))
                   {
                       groupFieldContainer = groups[groupName];
                   }
                   else
                   {
                     groupFieldContainer = new dojox.layout.TableContainer({
                           cols: 1,
                           "labelWidth": "300",
                           showLabels: true,
                           orientation: "horiz",
                           customClass: "formLabel"
                      });
                     groups[groupName] = groupFieldContainer;
                     var groupTitle = attributeWidgetFactory.groupName ? attributeWidgetFactory.groupName :
                                         groupName.charAt(0).toUpperCase() + groupName.slice(1);
                     var panel = new dijit.TitlePane({title: groupTitle, content: groupFieldContainer.domNode});
                     dialogContentArea.appendChild(dom.create("br"));
                     dialogContentArea.appendChild(panel.domNode);
                   }
                   groupFieldContainer.addChild(widget);
                 }
                 if (attributeWidgetFactory.hasOwnProperty("requiredFor") && !data[name])
                 {
                   requiredFor[attributeWidgetFactory.requiredFor] = widget;
                 }
              }

              this.applyMetadataToWidgets(dialogContent, category, type);

              // add onchange handler to set required property for dependent widget
              for(var widgetName in requiredFor)
              {
                var dependent = requiredFor[widgetName];
                var widget = widgets[widgetName];
                if (widget)
                {
                  widget.dependent = dependent;
                  widget.on("change", function(newValue){
                    this.dependent.set("required", newValue != "");
                  });
                }
              }
              var setAttributesDialog = new dijit.Dialog({
                 title: dialogTitle,
                 content: form
             });
             form.on("submit", function(e)
             {
               event.stop(e);
               try
               {
                 if(form.validate())
                 {
                   var values = {};
                   var formWidgets = form.getDescendants();
                   for(var i in formWidgets)
                   {
                       var widget = formWidgets[i];
                       var value = widget.value;
                       var propName = widget.name;
                       if (propName && !widget.disabled){
                           if ((widget instanceof dijit.form.CheckBox || widget instanceof dijit.form.RadioButton)) {
                               if (widget.checked != widget.initialValue) {
                                   values[ propName ] = widget.checked;
                               }
                           } else if (value != widget.initialValue) {
                               values[ propName ] = value ? value: null;
                           }
                       }
                   }

                     var that = this;
                     var url = putURL;
                     if (appendNameToUrl){
                       url = url + "/" + encodeURIComponent(values["name"]);
                     }
                     xhr.put({url: url , sync: true, handleAs: "json",
                              headers: { "Content-Type": "application/json"},
                              putData: json.stringify(values),
                              load: function(x) {that.success = true; },
                              error: function(error) {that.success = false; that.failureReason = error;}});
                     if(this.success === true)
                     {
                         setAttributesDialog.destroy();
                     }
                     else
                     {
                         util.xhrErrorHandler(this.failureReason);

                     }
                     return false;
                 }
                 else
                 {
                     alert('Form contains invalid data.  Please correct first');
                     return false;
                 }
               }
               catch(e)
               {
                  alert("Unexpected exception:" + e.message);
                  return false;
               }
             });
             form.connectChildren(true);
             setAttributesDialog.startup();
             var formWidgets = form.getDescendants();
             var aproximateHeight = 0;
             for(var i in formWidgets){
                 var widget = formWidgets[i];
                 var propName = widget.name;
                 if (propName) {
                     if ((widget instanceof dijit.form.CheckBox || widget instanceof dijit.form.RadioButton)) {
                         widget.initialValue = widget.checked;
                     } else {
                         widget.initialValue = widget.value;
                     }
                     aproximateHeight += 30;
                 }
             }
             dialogContentArea.style.overflow= "auto";
             dialogContentArea.style.height = "300";
             setAttributesDialog.on("hide", function(e){setAttributesDialog.destroy();});
             setAttributesDialog.show();
           };

           util.findAllWidgets = function(root)
           {
               return query("[widgetid]", root).map(registry.byNode).filter(function(w){ return w;});
           };

           util.xhrErrorHandler = function(error)
           {
             var fallback = "Unexpected error - see server logs";
             var statusCodeNode = dojo.byId("errorDialog.statusCode");
             var errorMessageNode = dojo.byId("errorDialog.errorMessage");
             var userMustReauth = false;

             if (error)
             {
               if (error.hasOwnProperty("status"))
               {
                 var hasMessage = error.hasOwnProperty("message");
                 var message;

                 if (error.status == 0)
                 {
                   message = "Unable to contact the Broker";
                 }
                 else if (error.status == 401)
                 {
                   message = "Authentication required";
                   userMustReauth = true;
                 }
                 else if (error.status == 403)
                 {
                   message =  "Access Forbidden";
                 }
                 else
                 {
                   message = hasMessage ? error.message : fallback;

                   // Try for a more detail error sent by the Broker as json
                   if (error.hasOwnProperty("responseText"))
                   {
                     try
                     {
                       var errorObj = json.parse(error.responseText);
                       message = errorObj.hasOwnProperty("errorMessage") ? errorObj.errorMessage : errorMessageNode;
                     }
                     catch (e)
                     {
                       // Ignore
                     }
                   }
                 }

                 errorMessageNode.innerHTML = entities.encode(message ? message : fallback);
                 statusCodeNode.innerHTML =  entities.encode(String(error.status));

                 dojo.byId("errorDialog.advice.retry").style.display = userMustReauth ? "none" : "block";
                 dojo.byId("errorDialog.advice.reconnect").style.display = userMustReauth ? "block" : "none";

                 domStyle.set(registry.byId("errorDialog.button.cancel").domNode, 'display', userMustReauth ? "none" : "block");
                 domStyle.set(registry.byId("errorDialog.button.relogin").domNode, 'display', userMustReauth ? "block" : "none");

               }
               else
               {
                 statusCodeNode.innerHTML = "";
                 errorMessageNode.innerHTML = fallback;
               }

               var dialog = dijit.byId("errorDialog");
               if (!dialog.open)
               {
                 dialog.show();
               }
             }
           };

           util.sendRequest = function (url, method, attributes, sync)
           {
               var success = false;
               var failureReason = "";
               var syncRequired = sync == undefined ? true : sync;
               if (method == "POST" || method == "PUT")
               {
                 xhr.put({
                   url: url,
                   sync: syncRequired,
                   handleAs: "json",
                   headers: { "Content-Type": "application/json"},
                   putData: json.stringify(attributes),
                   load: function(x) {success = true; },
                   error: function(error) {success = false; failureReason = error;}
                 });
               }
               else if (method == "DELETE")
               {
                 xhr.del({url: url, sync: syncRequired, handleAs: "json"}).then(
                       function(data) { success = true; },
                       function(error) {success = false; failureReason = error;}
                 );
               }

               if (syncRequired && !success)
               {
                   util.xhrErrorHandler(failureReason);
               }
               return success;
           }

           util.equals = function(object1, object2)
           {
             if (object1 && object2)
             {
               if (typeof object1 != typeof object2)
               {
                 return false;
               }
               else
               {
                 if (object1 instanceof Array || typeof object1 == "array")
                 {
                   if (object1.length != object2.length)
                   {
                     return false;
                   }

                   for (var i = 0, l=object1.length; i < l; i++)
                   {
                     var item = object1[i];
                     if (item && (item instanceof Array  || typeof item == "array" || item instanceof Object))
                     {
                       if (!this.equals(item, object2[i]))
                       {
                         return false;
                       }
                     }
                     else if (item != object2[i])
                     {
                         return false;
                     }
                   }

                   return true;
                 }
                 else if (object1 instanceof Object)
                 {
                   for (propName in object1)
                   {
                       if (object1.hasOwnProperty(propName) != object2.hasOwnProperty(propName))
                       {
                           return false;
                       }
                       else if (typeof object1[propName] != typeof object2[propName])
                       {
                           return false;
                       }
                   }

                   for(propName in object2)
                   {
                       var object1Prop = object1[propName];
                       var object2Prop = object2[propName];

                       if (object2.hasOwnProperty(propName) != object1.hasOwnProperty(propName))
                       {
                           return false;
                       }
                       else if (typeof object1Prop != typeof object2Prop)
                       {
                           return false;
                       }

                       if(!object2.hasOwnProperty(propName))
                       {
                         // skip functions
                         continue;
                       }

                       if (object1Prop && (object1Prop instanceof Array || typeof object1Prop == "array" || object1Prop instanceof Object))
                       {
                         if (!this.equals(object1Prop, object2Prop))
                         {
                           return false;
                         }
                       }
                       else if(object1Prop != object2Prop)
                       {
                          return false;
                       }
                   }
                   return true;
                 }
               }
             }
             return object1 === object2;
           }

           util.parseHtmlIntoDiv = function(containerNode, htmlTemplateLocation)
           {
                xhr.get({url: htmlTemplateLocation,
                                  sync: true,
                                  load:  function(template) {
                                    containerNode.innerHTML = template;
                                    parser.parse(containerNode);
                                  }});
           }
           util.buildUI = function(containerNode, parent, htmlTemplateLocation, fieldNames, obj)
           {
                this.parseHtmlIntoDiv(containerNode, htmlTemplateLocation);
                if (fieldNames && obj)
                {
                    for(var i=0; i<fieldNames.length;i++)
                    {
                       var fieldName = fieldNames[i];
                       obj[fieldName]= query("." + fieldName, containerNode)[0];
                    }
                }
           }

           util.buildEditUI = function(containerNode, htmlTemplateLocation, fieldNamePrefix, fieldNames, data)
           {
               this.parseHtmlIntoDiv(containerNode, htmlTemplateLocation);
               if (fieldNames)
               {
                   for(var i = 0; i < fieldNames.length; i++)
                   {
                     var fieldName = fieldNames[i];
                     var widget = registry.byId(fieldNamePrefix + fieldName);
                     widget.set("value", data[fieldName]);
                   }
               }
           }

           util.updateUI = function(data, fieldNames, obj)
           {
             for(var i=0; i<fieldNames.length;i++)
             {
               var fieldName = fieldNames[i];
               var value = data[fieldName];
               obj[fieldName].innerHTML= (value == undefined || value == null) ? "" : entities.encode(String(value));
             }
           }

           util.applyMetadataToWidgets = function(domRoot, category, type)
           {
             var widgets = util.findAllWidgets(domRoot);
             array.forEach(widgets,
               function (widget)
               {
                 widgetconfigurer.config(widget, category, type);
               });
           }

           util.getFormWidgetValues = function (form, initialData)
           {
               var values = {};
               var formWidgets = form.getChildren();
               for(var i in formWidgets)
               {
                   var widget = formWidgets[i];
                   var value = widget.value;
                   var propName = widget.name;
                   if (propName && (widget.required || value ))
                   {
                       if (widget.excluded)
                       {
                          continue;
                       }
                       if (widget.contextvar)
                       {
                         var context = values["context"];
                         if (!context)
                         {
                            context = {};
                            values["context"] = context;
                         }
                         context[propName]=String(value);
                       }
                       else if (widget instanceof dijit.form.RadioButton)
                       {
                           if (widget.checked)
                           {
                               var currentValue = values[propName];
                               if (currentValue)
                               {
                                   if (lang.isArray(currentValue))
                                   {
                                       currentValue.push(value)
                                   }
                                   else
                                   {
                                       values[ propName ] = [currentValue, value];
                                   }
                               }
                               else
                               {
                                   values[ propName ] = value;
                               }
                           }
                       }
                       else if (widget instanceof dijit.form.CheckBox)
                       {
                           values[ propName ] = widget.checked;
                       }
                       else
                       {
                           if (widget.get("type") == "password")
                           {
                                if (value)
                                {
                                    values[ propName ] = value;
                                }
                           }
                           else
                           {
                              values[ propName ] = value ? value: null;
                           }
                       }
                   }
               }
               if (initialData)
               {
                for(var propName in values)
                {
                     if (values[propName] == initialData[propName])
                     {
                        delete values[propName];
                     }
                }
               }
               return values;
           }

           util.updateUpdatableStore = function(updatableStore, data)
           {
               var currentRowCount = updatableStore.grid.rowCount;
               updatableStore.grid.domNode.style.display = data ? "block" : "none";
               updatableStore.update(data || []);
               if (data)
               {
                   if (currentRowCount == 0 && data.length == 1)
                   {
                       // grid with a single row is not rendering properly after being hidden
                       // force rendering
                       updatableStore.grid.render();
                   }
               }
           }

           util.makeTypeStore = function (types)
           {
               var typeData = [];
               for (var i = 0; i < types.length; i++) {
                   var type = types[i];
                   typeData.push({id: type, name: type});
               }
               return new Memory({ data: typeData });
           }

           util.setMultiSelectOptions = function(multiSelectWidget, options)
           {
               util.addMultiSelectOptions(multiSelectWidget, options, true);
           }

           util.addMultiSelectOptions = function(multiSelectWidget, options, clearExistingOptions)
           {
               if (clearExistingOptions)
               {
                   var children = multiSelectWidget.children;
                   var initialLength = children.length;
                   for (var i = initialLength - 1; i >= 0 ; i--)
                   {
                       var child = children.item(i);
                       multiSelectWidget.removeChild(child);
                   }
               }
               for (var i = 0; i < options.length; i++)
               {
                   // construct new option for list
                   var newOption = win.doc.createElement('option');
                   var value = options[i];
                   newOption.innerHTML = value;
                   newOption.value = value;

                   // add new option to list
                   multiSelectWidget.appendChild(newOption);
               }
           }

           var singleContextVarRegexp = "(\\${[\\w+\\.\\-:]+})";

           util.numericOrContextVarRegexp = function(constraints)
           {
             return "^(\\d+)|" + singleContextVarRegexp + "$";
           }

           util.nameOrContextVarRegexp = function(constraints)
           {
             return "^(\\w+)|" + singleContextVarRegexp + "$";
           }

           util.jdbcUrlOrContextVarRegexp = function(constraints)
           {
             return "^(jdbc:.*:.*)|" + singleContextVarRegexp + "$";
           }

           util.nodeAddressOrContextVarRegexp = function(constraints)
           {
             return "^(([0-9a-zA-Z.-_]|::)+:[0-9]{1,5})|" + singleContextVarRegexp + "$";
           }

           return util;
       });
