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

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;

public class TinyLog
{
    private LogService log_service = null;
    private LogService log_service_stdout = new StdoutLogService (LogService.LOG_DEBUG);
    private BundleContext context;
    private String logger_name;

    public TinyLog (String logger_name)
    {
        this.logger_name = logger_name;
    }

    public TinyLog (Class logger_clazz)
    {
        this (logger_clazz.getSimpleName());
    }

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

    @SuppressWarnings ("unchecked")
    private LogService get_log_service ()
    {
        // Try to obtain a bundle context
        if (context == null)
        {
            Bundle bundle = FrameworkUtil.getBundle (this.getClass ());
            context = bundle == null? null: bundle.getBundleContext ();
        }
        // Check, we may not have a context yet
        if (context != null)
        {
            // Validate the service
            if (log_service != null)
            {
                try
                {
                    // LogService still valid?
                    FrameworkUtil.getBundle (log_service.getClass ());
                }
                catch (IllegalStateException oops)
                {
                    log_service = null;
                }
            }
            // Try to retrieve a new log service
            if (log_service == null)
            {
                ServiceReference ref = context.getServiceReference (LogService.class.getName ());

                if (ref != null)
                {
                    log_service = (LogService)context.getService (ref);
                }
            }
        }
        // If we don't have any service available, fall back to stdout logging
        return (log_service == null? log_service_stdout: log_service);
    }

    private void write_log (int level, String msg, Object... args)
    {
        for (int pos, i = 0; i < args.length && (pos = msg.indexOf ("{}")) != -1; i++)
        {
            msg = msg.substring (0, pos) + conv_str (args [i]) + msg.substring (pos + 2);
        }

        // Let's assume it's always and the end of the list
        if (args.length > 0 && args [args.length - 1] instanceof Throwable)
        {
            get_log_service().log (level, msg, (Throwable)args [args.length - 1]);
        }
        else
        {
            get_log_service().log (level, msg);
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

    public class StdoutLogService implements LogService
    {
        private int log_level;

        public StdoutLogService (int log_level)
        {
            this.log_level = log_level;
        }

        @Override // LogService
        public void log (int i, String s)
        {
            log(null, i, s, null);
        }

        @Override // LogService
        public void log (int i, String s, Throwable throwable)
        {
            log(null, i, s, throwable);
        }

        @Override // LogService
        public void log (ServiceReference serviceReference, int i, String s)
        {
            log(serviceReference, i, s, null);
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
            String sr = (serviceReference == null) ? ": " : serviceReference.toString() + ": ";
            String th = (throwable == null) ? "" : " - " + throwable.toString();
            System.out.println(levels[i] + sr + s + th);
        }
    }
}

// EOF
