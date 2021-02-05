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
package org.holodeckb2b.ebms3.handlers.inflow;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.zip.ZipException;

import javax.activation.DataHandler;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPBody;
import org.apache.logging.log4j.Logger;
import org.holodeckb2b.as4.compression.DeCompressionFailure;
import org.holodeckb2b.common.errors.FailedDecryption;
import org.holodeckb2b.common.errors.MimeInconsistency;
import org.holodeckb2b.common.errors.OtherContentError;
import org.holodeckb2b.common.errors.ValueInconsistent;
import org.holodeckb2b.common.handlers.AbstractUserMessageHandler;
import org.holodeckb2b.common.messagemodel.EbmsError;
import org.holodeckb2b.common.messagemodel.Payload;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.core.StorageManager;
import org.holodeckb2b.interfaces.core.HolodeckB2BCoreInterface;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.general.EbMSConstants;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.persistency.PersistenceException;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;

/**
 * Is the <i>IN_FLOW</i> handler responsible for reading the payload content from the SOAP message. The payloads are
 * stored temporarily on the file system.
 * <p>Once the payloads are successfully read the UserMessage is ready for delivery to the business application. So this
 * handler changes the processing state to {@link ProcessingState#READY_FOR_DELIVERY}.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class SaveUserMsgAttachments extends AbstractUserMessageHandler {

    /**
     * The name of the directory used for temporarily storing payloads
     */
    private static final String PAYLOAD_DIR = "plcin";

    /**
     * Saves the payload contents to file
     *
     * @throws PersistenceException When a database problem occurs changing the processing state of the message unit
     */
    @Override
    protected InvocationResponse doProcessing(final IUserMessageEntity um, final IMessageProcessingContext procCtx, 
    										  final Logger log) throws PersistenceException {
        StorageManager updateManager = HolodeckB2BCore.getStorageManager();

        final Collection<IPayload> payloads = um.getPayloads();
        // If there are no payloads in the UserMessage directly continue processing
        if (Utils.isNullOrEmpty(payloads)) {
            log.debug("UserMessage contains no payloads.");
            return InvocationResponse.CONTINUE;
        }

        log.debug("UserMessage contains " + payloads.size() + " payloads.");
        try {
            // Get the directory where to store the payloads from the configuration
            final File tmpPayloadDir = getTempDir();
            log.trace("Payload content will be stored in " + tmpPayloadDir.getAbsolutePath());

            // Save each payload to a file
            // We built a new collection of payload meta-data so we can update the content location
            ArrayList<IPayload>  newPayloadData = new ArrayList<>(payloads.size());
            for(final IPayload ip : payloads) {
                // Convert to Payload object so we can set properties
                Payload p = new Payload(ip);
                // Create a unique filename for temporarily storing the payload
                final File plFile = File.createTempFile("pl-", null, tmpPayloadDir);

                // The reference defines how the payload is contained in the message
                final String plRef = p.getPayloadURI();
                switch (p.getContainment()) {
                    case BODY:
                        // Payload is a element from the SOAP body
                        OMElement plElement= null;
                        if (Utils.isNullOrEmpty(plRef)) {
                            log.trace("No reference included in payload meta data => SOAP body is the payload");
                            plElement = procCtx.getParentContext().getEnvelope().getBody().getFirstElement();
                        } else {
                            log.trace("Payload is element with id " + plRef + " of SOAP body");
                            plElement = getPayloadFromBody(procCtx.getParentContext().getEnvelope().getBody(), plRef);
                        }
                        if (plElement == null) {
                            // The reference is invalid as no element exists in the body. This makes this an
                            // invalid UserMessage! Create an ValueInconsistent error and store it in the MessageContext
                            createInconsistentError(procCtx, um, null, log);
                            return InvocationResponse.CONTINUE;
                        } else {
                            // Found the referenced element, save it (and its children) fo file
                            log.trace("Found referenced element in SOAP body");
                            saveXMLPayload(plElement, plFile);
                            log.trace("Payload saved to temporary file, set content location in meta data");
                            p.setMimeType("application/xml");
                            p.setContentLocation(plFile.getAbsolutePath());
                        }
                        break;
                    case ATTACHMENT:
                        log.trace("Payload is contained in attachment with MIME Content-id= " + plRef);
                        // Get access to the actual content
                        log.debug("Get DataHandler for attachment");
                        final DataHandler dh = procCtx.getParentContext().getAttachment(plRef);
                        if (dh == null) {
                            // The reference is invalid as no attachment with this Cid is available in the message.
                        	// Create an ValueInconsistent error and store it in the MessageContext
                            createInconsistentError(procCtx, um, plRef, log);
                            return InvocationResponse.CONTINUE;
                        } else {
                            try (final OutputStream aOS = new FileOutputStream(plFile))
                            {
                                dh.writeTo(aOS);
                            } catch (final IOException ioException) {
                                // Get root cause as this problem can be caused by failure to decompress, decrypt or
                                // writing to file system
                                Throwable rootCause = Utils.getRootCause(ioException);
                                // An error must be generated, which one depending on the what caused the exception
                                EbmsError writeFailure;
                                String  errMessage;
                                // Check if this IO exception is caused by decryption failure
                                if (rootCause instanceof ZipException) {
                                    errMessage = "decompressed";
                                    writeFailure = new DeCompressionFailure("Payload [" + plRef
                                                                        + "] in message could not be decompressed!",
                                                                        um.getMessageId());
                                } else if (Utils.getRootCause(ioException)
                                                                instanceof java.security.GeneralSecurityException) {
                                    errMessage = "decrypted";
                                    writeFailure = new FailedDecryption("Payload [" + plRef
                                                                        + "] in message could not be decrypted!",
                                                                        um.getMessageId());
                                } else {
                                    errMessage = "written to temp directory";
                                    writeFailure = new OtherContentError("Unexpected error in payload processing!",
                                                                        um.getMessageId());
                                }
                                log.info("Payload [" + plRef + "] in message [" + um.getMessageId() + "] could not be "
                                          + errMessage + "!\n\tDetails: " + rootCause.getMessage());
                                procCtx.addGeneratedError( writeFailure);
                                log.trace("Error generated and stored in MC, change processing state of user message");
                                updateManager.setProcessingState(um, ProcessingState.FAILURE);
                                return InvocationResponse.CONTINUE;
                            }
                            log.debug("Payload saved to temporary file, set content location in meta data");
                            p.setContentLocation(plFile.getAbsolutePath());
                            p.setMimeType(dh.getContentType());
                        }
                        break;
                    default:
                        log.debug("Payload is not contained in message but located at " + plRef);
                        // External payload are not processed by Holodeck B2B, the URI is just passed to business app
                }
                // Add update payload meta-data to collection
                newPayloadData.add(p);
            }

            log.debug("All payloads saved to temp file");
            // Update the message meta data in database 
            updateManager.setPayloadInformation(um, newPayloadData);
        } catch (IOException | XMLStreamException ex) {
            log.error("Payload(s) could not be saved to temporary file! Details:" + ex.getMessage());
            procCtx.addGeneratedError(new OtherContentError("Internal error", um.getMessageId()));
            updateManager.setProcessingState(um, ProcessingState.FAILURE);
            
        }

        return InvocationResponse.CONTINUE;
    }

    /**
     * Helper method to get the directory where the payload contents can be stored.
     *
     * @todo Consider moving this to util class!
     *
     * @return              {@link File} handler to the directory that must be used for storing payload content
     * @throws IOException   When the specified directory does not exist and can not be created.
     */
    private File getTempDir() throws IOException {
        Path tmpPayloadDir = HolodeckB2BCoreInterface.getConfiguration().getTempDirectory().resolve(PAYLOAD_DIR);
        
        if (!Files.exists(tmpPayloadDir))
        	tmpPayloadDir = Files.createDirectories(tmpPayloadDir);
        else if (!Files.isDirectory(tmpPayloadDir) || !Files.isWritable(tmpPayloadDir))
            throw new IOException("Temp directory for payloads (" + tmpPayloadDir.toString() + ") is not available!");
        
        return tmpPayloadDir.toFile();
    }

    /**
     * Searches for and returns the element in the SOAP body with the given id.
     * <p>This method only looks for the <code>xml:id</code> attribute of the elements.
     *
     * @param body  {@link SOAPBody} to search through
     * @param id    id to search for
     * @return      The first element with the given id,
     *              <code>null</code> if no element is found with the given id.
     */
    private OMElement getPayloadFromBody(final SOAPBody body, final String id) {
        // Search all children in the SOAP body for an element with given id
        final Iterator<?> bodyElements = body.getChildElements();
        OMElement e = null; boolean f = false;
        while (bodyElements.hasNext() && !f) {
            e = (OMElement) bodyElements.next();
            f = id.equals(e.getAttributeValue(EbMSConstants.QNAME_XMLID));
        }
        return (f ? e : null);
    }

    /**
     * Creates a <i>ValueInconsistent</i> or <i>MimeInconsistency</i> ebMS error as the reference in the user message
     * is invalid. Also changes the processing state of the user message to {@link ProcessingState#FAILURE} to
     * indicate the message can not be processed.
     *
     * @param mc            The current message context
     * @param um            The user message containing the invalid reference
     * @param invalidRef    The invalid payload reference, can be <code>null</code> if the body payload is missing
     * @param log			The Log to be used
     * @throws PersistenceException When updating the processing state fails.
     */
    private void createInconsistentError(final IMessageProcessingContext procCtx, final IUserMessageEntity um, 
    									 final String invalidRef, final Logger log) throws PersistenceException {
        log.info("UserMessage with id " + um.getMessageId() + " can not be processed because payload"
                 + (invalidRef != null ? " with href=" + invalidRef  : "") + " is not included in message");
        EbmsError error = null;
        if(invalidRef == null || invalidRef.startsWith("#"))
            error = new ValueInconsistent();
        else
            error = new MimeInconsistency();
        error.setRefToMessageInError(um.getMessageId());
        error.setErrorDetail("The payload" + (invalidRef != null ? " with href=" + invalidRef  : "")
                                           + " could not be found in the message!");
        procCtx.addGeneratedError(error);
        log.trace("Error stored in message context, changing processing state of message");
        HolodeckB2BCore.getStorageManager().setProcessingState(um, ProcessingState.FAILURE);
    }


    /**
     * Saves the XML contained in the given element to the specified file.
     *
     * @param e     The {@link OMElement} to save
     * @param f     {@link File} handle for the file where content should be saved
     * @throws IOException
     * @throws XMLStreamException
     */
    private void saveXMLPayload(final OMElement e, final File f) throws XMLStreamException, IOException {
        try (final Writer w = new FileWriter(f))
        {
            final XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(w);
            e.serialize(writer);
            writer.flush();
        }
    }
}
