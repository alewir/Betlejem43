package me.ugeno.betlejem.xtb;

import me.ugeno.betlejem.common.utils.xtb.BetlejemXtbUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pro.xstore.api.message.error.APICommunicationException;
import pro.xstore.api.message.response.CurrentUserDataResponse;
import pro.xstore.api.message.response.LoginResponse;
import pro.xstore.api.sync.SyncAPIConnector;
import pro.xstore.api.sync.XtbCredentials;

import java.io.IOException;

import static me.ugeno.betlejem.common.utils.BetlejemUtils.logError;
import static pro.xstore.api.message.command.APICommandFactory.executeCurrentUserDataCommand;
import static pro.xstore.api.message.command.APICommandFactory.executeLoginCommand;

/**
 * Created by alwi on 09/04/2021.
 * All rights reserved.
 */
public class XtbClient {
    private static final Logger LOG = LoggerFactory.getLogger(XtbClient.class);

    protected SyncAPIConnector connector;
    protected Boolean connected = Boolean.FALSE;

    private XtbCredentials xtbCredentials;

    protected XtbClient() {
        try {
            xtbCredentials = BetlejemXtbUtils.fetchXtbCredentials();
        } catch (IOException e) {
            logError(e);
        }
    }

    protected void connect() {
        if (connector != null) {
            try {
                connector.close();
            } catch (APICommunicationException e) {
                logError(e);
            }
        }

        try {
            LOG.debug("Connecting...");
            connector = new SyncAPIConnector(BetlejemXtbUtils.serverType);

            LOG.debug("Logging in...");
            LoginResponse loginResponse = executeLoginCommand(connector, xtbCredentials);
            LOG.debug(String.format("Log in response: %s%n", loginResponse));

            if (loginResponse != null && loginResponse.getStatus()) {
                LOG.debug("Fetching user data...");
                CurrentUserDataResponse user = executeCurrentUserDataCommand(connector);
                LOG.debug(String.format("User: %s logged in to server type=%s%n", user, BetlejemXtbUtils.serverType));

                connected = Boolean.TRUE;
                LOG.info("Connection initialized.");
                return;
            }
        } catch (Exception e) {
            LOG.error("Error during XTB login: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        connected = Boolean.FALSE;
        LOG.warn("Connection failed.");
    }
}
