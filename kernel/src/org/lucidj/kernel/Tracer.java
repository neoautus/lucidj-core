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

import org.lucidj.kernel.shared.TinyLog;
import org.osgi.framework.*;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.startlevel.FrameworkStartLevel;

import java.io.File;

public class Tracer implements FrameworkListener, SynchronousBundleListener, ServiceListener
{
    TinyLog log = new TinyLog (new File (System.getProperty ("system.log"), "framework.log"));

    private BundleContext fw_context;
    private FrameworkStartLevel fw_startlevel;

    private Tracer (BundleContext fw_context)
    {
        this.fw_context = fw_context;
        this.fw_startlevel = fw_context.getBundle ().adapt (FrameworkStartLevel.class);
    }

    public static Tracer start (BundleContext fw_context)
    {
        Tracer tracer = new Tracer (fw_context);
        fw_context.addFrameworkListener (tracer);
        fw_context.addBundleListener (tracer);
        fw_context.addServiceListener (tracer);
        return (tracer);
    }

    private String get_state_string (int state)
    {
        switch (state)
        {
            case Bundle.INSTALLED:   return ("INSTALLED");
            case Bundle.RESOLVED:    return ("RESOLVED");
            case Bundle.STARTING:    return ("STARTING");
            case Bundle.STOPPING:    return ("STOPPING");
            case Bundle.ACTIVE:      return ("ACTIVE");
            case Bundle.UNINSTALLED: return ("UNINSTALLED");
        }
        return ("Bundle:UnknownState[" + state + "]");
    }

    private String get_event_string (int type)
    {
        switch (type)
        {
            case BundleEvent.INSTALLED:       return ("INSTALLED");
            case BundleEvent.LAZY_ACTIVATION: return ("LAZY_ACTIVATION");
            case BundleEvent.RESOLVED:        return ("RESOLVED");
            case BundleEvent.STARTED:         return ("STARTED");
            case BundleEvent.STARTING:        return ("STARTING");
            case BundleEvent.STOPPED:         return ("STOPPED");
            case BundleEvent.STOPPING:        return ("STOPPING");
            case BundleEvent.UNINSTALLED:     return ("UNINSTALLED");
            case BundleEvent.UNRESOLVED:      return ("UNRESOLVED");
            case BundleEvent.UPDATED:         return ("UPDATED");
        }
        return ("BundleEvent:UnknownType[" + type + "]");
    }

    @Override // BundleListener
    public void bundleChanged (BundleEvent bundleEvent)
    {
        Bundle bnd = bundleEvent.getBundle ();
        long bnd_id = bnd.getBundleId ();
        String bundle = "[" + bnd_id + "] " + bnd.getSymbolicName();
        String event = get_event_string (bundleEvent.getType ());
        String state = get_state_string (bnd.getState ());
        BundleStartLevel bsl = bnd.adapt (BundleStartLevel.class);
        int bundle_start_level = bsl.getStartLevel ();
        int start_level = fw_startlevel.getStartLevel ();
        String flickering =
            (bundle_start_level != start_level && start_level != 0 && bundle_start_level != 0)? "-- FLICK --": "";

        log.info ("{} <{}> state={} {}:{} {}", bundle, event, state, bundle_start_level, start_level, flickering);
    }

    private String get_fw_event_string (int type)
    {
        switch (type)
        {
            case FrameworkEvent.ERROR:                          return ("ERROR");
            case FrameworkEvent.INFO:                           return ("INFO");
            case FrameworkEvent.PACKAGES_REFRESHED:             return ("PACKAGES_REFRESHED");
            case FrameworkEvent.STARTED:                        return ("STARTED");
            case FrameworkEvent.STARTLEVEL_CHANGED:             return ("STARTLEVEL_CHANGED");
            case FrameworkEvent.STOPPED:                        return ("STOPPED");
            case FrameworkEvent.STOPPED_BOOTCLASSPATH_MODIFIED: return ("STOPPED_BOOTCLASSPATH_MODIFIED");
            case FrameworkEvent.STOPPED_UPDATE:                 return ("STOPPED_UPDATE");
            case FrameworkEvent.WAIT_TIMEDOUT:                  return ("WAIT_TIMEDOUT");
            case FrameworkEvent.WARNING:                        return ("WARNING");
        }
        return ("FrameworkEvent:UnknownType[" + type + "]");
    }

