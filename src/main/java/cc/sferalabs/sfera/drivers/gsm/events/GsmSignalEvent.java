package cc.sferalabs.sfera.drivers.gsm.events;

import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.events.NumberEvent;

public class GsmSignalEvent extends NumberEvent implements GsmEvent {

	public GsmSignalEvent(Node source, int value) {
		super(source, "signal", value);
	}

}
