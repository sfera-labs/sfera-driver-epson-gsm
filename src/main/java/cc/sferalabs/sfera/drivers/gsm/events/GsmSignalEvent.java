package cc.sferalabs.sfera.drivers.gsm.events;

import cc.sferalabs.sfera.events.Node;
import cc.sferalabs.sfera.events.NumberEvent;

/**
 * Event triggered when the GSM signal level changes.
 * 
 * @sfera.event_id signal
 * @sfera.event_val 0-31
 * 
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class GsmSignalEvent extends NumberEvent implements GsmEvent {

	public GsmSignalEvent(Node source, int value) {
		super(source, "signal", value);
	}

}
