/*-
 * +======================================================================+
 * GSM
 * ---
 * Copyright (C) 2016 Sfera Labs S.r.l.
 * ---
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * -======================================================================-
 */

package cc.sferalabs.sfera.drivers.gsm.events;

import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.events.StringEvent;

/**
 * Event triggered when a SMS is received.
 * 
 * @sfera.event_id sms
 * @sfera.event_val text the text body of the SMS
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class SmsEvent extends StringEvent implements GsmEvent {

	private final String number;
	private final boolean error;

	/**
	 * 
	 * @param source
	 *            the source node
	 * @param number
	 *            the sender phone number
	 * @param body
	 *            the text body
	 * @param error
	 *            contains errors
	 */
	public SmsEvent(Node source, String number, String body, boolean error) {
		super(source, "sms", body);
		this.number = number;
		this.error = error;
	}

	/**
	 * 
	 * @return the phone number of the sender of the SMS
	 */
	public String getNumber() {
		return number;
	}

	/**
	 * 
	 * @return the text body of this SMS
	 */
	public String getBody() {
		return getValue();
	}

	/**
	 * 
	 * @return whether or not this SMS contains errors
	 */
	public boolean hasErrors() {
		return error;
	}
}
