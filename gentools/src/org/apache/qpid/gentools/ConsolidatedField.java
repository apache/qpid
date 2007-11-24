package org.apache.qpid.gentools;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Created by IntelliJ IDEA.
 * User: U146758
 * Date: 06-Mar-2007
 * Time: 09:22:21
 * To change this template use File | Settings | File Templates.
 */
public class ConsolidatedField
{
    private final String _name;
    private final String _type;
    private final List<String> _underlyingFields = new ArrayList<String>();
    private final Generator _generator;
    private boolean _isConsolidated;

    public ConsolidatedField(Generator generator, String name, String type)
    {
        this(generator,name,type,name,false);
    }

    public ConsolidatedField(Generator generator, String name, String type, String firstField)
    {
           this(generator,name,type,firstField,true);
    }

    public ConsolidatedField(Generator generator, String name, String type, String firstField, boolean consolidated)
    {

        _generator = generator;
        _name = name;
        _type = type;
        _isConsolidated = consolidated;
        _underlyingFields.add(firstField);

    }


    public void setConsolidated(boolean consolidated)
    {
        _isConsolidated = consolidated;
    }

    public String getName()
    {
        return _name;
    }

    public String getType()
    {
        return _type;
    }

    public String getNativeType()
    {
        return _generator.getNativeType(_type);
    }

    public String getEncodingType()
    {
        return _generator.getEncodingType(_type);
    }

    public void add(String name)
    {
        _underlyingFields.add(name);
    }

    public Collection<String> getUnderlyingFields()
    {
        return Collections.unmodifiableCollection(_underlyingFields);
    }

    public int getPosition(String fieldName)
    {
        return _underlyingFields.indexOf(fieldName);
    }

    public boolean isConsolidated()
    {
        return _isConsolidated;
    }

    public boolean isFixedSize()
    {
        return _generator.isFixedSizeType( getType() );
    }

    public int getSize()
    {
        return _generator.getTypeSize( getType() );
    }

}
