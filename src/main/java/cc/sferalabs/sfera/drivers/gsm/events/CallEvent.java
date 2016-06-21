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
