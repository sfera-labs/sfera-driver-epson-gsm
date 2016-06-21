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
