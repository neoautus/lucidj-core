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

package org.lucidj.bootstrap;

import java.io.File;
import java.util.Arrays;

import org.osgi.framework.*;
import org.osgi.framework.startlevel.FrameworkStartLevel;
import org.osgi.framework.wiring.BundleRevision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Scan the $FELIX_HOME/boot.d dir and process it as follows:
//
// 1) Parse every directory using the format:
//
//    <runlevel>-<action>[-human-comments]
//
//    runlevel: is the runlevel set for the bundle when starting
//    action: is one of the following
//      deploy (default) - Install AND start bundle
//      install - bundle.install()
//      start - bundle.start()
//      update - bundle.update()
//      uninstall - bundle.uninstall()
//
//    Before moving to next runlevel, all bundles are stabilized.
//
//    Ex.:
//      01-deploy-slf4j
//      10-install
//      11-start
//      20-deploy-http
//      25-install-cluster-protocols
//      30-deploy-cluster
//
public class Bootstrap implements FrameworkListener, BundleListener
{
    private final static Logger log = LoggerFactory.getLogger (Bootstrap.class);

    public static final String AUTO_DEPLOY_DIR_PROPERTY  = "felix.auto.deploy.dir";       // From AutoProcessor
    public static final String AUTO_DEPLOY_DIR_VALUE     = "bundle";                      // From AutoProcessor
    public static final String FINAL_STARTLEVEL_PROPERTY = "bootstrap.final.startlevel";
    public static final int    FINAL_STARTLEVEL_VALUE    = 100;
    public static final String SYSTEM_HOME_PROP          = "system.home";

    private BundleContext context;
    private Bundle fw_bundle;
    private FrameworkStartLevel fw_start_level;
    private File bundle_d = null;
    private int final_startlevel = FINAL_STARTLEVEL_VALUE;

    public Bootstrap (BundleContext context)
    {
        this.context = context;

        // Init framework bundle and framework start level
        fw_bundle = context.getBundle (0);
        fw_start_level = fw_bundle.adapt (FrameworkStartLevel.class);
    }

    private String get_state_string (Bundle bnd)
    {
        switch (bnd.getState ())
        {
            case Bundle.INSTALLED:   return ("INSTALLED");
            case Bundle.RESOLVED:    return (is_fragment(bnd)? "FRAGMENT": "RESOLVED");
            case Bundle.STARTING:    return ("STARTING");
            case Bundle.STOPPING:    return ("STOPPING");
            case Bundle.ACTIVE:      return ("ACTIVE");
            case Bundle.UNINSTALLED: return ("UNINSTALLED");
        }
        return ("Unknown");
    }

    private boolean valid_file (File f)
    {
        return (f != null && f.exists () && f.isFile () && f.canRead ());
    }

    private boolean is_fragment (Bundle bnd)
    {
        return ((bnd.adapt (BundleRevision.class).getTypes() & BundleRevision.TYPE_FRAGMENT) != 0);
    }

    @Override // BundleListener
    public void bundleChanged(BundleEvent bundleEvent)
    {
        if (bundleEvent.getType() == BundleEvent.UPDATED)
        {
            log.warn ((bundleEvent.getOrigin() != null)? "Bundle {} updated (origin {})": "Bundle {} updated",
                bundleEvent.getBundle (), bundleEvent.getOrigin());
        }
    }

    enum ActionCode
    {
        ACTION_UNKNOWN,
        ACTION_DEPLOY,
        ACTION_INSTALL,
        ACTION_START,
        ACTION_STARTTRANSIENT,
        ACTION_STARTPOLICY,
        ACTION_UPDATE,
        ACTION_STOP,
        ACTION_STOPTRANSIENT,
        ACTION_UNINSTALL
    };

    private int get_int_start_level (String probable_int)
    {
        try
        {
            return ((probable_int == null)? -1: Integer.parseInt (probable_int));
        }
        catch (NumberFormatException ignore)
        {
            return (-1);
        }
    }

    private int get_dir_start_level (File dir)
    {
        String dirname = dir.getName ();
        int dash = dirname.indexOf ('-');

        return (get_int_start_level (dash == -1? dirname: dirname.substring (0, dash)));
    }