    @Override // FrameworkListener
    public void frameworkEvent (FrameworkEvent frameworkEvent)
    {
        Bundle bnd = frameworkEvent.getBundle ();
        long bnd_id = bnd.getBundleId ();
        String bundle = (bnd_id == 0)? "": bnd.toString();
        Throwable th = frameworkEvent.getThrowable ();
        String type = get_fw_event_string (frameworkEvent.getType ());
        int initial_start_level = fw_startlevel.getInitialBundleStartLevel ();
        int start_level = fw_startlevel.getStartLevel ();

        log.info ("===Framework=== {}<{}> level={}/{} {}",
            bundle, type, start_level, initial_start_level, (th == null)? "": th);
    }

    private String get_svc_event_string (int type)
    {
        switch (type)
        {
            case ServiceEvent.MODIFIED:          return ("MODIFIED");
            case ServiceEvent.MODIFIED_ENDMATCH: return ("MODIFIED_ENDMATCH");
            case ServiceEvent.REGISTERED:        return ("REGISTERED");
            case ServiceEvent.UNREGISTERING:     return ("UNREGISTERING");
        }
        return ("ServiceEvent:UnknownType[" + type + "]");
    }

    private String get_location ()
    {
        final StackTraceElement[] stackTrace = new Throwable ().getStackTrace ();

        for (int i = 2 /* skip this+serviceChanged */; i < stackTrace.length; i++)
        {
            StackTraceElement ste = stackTrace [i];
            String class_name = ste.getClassName ();

            if (!class_name.startsWith ("org.apache.felix.framework.")      // Skip framework (todo: add more fws)
                    && !class_name.equals (this.getClass ().getName ()))    // Skip ourselves
            {
                return (ste.toString ());
            }
        }
        return ("StackTraceElement:Unknown");
    }

    private void log_call_stack (String place)
    {
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        // TODO: THIS SHOULD BE TRACE LEVEL
        for (int i = stackTrace.length - 1; i > 0; i--)
        {
            log.info ("{} <STACKTRACE>\t{}", place, stackTrace [i].toString());
        }
    }

    @Override // ServiceListener
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        ServiceReference sr = serviceEvent.getServiceReference ();
        String type = get_svc_event_string (serviceEvent.getType ());
        Bundle bnd = sr.getBundle ();
        long bnd_id = bnd.getBundleId ();
        String bundle = "[" + bnd_id + "] " + bnd.getSymbolicName();
        String source = get_location ();        // TODO: THIS PROBABLY SHOULD BE DEBUG LEVEL
        Bundle[] using_bundles = sr.getUsingBundles ();

        log.info ("{} <{}> {} from {} {}",
            bundle, type, sr.toString(), source, (using_bundles == null)? "": using_bundles);
        log_call_stack (bundle);

    }

/*
    private String get_conf_event_string (int type)
    {
        switch (type)
        {
            case ConfigurationEvent.CM_DELETED:          return ("CM_DELETED");
            case ConfigurationEvent.CM_LOCATION_CHANGED: return ("CM_LOCATION_CHANGED");
            case ConfigurationEvent.CM_UPDATED:          return ("CM_UPDATED");
        }
        return ("ConfigurationEvent:Unknown");
    }

    @Override
    public void configurationEvent(ConfigurationEvent configurationEvent)
    {
        String type = get_conf_event_string (configurationEvent.getType ());
        String fpid = configurationEvent.getFactoryPid ();
        String pid = configurationEvent.getPid ();
        ServiceReference ref = configurationEvent.getReference ();
        Bundle bnd = ref.getBundle ();
        long bnd_id = bnd.getBundleId ();
        String bundle = "[" + bnd_id + "] " + bnd.getSymbolicName();

        log.info ("{} <{}> {} / {} {}", bundle, type, fpid, pid, ref);
    }
*/
}

// EOF
