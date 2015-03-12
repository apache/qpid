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
define([
        "dojo/_base/event",
        "dojo/_base/lang",
        "dojo/_base/array",
        "dojo/dom",
        "dojo/dom-construct",
        "dojo/json",
        "dojo/parser",
        "dojo/store/Memory",
        "dojo/window",
        "dojo/on",
        "dojox/lang/functional/object",
        "dijit/registry",
        "dijit/Dialog",
        "dijit/form/Button",
        "dijit/form/FilteringSelect",
        "qpid/common/properties",
        "qpid/common/util",
        "qpid/common/metadata",
        "dojo/text!addVirtualHostNodeAndVirtualHost.html",
        "qpid/common/ContextVariablesEditor",
        "dijit/TitlePane",
        "dijit/layout/ContentPane",
        "dijit/form/Form",
        "dijit/form/CheckBox",
        "dijit/form/RadioButton",
        "dojox/form/Uploader",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
  function (event, lang, array, dom, domConstruct, json, parser, Memory, win, on, fobject, registry, Dialog, Button, FilteringSelect, properties, util, metadata, template)
  {

    var addVirtualHostNodeAndVirtualHost =
    {
      init: function()
      {
        var that=this;
        this.containerNode = domConstruct.create("div", {innerHTML: template});
        parser.parse(this.containerNode).then(function(instances) { that._postParse(); });
      },
      _postParse: function()
      {
        var that=this;
        var virtualHostNodeName = registry.byId("addVirtualHostNode.nodeName");
        virtualHostNodeName.set("regExpGen", util.nameOrContextVarRegexp);

        // Readers are HTML5
        this.reader = window.FileReader ? new FileReader() : undefined;

        this.dialog = registry.byId("addVirtualHostNodeAndVirtualHost");
        this.addButton = registry.byId("addVirtualHostNodeAndVirtualHost.addButton");
        this.cancelButton = registry.byId("addVirtualHostNodeAndVirtualHost.cancelButton");
        this.cancelButton.on("click", function(e){that._cancel(e);});
        this.addButton.on("click", function(e){that._add(e);});

        this.virtualHostNodeTypeFieldsContainer = dom.byId("addVirtualHostNode.typeFields");
        this.virtualHostNodeSelectedFileContainer = dom.byId("addVirtualHostNode.selectedFile");
        this.virtualHostNodeSelectedFileStatusContainer = dom.byId("addVirtualHostNode.selectedFileStatus");
        this.virtualHostNodeUploadFields = dom.byId("addVirtualHostNode.uploadFields");
        this.virtualHostNodeFileFields = dom.byId("addVirtualHostNode.fileFields");

        this.virtualHostNodeForm = registry.byId("addVirtualHostNode.form");
        this.virtualHostNodeType = registry.byId("addVirtualHostNode.type");
        this.virtualHostNodeFileCheck = registry.byId("addVirtualHostNode.upload");
        this.virtualHostNodeFile = registry.byId("addVirtualHostNode.file");

        this.virtualHostNodeType.set("disabled", true);

        this.virtualHostTypeFieldsContainer = dom.byId("addVirtualHost.typeFields");
        this.virtualHostForm = registry.byId("addVirtualHost.form");
        this.virtualHostType = registry.byId("addVirtualHost.type");

        this.virtualHostType.set("disabled", true);

        var supportedVirtualHostNodeTypes = metadata.getTypesForCategory("VirtualHostNode");
        supportedVirtualHostNodeTypes.sort();

        var virtualHostNodeTypeStore = util.makeTypeStore(supportedVirtualHostNodeTypes);
        this.virtualHostNodeType.set("store", virtualHostNodeTypeStore);
        this.virtualHostNodeType.set("disabled", false);
        this.virtualHostNodeType.on("change", function(type){that._vhnTypeChanged(type, that.virtualHostNodeTypeFieldsContainer, "qpid/management/virtualhostnode/");});

        this.virtualHostType.set("disabled", true);
        this.virtualHostType.on("change", function(type){that._vhTypeChanged(type, that.virtualHostTypeFieldsContainer, "qpid/management/virtualhost/");});

        if (this.reader)
        {
          this.reader.onload = function(evt) {that._vhnUploadFileComplete(evt);};
          this.reader.onerror = function(ex) {console.error("Failed to load JSON file", ex);};
          this.virtualHostNodeFile.on("change",  function(selected){that._vhnFileChanged(selected)});
          this.virtualHostNodeFileCheck.on("change",  function(selected){that._vhnFileFlagChanged(selected)});
        }
        else
        {
          // Fall back for IE8/9 which do not support FileReader
          this.virtualHostNodeFileCheck.set("disabled", "disabled");
          this.virtualHostNodeFileCheck.set("title", "Requires a more recent browser with HTML5 support");
          this.virtualHostNodeFileFields.style.display = "none";
        }

        this.virtualHostNodeUploadFields.style.display = "none";
      },
      show: function()
      {
        this.virtualHostNodeForm.reset();
        this.virtualHostNodeType.set("value", null);

        this.virtualHostForm.reset();
        this.virtualHostType.set("value", null);
        if (!this.virtualHostNodeContext)
        {
                this.virtualHostNodeContext = new qpid.common.ContextVariablesEditor({name: 'context', title: 'Context variables'});
                this.virtualHostNodeContext.placeAt(dom.byId("addVirtualHostNode.context"));
                var that = this;
                this.virtualHostNodeContext.on("change", function(value){
                    var inherited = that.virtualHostContext.inheritedActualValues;
                    var effective = that.virtualHostContext.effectiveValues;
                    var actuals = that.virtualHostContext.value;
                    for(var key in value)
                    {
                        var val = value[key];
                        if (!(key in actuals))
                        {
                            inherited[key] = val;
                            if (!(key in effective))
                            {
                                effective[key] = val.indexOf("${") == -1 ? val : "";
                            }
                        }
                    }
                    that.virtualHostContext.setData(that.virtualHostContext.value,effective,inherited);
                });
        }
        if (!this.virtualHostContext)
        {
                this.virtualHostContext = new qpid.common.ContextVariablesEditor({name: 'context', title: 'Context variables'});
                this.virtualHostContext.placeAt(dom.byId("addVirtualHost.context"));

        }

        this.virtualHostNodeContext.loadInheritedData("api/latest/broker");
        this.virtualHostContext.setData({}, this.virtualHostNodeContext.effectiveValues,this.virtualHostNodeContext.inheritedActualValues);

        this.dialog.show();
        if (!this.resizeEventRegistered)
        {
            this.resizeEventRegistered = true;
            util.resizeContentAreaAndRepositionDialog(dom.byId("addVirtualHostNodeAndVirtualHost.contentPane"), this.dialog);
        }
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
      _vhnTypeChanged: function (type, typeFieldsContainer, urlStem)
      {
        var validChildTypes = metadata.validChildTypes("VirtualHostNode", type, "VirtualHost");
        validChildTypes.sort();

        var virtualHostTypeStore = util.makeTypeStore( validChildTypes );

        this.virtualHostType.set("store", virtualHostTypeStore);
        this.virtualHostType.set("disabled", validChildTypes.length <= 1);
        if (validChildTypes.length == 1)
        {
          this.virtualHostType.set("value", validChildTypes[0]);
        }
        else
        {
          this.virtualHostType.reset();
        }

        var vhnTypeSelected =  !(type == '');
        this.virtualHostNodeUploadFields.style.display = vhnTypeSelected ? "block" : "none";

        if (!vhnTypeSelected)
        {
          this._vhnFileFlagChanged(false);
        }

        this._typeChanged(type, typeFieldsContainer, urlStem, "VirtualHostNode");
      },
      _vhTypeChanged: function (type, typeFieldsContainer, urlStem)
      {
        this._typeChanged(type, typeFieldsContainer, urlStem, "VirtualHost");
      },
      _typeChanged: function (type, typeFieldsContainer, urlStem, category)
      {
          var widgets = registry.findWidgets(typeFieldsContainer);
          array.forEach(widgets, function(item) { item.destroyRecursive();});
          domConstruct.empty(typeFieldsContainer);
          if (category)
          {
              var context = this["v" + category.substring(1) + "Context"];
              if (context)
              {
                context.removeDynamicallyAddedInheritedContext();
              }
          }
          if (type)
          {
            var that = this;
            require([urlStem + type.toLowerCase() + "/add"],
              function(typeUI)
              {
                  try
                  {
                      typeUI.show({containerNode:typeFieldsContainer, parent: that});

                      util.applyMetadataToWidgets(typeFieldsContainer,category, type);
                  }
                  catch(e)
                  {
                      console.warn(e);
                  }
              }
            );
          }
      },
      _vhnFileFlagChanged: function (selected)
      {
        this.virtualHostForm.domNode.style.display = selected ?  "none" : "block";
        this.virtualHostNodeFileFields.style.display = selected ?  "block" : "none";
        this.virtualHostType.set("required", !selected);
        this.virtualHostNodeFile.reset();
        this.virtualHostInitialConfiguration = undefined;
        this.virtualHostNodeSelectedFileContainer.innerHTML = "";
        this.virtualHostNodeSelectedFileStatusContainer.className = "";
      },
      _vhnFileChanged: function (evt)
      {
        // We only ever expect a single file
        var file = this.virtualHostNodeFile.domNode.children[0].files[0];

        this.addButton.set("disabled", true);
        this.virtualHostNodeSelectedFileContainer.innerHTML = file.name;
        this.virtualHostNodeSelectedFileStatusContainer.className = "loadingIcon";

        console.log("Beginning to read file " + file.name);
        this.reader.readAsDataURL(file);
      },
      _vhnUploadFileComplete: function(evt)
      {
        var reader = evt.target;
        var result = reader.result;
        console.log("File read complete, contents " + result);
        this.virtualHostInitialConfiguration = result;
        this.addButton.set("disabled", false);
        this.virtualHostNodeSelectedFileStatusContainer.className = "loadedIcon";
      },
      _cancel: function(e)
      {
          if (this.reader)
          {
            this.reader.abort();
          }
          this.dialog.hide();
      },
      _add: function(e)
      {
        event.stop(e);
        this._submit();
      },
      _submit: function()
      {

        var uploadVHConfig = this.virtualHostNodeFileCheck.get("checked");
        var virtualHostNodeData = undefined;

        if (uploadVHConfig && this.virtualHostNodeFile.getFileList().length > 0 && this.virtualHostNodeForm.validate())
        {
          // VH config is being uploaded
          virtualHostNodeData = this._getValues(this.virtualHostNodeForm);
          var virtualHostNodeContext = this.virtualHostNodeContext.get("value");
          if (virtualHostNodeContext)
          {
            virtualHostNodeData["context"] = virtualHostNodeContext;
          }

          // Add the loaded virtualhost configuration
          virtualHostNodeData["virtualHostInitialConfiguration"] = this.virtualHostInitialConfiguration;
        }
        else if (!uploadVHConfig && this.virtualHostNodeForm.validate() && this.virtualHostForm.validate())
        {
          virtualHostNodeData = this._getValues(this.virtualHostNodeForm);
          var virtualHostNodeContext = this.virtualHostNodeContext.get("value");
          if (virtualHostNodeContext)
          {
            virtualHostNodeData["context"] = virtualHostNodeContext;
          }

          var virtualHostData = this._getValues(this.virtualHostForm);
          var virtualHostContext = this.virtualHostContext.get("value");
          if (virtualHostContext)
          {
            virtualHostData["context"] = virtualHostContext;
          }

          //Default the VH name to be the same as the VHN name.
          virtualHostData["name"] = virtualHostNodeData["name"];

          virtualHostNodeData["virtualHostInitialConfiguration"] = json.stringify(virtualHostData)

        }
        else
        {
          alert('Form contains invalid data. Please correct first');
          return;
        }

        var that = this;
        var encodedVirtualHostNodeName = encodeURIComponent(virtualHostNodeData.name);
        util.post("api/latest/virtualhostnode/" + encodedVirtualHostNodeName, virtualHostNodeData, function(x){that.dialog.hide();});
      },
      _getValues: function (form)
      {
        var values = {};
        var contextMap = {};

        var formWidgets = form.getChildren();
        for(var i in formWidgets)
        {
            var widget = formWidgets[i];
            var value = widget.value;
            var propName = widget.name;
            if (propName && (widget.required || value ))
            {
                if (widget.contextvar)
                {
                    contextMap [propName] = String(value); // Broker requires that context values are Strings
                }
                else
                {
                    if (widget instanceof dijit.form.CheckBox)
                    {
                        values[ propName ] = widget.checked;
                    }
                    else if (widget instanceof dijit.form.RadioButton && value)
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
                    else
                    {
                        values[ propName ] = value ? value: null;
                    }

                }
            }
        }

        // One or more context variables were defined on form
        if (fobject.keys(contextMap).length > 0)
        {
            values ["context"] = contextMap;
        }
        return values;
      }
    };

    addVirtualHostNodeAndVirtualHost.init();

    return addVirtualHostNodeAndVirtualHost;
  }
);
