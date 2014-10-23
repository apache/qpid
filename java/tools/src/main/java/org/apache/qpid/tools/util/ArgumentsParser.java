package org.apache.qpid.tools.util;


import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;

public class ArgumentsParser
{
    public ArgumentsParser()
    {
    }

    public <T> T parse(String[] args, Class<T> pojoClass)
    {
        T object;
        try
        {
            object = pojoClass.newInstance();
        }
        catch (Exception e)
        {
            throw  new IllegalArgumentException("Cannot instantiate object of class " + pojoClass, e);
        }

        for (String arg: args)
        {
            int pos = arg.indexOf('=');
            if (pos == -1)
            {
                throw new IllegalArgumentException("Invalid argument '" + arg + "' Argument should be specified in format <name>=<value>");
            }
            String name = arg.substring(0, pos);
            String value = arg.substring(pos + 1);

            Field field = findField(pojoClass, name);
            if (field != null)
            {
                setField(object, field, value);
            }
        }
        return object;
    }

    private Field findField(Class<?> objectClass, String name)
    {
        Field[] fields = objectClass.getDeclaredFields();

        Field field = null;
        for (int i = 0 ; i< fields.length ; i++)
        {
            if (fields[i].getName().equals(name) && !Modifier.isFinal(fields[i].getModifiers()))
            {
                field = fields[i];
                break;
            }
        }
        return field;
    }

    private void setField(Object object, Field field, String value)
    {
        Object convertedValue = convertStringToType(value, field.getType(), field.getName());

        field.setAccessible(true);

        try
        {
            field.set(object, convertedValue);
        }
        catch (IllegalAccessException e)
        {
            throw new RuntimeException("Cannot access field " + field.getName());
        }
    }

    private Object convertStringToType(String value, Class<?> fieldType, String fieldName)
    {
        Object o;
        if (fieldType == String.class)
        {
            o = value;
        }
        else  if (fieldType == boolean.class)
        {
            try
            {
                o = Boolean.parseBoolean(value);
            }
            catch(Exception e)
            {
                throw new RuntimeException("Cannot convert to boolean argument " + fieldName);
            }
        }
        else  if (fieldType == int.class)
        {
            try
            {
                o = Integer.parseInt(value);
            }
            catch(Exception e)
            {
                throw new RuntimeException("Cannot convert to int argument " + fieldName);
            }
        }
        else
        {
            throw new RuntimeException("Unsupported tye " + fieldType + " in " + fieldName);
        }
        return o;
    }

    public void usage(Class<?> objectClass, Set<String> requiredFields)
    {
        System.out.println("Supported arguments:");
        Field[] fields = objectClass.getDeclaredFields();

        Object object = null;
        try
        {
            object = objectClass.newInstance();
        }
        catch(Exception e)
        {
            // ignore any
        }

        for (int i = 0 ; i< fields.length ; i++)
        {
            Field field = fields[i];
            if (!Modifier.isFinal(field.getModifiers()))
            {
                Object defaultValue = null;
                try
                {
                    field.setAccessible(true);
                    defaultValue = field.get(object);
                }
                catch(Exception e)
                {
                    // ignore any
                }

                System.out.println("    " + field.getName()  + " ( type: "
                        + field.getType().getSimpleName().toLowerCase()
                        + (object != null ? ", default: " + defaultValue : "")
                        + (requiredFields != null && requiredFields.contains(field.getName()) ? ", mandatory" : "")
                        + ")");
            }
        }
    }
}
