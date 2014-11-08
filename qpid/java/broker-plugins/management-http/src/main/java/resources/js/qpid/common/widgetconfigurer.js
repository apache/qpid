/*
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
 */

define(["dojo/_base/xhr",
        "dojo/string",
        "dojo/query",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/dom-attr",
        "dijit/registry",
        "qpid/common/properties",
        "qpid/common/metadata",
        "dojo/text!strings.html",
        "dojo/domReady!"
        ],
  function (xhr, string, query, dom, domConstruct, domAttr, registry, properties, metadata, template)
  {
   var widgetconfigurer =
   {
     _init: function ()
     {
       var stringsTemplate = domConstruct.create("div", {innerHTML: template});
       var promptTemplateWithDefaultNode = query("[id='promptTemplateWithDefault']", stringsTemplate)[0];

       // The following will contain ${prompt} and ${default} formatted with html elements
       this.promptTemplateWithDefault = promptTemplateWithDefaultNode.innerHTML;

       domConstruct.destroy(stringsTemplate);
     },
     _processWidgetPrompt: function (widget, category, type)
     {
       var widgetName = widget.name;
       if (widgetName && (widget instanceof dijit.form.ValidationTextBox || widget instanceof dijit.form.FilteringSelect))
       {
           // If not done so already, save the prompt text specified on the widget.  We do this so if we
           // config the same widget again, we can apply the default again (which may be different if the user
           // has selected a different type within the category).
           if (typeof widget.get("qpid.originalPromptMessage") == "undefined")
           {
               widget.set("qpid.originalPromptMessage", widget.get("promptMessage"));
           }

           var promptMessage = widget.get("qpid.originalPromptMessage");
           var defaultValue = metadata.getDefaultValueForAttribute(category, type, widgetName);
           if (defaultValue)
           {
               var newPromptMessage = string.substitute(this.promptTemplateWithDefault, { 'default': defaultValue, 'prompt': promptMessage });

               if (promptMessage != newPromptMessage)
               {
                   widget.set("promptMessage", newPromptMessage);
               }
           }
       }
       else if  (widget instanceof dijit.Tooltip)
       {
         // If it is a tooltop, find the connected widget and use its name to lookup the default from the metadata.
         if (typeof widget.get("qpid.originalLabel") == "undefined")
         {
           widget.set("qpid.originalLabel", widget.get("label"));
         }

         var message = widget.get("qpid.originalLabel");
         var connectId = widget.get("connectId")[0];
         var connectWidget = registry.byId(connectId);
         if (connectWidget)
         {
           var connectWidgetName = connectWidget.get("name");
           var defaultValue = metadata.getDefaultValueForAttribute(category, type, connectWidgetName);
           if (defaultValue)
           {
             var newMessage = string.substitute(this.promptTemplateWithDefault, { 'default': defaultValue, 'prompt': message });

             if (message != newMessage)
             {
               widget.set("label", newMessage);
             }
           }
         }
       }
     },
     _processWidgetValue: function (widget, category, type, data)
     {
       var widgetName = widget.name;

       if (widgetName)
       {
         var defaultValue = metadata.getDefaultValueForAttribute(category, type, widgetName);
         var dataValue = data && widgetName in data ? data[widgetName] : null;

         // Stash the default value and initial value so we can later differentiate
         // when sending updates to the server

         if (defaultValue)
         {
           widget.defaultValue = defaultValue;
         }

         if (dataValue)
         {
           widget.initialValue = dataValue;
         }

         if (widget instanceof dijit.form.FilteringSelect || widget instanceof dojox.form.CheckedMultiSelect)
         {
           var widgetValue = dataValue ? dataValue : defaultValue;
           if (widgetValue)
           {
             widget.set("value", widgetValue);
           }
         }
         else
         {
           if (dataValue)
           {
             widget.set("value", dataValue);
           }
         }
       }
     },
     config: function (widget, category, type, data)
     {
         this._processWidgetPrompt(widget, category, type);
         this._processWidgetValue(widget, category, type, data);
     }
   };

   widgetconfigurer._init();

   return widgetconfigurer;
  });
