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

package org.lucidj.kernel;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

public class KernelLogService implements LogService
{
    private int log_level = LogService.LOG_INFO;
    private BundleContext context;

    public KernelLogService (BundleContext context)
    {
        this.context = context;
    }

    @Override // LogService
    public void log (int i, String s)
    {
        log (null, i, s, null);
    }

    @Override // LogService
    public void log (int i, String s, Throwable throwable)
    {
        log (null, i, s, throwable);
    }

    @Override // LogService
    public void log (ServiceReference serviceReference, int i, String s)
    {
        log (serviceReference, i, s, null);
    }

    @Override // LogService
    public void log (ServiceReference serviceReference, int i, String s, Throwable throwable)
    {
        if (i > log_level)
        {
            return;
        }
        String[] levels =
        {
            "", "ERROR", "WARN", "INFO", "DEBUG"
        };
        String sr = (serviceReference == null)? ": ": serviceReference.toString() + ": ";
        String th = (throwable == null)? "": " - " + throwable.toString();
        System.out.println (levels [i] + sr + s + th);
    }

    //-----------------------------
    // A small poor man's SLF4J :)
    //-----------------------------

    private String conv_str (Object obj)
    {
        if (obj == null)
        {
            return ("null");
        }
        else if (obj instanceof Object[])
        {
            Object[] obj_list = (Object[])obj;
            String result = "";

            for (int i = 0; i < obj_list.length; i++)
            {
                if (!result.isEmpty ())
                {
                    result += ",";
                }
                result += conv_str (obj_list [i]);
            }
            return ("[" + result + "]");
        }
        return (obj.toString ());
    }

    private void write_log (int level, String msg, Object... args)
    {
        for (int i = 0; i < args.length && msg.contains ("{}"); i++)
        {
            msg = msg.replaceFirst ("\\{\\}", conv_str (args [i]));
        }

        // Let's assume it's always and the end of the list
        if (args.length > 0 && args [args.length - 1] instanceof Throwable)
        {
            log (level, msg, (Throwable)args [args.length - 1]);
        }
        else
        {
            log (level, msg);
        }
    }

    public void debug (String msg, Object... args)
    {
        write_log (LogService.LOG_DEBUG, msg, args);
    }

    public void info (String msg, Object... args)
    {
        write_log (LogService.LOG_INFO, msg, args);
    }

    public void warn (String msg, Object... args)
    {
        write_log (LogService.LOG_WARNING, msg, args);
    }

    public void error (String msg, Object... args)
    {
        write_log (LogService.LOG_ERROR, msg, args);
    }
}

// EOF
