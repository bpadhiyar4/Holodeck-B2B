/*
 * Copyright (C) 2016 The Holodeck B2B Team, Sander Fieten
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
package org.holodeckb2b.core.validation.header;

import java.util.Collection;
import java.util.HashSet;
import org.holodeckb2b.common.util.Utils;
import org.holodeckb2b.ebms3.handlers.inflow.HeaderValidation;
import org.holodeckb2b.interfaces.customvalidation.IMessageValidator;
import org.holodeckb2b.interfaces.customvalidation.MessageValidationError;
import org.holodeckb2b.interfaces.general.IPartyId;
import org.holodeckb2b.interfaces.general.IProperty;
import org.holodeckb2b.interfaces.general.IService;
import org.holodeckb2b.interfaces.general.ITradingPartner;
import org.holodeckb2b.interfaces.messagemodel.ICollaborationInfo;
import org.holodeckb2b.interfaces.messagemodel.IPayload;
import org.holodeckb2b.interfaces.messagemodel.IUserMessage;

/**
 * Provides the validation of the ebMS header information specific for <i>User Message</i> message units.
 * <p>NOTE: Using custom validation additional validations can be used for User Message message units that can also
 * include checks on payloads included in the User Message and are separately configured in the P-Mode.
 *
 * @author Sander Fieten <sander at holodeck-b2b.org>
 * @since  HB2B_NEXT_VERSION
 */
