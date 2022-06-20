package me.ugeno.betlejem.common;

import me.ugeno.betlejem.common.utils.BetlejemException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class WindowsCommandExecutor {
    /**
     * Remote commands - timeout in milliseconds.
     */
    private static final int WIN_REMOTE_CMD_TIMEOUT = 900000; // 15 minutes
    private static final Logger logger = Logger.getLogger(WindowsCommandExecutor.class.getSimpleName());
    private boolean timeout;
    private boolean commandFinished;

    public String executeInWindowsCmd(String windowsCommand, File workingDir) throws IOException, InterruptedException {
        // Start the process
        logger.info("Executing windows command >" + windowsCommand + "<");
        final Process pr = Runtime.getRuntime().exec(windowsCommand, null, workingDir);

        // Read error output
        StreamGobbler errorGobbler = new StreamGobbler(pr.getErrorStream(), "ERR");

        // Read standard output
        ByteArrayOutputStream resultOutputStream = new ByteArrayOutputStream();
        StreamGobbler outputGobbler = new StreamGobbler(pr.getInputStream(), "OUT", resultOutputStream);

        // kick them off
        errorGobbler.start();
        outputGobbler.start();

        // Start the guard
        new Thread(() -> {
            try {
                Thread.sleep(WIN_REMOTE_CMD_TIMEOUT);

                if (!commandFinished) {
                    logger.warning("ERROR: Timeout!");
                    pr.destroy();
                    timeout = true;
                }
            } catch (Exception e) {
                // Can't help..
            }
        }).start();

        logger.info("Command invoked... Waiting " + (WIN_REMOTE_CMD_TIMEOUT / 1000) + " seconds for results...");
        int exitVal = pr.waitFor(); // Waiting for process executing command to finish...
        commandFinished = true;

        String result = new String(resultOutputStream.toByteArray(), StandardCharsets.UTF_8);

        logger.info("Exited with error code: " + exitVal);

        if ((exitVal != 0) && !timeout) {
            throw new BetlejemException("Error response from remote command: " + result);
        }

        if (timeout) {
            throw new BetlejemException("Timeout while executing remote command.");
        }

        return result;
    }
}

/**
 * Reads the stream and prints it out.
 */
class StreamGobbler extends Thread {
    private BufferedReader inputReader;
    private PrintWriter outputWriter;
    private String type;
    private Logger logger = Logger.getLogger(StreamGobbler.class.getSimpleName());

    /**
     * Creates a new StreamGobbler object.
     *
     * @param inputStream -
     * @param label       -
     */
    StreamGobbler(InputStream inputStream, String label) {
        this(inputStream, label, null);
    }

    /**
     * Creates a new StreamGobbler object.
     *
     * @param inputStream  -
     * @param label        -
     * @param outputStream -
     */
    StreamGobbler(InputStream inputStream, String label, OutputStream outputStream) {
        this.inputReader = new BufferedReader(new InputStreamReader(inputStream));
        this.type = label;

        if (outputStream != null) {
            this.outputWriter = new PrintWriter(outputStream);
        }
    }

    /**
     * Reads the stream.
     */
    @Override
    public void run() {
        try {
            String line;

            while ((line = inputReader.readLine()) != null) {
                if (type.equals("ERR")) {
                    System.err.println(line);
                } else {
                    System.out.println(line);
                }

                if (outputWriter != null) {
                    outputWriter.println(line);
                }
            }

            if (outputWriter != null) {
                outputWriter.flush();
            }
        } catch (IOException ioe) {
            logger.warning("IOException while reading input stream: " + ioe.getMessage());
        }
    }
}
