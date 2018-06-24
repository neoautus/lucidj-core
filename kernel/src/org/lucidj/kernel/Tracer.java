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
        return ("Unknown");
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
        return ("Unknown");
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
        return ("Unknown");
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
        return ("Unknown");
    }

    @Override // ServiceListener
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        ServiceReference sr = serviceEvent.getServiceReference ();
        String type = get_svc_event_string (serviceEvent.getType ());
        Bundle bnd = sr.getBundle ();
        long bnd_id = bnd.getBundleId ();
        String bundle = "[" + bnd_id + "] " + bnd.getSymbolicName();
        Bundle[] using_bundles = sr.getUsingBundles ();

        log.info ("{} <{}> {} {}", bundle, type, sr.toString(), (using_bundles == null)? "": using_bundles);
    }
}

// EOF
