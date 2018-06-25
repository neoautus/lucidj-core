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

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TinyLog
{
    public final static String[] LOG_LEVELS =
    {
        // These log level strings mirrors the OSGi log level values
        // (i.e. ERROR = 1, etc). So this array MUST NOT be changed.
        "OFF", "ERROR", "WARN", "INFO", "DEBUG"
    };

    private LogService log_service = null;
    private LogService log_service_builtin;
    private boolean use_only_builtion_log = false;
    private BundleContext context;
    private String logger_name;

    public TinyLog ()
    {
        // Tinylog level can be set using environment variable TINYLOG={ OFF|ERROR|WARN|INFO|DEBUG }
        log_service_builtin = new BuiltinLogService (getConfiguredLogLevel (logger_name));
    }

    public TinyLog (String logger_name)
    {
        this ();
        this.logger_name = logger_name;
    }

    public TinyLog (Class logger_clazz)
    {
        this (logger_clazz.getSimpleName ());
    }

    public TinyLog (File log_file)
    {
        // Log to a specific file
        log_service_builtin = new BuiltinLogService (getConfiguredLogLevel (logger_name),
                                                     log_file.getAbsolutePath());
        use_only_builtion_log = true;
    }

    public static int getConfiguredLogLevel (String logger_name)
    {
        String tinylog_env_default = System.getenv ("TINYLOG");
        String tinylog_default = System.getProperty ("tinylog",
            tinylog_env_default == null? LOG_LEVELS[LogService.LOG_INFO]: tinylog_env_default);
        String tinylog_property = System.getProperty ("tinylog_" + logger_name, tinylog_default).toUpperCase();

        if (logger_name == null)
        {
            return (LogService.LOG_INFO);
        }

        for (int level = 0; level < LOG_LEVELS.length; level++)
        {
            if (LOG_LEVELS[level].equals(tinylog_property))
            {
                return (level);
            }
        }
        return (LogService.LOG_INFO);
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
        if (use_only_builtion_log)
        {
            return (log_service_builtin);
        }

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
        return (log_service == null? log_service_builtin: log_service);
    }

    private void write_log (int level, String msg, Object... args)
    {
        for (int pos, i = 0; i < args.length && (pos = msg.indexOf ("{}")) != -1; i++)
        {
            msg = msg.substring (0, pos) + conv_str (args [i]) + msg.substring (pos + 2);
        }

        // Let's assume Throwable is always at the end of the list
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

    public void log (ServiceReference serviceReference, int i, String s, Throwable throwable)
    {
        get_log_service ().log (serviceReference, i, s, throwable);
    }

    public PrintStream newLoggingStream (OutputStream parent_stream, int log_level)
    {
        return (new PrintStream (new LoggingOutputStream (parent_stream, log_level)));
    }

    public class BuiltinLogService implements LogService
    {
        private String log_file = System.getProperty ("system.log.file", "system.log");
        private SimpleDateFormat timestamp_format_info = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss ");
        private int log_level;
        private volatile OutputStream log_stream;

        public BuiltinLogService (int log_level)
        {
            this.log_level = log_level;
        }

        public BuiltinLogService (int log_level, String log_file)
        {
            this.log_level = log_level;
            this.log_file = log_file;
            this.timestamp_format_info = new SimpleDateFormat ("yyyy-MM-dd HH:mm:ss.SSS ");
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
        public synchronized void log (ServiceReference serviceReference, int i, String s, Throwable throwable)
        {
            if (i > log_level)
            {
                return;
            }

            if (log_stream == null)
            {
                try
                {
                    log_stream = new FileOutputStream (log_file, true);
                }
                catch (Exception e)
                {
                    // TODO: LOG TO QUARANTINE
                }
            }

            if (log_stream == null)
            {
                // Keep account using op counters
                return;
            }

            String timestamp = timestamp_format_info.format (new Date ());
            String sr = (serviceReference == null) ? "" : serviceReference.toString () + ": ";
            String th = (throwable == null) ? "" : " - " + throwable.toString ();
            String logger = (logger_name == null)? "": "[" + logger_name + "] ";
            String logdata = timestamp + TinyLog.LOG_LEVELS[i] + "  " + logger + sr + s + th + "\n";

            try
            {
                log_stream.write (logdata.getBytes ());
            }
            catch (IOException e)
            {
                // TODO: LOG TO QUARANTINE

                if (log_stream != null)
                {
                    try
                    {
                        log_stream.close ();
                    }
                    catch (IOException close_silently) {};

                    log_stream = null;
                }
            }
        }
    }

    class LoggingOutputStream extends OutputStream
    {
        private byte[] line = new byte [8192];      // TODO: FIX CONCURRENT THREAD WRITE INTERLEAVING
        private int line_ptr = 0;
        private OutputStream shadow_stream;
        private int log_level = LogService.LOG_INFO;

        public LoggingOutputStream (OutputStream shadow_stream, int log_level)
        {
            super ();
            this.shadow_stream = shadow_stream;
            this.log_level = log_level;
        }

        @Override // OutputStream
        public void close ()
            throws IOException
        {
            shadow_stream.close ();
            super.close();
        }

        @Override // OutputStream
        public void flush ()
            throws IOException
        {
            shadow_stream.flush ();
        }

        private void inferring_log (int level, String message)
        {
            // If the message have the intended log level backed in,
            // we will try to get it and erase the log level marker.
            for (int i = 1; i < LOG_LEVELS.length; i++)
            {
                // Form 1: ....] LEVEL ....
                String level_str = "] " + LOG_LEVELS [i] + " ";

                if (message.contains (level_str))
                {
                    // We discovered the actual level of the message
                    level = i;

                    // Erase the level string, since it will added later
                    message = message.replace (level_str, "] ");
                    break;
                }

                // Form 2: .... LEVEL: ....
                level_str = LOG_LEVELS[i] + ": ";

                if (message.contains (level_str))
                {
                    // We discovered the actual level of the message
                    level = i;

                    // Erase the level string, since it will added later
                    message = message.replace (level_str, "");
                    break;
                }
            }

            // Log the analysed message
            log (null, level, message, null);
        }

        @Override // OutputStream
        public void write (int b)
            throws IOException
        {
            // TODO: MAKE THIS UTF-8 COMPLIANT
            if (b == '\n' || line_ptr == line.length)
            {
                if (line_ptr > 0) // Do not log empty lines
                {
                    inferring_log (log_level, new String (line, 0, line_ptr));
                }
                line_ptr = 0;
            }
            else if (b == 9 && line_ptr < line.length - 8) // Tab
            {
                for (int i = 0; i < 8; i++, line [line_ptr++] = ' ');
            }
            else if (b >= ' ') // No ctrl chars
            {
                line [line_ptr++] = (byte)b;
            }
        }
    }
}

// EOF
