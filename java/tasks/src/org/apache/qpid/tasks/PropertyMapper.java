package org.apache.qpid.tasks;

import org.apache.tools.ant.Project;
import org.apache.tools.ant.util.GlobPatternMapper;

public class PropertyMapper extends GlobPatternMapper
{

    Project _project;

    public PropertyMapper(Project project)
    {
        super();
        _project = project;
    }

    public String[] mapFileName(String sourceFileName)
    {
        String[] fixed = super.mapFileName(sourceFileName);

        if (fixed == null)
        {
            return null;
        }
        
        return new String[]{ _project.getProperty(fixed[0]) };
    }


}