/**
 * Copyright (C) 2014 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dashbuilder.dataset.backend;

import java.io.*;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.dashbuilder.config.Config;
import org.dashbuilder.dataprovider.DataSetProviderRegistry;
import org.dashbuilder.dataset.def.DataSetDef;
import org.dashbuilder.dataset.def.DataSetDefRegistry;
import org.slf4j.Logger;

/**
 * This class looks for Data set definition files within an specific server directory and deploys them.
 */
@ApplicationScoped
public class DataSetDefDeployer {

    @Inject @Config("")
    protected String directory;

    @Inject @Config("3000")
    protected int pollingTime;

    @Inject
    protected DataSetDefRegistry dataSetDefRegistry;

    @Inject
    protected DataSetProviderRegistry dataSetProviderRegistry;

    @Inject
    protected DataSetDefJSONMarshaller jsonMarshaller;

    @Inject
    protected Logger log;

    protected Thread watcherThread;
    protected Map<String,DataSetDefRecord> deployed = new HashMap<String,DataSetDefRecord>();

    public String getDirectory() {
        return directory;
    }

    public boolean isRunning() {
        return !StringUtils.isBlank(directory);
    }

    @PostConstruct
    protected void init() {
        if (!StringUtils.isBlank(directory)) {
            deploy(directory);
        }
    }

    @PreDestroy
    public synchronized void stop() {
        directory = null;
    }

    public synchronized void deploy(String dir) {
        if (validateDirectory(dir)) {
            log.info("Data sets deployment directory = " + dir);
            directory = dir;
            doDeploy();

            if (pollingTime > 0) {
                watcherThread = new Thread(new Runnable() {
                    public void run() {
                        // TODO: replace by NIO WatcherService (requires upgrade to Java 7)
                        while (directory != null) {
                            try {
                                Thread.sleep(pollingTime);
                                doDeploy();
                            } catch (InterruptedException e) {
                                log.error("Data set watcher thread error.", e);
                            }
                        }
                    }
                });
                watcherThread.start();
            }
        }
        else {
            log.warn("Data sets deployment directory invalid: " + dir);
            directory = null;
        }
    }

    protected boolean validateDirectory(String dir) {
        if (StringUtils.isBlank(dir)) {
            return false;
        }
        File rootDir = new File(dir);
        if (!rootDir.exists()) {
            return false;
        }
        if (!rootDir.isDirectory()) {
            return false;
        }
        return true;
    }

    /**
     * Look into the deployment directory and processes any data set definition file found.
     */
    protected synchronized void doDeploy() {
        if (StringUtils.isBlank(directory)) return;
        File[] files = new File(directory).listFiles(_deployFilter);
        if (files == null) return;

        // Look for new data set deployments and updates
        for (File f : files) {
            try {
                // Avoid repetitions
                DataSetDefRecord r = deployed.get(f.getName());
                if (r != null && !r.isOutdated()) continue;

                // Read & Parse the definition file
                FileReader fileReader = new FileReader(f);
                String json = IOUtils.toString(fileReader);
                DataSetDef def = jsonMarshaller.fromJson(json);
                if (StringUtils.isBlank(def.getUUID())) def.setUUID(f.getName());
                def.setDefFilePath(f.getAbsolutePath());

                // Check if the data set really needs to be registered.
                DataSetDef existingDef = dataSetDefRegistry.getDataSetDef(def.getUUID());
                if (existingDef == null || !jsonMarshaller.toJsonString(existingDef).equals(jsonMarshaller.toJsonString(def))) {

                    // Register the data set
                    deployed.put(f.getName(), new DataSetDefRecord(def, f));
                    if (r == null) {
                        dataSetDefRegistry.registerDataSetDef(def, "---", "deploy(" + def.getUUID() + ")");
                                log.info("Data set deployed: " + def.getUUID());
                    } else {
                        dataSetDefRegistry.registerDataSetDef(def, "---", "redeploy(" + def.getUUID() + ")");
                                log.info("Data set re-deployed: " + def.getUUID());
                    }
                }
                else {
                    deployed.put(f.getName(), new DataSetDefRecord(existingDef, f));
                    log.info("Data set found: " + def.getUUID());
                }

            }
            catch (Exception e) {
                log.error("Error parsing the data set definition file: " + f.getName(), e);
            }
        }
        // Look for data set removals
        files = new File(directory).listFiles(_undeployFilter);
        if (files != null) {
            for (File f : files) {
                if (f.delete()) {
                    DataSetDefRecord r = deployed.remove(f.getName());
                    if (r == null) continue;

                    dataSetDefRegistry.removeDataSetDef(r.def.getUUID(), "---", "undeploy(" + r.def.getUUID() + ")");
                    log.info("Data set removed: " + r.def.getUUID());
                }
            }
        }
    }

    FilenameFilter _deployFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(".dset");
        }
    };

    FilenameFilter _undeployFilter = new FilenameFilter() {
        public boolean accept(File dir, String name) {
            return name.endsWith(".undeploy");
        }
    };

    protected class DataSetDefRecord {

        DataSetDef def;
        File defFile;
        long regTime;

        DataSetDefRecord(DataSetDef def, File f) {
            this.def = def;
            this.regTime = new Date().getTime();
            this.defFile = f;
        }

        boolean isOutdated() {
            return defFile.lastModified() > regTime;
        }
    }
}