    private void process_dir (File bundle_dir)
    {
        String dirname = bundle_dir.getName ();
        String[] params = dirname.split ("-", 3);
        int start_level = Integer.parseInt (params [0]);

        if (start_level == -1)
        {
            log.warn ("Start level '{}' invalid -- ignoring {}", params [0], bundle_dir.getName ());
            return;
        }

        String action = params.length > 1? params [1].toLowerCase(): "deploy";
        String description = params.length > 2? params [2]: "";

        ActionCode action_code;
        switch (action)
        {
            case "deploy":          action_code = ActionCode.ACTION_DEPLOY;         break;
            case "install":         action_code = ActionCode.ACTION_INSTALL;        break;
            case "start":           action_code = ActionCode.ACTION_START;          break;
            case "startpolicy":     action_code = ActionCode.ACTION_STARTPOLICY;    break;
            case "starttransient":  action_code = ActionCode.ACTION_STARTTRANSIENT; break;
            case "update":          action_code = ActionCode.ACTION_UPDATE;         break;
            case "stop":            action_code = ActionCode.ACTION_STOP;           break;
            case "stoptransient":   action_code = ActionCode.ACTION_STOPTRANSIENT;  break;
            case "uninstall":       action_code = ActionCode.ACTION_UNINSTALL;      break;
            default:                action_code = ActionCode.ACTION_UNKNOWN;        break;
        }

        if (action_code == ActionCode.ACTION_UNKNOWN)
        {
            log.warn ("Action '{}' is unknown -- ignoring {}", action, bundle_dir.getName ());
            return;
        }

        // Set default start level for the installed bundles
        fw_start_level.setInitialBundleStartLevel (start_level);

        File[] bundle_list = bundle_dir.listFiles();

        for (int i = 0; bundle_list != null && i < bundle_list.length; i++)
        {
            String bundle_uri = bundle_list [i].toURI ().toString ();

            try
            {
                Bundle bnd = null;

                // TODO: INSTALL FRAGMENTS LAST?
                if (action_code == ActionCode.ACTION_DEPLOY || action_code == ActionCode.ACTION_INSTALL)
                {
                    try
                    {
                        if ((bnd = context.installBundle (bundle_uri)) == null)
                        {
                            log.error ("Error installing {}", bundle_uri);
                            continue;
                        }
                    }
                    catch (BundleException e)
                    {
                        if (e.getType() == BundleException.DUPLICATE_BUNDLE_ERROR)
                        {
                            log.warn ("Bundle already installed: {}", bundle_uri);
                        }
                        else
                        {
                            log.error ("Exception installing: {}", bundle_uri, e);
                        }
                        continue;
                    }
                }
                else if ((bnd = context.getBundle (bundle_uri)) == null)
                {
                    log.error ("Action {} requires existing bundle {}", action, bundle_uri);
                    continue;
                }

                switch (action_code)
                {
                    case ACTION_DEPLOY:
                    case ACTION_START:
                    {
                        if (!is_fragment (bnd))
                        {
                            bnd.start ();
                        }
                        break;
                    }
                    case ACTION_STARTPOLICY:
                    {
                        bnd.start (Bundle.START_ACTIVATION_POLICY);
                        break;
                    }
                    case ACTION_STARTTRANSIENT:
                    {
                        bnd.start (Bundle.START_TRANSIENT);
                        break;
                    }
                    case ACTION_UPDATE:
                    {
                        bnd.update ();
                        break;
                    }
                    case ACTION_STOP:
                    {
                        bnd.stop ();
                        break;
                    }
                    case ACTION_STOPTRANSIENT:
                    {
                        bnd.stop (Bundle.STOP_TRANSIENT);
                        break;
                    }
                    case ACTION_UNINSTALL:
                    {
                        bnd.uninstall ();
                        break;
                    }
                }
            }
            catch (BundleException e)
            {
                log.error ("Exception installing bundle: {}", bundle_uri, e);
            }
        }

        if (start_level > 1)
        {
            // Just set and let the framework bring the level up
            fw_start_level.setStartLevel (start_level, (FrameworkListener[])null);
        }

        //---------
        // Summary
        //---------

        int active = 0, fragment = 0, resolved = 0, installed = 0;

        for (int i = 0; bundle_list != null && i < bundle_list.length; i++)
        {
            Bundle bnd = context.getBundle (bundle_list [i].toURI ().toString ());

            if (bnd == null)
            {
                continue;
            }

            log.info ("[{}] {} [{}] {} {} {}", dirname, bundle_list [i].getName(),
                bnd.getBundleId(), bnd.getSymbolicName(), bnd.getVersion(), get_state_string (bnd));

            switch (bnd.getState ())
            {
                case Bundle.ACTIVE:
                {
                    active++;
                    break;
                }
                case Bundle.RESOLVED:
                {
                    if (is_fragment(bnd))
                    {
                        fragment++;
                    }
                    else
                    {
                        resolved++;
                    }
                    break;
                }
                case Bundle.INSTALLED:
                {
                    installed++;
                    break;
                }
            }
        }

        log.info ("[{}] Bundle summary: level={} active={} fragment={} resolved={} installed={}",
            dirname, fw_start_level.getStartLevel(), active, fragment, resolved, installed);
    }

