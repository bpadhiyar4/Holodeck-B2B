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
package org.holodeckb2b.core.handlers.inflow;

import org.apache.logging.log4j.Logger;
import org.holodeckb2b.common.errors.OtherContentError;
import org.holodeckb2b.common.events.impl.MessageDelivered;
import org.holodeckb2b.common.events.impl.MessageDeliveryFailure;
import org.holodeckb2b.common.handlers.AbstractUserMessageHandler;
import org.holodeckb2b.core.HolodeckB2BCore;
import org.holodeckb2b.core.StorageManager;
import org.holodeckb2b.core.pmode.PModeUtils;
import org.holodeckb2b.interfaces.core.IMessageProcessingContext;
import org.holodeckb2b.interfaces.delivery.IDeliverySpecification;
import org.holodeckb2b.interfaces.delivery.IMessageDeliverer;
import org.holodeckb2b.interfaces.delivery.MessageDeliveryException;
import org.holodeckb2b.interfaces.persistency.PersistenceException;
import org.holodeckb2b.interfaces.persistency.entities.IUserMessageEntity;
import org.holodeckb2b.interfaces.processingmodel.ProcessingState;

/**
 * Is the <i>IN_FLOW</i> handler responsible for the delivery of the User message message unit to the business
 * application.
 * <p>To prevent that the message unit is delivered twice in parallel delivery only takes place when the processing
 * state can be successfully changed from {@link ProcessingState#READY_FOR_DELIVERY} to
 * {@link ProcessingState#OUT_FOR_DELIVERY}.
 * <p>NOTE: The actual delivery to the business application is done through a <i>DeliveryMethod</i> which is specified
 * in the P-Mode for this message unit.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 */
public class DeliverUserMessage extends AbstractUserMessageHandler {

    @Override
    protected InvocationResponse doProcessing(final IUserMessageEntity um, final IMessageProcessingContext procCtx, 
    										  final Logger log) throws PersistenceException {
        StorageManager updateManager = HolodeckB2BCore.getStorageManager();
        // Prepare message for delivery by checking it is still ready for delivery and then
        // change its processing state to "out for delivery"
        log.trace("Prepare message [" + um.getMessageId() + "] for delivery");
        if(updateManager.setProcessingState(um, ProcessingState.READY_FOR_DELIVERY, ProcessingState.OUT_FOR_DELIVERY)) {
            // Message can be delivered to business application
            log.trace("Start delivery of user message");
            MessageDeliveryException failure = null;
            try {
                // Get the delivery specification from the P-Mode
                final IDeliverySpecification deliveryMethod = PModeUtils.getLeg(um).getDefaultDelivery();
                final IMessageDeliverer deliverer = HolodeckB2BCore.getMessageDeliverer(deliveryMethod);
                try {
                    log.debug("Delivering the message using delivery specification: " + deliveryMethod.getId());
                    deliverer.deliver(um);
                } catch (final MessageDeliveryException ex) {
                    // There was an "normal/expected" issue during delivery, continue as normal
                    throw ex;
                } catch (final Throwable t) {
                    // Catch of Throwable used for extra safety in case the DeliveryMethod implementation does not
                    // handle all exceptions correctly
                    log.warn(deliverer.getClass().getSimpleName() + " threw " + t.getClass().getSimpleName()
                             + " instead of MessageDeliveryException!");
                    throw new MessageDeliveryException("Unhandled exception during message delivery", t);
                }
                log.info("Successfully delivered user message [msgId=" + um.getMessageId() +"]");
                updateManager.setProcessingState(um, ProcessingState.DELIVERED);                
            } catch (final MessageDeliveryException ex) {
                failure = ex;
                log.error("Could not deliver the user message [msgId=" + um.getMessageId()
                            + "] using specified delivery method!" + "\n\tError details: " + ex.getMessage());
                // Indicate failure in processing state
                updateManager.setProcessingState(um, ProcessingState.DELIVERY_FAILED);
                // If the problem that occurred is indicated as permanent, we can set the state to FAILURE and return
                //  an ebMS Error to the sender
                if (ex.isPermanent()) {
                    updateManager.setProcessingState(um, ProcessingState.FAILURE);
                    procCtx.addGeneratedError( new OtherContentError("Message delivery impossible!",
                                                                                um.getMessageId()));
                }
            }
            // Raise delivery event to inform external com ponents
            HolodeckB2BCore.getEventProcessor().raiseEvent(failure == null ? new MessageDelivered(um)
            											  				   : new MessageDeliveryFailure(um, failure));
        } else {
            // This message is not ready for delivery now which is caused by it already been delivered by another
            // thread. This however should not occur normaly.
            log.warn("Usermessage [" + um.getMessageId() + "] is already being delivered!");
        }

        return InvocationResponse.CONTINUE;
    }
}
