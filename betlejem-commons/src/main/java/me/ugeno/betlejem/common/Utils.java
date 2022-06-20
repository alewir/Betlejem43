package me.ugeno.betlejem.common;

import com.google.common.base.Strings;
import me.ugeno.betlejem.common.utils.BetlejemException;
import me.ugeno.betlejem.common.utils.Value;
import org.apache.log4j.Logger;
import org.custommonkey.xmlunit.SimpleNamespaceContext;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.XpathEngine;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class Utils {
    private static final Logger logger = Logger.getLogger(Utils.class);

    private static final Map<String, String> nameSpaceMap = new HashMap<>();

    static {
        nameSpace("");
    }

    private Utils() {
        // Utility class - should not be instantiated
    }

    /**
     * Creates Document object from given XML.
     */
    private static Document doc(String xml) {
        try {
            String xmlCleaned = removeEmptyNameSpace(xml);
            logger.debug("XML content:\n" + xmlCleaned);
            return XMLUnit.buildControlDocument(xmlCleaned);
        } catch (Exception e) {
            throw new BetlejemException("Building XML document failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Need to get rid of it because it messes up xpath processing
     * See www.xml.com/pub/a/2004/02/25/quanda.html for an explanation
     */
    private static String removeEmptyNameSpace(String xmlInitial, String quote) {
        String xml = xmlInitial;
        while (true) {
            int start = xml.indexOf(" xmlns=" + quote);
            if (start < 0) {
                return xml;
            }
            if (start >= 0) {
                int end = xml.indexOf(quote, start + " xmlns=".length() + 1);
                if (end < 0) {
                    return xml;
                }
                xml = xml.substring(0, start) + xml.substring(end + 1);
            }
        }
    }

    /**
     * Removes empty namespaces.
     *
     * @see #removeEmptyNameSpace
     */
    private static String removeEmptyNameSpace(String xmlInitial) {
        String xml = xmlInitial;
        xml = removeEmptyNameSpace(xml, "'");
        return removeEmptyNameSpace(xml, "\"");
    }

    /**
     * Searches for element according to given XPath.
     *
     * @param xPathExpression to follow in search.
     * @param xmlFragment     where the search is done.
     * @return null if not found or text content of found element.
     */
    public static String getByXPath(String xPathExpression, String xmlFragment) {
        XpathEngine engine = XMLUnit.newXpathEngine();
        engine.setNamespaceContext(new SimpleNamespaceContext(nameSpaceMap));
        String found;
        try {
            found = engine.evaluate(xPathExpression, doc(xmlFragment));
            logger.info(xPathExpression + " = " + found);
            return found;
        } catch (XpathException e) {
            throw new BetlejemException("XPath search failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * @param xPathToElement XPath to find proper tag
     * @param xmlFragment    XML document to perform searching on
     * @param attribute      of the tag found by xPathToElement
     * @return value of the attribute within found tag
     */
    public static List<String> getAttributeListByXPath(String xPathToElement, String xmlFragment, String attribute) {
        String found;
        NodeList matchingNodes = getNodesByXPath(xPathToElement, xmlFragment);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < matchingNodes.getLength(); i++) {
            NamedNodeMap attributes = matchingNodes.item(i).getAttributes();
            if (attributes != null) {
                found = attributes.getNamedItem(attribute).getTextContent();
                result.add(found);
                logger.info(xPathToElement + '[' + attribute + "] = " + found);
            } else {
                logger.info(xPathToElement + " has no attributes.");
            }
        }
        return result;
    }

    public static NodeList getNodesByXPath(String xPathToElement, String xmlFragment) {
        XpathEngine engine = XMLUnit.newXpathEngine();
        engine.setNamespaceContext(new SimpleNamespaceContext(nameSpaceMap));
        try {
            return engine.getMatchingNodes(xPathToElement, doc(xmlFragment));
        } catch (XpathException e) {
            throw new BetlejemException("XPath search failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void nameSpace(String prefix) {
        nameSpaceMap.put(prefix, "urn:" + prefix);
        XMLUnit.setXpathNamespaceContext(new SimpleNamespaceContext(nameSpaceMap));
    }

    /**
     * Generates random numbers with given length = number of digits (may contain leading '0' characters).
     *
     * @param length of string containing random number.
     * @return generated random number of given length.
     */
    public static String random(int length) {
        return Value.gen(Value.DIGITS, length);
    }

    /**
     * Store property in test-execution-properties file.
     */
    public static void storeTestExecutionProperty(String key, String value, String propertiesFilePath) throws IOException {
        logger.info("(storing " + key + '=' + value + " in " + propertiesFilePath + ')');

        // Create properties file if does not exist
        File propertiesFile = new File(propertiesFilePath);

        if (!propertiesFile.exists()) {
            if (!propertiesFile.createNewFile()) {
                logger.warn("Could not create " + propertiesFilePath + " file.");
            }
        }

        // Store given property
        Properties antProperties = new Properties();
        antProperties.load(new FileInputStream(propertiesFile));
        antProperties.setProperty(key, value);
        antProperties.store(new FileOutputStream(propertiesFile), null);
    }

    /**
     * Loads Configuration class using fully qualified class name given as parameter.
     * The method does SYSTEM.EXIT on failure.
     *
     * @param configClassEnvKey environmental variable key
     * @param type              expected config class type
     * @param <T>               expected config class type
     */
    public static <T> T loadClassFromEnv(String configClassEnvKey, Class<T> type) {
        String configClassName = System.getenv(configClassEnvKey);
        System.out.println("Loading configuration class for env. variable:" + configClassEnvKey + '=' + configClassName);
        if (configClassName == null) {
            System.err.println("Tests require setting environment variable: " + configClassEnvKey);
        } else {
            try {
                @SuppressWarnings("unchecked")
                Class<Object> configClass = (Class<Object>) Class.forName(configClassName);
                if (type.isAssignableFrom(configClass)) {
                    return type.cast(configClass.newInstance());
                } else {
                    System.err.println(configClassName + " is not instance of " + type.getSimpleName() + ". Correct the class implementation.");
                }
            } catch (Exception e) {
                System.err.println(e.getMessage() + " - correct the environment variable " + configClassEnvKey);
            }
        }

        throw new BetlejemException("ERROR: Couldn't load config class. Stopping test framework...");
    }

    /**
     * @return current month English name.
     */
    public static String getCurrentMonthName() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM", Locale.US);
        return sdf.format(Calendar.getInstance(Locale.US).getTime());
    }

    /**
     * Method formats date with given offset
     *
     * @param offsetDays (how many days have to be added)
     * @param dateFormat (Simple Date Format)
     * @return day time with given offset
     */
    public static String getDateWithDaysOffset(int offsetDays, String dateFormat) {
        return getDateWithDaysOffset(offsetDays, dateFormat, new Date());
    }

    /**
     * Method formats date with given offset
     *
     * @param offsetDays (how many days have to be added)
     * @param dateFormat (Simple Date Format)
     * @return day time with given offset
     */
    public static String getDateWithDaysOffset(int offsetDays, String dateFormat, Date inputDate) {
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
        Calendar cal = Calendar.getInstance(Locale.US);
        try {
            cal.setTime(sdf.parse(sdf.format(inputDate)));
        } catch (ParseException e) {
            throw new BetlejemException("Time Parsing is not working in method setDate()");
        }
        cal.add(Calendar.DATE, offsetDays);
        return sdf.format(cal.getTime());
    }

    /**
     * Method formats date with given offset
     *
     * @param offsetMonths (how many days have to be added)
     * @param dateFormat   (Simple Date Format)
     * @return day time with given offset
     */
    public static String getDateWithMonthsOffset(int offsetMonths, String dateFormat) {
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat, Locale.US);
        Calendar cal = Calendar.getInstance();
        try {
            cal.setTime(sdf.parse(sdf.format(new Date())));
        } catch (ParseException e) {
            throw new BetlejemException("Time Parsing is not working in method setDate()");
        }
        cal.add(Calendar.MONTH, offsetMonths);
        return sdf.format(cal.getTime());
    }

    /**
     * Method change date format
     *
     * @param oldDate       (old date, ex. 12 NOV 2010)
     * @param oldDateFormat (old date format, ex. dd MMM yyyy)
     * @param newDateFormat (new date format, ex. MM/dd/yyyy)
     * @return date in new format
     */
    public static String changeDateFormat(String oldDate, String oldDateFormat, String newDateFormat) throws ParseException {
        DateFormat originalFormat = new SimpleDateFormat(oldDateFormat, Locale.US);
        DateFormat targetFormat = new SimpleDateFormat(newDateFormat);
        Date dateObj = originalFormat.parse(oldDate);
        return targetFormat.format(dateObj);
    }

    /**
     * Sleeps for given time and prints detailed information about it.
     *
     * @param milliseconds delay time
     */
    public static void delayNextOperations(int milliseconds) {
        delayNextOperations(milliseconds, true);
    }

    /**
     * @param minutes            wait minutes
     * @param guaranteeInSeconds Wait seconds even if minutes=0
     */
    public static void delayNextOperations(int minutes, int guaranteeInSeconds) {
        delayNextOperations(minutes * 60 * 1000 + guaranteeInSeconds * 1000, true);
    }

    /**
     * Sleeps for given time. Can print detailed information.
     *
     * @param milliseconds delay time
     * @param verbose      if set to true - prints detailed information.
     */
    public static void delayNextOperations(int milliseconds, boolean verbose) {
        try {
            if (verbose) {
                // Preparing detailed printout about elapsing time
                int seconds = milliseconds / 1000;
                if (seconds > 0) {
                    // Delaying more than 1 second - printing remaining seconds
                    logger.info("Delaying next operations. Sleeping for " + seconds + " seconds. Please wait...");

                    for (int i = 0; i < seconds; i++) {
                        if (i == 0) {
                            System.out.print(Strings.padStart(String.valueOf(seconds), 4, '0') + ' ');
                        } else if (i % 20 == 0) {
                            // Break line
                            System.out.println(Strings.padStart(String.valueOf(seconds - i), 4, '0'));
                            // Add indentation
                            System.out.print(Strings.padStart("", 5, ' '));
                        } else {
                            System.out.print(Strings.padStart(String.valueOf(seconds - i), 4, '0') + ' ');
                        }
                        // Wait 1 second
                        Thread.sleep(1000);
                    }

                    // Add break after the last line
                    System.out.println();

                    // Sleep for the reminder of milliseconds to elapse
                    Thread.sleep(milliseconds % 1000);
                } else {
                    // Delaying less than 1 second - simple printout
                    logger.info("Delaying next operations. Sleeping for " + milliseconds + " milliseconds.");
                    Thread.sleep(milliseconds);
                }
            } else {
                // No information about delay time is printed
                Thread.sleep(milliseconds);
            }
        } catch (InterruptedException e) {
            logger.warn("InterruptedException while delaying next operations.");
        }
    }

    /**
     * Executes windows command.
     *
     * @param windowsCommand Windows shell command
     * @param workingDir     where command is executed
     * @return command result
     */
    public static String executeWindowsCommand(String windowsCommand, File workingDir) {
        try {
            WindowsCommandExecutor wce = new WindowsCommandExecutor();
            return wce.executeInWindowsCmd(windowsCommand, workingDir);
        } catch (IOException e) {
            throw new BetlejemException("Problem with system command - IOException: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new BetlejemException("Problem with system command - interrupted while waiting for response:" + e.getMessage());
        }
    }

    /**
     * Unzip given file and put results into specified directory. Remove zip file after unzipping
     *
     * @param pZip       zip to extract.
     * @param pOutputDir output (results) directory.
     * @return list of extracted files.
     * @throws IOException thrown on any IO error.
     */
    public static List<File> unzip(File pZip, File pOutputDir) throws IOException {
        int buffer = 2048;
        List<File> results = new ArrayList<>();

        logger.info("Extracting: " + pZip.getName() + " to " + pOutputDir);
        FileInputStream fis = new FileInputStream(pZip);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
        StringBuilder extractInfo = new StringBuilder();
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            logger.debug("Entry found in zip: " + entry.getName());
            extractInfo.append('\t').append(entry.getName()).append('\n');
            int count;
            byte[] data = new byte[buffer];
            File unpackedFile = new File(pOutputDir, entry.getName());
            results.add(unpackedFile);
            FileOutputStream fos = new FileOutputStream(unpackedFile);
            BufferedOutputStream bos = new BufferedOutputStream(fos, buffer);
            while ((count = zis.read(data, 0, buffer)) != -1) {
                bos.write(data, 0, count);
            }
            bos.flush();
            bos.close();
        }
        logger.info("Extracted:\n" + extractInfo.toString());
        zis.close();
        if (pZip.exists() && pZip.isFile()) {
            if (!pZip.delete()) {
                logger.warn("Could not delete: " + pZip.getName());
            }
        }
        return results;
    }

    /**
     * Changing files extension
     *
     * @param file         to rename
     * @param oldExtension to 'rename from'
     * @param newExtension to 'rename to'
     */
    public static File replaceExtension(File file, String oldExtension, String newExtension) {
        assert file != null : "Given file is null - cannot replace extension!";
        String originalName = file.getName();
        int dotInd = originalName.lastIndexOf('.');
        if (dotInd > 0) {
            String originalExtension = originalName.substring(dotInd + 1);
            if (originalExtension != null && originalExtension.isEmpty()) {
                // Should never happen at this point ;)
                logger.error("File " + originalName + " does not have given extension='" + oldExtension + '\'');
            } else {
                String newName = originalName.replaceFirst(Pattern.quote('.' + originalExtension) + '$', Matcher.quoteReplacement('.' + newExtension));
                logger.info("Renaming file " + originalName + " to " + newName);
                File newFile = new File(file.getParentFile().getAbsolutePath() + File.separator + newName);
                boolean successfulRename = file.renameTo(newFile);
                if (successfulRename) {
                    logger.info("File successfully renamed to: " + newFile.getAbsolutePath());
                    return newFile;
                } else {
                    logger.error("Could not perform renaming of file " + originalName + " to " + newName);
                    logger.error("File already exists=" + newFile.exists());
                }
            }
        } else {
            logger.error("File " + originalName + " does not have given extension to rename.");
        }

        throw new BetlejemException("Could not replace extension='" + oldExtension + "' to '" + newExtension + "' for file=" + file.getAbsolutePath());
    }

    /**
     * Replaces last occurrence of given substring.
     *
     * @param string to search through
     * @param from   substring to find
     * @param to     replacement substring
     * @return string after replacing string
     */
    public static String replaceLast(String string, String from, String to) {
        int lastIndex = string.lastIndexOf(from);
        if (lastIndex < 0) {
            return string;
        }
        String tail = string.substring(lastIndex).replaceFirst(from, to);
        return string.substring(0, lastIndex) + tail;
    }

    /**
     * Search for given line (pattern) in file
     */
    public static boolean isPatternPresentInFile(String pattern, String fileName) {
        Pattern regexp = Pattern.compile(pattern);
        Matcher matcher = regexp.matcher("");
        LineNumberReader lineReader = null;
        try {
            lineReader = new LineNumberReader(new FileReader(fileName));
            String line;
            logger.info("Scanning file line-by-line: " + fileName);
            while ((line = lineReader.readLine()) != null) {
                System.out.println(line);
                matcher.reset(line); // Reset the input
                if (matcher.find()) {
                    logger.info("Matching line found: " + line);
                    return true;
                }
            }
        } catch (FileNotFoundException ex) {
            logger.error("FileNotFoundException while searching for pattern in file: " + ex.getMessage());
        } catch (IOException ex) {
            logger.error("IOException while searching for pattern in file: " + ex.getMessage());
        } finally {
            try {
                if (lineReader != null) {
                    lineReader.close();
                }
            } catch (IOException ex) {
                logger.error("IOException while closing file after searching for pattern: " + ex.getMessage());
            }
        }
        return false;
    }

    public static String changeHtmlIntoPipeSeparatedString(String content) {
        return content.replaceAll("\n", "").replaceAll("\r", "").replaceAll(" </", "</").replaceAll("[ ]{2,}", "").replaceAll("</TR>", "</TR>\n").replaceAll("</TD><TD", "</TD>|<TD").replaceAll("<.*?>", "");
    }

    public static String removePrecisionFromCurrency(String currencyAsString) {
        return currencyAsString.substring(0, 3);
    }

    public static String fixedLengthString(String string, int length, boolean trim) {
        String formatted = String.format("%1$" + length + "s", string);
        return formatted.substring(0, trim ? length : formatted.length());
    }
}
