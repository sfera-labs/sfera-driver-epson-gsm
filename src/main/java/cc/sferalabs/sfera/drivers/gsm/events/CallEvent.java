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
 * Event triggered when an incoming call is in progress. An event is generated
 * for each "ring" during the call.
 * 
 * @sfera.event_id call
 * @sfera.event_val number phone number the call is coming from (the value is a
 *                  String, can be null if unknown)
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class CallEvent extends StringEvent implements GsmEvent {

	public CallEvent(Node source, String number) {
		super(source, "call", number);
	}

}
