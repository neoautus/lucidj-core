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

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Layouts
{
    private static TinyLog log = new TinyLog (Layouts.class);

    private static final String DEFAULT_LAYOUTS_FILE = "layouts.properties";

    public static Properties load_layout_properties (Path basedir)
    {
        Properties layout_props = new Properties ();
        InputStream is = null;

        //-------------------------------------------------------------
        // First try to load layouts.properties from alongside the Jar
        //-------------------------------------------------------------
        File layout_file = basedir.resolve (DEFAULT_LAYOUTS_FILE).toFile ();

        if (layout_file.exists())
        {
            try
            {
                is = new FileInputStream (layout_file);
                log.debug ("Using {} from file", DEFAULT_LAYOUTS_FILE);
            }
            catch (FileNotFoundException e)
            {
                log.debug ("Exception opening {}", DEFAULT_LAYOUTS_FILE, e);
            }
        }

        //--------------------------------------------------------------------
        // If layouts.properties file is not available, use embedded from Jar
        //--------------------------------------------------------------------
        if (is == null)
        {
            URL prop_url = Main.class.getClassLoader ().getResource (DEFAULT_LAYOUTS_FILE);

            if (prop_url != null)
            {
                try
                {
                    is = prop_url.openConnection().getInputStream();
                    log.debug ("Using embedded {}", DEFAULT_LAYOUTS_FILE);
                }
                catch (IOException e)
                {
                    log.debug ("Exception reading embedded {}", DEFAULT_LAYOUTS_FILE, e);
                }
            }
        }

        //-----------------------------------------
        // If we have at least one source, load it
        //-----------------------------------------
        if (is != null)
        {
            try
            {
                layout_props.load (is);
            }
            catch (IOException e)
            {
                log.debug ("Exception reading properties", e);
            }
            finally
            {
                try
                {
                    is.close();
                }
                catch (IOException ignore) {};
            }
        }
        return (layout_props);
    }

    public static boolean processLayouts ()
    {
        URI jar_uri;

        try
        {
            jar_uri = Main.class.getProtectionDomain ().getCodeSource ().getLocation ().toURI ();
        }
        catch (URISyntaxException e)
        {
            return (false);
        }

        Path jar_path = Paths.get (jar_uri);
        Path basedir_path = jar_path.getParent ();

        System.setProperty ("lucidj.kernel.jar.uri", jar_uri.toString ());
        System.setProperty ("lucidj.kernel.jar.path", jar_path.toString ());
        System.setProperty ("lucidj.basedir.path", basedir_path.toString ());

        PropertiesEx layout_map = new PropertiesEx (load_layout_properties (basedir_path));
        layout_map.setProperty ("basedir", basedir_path.toUri ().toString ());

        String layouts = layout_map.getProperty ("layouts");

        for (String layout: layouts.split ("\\s*[,]\\s*"))
        {
            Map<String, String> found_props = new HashMap<> ();
            String prefix = layout + ".";
            int prefix_len = prefix.length ();

            log.debug ("Verifying layout: {}", layout);

            for (Enumeration keys = layout_map.propertyNames (); keys.hasMoreElements (); )
            {
                String key = (String)keys.nextElement ();

                if (key.startsWith (prefix))
                {
                    try
                    {
                        File dir = new File (new URI (layout_map.getProperty (key)));
                        log.debug ("Dir {} = {} -> {}", key, layout_map.getProperty (key), dir.exists ());

                        if (dir.exists ())
                        {
                            found_props.put (key.substring (prefix_len), dir.getCanonicalPath ());
                        }
                        else
                        {
                            found_props = null;
                            break;
                        }
                    }
                    catch (URISyntaxException | IOException e)
                    {
                        found_props = null;
                        log.debug ("Exception checking {} -> {}", key, layout_map.getProperty (key), e);
                        break;
                    }
                }
            }

            if (found_props != null)
            {
                log.debug ("Layout found: {}", layout);

                // Set all found directories
                for (String key: found_props.keySet ())
                {
                    log.debug ("Setting {} -> {}", key, found_props.get (key));
                    System.setProperty (key, found_props.get (key));
                }

                String export_prefix = "export.";

                // Set all exported properties
                for (Enumeration keys = layout_map.propertyNames (); keys.hasMoreElements (); )
                {
                    String map_key = (String)keys.nextElement ();

                    if (map_key.startsWith (export_prefix))
                    {
                        String key = map_key.substring (export_prefix.length ());
                        String value = layout_map.getProperty (map_key);
                        log.debug ("Setting {} -> {}", key, value);
                        System.setProperty (key, value);
                    }
                }
                return (true);
            }
        }
        return (false);
    }
}

// EOF
