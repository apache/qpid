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
        "dojox/html/entities",
        "dojo/_base/array",
        "dojo/_base/event",
        "dojo/_base/lang",
        "dojo/_base/window",
        "dojo/dom",
        "dojo/dom-construct",
        "dijit/registry",
        "dojo/parser",
        'dojo/json',
        "dojo/query",
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "qpid/common/util",
        "dojo/text!editVirtualHostNode.html",
        "qpid/common/ContextVariablesEditor",
        "dijit/Dialog",
        "dijit/form/CheckBox",
        "dijit/form/FilteringSelect",
        "dijit/form/ValidationTextBox",
        "dijit/form/Button",
        "dijit/form/Form",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (xhr, entities, array, event, lang, win, dom, domConstruct, registry, parser, json, query, Memory, ObjectStore, util, template)
  {
    var virtualHostNodeEditor =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode);
        this.typeFieldsContainer = dom.byId("editVirtualHostNode.typeFields");
        this.dialog = registry.byId("editVirtualHostNodeDialog");
        this.saveButton = registry.byId("editVirtualHostNode.saveButton");
        this.cancelButton = registry.byId("editVirtualHostNode.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.saveButton.on("click", function(e){that._save(e);});
        this.name = registry.byId("editVirtualHostNode.name");
        this.form = registry.byId("editVirtualHostNodeForm");
        this.form.on("submit", function(){return false;});
      },
      show: function(effectiveData)
      {
        var that=this;
        if (!this.context)
        {
         this.context = new qpid.common.ContextVariablesEditor({name: 'context', title: 'Context variables'});
         this.context.placeAt(dom.byId("editVirtualHostNode.context"));
        }
        this.query = "api/latest/virtualhostnode/" + encodeURIComponent(effectiveData.name);
        this.dialog.set("title", "Edit Virtual Host Node - " + entities.encode(String(effectiveData.name)));
        xhr.get(
            {
              url: this.query,
              sync: true,
              content: { actuals: true },
              handleAs: "json",
              load: function(data)
              {
                that._show(data[0], effectiveData);
              }
            }
        );
      },
      destroy: function()
      {
        if (this.dialog)
        {
            this.dialog.destroyRecursive();
            this.dialog = null;
        }

        if (this.containerNode)
        {
            domConstruct.destroy(this.containerNode);
            this.containerNode = null;
        }
      },
      _cancel: function(e)
      {
          this.dialog.hide();
      },
      _save: function(e)
      {
          event.stop(e);
          if(this.form.validate())
          {
              var data = util.getFormWidgetValues(this.form, this.initialData);
              var context = this.context.get("value");
              if (context && !util.equals(context, this.initialData.context))
              {
                data["context"] = context;
              }
              var success = false,failureReason=null;
              xhr.put({
                  url: this.query,
                  sync: true,
                  handleAs: "json",
                  headers: { "Content-Type": "application/json"},
                  putData: json.stringify(data),
                  load: function(x) {success = true; },
                  error: function(error) {success = false; failureReason = error;}
              });

              if(success === true)
              {
                  this.dialog.hide();
              }
              else
              {
                  util.xhrErrorHandler(failureReason);
              }
          }
          else
          {
              alert('Form contains invalid data.  Please correct first');
          }
      },
      _show:function(actualData, effectiveData)
      {
          this.initialData = actualData;
          this.name.set("value", actualData.name);
          this.context.load( this.query, {actualValues: actualData.context, effectiveValues: effectiveData.context});

          var that = this;

          var widgets = registry.findWidgets(this.typeFieldsContainer);
          array.forEach(widgets, function(item) { item.destroyRecursive();});
          domConstruct.empty(this.typeFieldsContainer);

          require(["qpid/management/virtualhostnode/" + actualData.type.toLowerCase() + "/edit"],
             function(TypeUI)
             {
                try
                {
                    TypeUI.show({containerNode:that.typeFieldsContainer, parent: that, data: actualData, effectiveData: effectiveData});
                    that.form.connectChildren();

                    util.applyMetadataToWidgets(that.allFieldsContainer, "VirtualHostNode", actualData.type);
                }
                catch(e)
                {
                    if (console && console.warn )
                    {
                        console.warn(e);
                    }
                }
             }
          );

          this.dialog.startup();
          this.dialog.show();
          if (!this.resizeEventRegistered)
          {
            this.resizeEventRegistered = true;
            util.resizeContentAreaAndRepositionDialog(dom.byId("editVirtualHostNode.contentPane"), this.dialog);
          }
      }
    };

    virtualHostNodeEditor.init();

    return virtualHostNodeEditor;
  }
);
