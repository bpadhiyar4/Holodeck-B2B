/*
 * Copyright (C) 2013 The Holodeck B2B Team, Sander Fieten
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
package org.holodeckb2b.common.exceptions;

/**
 * Signals errors that occur when an object was (de)serialized. 
 * 
 * @author Sander Fieten <sander at holodeck-b2b.org>
 */
public class ObjectSerializationException extends Exception {

    public ObjectSerializationException() {
        super();
    }
    
    public ObjectSerializationException(String msg) {
        super(msg);
    }
    
    public ObjectSerializationException(String msg, Exception cause) {
        super(msg, cause);
    }
    
}