    public boolean start ()
    {
        // Let's guess first using felix.home if it exists
        String felix_home_dir = System.getProperty (SYSTEM_HOME_PROP);

        if (felix_home_dir != null)
        {
            // With felix.home, try to use ${felix_home}/boot.d
            bundle_d = new File (new File (felix_home_dir), "boot.d");
        }
        else // No felix.home, let's try Felix default
        {
            bundle_d = new File (AUTO_DEPLOY_DIR_VALUE);
        }

        if (!bundle_d.exists ())
        {
            log.warn ("Deploy dir '{}' not found for bootstrap",
                AUTO_DEPLOY_DIR_PROPERTY);
            return (false);
        }

        String final_startlevel_prop = System.getProperty (FINAL_STARTLEVEL_PROPERTY);

        if (final_startlevel_prop != null)
        {
            if ((final_startlevel = get_int_start_level (final_startlevel_prop)) == -1)
            {
                // An invalid number just leaves it as default
                final_startlevel = FINAL_STARTLEVEL_VALUE;
            }
        }

        // We are good to go!
        context.addFrameworkListener (this);
        context.addBundleListener (this);
        log.info ("Bootstrap directory: {}", bundle_d.getAbsolutePath ());
        return (true);
    }

    public void stop ()
    {
        context.removeFrameworkListener (this);
    }

    @Override // FrameworkListener
    public void frameworkEvent (FrameworkEvent frameworkEvent)
    {
        if (frameworkEvent.getType () == FrameworkEvent.STARTED
            || frameworkEvent.getType () == FrameworkEvent.STARTLEVEL_CHANGED)
        {
            int start_level = fw_start_level.getStartLevel ();

            if (start_level >= final_startlevel)
            {
                // No need to track framework anymore
                stop ();

                // Set default start level for the next bundles installed
                fw_start_level.setInitialBundleStartLevel (final_startlevel);
                log.info ("Bootstrap finished with start level {} / {}", final_startlevel, start_level);
                return;
            }

            log.info ("Framework start level: {}", start_level);

            //------------------------------------------------------------------
            // Locate next bundle.d start level directory > current start level
            //------------------------------------------------------------------

            File[] files = bundle_d.listFiles ();
            File target_level_dir = null;

            if (files != null)
            {
                // TODO: CHANGE SOMEDAY TO NUMERIC ORDERING
                // TODO: MERGE EQUAL START-LEVELS
                Arrays.sort (files);

                for (File file: files)
                {
                    int dir_startlevel = get_dir_start_level (file);

                    // Locate the first directory whose level is above current start level
                    if (file.isDirectory() && !file.getName ().endsWith (".jar")
                        && dir_startlevel != -1
                        && get_dir_start_level (file) > start_level
                        && dir_startlevel <= final_startlevel)
                    {
                        target_level_dir = file;
                        break;
                    }
                }
            }

            //--------------------------------------
            // Process the found bundle.d directory
            //--------------------------------------

            if (target_level_dir != null)
            {
                try
                {
                    process_dir (target_level_dir);
                }
                catch (Throwable e)
                {
                    log.error ("Exception running System Bootstrap", e);
                }
            }
            else
            {
                // Nothing more to do, set final startlevel
                fw_start_level.setStartLevel (final_startlevel, (FrameworkListener [])null);
            }
        }
    }
}

// EOF
