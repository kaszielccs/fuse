/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.agent.download;

import org.apache.karaf.features.Feature;
import org.apache.karaf.features.Repository;
import org.fusesource.fabric.agent.mvn.Parser;
import org.fusesource.fabric.agent.utils.AgentUtils;
import org.fusesource.fabric.api.FabricService;
import org.fusesource.fabric.api.Profile;
import org.fusesource.fabric.api.Version;
import org.fusesource.fabric.utils.Files;
import org.fusesource.fabric.utils.Strings;
import org.fusesource.fabric.utils.features.FeatureUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * A helper service for downloading bundles, features and FABs from a profile or version
 */
public class ProfileDownloader {
    private static final transient Logger LOG = LoggerFactory.getLogger(ProfileDownloader.class);

    private final FabricService fabricService;
    private final File target;
    private final boolean force;
    private final ExecutorService executorService;
    private final Set<File> processedFiles = new HashSet<File>();
    private boolean stopOnFailure;
    private final Map<String,Exception> errors = new HashMap<String, Exception>();

    public ProfileDownloader(FabricService fabricService, File target, boolean force, ExecutorService executorService) {
        this.fabricService = fabricService;
        this.target = target;
        this.force = force;
        this.executorService = executorService;
    }

    /**
     * Downloads the bundles, features and FABs for all the profiles in this version
     */
    public void downloadVersion(Version version) throws Exception {
        Profile[] profiles = version.getProfiles();
        for (Profile profile : profiles) {
            if (stopOnFailure) {
                downloadProfile(profile);
            } else {
                try {
                    downloadProfile(profile);
                } catch (Exception e) {
                    String id = profile.getId();
                    errors.put(id, e);
                    LOG.error("Failed to download profile " + id + " " + e, e);
                }
            }
        }
    }

    /**
     * Downloads the bundles, features and FABs for this profile.
     */
    public void downloadProfile(Profile profile) throws Exception {
        if (!profile.isOverlay()) {
            profile = profile.getOverlay();
        }

        DownloadManager downloadManager = DownloadManagers.createDownloadManager(fabricService, profile, executorService);

        Set<String> bundles = new LinkedHashSet<String>();
        Set<Feature> features = new LinkedHashSet<Feature>();
        addMavenBundles(bundles, profile.getBundles());
        addMavenBundles(bundles, profile.getFabs());
        addFeatures(features, profile, downloadManager);

        Map<String, File> files = AgentUtils.downloadBundles(downloadManager, features, bundles,
                Collections.<String>emptySet());
        Set<Map.Entry<String, File>> entries = files.entrySet();
        for (Map.Entry<String, File> entry : entries) {
            String name = entry.getKey();
            File file = entry.getValue();
            if (processedFiles.add(file)) {
                String fileName = file.getName();
                String mvnCoords = getMavenCoords(name);

                File destFile;
                if (mvnCoords != null) {
                    Parser parser = new Parser(mvnCoords);
                    destFile = new File(target, parser.getArtifactPath());
                } else {
                    destFile = new File(target, fileName);
                }
                if (force || !destFile.exists()) {
                    LOG.info("Copying file " + name + " to :  " + destFile.getCanonicalPath());
                    Files.copy(file, destFile);
                }
            }
        }
    }


    /**
     * Returns the mvn coordinates URL from the URI string, stripping any prefix like "wrap:" or "war: " or whatnot; or return null if there is no maven URL inside the URI
     */
    public static String getMavenCoords(String bundle) {
        if (bundle.startsWith("mvn:")) {
            return bundle.substring(4);
        } else {
            int idx = bundle.indexOf(":mvn:", 1);
            if (idx > 0) {
                return bundle.substring(idx + 5);
            }
        }
        return null;
    }

    /**
     * Extracts the {@link java.net.URI}/{@link org.apache.karaf.features.Repository} map from the profile.
     */
    public Map<URI, Repository> getRepositories(Profile profile, DownloadManager downloadManager) throws Exception {
        Map<URI, Repository> repositories = new HashMap<URI, Repository>();
        for (String repositoryUrl : profile.getRepositories()) {
            if (Strings.isNotBlank(repositoryUrl)) {
                URI repoUri = new URI(repositoryUrl);
                try {
                    AgentUtils.addRepository(downloadManager, repositories, repoUri);
                } catch (Exception e) {
                    LOG.warn("Failed to add repository " + repositoryUrl + " for profile " + profile.getId() + ". " + e);
                }
            }
        }
        return repositories;
    }

    /**
     * Returns the number of files succesfully processed
     */
    public int getProcessedFileCount() {
        return processedFiles.size();
    }


    /**
     * Returns the list of profile IDs which failed
     */

    public List<String> getFailedProfileIDs() {
        return new ArrayList<String>(errors.keySet());
    }


    protected void addMavenBundles(Set<String> bundles, List<String> bundleList) {
        for (String bundle : bundleList) {
            String mvnCoords = getMavenCoords(bundle);
            if (mvnCoords != null) {
                bundles.add(mvnCoords);
            }
        }
    }
    protected void addFeatures(Set<Feature> features, Profile profile, DownloadManager downloadManager) throws Exception {
        List<String> featureNames = profile.getFeatures();
        Map<URI, Repository> repositories = getRepositories(profile, downloadManager);
        for (String featureName : featureNames) {
            Collection<Repository> repositoryCollection = repositories.values();
            Feature search = FeatureUtils.search(featureName, repositoryCollection);
            if (search == null) {
                LOG.warn("Could not find feature " + featureName
                        + " for profile " + profile.getId()
                        + " in repositories " + repositoryCollection);
            } else {
                features.add(search);
            }
        }
    }

}
