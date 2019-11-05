/**
 * Copyright (C) 2014 The Holodeck B2B Team, Sander Fieten
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.core.config;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.util.Random;

import org.apache.axis2.context.ConfigurationContext;
import org.holodeckb2b.common.util.Utils;

/**
 * Contains the configuration of the Holodeck B2B Core.
 * <p>The Holodeck B2B configuration is located in the <code><b>«HB2B_HOME»</b>/conf</code> directory where
 * <b>HB2B_HOME</b> is the directory where Holodeck B2B is installed or the system property <i>"holodeckb2b.home"</i>.
 * <p>The structure of the config file is defined by the XML Schema Definition
 * <code>http://holodeck-b2b.org/schemas/2015/10/config</code> which can be found in <code>
 * src/main/resources/xsd/hb2b-config.xsd</code>.
 * <p>NOTE: Current implementation does not encrypt the keystore passwords! Therefore access to the config file
 * SHOULD be limited to the account that is used to run Holodeck B2B.
 * todo Encryption of keystore passwords
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class Config implements InternalConfiguration {

    /*
     * The Holodeck B2B home directory
     */
    private String holodeckHome = null;

    /*
     * The host name
     */
    private String  hostName = null;

    /*
     * The location of the workerpool configuration
     */
    private String  workerConfigFile = null;

    /*
     * The directory where files can be temporarily stored
     */
    private String  tempDir = null;

    /*
     * Indication whether bundling of signals is allowed
     */
    private boolean allowSignalBundling = false;

    /*
     * Default setting whether errors on errors should be reported to sender or not
     */
    private boolean defaultReportErrorOnError = false;

    /*
     * Default setting whether errors on receipts should be reported to sender or not
     */
    private boolean defaultReportErrorOnReceipt = false;

    /*
     * The Axis2 configuration context that is used to process the messages
     */
    private ConfigurationContext    axisCfgCtx = null;

    /*
     * The class name of the event processor that is to be used for handling message processing events
     * @since 2.1.0
     */
    private String messageProcessingEventProcessorClass = null;

    /*
     * The class name of the component that should be used to validate P-Modes before deploying them
     * @since  3.0.0
     */
    private String pmodeValidatorClass = null;

    /*
     * The class name of the component that should be used to store deployed P-Modes
     * @since  3.0.0
     */
    private String pmodeStorageClass = null;

    /*
     * The class name of the persistency provider that should be used to store the meta-data on processed message units
     * @since  3.0.0
     */
    private String persistencyProviderClass = null;

    /*
     * The class name of the security provider that should be used to process the WS-Security header of messages
     * @since 4.0.0
     */
    private String securityProviderClass = null;

    /*
     * The class name of the certificate manager that should be used for managing and checking certificates
     * @since 5.0.0
     */
    private String certManagerClass = null;
    
    /*
     * Indicator whether strict header validation should be applied to all messages
     * @since 4.0.0
     */
    private boolean useStrictHeaderValidation = false;

    private boolean isTrue (final String s) {
      return "on".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s) || "1".equalsIgnoreCase(s);
    }

    /**
     * Initializes the configuration object using the Holodeck B2B configuration file located in <code>
     * «HB2B_HOME»/conf/holodeckb2b.xml</code> where <b>HB2B_HOME</b> is the directory where Holodeck B2B is installed.
     *
     * @param configCtx     The Axis2 configuration context containing the Axis2 settings
     * @throws Exception    When the configuration can not be initialized
     */
    public Config(final ConfigurationContext configCtx) throws Exception {
        // The Axis2 configuration context
        axisCfgCtx = configCtx;

        // Set the Holodeck B2B home directory
        this.holodeckHome = axisCfgCtx.getRealPath("").getParent() + File.separatorChar; 

        // Read the configuration file
        final ConfigXmlFile configFile = ConfigXmlFile.loadFromFile(
                                                        Paths.get(holodeckHome, "conf", "holodeckb2b.xml").toString());

        // The hostname to use in message processing
        hostName = configFile.getParameter("ExternalHostName");
        if (Utils.isNullOrEmpty(hostName)) {
            try {
            	//TODO: Getting the hostname this way can be slow, can we change?
                hostName = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (final UnknownHostException e) {}

            if (Utils.isNullOrEmpty(hostName)) {
                // If we still have no hostname, we just generate a random hex number to use as hostname
                final Random r = new Random();
                hostName = Long.toHexString((long) r.nextInt() * (long) r.nextInt()) + ".generated";
            }
        }

        /* The configuration of the workerpool. By default the "workers.xml" in the
         * conf directory is used. But it is possible to specify another location
         * using the "WorkerConfig" parameter
         */
        String workerCfgFile = configFile.getParameter("WorkerConfig");
        if (workerCfgFile == null || workerCfgFile.isEmpty())
            // Not specified, use default
            workerCfgFile = holodeckHome + "/conf/workers.xml";

        workerConfigFile = workerCfgFile;

        // The temp dir
        tempDir = configFile.getParameter("TempDir");
        if (Utils.isNullOrEmpty(tempDir))
            // Not specified, use default
            tempDir = holodeckHome + "/temp/";

        // Ensure the path ends with a folder separator
        tempDir = (tempDir.endsWith(FileSystems.getDefault().getSeparator()) ? tempDir
                            : tempDir + FileSystems.getDefault().getSeparator());

        // Option to enable signal bundling
        final String bundling = configFile.getParameter("AllowSignalBundling");
        allowSignalBundling = isTrue (bundling);

        // Default setting for reporting Errors on Errors
        String defErrReporting = configFile.getParameter("ReportErrorOnError");
        defaultReportErrorOnError = isTrue(defErrReporting);
        // Default setting for reporting Errors on Receipts
        defErrReporting = configFile.getParameter("ReportErrorOnReceipt");
        defaultReportErrorOnReceipt = isTrue(defErrReporting);
     
        // The class name of the event processor
        messageProcessingEventProcessorClass = configFile.getParameter("MessageProcessingEventProcessor");

        // The class name of the P-Mode validator
        pmodeValidatorClass = configFile.getParameter("PModeValidator");

        // The class name of the component to store P-Modes
        pmodeStorageClass = configFile.getParameter("PModeStorageImplementation");

        // The class name of the persistency provider
        persistencyProviderClass = configFile.getParameter("PersistencyProvider");

        // The class name of the security provider
        securityProviderClass = configFile.getParameter("SecurityProvider");

        // The class name of the certificate manager
        certManagerClass = configFile.getParameter("CertificateManager");
        
        // Indicator whether strict header validation should be performed
        final String strictHeaderValidation = configFile.getParameter("StrictHeaderValidation");
        useStrictHeaderValidation = isTrue(strictHeaderValidation);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigurationContext getAxisConfigurationContext() {
        return axisCfgCtx;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHostName () {
        return hostName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getHolodeckB2BHome () {
        return holodeckHome;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getWorkerPoolCfgFile() {
        return workerConfigFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTempDirectory() {
        return tempDir;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Deprecated
    public boolean allowSignalBundling() {
        return allowSignalBundling;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldReportErrorOnError() {
        return defaultReportErrorOnError;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean shouldReportErrorOnReceipt() {
        return defaultReportErrorOnReceipt;
    }

    /**
     * {@inheritDoc}
     * @since 2.1.0
     */
    @Override
    public String getMessageProcessingEventProcessor() {
        return messageProcessingEventProcessorClass;
    }

    /**
     * {@inheritDoc}
     * @since 3.0.0
     */
    @Override
    public String getPModeValidatorImplClass() {
        return pmodeValidatorClass;
    }

    /**
     * {@inheritDoc}
     * @since 3.0.0
     */
    @Override
    public String getPModeStorageImplClass() {
        return pmodeStorageClass;
    }

    /**
     * {@inheritDoc}
     * @since 3.0.0
     */
    @Override
    public String getPersistencyProviderClass() {
        return persistencyProviderClass;
    }

    /**
     * {@inheritDoc}
     * @since 4.0.0
     */
    @Override
    public boolean useStrictHeaderValidation() {
        return useStrictHeaderValidation;
    }

    /**
     * {@inheritDoc}
     * @since 4.0.0
     */
    @Override
    public String getSecurityProviderClass() {
        return securityProviderClass;
    }
    
    /**
     * {@inheritDoc}
     * @since 5.0.0
     */
    @Override
    public String getCertManagerClass() {
    	return certManagerClass;
    }
}