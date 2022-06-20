package me.ugeno.betlejem.common.utils;


import org.codehaus.plexus.util.DirectoryScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Class for finding and accessing resources on local hard drive
 */
// This class may be used before logger is innitialized
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Resources {
    private static final Logger LOG = LoggerFactory.getLogger(Resources.class);

    private static final String RESOURCES_FILE = "resources.properties";

    // Private caching field
    private static String resourcesFileLocation;

    private Resources() {
        // Utility class - should not be instantiated
    }

    /**
     * The method verifies where The Test Framework was started from and returns the path to root dir of test projects
     * ("FunctionalTesting" dir used by framework to load/store resources). There are two cases for starting tests:
     * 1 - starting from Intellij (working dir = "FunctionalTesting"), 2 - starting from gradle (working dir = "FunctionalTesting/module-name")
     * Method checks if resources.properties file exists in current working directory or it's parent. resource.properties file contains
     * relative paths to resources in different modules stored as property-value pairs.
     *
     * @return path to root dir of test projects
     */
    private static String getResourcesFileLocation() {
        String workingDir = System.getProperty("user.dir");
        LOG.debug("user.dir = " + workingDir);
        File projectDirCandidate = new File(workingDir);
        File resourceFile = new File(projectDirCandidate.getAbsolutePath() + '/' + RESOURCES_FILE);
        if (resourceFile.exists()) {
            LOG.debug("Tests started from project root dir.");
        } else {
            projectDirCandidate = projectDirCandidate.getParentFile();
            resourceFile = new File(projectDirCandidate.getAbsolutePath() + '/' + RESOURCES_FILE);
            if (resourceFile.exists()) {
                LOG.debug("Tests started from Test Module = " + workingDir + ". Switching to root dir.");
            } else {
                throw new BetlejemException("Project root main directory not found.");
            }
        }
        LOG.debug("Resources file found in: " + projectDirCandidate.getAbsolutePath());
        return projectDirCandidate.getAbsolutePath();
    }

    /**
     * Returns absolute path to resource defined by given property
     */
    public static String getPath(String resourceProperty) {
        if (resourcesFileLocation == null) {
            resourcesFileLocation = getResourcesFileLocation();
        }
        Properties resourcesProperties = new Properties();
        try {
            resourcesProperties.load(new FileInputStream(resourcesFileLocation + '/' + RESOURCES_FILE));
        } catch (IOException e) {
            throw new BetlejemException(RESOURCES_FILE + " not found in " + resourcesFileLocation);
        }
        String relativeResourcePath = (String) resourcesProperties.get(resourceProperty);
        if (relativeResourcePath == null) {
            throw new BetlejemException("Path for property " + resourceProperty + " not found in " + RESOURCES_FILE);
        }
        String resourcePath = resourcesFileLocation + '/' + relativeResourcePath;
        LOG.debug("Absolute path for: " + resourceProperty + '=' + resourcePath);
        return resourcePath;
    }

    /**
     * Returns List of files under root directory that match given patterns
     */
    public static String[] findFiles(String rootDir, String... patterns) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(rootDir);
        scanner.setIncludes(patterns);
        scanner.scan();
        return scanner.getIncludedFiles();
    }

    /**
     * Returns List of directories under root directory that match given patterns
     */
    public static String[] findDirectories(String rootDir, String... patterns) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(rootDir);
        scanner.setIncludes(patterns);
        scanner.scan();
        return scanner.getIncludedDirectories();
    }
}
