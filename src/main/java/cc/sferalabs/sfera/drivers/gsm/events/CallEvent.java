package cc.sferalabs.sfera.drivers.gsm.events;

import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.events.StringEvent;

public class CallEvent extends StringEvent implements GsmEvent {

	public CallEvent(Node source, String number) {
		super(source, "call", number);
	}

}