class UserMessageValidator extends GeneralMessageUnitValidator<IUserMessage>
                           implements IMessageValidator<IUserMessage> {

    UserMessageValidator(boolean useStrictValidation) {
        super(useStrictValidation);
    }

    /**
     * Performs the basic validation of the ebMS header meta-data specific for a User Message message unit.
     * <p>This includes a check on the general message meta-data and information about the sender and receiver of the
     * message as well as business information like service and action are available.
     *
     * @param messageUnit       The User Message message unit which header must be validated
     * @param validationErrors  Collection of {@link MessageValidationError}s to which validation errors must be added
     */
    @Override
    protected void doBasicValidation(final IUserMessage messageUnit,
                                     Collection<MessageValidationError> validationErrors) {
        // First do genereal validation
        super.doBasicValidation(messageUnit, validationErrors);

        // Cast to IUserMessage
        IUserMessage userMessageInfo = (IUserMessage) messageUnit;
        // Check that To and From party are included
        doBasicPartyInfoValidation(userMessageInfo.getSender(), "Sender", validationErrors);
        doBasicPartyInfoValidation(userMessageInfo.getReceiver(), "Receiver", validationErrors);

        // Check collaboration information, i.e. Service, Action and ConversationId
        final ICollaborationInfo collabInfo = userMessageInfo.getCollaborationInfo();
        if (collabInfo == null)
            validationErrors.add(new MessageValidationError("Collaboration information is missing"));
        else {
            if (Utils.isNullOrEmpty(collabInfo.getAction()))
                validationErrors.add(new MessageValidationError("Action is missing"));
            if (collabInfo.getService() == null || Utils.isNullOrEmpty(collabInfo.getService().getName()))
                validationErrors.add(new MessageValidationError("Service information is missing"));
        }
        // Check MessageProperties (if provided)
        checkPropertiesNames(userMessageInfo.getMessageProperties(), "Message", validationErrors);

        // Check PayloadInfo, in this validator only the part properties are checked
        final Collection<IPayload> payloadInfo = (Collection<IPayload>) userMessageInfo.getPayloads();
        if (!Utils.isNullOrEmpty(payloadInfo))
            for (IPayload p : payloadInfo)
                checkPropertiesNames(p.getProperties(), "Part", validationErrors);
    }

    /**
     * Performs the strict validation of the ebMS header meta-data specific for a User Message message unit
     * <p>In addition to the basic validations it is checked that:<ul>
     * <li>the PartyIds are URI when they don't have a type and if there are multiple PartyIds for partner that have a
     * type, these types are unique.</li>
     * <li>the Service is an URI when no type is specfied.</i>
     * <li>All properties, both message and part, have a value.</li></ul>
     * As the <i>ebMS V3 Core Specification<i> specifies that a violation of the first two rules should result in a
     * <i>ValueInconsistent</i> error (instead of <i>InvalidHeader<i>) the {@link MessageValidationError#details} field
     * is used to indicate this by setting its value to "<i>ValueInconsistent</i>". The {@link HeaderValidation} handler
     * can then create the correct error.
     *
     * @param messageUnit       The User Message message unit which header must be validated
     * @param validationErrors  Collection of {@link MessageValidationError}s to which validation errors must be added
     */
    @Override
    protected void doStrictValidation(final IUserMessage messageUnit,
                                      Collection<MessageValidationError> validationErrors) {
        // First do general validation
        super.doStrictValidation(messageUnit, validationErrors);

        // Cast to IUserMessage
        IUserMessage userMessageInfo = (IUserMessage) messageUnit;

        // Check the PartyIds of the sender and receiver
        doStrictPartyIdValidation(userMessageInfo.getSender().getPartyIds(), "Sender", validationErrors);
        doStrictPartyIdValidation(userMessageInfo.getReceiver().getPartyIds(), "Receiver", validationErrors);

        // Check that the Service value is an URI if it's untyped
        if (userMessageInfo.getCollaborationInfo() != null) {
            IService service = userMessageInfo.getCollaborationInfo().getService();
            if (service != null && Utils.isNullOrEmpty(service.getType()) && !Utils.isValidURI(service.getName()))
               validationErrors.add(new MessageValidationError("Untype Service value [" + service.getName()
                                                                                                    + "] is not URI"));
        }
        // Check that all properties have a value
        // Check MessageProperties (if provided)
        checkPropertiesValues(userMessageInfo.getMessageProperties(), "Message", validationErrors);

        // Check PayloadInfo, in this validator only the part properties are checked
        final Collection<IPayload> payloadInfo = (Collection<IPayload>) userMessageInfo.getPayloads();
        if (!Utils.isNullOrEmpty(payloadInfo))
            for (IPayload p : payloadInfo)
                checkPropertiesValues(p.getProperties(), "Part", validationErrors);
    }

    /**
     * Perform a basic checks of information about the sender or receiver contained in the User Message
     *
     * @param partnerInfo       The meta-data of the sender or receiver
     * @parem partnerName       The text to identify this partner in error message, i.e. "Sender" or "Receiver"
     * @param validationErrors  Collection of {@link MessageValidationError}s to which validation errors must be added
     */
    private void doBasicPartyInfoValidation(final ITradingPartner partnerInfo, final String partnerName,
                                            Collection<MessageValidationError> validationErrors) {
        if (partnerInfo == null) {
            validationErrors.add(new MessageValidationError(partnerName + " information is missing"));
        } else {
            if (Utils.isNullOrEmpty(partnerInfo.getRole()))
                validationErrors.add(new MessageValidationError("Role of " + partnerName + " is missing"));
            if (Utils.isNullOrEmpty(partnerInfo.getPartyIds()))
                validationErrors.add(new MessageValidationError("Identification of " + partnerName + " is missing"));
            else if (partnerInfo.getPartyIds().stream().anyMatch(pid -> Utils.isNullOrEmpty(pid.getId())))
                validationErrors.add(new MessageValidationError("Empty PartyId for " + partnerName + " is not allowed"));
        }
    }

    /**
     * Perform a check on the <i>PartyId</i>s of the sender or receiver contained in the User Message.
     *
     * @param partyIds          The partyIds of the sender or receiver
     * @parem partnerName       The text to identify this partner in error message, i.e. "Sender" or "Receiver"
     * @param validationErrors  Collection of {@link MessageValidationError}s to which validation errors must be added
     */
    private void doStrictPartyIdValidation(final Collection<IPartyId> partyIds, final String partnerName,
                                           Collection<MessageValidationError> validationErrors) {
        if (Utils.isNullOrEmpty(partyIds))
            return;

        Collection<String>  types = new HashSet<>(partyIds.size());
        boolean multipleSameType = false;
        for (IPartyId pid : partyIds) {
            if (!Utils.isNullOrEmpty(pid.getType()))
                // Check that the type of this id isn't used earlier by making sure the set did grow
                multipleSameType = !types.add(pid.getType());
            else
                // No type for this partyId, ensure it is an URI
                if (!Utils.isValidURI(pid.getId()))
                    validationErrors.add(new MessageValidationError(partnerName + " partyId [" + pid.getId()
                                                                           + " does not have a type and is not an URI",
                                                                    MessageValidationError.Severity.Failure,
                                                                    "ValueInconsistent"));
        }
        if (multipleSameType)
            validationErrors.add(new MessageValidationError(partnerName + " has multiple partyIds of the same type"));
    }

    /**
     * Checks whether all properties in a set of properties have a name.
     *
     * @param properties        The set of properties to check
     * @param propSetName       The name of the set, i.e. "Message" or "Part"
     * @param validationErrors  Collection of {@link MessageValidationError}s to which validation errors must be added
     */
    private void checkPropertiesNames(final Collection<IProperty> properties, final String propSetName,
                                          Collection<MessageValidationError> validationErrors) {
        if (!Utils.isNullOrEmpty(properties) && properties.stream().anyMatch(p -> Utils.isNullOrEmpty(p.getName())))
            validationErrors.add(new MessageValidationError("Unnamed " + propSetName + " property is not allowed"));
    }

    /**
     * Checks whether all properties in a set of properties have a value.
     *
     * @param properties        The set of properties to check
     * @param propSetName       The name of the set, i.e. "Message" or "Part"
     * @param validationErrors  Collection of {@link MessageValidationError}s to which validation errors must be added
     */
    private void checkPropertiesValues(final Collection<IProperty> properties, final String propSetName,
                                          Collection<MessageValidationError> validationErrors) {
        if (!Utils.isNullOrEmpty(properties))
            properties.stream().filter(p -> Utils.isNullOrEmpty(p.getValue()))
                               .forEachOrdered(p ->
                                       validationErrors.add(new MessageValidationError(propSetName + " property " +
                                                                   p.getName() + " must have a non-empty value!")));
    }
}
