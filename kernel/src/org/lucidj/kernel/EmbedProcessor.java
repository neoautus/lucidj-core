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
import org.osgi.framework.BundleException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

public class EmbedProcessor
{
    private static TinyLog log = new TinyLog (EmbedProcessor.class);

    /**
     * The default name used for the embedded bundle directory.
     **/
    public static final String EMBEDDED_DEPLOY_DIR_VALUE = "/bundles";

    /**
     * Scan the containing jar file looking for embedded bundles,
     * and auto-install/auto-start all bundles found. The default
     * directory for embedded bundles is '/bundles'.
     *
     * NOTICE:
     *
     * @param context The system bundle context.
     **/
    public static void process (BundleContext context)
    {
        URL embedded_bundle_url = Main.class.getResource (EMBEDDED_DEPLOY_DIR_VALUE);
        Stream<Path> walk;

        // The embedded bundles are a feature intended to preconfigure
        // the framework to a minimum set of features. After the first
        // boot, we can safely bypass this. Also we allow for changes
        // on the embedded bundles after the bootstrap, so we should
        // respect any configuration done after the first start.
        // Also, this will cut the startup time a few millisecs :)
        if (context.getBundles().length > 1)
        {
            log.info ("Embedded bundles ready to start");
            return;
        }

        if (embedded_bundle_url == null)
        {
            log.info ("Embedded {} not found", EMBEDDED_DEPLOY_DIR_VALUE);
            return;
        }

        try
        {
            FileSystem fs = FileSystems.newFileSystem (embedded_bundle_url.toURI (), Collections.EMPTY_MAP);
            Path embedded_bundle_path = fs.getPath (EMBEDDED_DEPLOY_DIR_VALUE);

            if (embedded_bundle_path == null)
            {
                log.info ("Embedded {} not available", EMBEDDED_DEPLOY_DIR_VALUE);
                return;
            }
            walk = Files.walk (embedded_bundle_path, 1);
        }
        catch (URISyntaxException | IOException e)
        {
            log.error ("Exception searching bundles on: {}", embedded_bundle_url, e);
            return;
        }

        log.info ("Locating embedded bundles on {}", embedded_bundle_url);

        for (Iterator<Path> it = walk.iterator(); it.hasNext();)
        {
            Path embedded_jar = it.next ();
            String embedded_jar_filename = embedded_jar.getFileName ().toString ();

            if (embedded_jar_filename.endsWith (".jar"))
            {
                // Install and start the embedded bundles
                try
                {
                    String embedded_jar_uri = URLDecoder.decode (embedded_jar.toUri ().toString (), "UTF-8");
                    Bundle embedded_bundle = context.installBundle (embedded_jar_uri);
                    embedded_bundle.start ();
                    log.info ("Embedded bundle {} installed from {}", embedded_bundle, embedded_jar_filename);
                }
                catch (UnsupportedEncodingException | BundleException e)
                {
                    log.error ("Exception installing {}", embedded_jar.getFileName(), e);
                }
            }
        }
    }
}

// EOF
