/*
 * Copyright (C) 2017 The Holodeck B2B Team.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.holodeckb2b.interfaces.customvalidation;

/**
 * Represents an error that is found during the validation of a <i>User Message</i> message unit by a custom {@link
 * IMessageValidator}.
 * <p>As these errors are related to the business protocol and not the transport protocol for which the Core is
 * responsible the data provided in the error is up to the validator.
 *
 * @author Sander Fieten (sander at holodeck-b2b.org)
 * @since 4.0.0
 */
public class MessageValidationError {

    /**
     * Enumerates the severity levels that validation errors can have
     */
    public enum Severity {
        Info, Warning, Failure
    }

    /**
     * The severity level of the error, initialized to <i>Failure</i> by defailt.
     */
    private Severity      severityLevel = Severity.Failure;

    /**
     * Short description of the error
     */
    private String        description = "Unknown validation error occurred";

    /**
     * Details of the error
     */
    private String        details = null;

    /**
     * Creates a new <code>MessageValidationError</code> of severity <i>Failure</i> with only a short description of
     * the detected validation issue.
     *
     * @param description   A short description of the detected validation issue
     */
    public MessageValidationError(final String description) {
        this(description, Severity.Failure, null);
    }

    /**
     * Creates a new <code>MessageValidationError</code> with only a short description of the detected validation issue
     * and the given severity level.
     *
     * @param description   A short description of the detected validation issue
     * @param severity      The severity level of the error
     */
    public MessageValidationError(final String description, final Severity severity) {
        this(description, severity, null);
    }

    /**
     * Creates a new <code>MessageValidationError</code> with a full set of information.
     *
     * @param description   A short description of the detected validation issue. If <code>null</code> or empty a
     *                      default <i>"Unknown validation error occurred"</i> is set
     * @param severity      The severity level of the error, if not specified the default <i>Failure</i> level is used
     * @param details       Detailed description of the validation issues found
     */
    public MessageValidationError(final String description, final Severity severity, final String details) {
        if (severity != null)
            this.severityLevel = severity;
        if (description != null && !description.isEmpty())
            this.description = description;
        this.details = details;
    }

    /**
     * @return the severityLevel
     */
    public Severity getSeverityLevel() {
        return severityLevel;
    }

    /**
     * @param severityLevel the severityLevel to set
     */
    public void setSeverityLevel(Severity severityLevel) {
        this.severityLevel = severityLevel;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the details
     */
    public String getDetails() {
        return details;
    }

    /**
     * @param details the details to set
     */
    public void setDetails(String details) {
        this.details = details;
    }
}
