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
        "qpid/common/util",
        "dojo/_base/xhr",
        "dojo/_base/declare",
        "dojo/_base/array",
        "dojo/_base/connect",
        "dojo/_base/lang",
        "dojo/dom-construct",
        "dojo/parser",
        "dojo/query",
        "dojo/store/Memory",
        "dojo/data/ObjectStore",
        "dijit/_WidgetBase",
        "dijit/registry",
        "dojo/text!common/ContextVariablesEditor.html",
        "dijit/form/Button",
        "dojox/grid/EnhancedGrid",
        "dojox/grid/enhanced/_Plugin",
        "dijit/form/Select",
        "dojox/validate/us",
        "dojox/validate/web",
        "dojo/domReady!"],
function (util, xhr, declare, array, connect, lang, domConstruct, parser, query, Memory, ObjectStore, _WidgetBase, registry, template)
 {

  return declare("qpid.common.ContextVariablesEditor", [_WidgetBase], {

    value: null,
    effectiveValues: null,
    inheritedActualValues: null,
    domNode: null,
    _grid: null,
    _addButton: null,
    _deleteButton: null,
    _filterBox: null,
    _nextGridItemId: 0,
    _dynamicInheritedContext: {},

    constructor: function(args)
    {
      this._args = args;
    },

    buildRendering: function()
    {
      this.domNode = domConstruct.create("div", {innerHTML: template});
      parser.parse(this.domNode);
    },

    postCreate: function()
    {
        this.inherited(arguments);
        var that = this;
        var gridNode = query(".grid", this.domNode)[0];
        var addButtonNode = query(".addButton", this.domNode)[0];
        var deleteButtonNode = query(".deleteButton", this.domNode)[0];
        var addButton = registry.byNode(addButtonNode);
        var deleteButton = registry.byNode(deleteButtonNode);
        var layout = [[
                      { name: "Name", field: "name", width: "40%", editable: true},
                      { name: 'Actual Value', field: 'actualValue', width: '30%', editable: true},
                      { name: 'Effective Value', field: 'effectiveValue', width: '30%', editable: false}
                    ]];
        var data = [];
        var objectStore = new dojo.data.ObjectStore({objectStore: new Memory({data:data, idProperty: "id"})});
        var grid = new dojox.grid.EnhancedGrid({
                selectionMode: "multiple",
                store: objectStore,
                singleClickEdit: true,
                structure: layout,
                autoHeight: true,
                sortFields: [{attribute: 'name', descending: false}],
                plugins: {indirectSelection: true}
                }, gridNode);
        grid.canEdit =  function(inCell, inRowIndex)
        {
            var item = grid.getItem(inRowIndex);
            return inCell.field == "actualValue" || (inCell.field=="name" && item && item["inherited"] == false);
        };

        this._grid = grid;
        this._deleteButton = deleteButton;
        this._addButtonNode = addButtonNode;

        var toggleGridButtons =  function(index)
        {
            var data = grid.selection.getSelected();
            deleteButton.set("disabled", !data || data.length==0);
        };

        connect.connect(grid.selection, 'onSelected', toggleGridButtons);
        connect.connect(grid.selection, 'onDeselected', toggleGridButtons);
        connect.connect(grid, 'onStyleRow' , this, function(row) { that._onStyleRow(row); });

        deleteButton.set("disabled", true);
        addButton.on("click", function(event) { that._newItem(); });
        deleteButton.on("click", function(event) { that._deleteSelected(); });
        grid.on("applyEdit", function(inRowIndex) { that._onEdit(inRowIndex); });
        grid.startup();
        this._filterBox = registry.byNode(query(".filter", this.domNode)[0]);
        this._filterBox.on("change", function(value) { if (value) { that._filter(value); } });
    },
    resize: function()
    {
        this._grid.render();
    },
    load: function(restUrl, data)
    {
        data = data || {};
        var actualValues = data.actualValues;
        var allEffectiveValues = data.effectiveValues;
        var inheritedActualValues = data.inheritedActualValues;
        if (!actualValues)
        {
            xhr.get(
                {
                  url: restUrl,
                  sync: true,
                  content: { actuals: true },
                  handleAs: "json",
                  load: function(data)
                  {
                    actualValues = data[0].context;
                  }
                }
            );
        }
        if (!allEffectiveValues)
        {
            xhr.get(
                {
                  url: restUrl,
                  sync: true,
                  content: { actuals: false },
                  handleAs: "json",
                  load: function(data)
                  {
                    allEffectiveValues = data[0].context;
                  }
                }
            );
        }
        if (!inheritedActualValues)
        {
            xhr.get(
                {
                  url: restUrl,
                  sync: true,
                  content: { actuals: true, inheritedActuals: true},
                  handleAs: "json",
                  load: function(data)
                  {
                    inheritedActualValues = data[0].context;
                  }
                }
            );
        }
        this.setData(actualValues, allEffectiveValues, inheritedActualValues);
    },
    loadInheritedData: function(restUrl)
    {
        var allEffectiveValues = null;
        xhr.get(
            {
              url: restUrl,
              sync: true,
              content: { actuals: false },
              handleAs: "json",
              load: function(data)
              {
                allEffectiveValues = data[0].context;
              }
            }
        );

        var inheritedActualValues = null;
        xhr.get(
            {
              url: restUrl,
              sync: true,
              content: { actuals: true, inheritedActuals: true},
              handleAs: "json",
              load: function(data)
              {
                inheritedActualValues = data[0].context;
              }
            }
        );

        this.setData({}, allEffectiveValues, inheritedActualValues);
    },
    setData: function(actualValues, allEffectiveValues, inheritedActualValues)
    {
      this.value = actualValues;
      this.effectiveValues = allEffectiveValues;
      this.inheritedActualValues = inheritedActualValues;

      var values = this._mergeValues(actualValues, allEffectiveValues, inheritedActualValues);
      this._originalValues = values;

      var grid = this._grid;
      if (grid)
      {
        // delete previous store data
        grid.store.fetch({
            onComplete:function(items,request)
            {
                if(items.length)
                {
                    array.forEach(items, function(item)
                    {
                       grid.store.deleteItem(item);
                    });
                }
            }
        });

        // add new data into grid store
        this._nextGridItemId = 0;
        for(var i=0; i<values.length; i++)
        {
          var item = values[i];
          var storeItem = {
                            id: this._nextId(),
                            name: item.name,
                            actualValue: item.actualValue,
                            effectiveValue: item.effectiveValue,
                            "inherited": item["inherited"],
                            changed: false
                          };
          grid.store.newItem(storeItem);
        }
        grid.store.save();
      }
      this._filter(this._filterBox.value);
      this._handleOnChange(actualValues);
    },
    addInheritedContext: function(object)
    {
        if (object)
        {
            var grid = this._grid;
            for(key in object)
            {
                for(var i=0;i< this._originalValues.length;i++)
                {
                    var varExists = false;
                    if (this._originalValues[i].name == key)
                    {
                        varExists = true;
                        break;
                    }
                }
                if (!varExists && !(key in this._dynamicInheritedContext))
                {
                    this._dynamicInheritedContext[key] = object[key];
                    var storeItem = {
                        id: this._nextId(),
                        name: key,
                        actualValue: object[key],
                        effectiveValue: "",
                        "inherited": true,
                        changed: false
                    };
                    grid.store.newItem(storeItem);
                    this._originalValues.push({name: key,
                                           actualValue: object[key],
                                           effectiveValue: "",
                                           "inherited": true,
                                           changed: false});
                }
            }
            grid.store.save();
            this._filter(this._filterBox.value);
        }
    },
    removeDynamicallyAddedInheritedContext: function()
    {
        if (this._dynamicInheritedContext)
        {
            var that = this;
            var grid = this._grid;
            grid.store.fetch({
                onComplete:function(items,request)
                {
                    if(items.length)
                    {
                        for(key in that._dynamicInheritedContext)
                        {
                            var item = null;
                            for(var i=0;i<items.length;i++)
                            {
                                if (items[i].name == key)
                                {
                                    item = items[i];
                                    break;
                                }
                            }
                            if (item && !item.changed)
                            {
                                grid.store.deleteItem(item);
                                that._deleteOriginalItem(item);
                            }
                        }
                        grid.store.save();
                        that._dynamicInheritedContext = {};
                    }
                }
            });
        }
    },
    destroy: function()
    {
      if (this.domNode)
      {
        this.domNode.destroy();
        this.domNode = null;
      }
      if (this._grid != null)
      {
        this._grid.destroyRecursively();
        this._grid = null;
      }
      if (this._addButton != null)
      {
        this._addButton.destroyRecursively();
        this._addButton = null;
      }
      if (this._deleteButton != null)
      {
        this._deleteButton.destroyRecursively();
        this._deleteButton = null;
      }
    },
    onChange: function(newValue){},
    _newItem: function()
    {
        var newItem = { id: this._nextId(), name: "", actualValue: "", effectiveValue: "", "inherited": false, changed: true};
        var grid = this._grid;
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
    },
    _deleteSelected: function()
    {
        var that = this;
        var grid = this._grid;
        var data = grid.selection.getSelected();
        if(data.length > 0)
        {
            array.forEach(data, function(selectedItem) {
                if (selectedItem !== null && !selectedItem["inherited"])
                {
                    grid.store.deleteItem(selectedItem);
                    that._deleteOriginalItem(selectedItem.name);
                }
            });
            grid.store.save();
            grid.selection.deselectAll();
            this._valueChanged();
        }
    },
    _deleteOriginalItem: function(key)
    {
        for(var i=0;i< this._originalValues.length;i++)
        {
            if (this._originalValues[i].name == key)
            {
                this._originalValues = this._originalValues.splice(i, 1);
                break;
            }
        }
    },
    _onEdit:function(inRowIndex)
    {
        var grid = this._grid;
        var item = grid.getItem(inRowIndex);
        var previousItems = this._originalValues;
        var previousItemActualValue = null;
        for(var i=0;i<previousItems.length;i++)
        {
            if (previousItems[i].name == item.name)
            {
                previousItemActualValue = previousItems[i].actualValue;
                break;
            }
        }

        if (item.actualValue != previousItemActualValue)
        {
            if (!item.changed)
            {
                grid.store.setValue(item, "changed", true);
                grid.store.save();
            }
        }
        else
        {
            if (item["inherited"]== true && item.changed)
            {
                grid.store.setValue(item, "changed", false);
                grid.store.save();
            }
        }
        this._valueChanged();
    },
    _onStyleRow:  function(row)
     {
        var grid = this._grid;
        var inRowIndex = row.index;
        var item = grid.getItem(inRowIndex);
        if (item && (item["inherited"] == false || item.changed))
        {
          row.customClasses += " highlightedText";
        }
        else
        {
          row.customClasses += " normalText";
        }
        grid.focus.styleRow(row);
        grid.edit.styleRow(row);
    },
    _filter: function(value)
    {
        this._grid.filter({"inherited": value});
    },
    _nextId: function()
    {
        this._nextGridItemId = this._nextGridItemId + 1;
        return this._nextGridItemId;
    },
    _valueChanged: function()
    {
        if (this._grid)
        {
            var value ={};
            var grid = this._grid;
            grid.store.fetch({
                    onComplete:function(items,request)
                    {
                        if(items.length > 0)
                        {
                            array.forEach(items, function(item)
                            {
                               if (item !== null && item.name && ((item["inherited"] && item.changed) || !item["inherited"]))
                               {
                                   value[item.name]=item.actualValue;
                               }
                            });
                        }
                    }
            });
            if (!util.equals(this.value, value))
            {
                this.value = value;
                this._handleOnChange(value);
            }
        }
    },
    _setValueAttr: function(actualValues)
    {
        this.value = actualValues;
        if (this.inheritedActualValues!=null && this.effectiveValues != null)
        {
            this.setData(this.value, this.effectiveValues, this.inheritedActualValues);
        }
    },
    _setEffectiveValuesAttr: function(effectiveValues)
    {
      this.effectiveValues = effectiveValues;
      if (this.value != null && this.inheritedActualValues !=null)
      {
        this.setData(this.value, this.effectiveValues, this.inheritedActualValues);
      }
    },
    _setInheritedActualValues: function(inheritedActualValues)
    {
      this.inheritedActualValues = inheritedActualValues;
      if (this.value!= null && this.effectiveValues != null)
      {
        this.setData(this.value, this.effectiveValues, this.inheritedActualValues);
      }
    },
    _mergeValues: function(actualValues, allEffectiveValues, inheritedActualValues)
    {
        var fields = [];

        if (allEffectiveValues)
        {
            for(var key in allEffectiveValues)
            {
                if (!actualValues || !(key in actualValues))
                {
                    var actualValue = inheritedActualValues &&  key in inheritedActualValues ? inheritedActualValues[key] : allEffectiveValues[key];
                    fields.push({name: key, actualValue: actualValue, effectiveValue:  allEffectiveValues[key], "inherited": true});
                }
            }
        }

        if (actualValues)
        {
            for(var key in actualValues)
            {
                var effectiveValue = allEffectiveValues && key in allEffectiveValues ? allEffectiveValues[key]: actualValues[key];
                fields.push({name: key, actualValue: actualValues[key], effectiveValue: effectiveValue, "inherited": false});
            }
        }
        return fields;
    },
    _handleOnChange: function(newValue)
    {
      if (!util.equals(this._lastValueReported, newValue))
      {
        this._lastValueReported = newValue;
        if(this._onChangeHandle)
        {
          this._onChangeHandle.remove();
        }
        this._onChangeHandle = this.defer(function()
        {
            this._onChangeHandle = null;
            this.onChange(newValue);
        });
      }
    }

  });
});
