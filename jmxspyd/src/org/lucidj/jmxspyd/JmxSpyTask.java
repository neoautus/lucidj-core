/*
 * Copyright 2018 NEOautus Ltd. (http://neoautus.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.lucidj.jmxspyd;

import org.lucidj.admind.shared.AdmindUtil;
import org.lucidj.api.admind.Task;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.*;

public class JmxSpyTask implements Task
{
    private final static Logger log = LoggerFactory.getLogger (JmxSpyTask.class);

    private final static String CMD_LIST = "list";      // Human-friendly output
    private final static String CMD_GET = "get";        // Machine-friendly
    private final static String CMD_SET = "set";        // Machine-friendly
    private final static String CMD_INVOKE = "invoke";  // Machine-friendly

    private final static String[] valid_commands =
    {
        // Don't forget to keep this ordered!
        CMD_GET, CMD_INVOKE, CMD_LIST, CMD_SET
    };

    private BundleContext context;
    private MBeanServer mbean_server;
    private String[] args;
    private PrintStream out;
    private PrintStream err;

    public JmxSpyTask (BundleContext context, MBeanServer mbean_server,
                       InputStream in, OutputStream out, OutputStream err,
                       String name, String... options)
        throws IOException
    {
        this.context = context;
        this.mbean_server = mbean_server;
        this.out = new PrintStream (out);
        this.err = new PrintStream (err);
        this.args = AdmindUtil.decodeArgs (in);
    }

    private void list_domains ()
    {
        String[] domain_names = mbean_server.getDomains ();
        Arrays.sort (domain_names);

        out.println ("#");
        out.println ("# Available domains on " + ManagementFactory.getRuntimeMXBean ().getName ());
        out.println ("# Use 'ladmin jmx <domain>' to view domain mbeans");
        out.println ("#");

        for (String name: domain_names)
        {
            out.println ("    " + name);
        }
    }

    private String display_name (String name)
    {
        //------------------------------------------------------
        // Filter characters that must be quoted for use by JMX
        //------------------------------------------------------

        char[] mbean_bad_chars = { '\"', '\n', '\\', '*', '?' };
        boolean quote_mbean = false;

        for (int i = 0; !quote_mbean && i < mbean_bad_chars.length; i++)
        {
            quote_mbean = name.indexOf (mbean_bad_chars [i]) != -1;
        }

        if (quote_mbean)
        {
            name = ObjectName.quote (name);
        }

        //------------------------------------------------------------
        // Filter characters that we should care for use on the shell
        //------------------------------------------------------------

        // This surely will need more work....
        char[] shell_bad_chars = { ' ', '|', '<', '>', '"', '&', '$' };
        boolean quote_shell = false;

        for (int i = 0; !quote_shell && i < shell_bad_chars.length; i++)
        {
            quote_shell = name.indexOf (shell_bad_chars [i]) != -1;
        }

        if (quote_shell)
        {
            // Quoted name to make easier to just copy/paste on terminal
            return ("'" + name + "'");
        }
        return (name);
    }

    private void list_mbeans (String domain_name_str)
    {
        Set<ObjectInstance> mbeans;

        if (domain_name_str == null)
        {
            mbeans = mbean_server.queryMBeans (null, null);
        }
        else
        {
            mbeans = mbean_server.queryMBeans (null, new QueryExp ()
            {
                @Override
                public boolean apply (ObjectName name)
                {
                    return (name != null && domain_name_str.equals (name.getDomain ()));
                }

                @Override
                public void setMBeanServer (MBeanServer s)
                {
                    // Nop
                }
            });
        }

        List<ObjectInstance> object_instances = new ArrayList<> (mbeans);
        Collections.sort (object_instances, new Comparator<ObjectInstance> ()
        {
            @Override
            public int compare (ObjectInstance o1, ObjectInstance o2)
            {
                return (o1.getObjectName ().toString ().compareTo (o2.getObjectName ().toString ()));
            }
        });

        out.println ("#");
        out.println ("# Available mbeans on domain: " + domain_name_str);
        out.println ("# Use 'ladmin jmx <mbean>' to view mbean attributes and operations");
        out.println ("#");

        for (ObjectInstance mbean: object_instances)
        {
            out.println ("    " + display_name (mbean.getObjectName ().getCanonicalName ()));
        }
    }

    private void show_mbean (String object_name_str)
        throws Exception
    {
        ObjectName object_name;
        try
        {
            object_name = new ObjectName (object_name_str);
        }
        catch (MalformedObjectNameException mone)
        {
            err.println ("Invalid object name: " + object_name_str);
            return;
        }

        MBeanInfo mbeanInfo = mbean_server.getMBeanInfo (object_name);

        out.println ("#");
        out.println ("# MBean: " + display_name (object_name.getCanonicalName()));
        out.println ("# Available attributes on mbean: " + mbeanInfo.getClassName ());
        if (mbeanInfo.getDescription() != null)
        {
            out.println ("# " + mbeanInfo.getDescription());
        }
        out.println ("# Use 'ladmin jmx <mbean-attribute>' to view mbean attribute details");
        out.println ("#");

        displayAttributes (object_name, mbeanInfo);
        displayOperations (object_name, mbeanInfo);
    }

    private void show_attribute (String object_name_str, String attribute_str)
        throws Exception
    {
        ObjectName objectName;
        try
        {
            objectName = new ObjectName (object_name_str);
        }
        catch (MalformedObjectNameException mone)
        {
            err.println ("Invalid object name: " + object_name_str);
            return;
        }

        MBeanInfo mbeanInfo = mbean_server.getMBeanInfo (objectName);
        MBeanAttributeInfo[] attributes = mbeanInfo.getAttributes();
        MBeanAttributeInfo attribute = null;

        for (MBeanAttributeInfo a: attributes)
        {
            if (a.getName().equals(attribute_str))
            {
                attribute = a;
                break;
            }
        }

        if (attribute == null)
        {
            err.println ("Attribute not found: " + object_name_str);
            return;
        }

        out.println ("#");
        out.println ("# Attribute on mbean: " + mbeanInfo.getClassName ());
        if (mbeanInfo.getDescription() != null)
        {
            out.println ("# " + mbeanInfo.getDescription());
        }
        out.println ("#");

        displayAttribute (objectName, attribute);
    }

    private String parse_type (String type)
    {
        int arrays = type.lastIndexOf ('[') + 1;

        if (arrays == 0)
        {
            return (type);
        }

        String name = "imaginary";

        switch (type.charAt (arrays))
        {
            case 'I': name = "int";     break;
            case 'Z': name = "boolean"; break;
            case 'F': name = "float";   break;
            case 'J': name = "long";    break;
            case 'S': name = "short";   break;
            case 'B': name = "byte";    break;
            case 'D': name = "double";  break;
            case 'C': name = "char";    break;
            case 'L':
            {
                name = type.substring (arrays + 1, type.indexOf (';'));
                break;
            }
        }

        while (arrays-- > 0)
        {
            name += "[]";
        }
        return (name);
    }

    private String conv_str (Object obj)
    {
        if (obj == null)
        {
            return ("null");
        }
        else if (obj instanceof CompositeData)
        {
            return ("<<CompositeData not supported yet>>");
        }
        else if (obj instanceof TabularData)
        {
            return ("<<TabularData not supported yet>>");
        }
        else if (obj instanceof Object[])
        {
            Object[] obj_list = (Object[])obj;
            String result = "";

            for (int i = 0; i < obj_list.length; i++)
            {
                if (!result.isEmpty ())
                {
                    result += ", ";
                }
                result += conv_str (obj_list [i]);
            }

            return ("[ " + result + " ]");
        }

        return (obj.toString ());
    }

    private void displayAttribute (ObjectName object_name, MBeanAttributeInfo attribute)
    {
        String name = attribute.getName();
        Object value = null;

        if (attribute.isReadable ())
        {
            try
            {
                value = mbean_server.getAttribute (object_name, name);
            }
            catch (Exception e)
            {
                value = "<<" + e.toString() + ">>";
            }
        }
        else
        {
            value = "(not readable)";
        }
        out.println ();
        out.println ("Attribute: " + name);

        if (attribute.getDescription () != null)
        {
            out.println ("  Details: " + attribute.getDescription ());
        }
        out.println ("     Type: " + parse_type (attribute.getType ()) + (attribute.isWritable () ? " (Read/Write)" : " (Read-only)"));

        if (value.getClass ().isArray ())
        {
            Object[] array = (Object[])value;

            for (int i = 0; i < array.length; i++)
            {
                out.println (" Value[" + i + "]: " + conv_str (array [i]));
            }
        }
        else
        {
            out.println ("    Value: " + conv_str (value));
        }
    }

    private void displayAttributes (ObjectName object_name, MBeanInfo mbean_info)
    {
        for (MBeanAttributeInfo attribute : mbean_info.getAttributes ())
        {
            displayAttribute (object_name, attribute);
        }
    }

    private boolean is_get_set (String name)
    {
        return (name.startsWith ("is") || name.startsWith ("get") || name.startsWith ("set"));
    }

    private void displayOperations (ObjectName object_name, MBeanInfo mbean_info)
        throws IOException
    {
        int maxParams = 1;
        boolean noOperations = true;

        for (MBeanOperationInfo operation : mbean_info.getOperations ())
        {
            if (is_get_set (operation.getName()))
            {
                continue;
            }
            MBeanParameterInfo[] params = operation.getSignature();
            if (params.length > maxParams) {
                maxParams = params.length;
            }
            noOperations = false;
        }

        if (noOperations)
        {
            out.println ();
            out.println ("No operations found");
            return;
        }

        for (MBeanOperationInfo operation : mbean_info.getOperations())
        {
            String name = operation.getName ();

            if (is_get_set (name))
            {
                continue;
            }

            StringBuilder sb = new StringBuilder ();
            sb.append (name);

            for (MBeanParameterInfo param : operation.getSignature ())
            {
                sb.append (' ');
                sb.append (param.getType ());
            }
            out.println ("<OPERATION> " + sb.toString());
        }
    }

    @Override // Task
    public boolean run ()
        throws Exception
    {
        String command = CMD_LIST;
        String argument = null;

        if (args.length == 1)
        {
            int command_index = Arrays.binarySearch (valid_commands, args [0]);

            if (command_index < 0)
            {
                command = CMD_LIST;
                argument = args [0];
            }
            else
            {
                command = args [0];
            }
        }
        else if (args.length > 1)
        {
            command = args [0];
            argument = args [1];
        }

        switch (command)
        {
            case CMD_LIST:
            {
                if (argument == null)
                {
                    list_domains();
                }
                else if (argument.contains(":"))
                {
                    if (args.length == 3)
                    {
                        show_attribute (argument, args [2]);
                    }
                    else
                    {
                        show_mbean (argument);
                    }
                }
                else
                {
                    list_mbeans (argument);
                }
                break;
            }
        }
        return (true);
    }
}

// EOF
