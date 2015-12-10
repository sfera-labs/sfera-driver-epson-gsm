package cc.sferalabs.sfera.drivers.gsm.events;

import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.events.StringEvent;

public class SmsEvent extends StringEvent implements GsmEvent {

	private final String number;
	private final boolean error;

	/**
	 * 
	 * @param source
	 * @param number
	 * @param body
	 * @param error
	 */
	public SmsEvent(Node source, String number, String body, boolean error) {
		super(source, "sms", body);
		this.number = number;
		this.error = error;
	}

	/**
	 * 
	 * @return
	 */
	public String getNumber() {
		return number;
	}

	/**
	 * 
	 * @return
	 */
	public String getBody() {
		return getValue();
	}

	/**
	 * 
	 * @return
	 */
	public boolean hasErrors() {
		return error;
	}
}
