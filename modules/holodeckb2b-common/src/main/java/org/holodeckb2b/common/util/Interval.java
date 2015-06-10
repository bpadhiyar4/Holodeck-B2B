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
package org.holodeckb2b.common.util;

import java.util.concurrent.TimeUnit;

/**
 * Represents a time interval defined by the length of the interval and the unit of time
 * the length is specified in.
 * 
 * @author Sander Fieten <sander@holodeck-b2b.org>
 */
public class Interval {
    
    /**
     * The unit of time the interval is specified in
     */
    private TimeUnit  unit;
    
    /**
     * The length of the interval
     */
    private long  length;

    /**
     * Create an Interval with specified length
     */
    public Interval(long length, TimeUnit unit) {
        this.length = length;
        this.unit = unit;
    }
        
    /**
     * Gets the unit of time the interval is specified in
     * 
     * @return the unit
     */
    public TimeUnit getUnit() {
        return unit;
    }

    /**
     * Sets the unit of time the interval is specified in
     * 
     * @param unit the unit to set
     */
    public void setUnit(TimeUnit unit) {
        this.unit = unit;
    }

    /**
     * Gets the length of the interval
     * 
     * @return the length
     */
    public long getLength() {
        return length;
    }

    /**
     * Sets the length of the interval
     * 
     * @param length the length to set
     */
    public void setLength(long length) {
        this.length = length;
    }
    
    /**
     * Determines if two <code>Interval</code> objects are equal, i.e. last the same amount of time.
     * <p>NOTE: Currently both the length of the interval and time unit must be equal. The intervals
     * are not recalculated to a common unit of time!
     * 
     * @param   o   The object to compare with, should be an instance of {@link Interval} 
     * @return      <code>true</code> if <code>this.length == i.length && this.unit == i.unit</code>,<br>
     *              <code>false</code> otherwise
     */
    public boolean equals(Object o) {
        if (o instanceof Interval) 
            return this.length == ((Interval) o).length && this.unit == ((Interval) o).unit;
        else
            return false;
    }
    
}