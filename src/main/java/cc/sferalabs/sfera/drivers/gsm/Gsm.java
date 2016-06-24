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

package cc.sferalabs.sfera.drivers.gsm;

import cc.sferalabs.sfera.core.Configuration;
import cc.sferalabs.sfera.drivers.Driver;
import cc.sferalabs.sfera.io.comm.CommPort;
import cc.sferalabs.sfera.io.comm.CommPortException;

/**
 *
 * @author Giampiero Baggiani
 *
 * @version 1.0.0
 *
 */
public class Gsm extends Driver {

	private CommPort commPort;
	private int stateErrorCount;
	private CommunicationHandler commHandler;
	private boolean ready = false;

	public Gsm(String id) {
		super(id);
	}

	@Override
	protected boolean onInit(Configuration config) throws InterruptedException {
		ready = false;
		stateErrorCount = 0;

		String portName = config.get("address", null);
		if (portName == null) {
			log.error("Address not specified in configuration");
			return false;
		}

		try {
			commPort = CommPort.open(portName);
			int baudRate = config.get("baud_rate", 115200);
			int dataBits = config.get("data_bits", 8);
			int stopBits = config.get("stop_bits", 1);
			int parity = config.get("parity", CommPort.PARITY_NONE);
			int flowControl = config.get("flow_control", CommPort.FLOWCONTROL_RTSCTS);
			commPort.setParams(baudRate, dataBits, stopBits, parity, flowControl);

			Integer countryCode = config.get("country_code", null);
			boolean useSimPhase2 = config.get("sim_phase_2", false);
			commHandler = new CommunicationHandler(this, commPort, countryCode, useSimPhase2, log);
			commPort.setListener(commHandler);

			while (commPort.getAvailableBytesCount() > 0) {
				// let the listener consume the bytes
				Thread.sleep(500);
			}
			commHandler.clearMessages();

		} catch (CommPortException e) {
			log.error("Error initializing serial port", e);
			return false;
		}

		try {
			Integer pin = config.get("pin", null);
			String serviceCenterAddr = config.get("service_center", null);
			commHandler.initGsm(pin, serviceCenterAddr);
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error initializing GSM modem", e);
			return false;
		}

		ready = true;
		return true;
	}

	@Override
	protected boolean loop() throws InterruptedException {
		try {
			if (!commHandler.checkState()) {
				stateErrorCount++;
			} else {
				stateErrorCount = 0;
			}

			if (stateErrorCount > 5) {
				throw new Exception("too many errors");
			}

		} catch (InterruptedException e) {
			throw e;

		} catch (Exception e) {
			log.error("Exception in loop", e);
			return false;
		}

		Thread.sleep(10000);
		return true;
	}

	/**
	 * Sends the specified body text to the specified phone number.
	 * 
	 * @param to
	 *            the recipient phone number
	 * @param body
	 *            the body text to send
	 * @return whether or not the operation was successful
	 */
	public boolean send(long to, String body) {
		return send(Long.toString(to), body);
	}

	/**
	 * Sends the specified body text to the specified phone number.
	 * 
	 * @param to
	 *            the recipient phone number
	 * @param body
	 *            the body text to send
	 * @return whether or not the operation was successful
	 */
	public boolean send(String to, String body) {
		if (!ready) {
			return false;
		}
		return commHandler.send(to, body);
	}

	@Override
	protected void onQuit() {
		ready = false;
		if (commHandler != null) {
			commHandler.quit();
		}
		try {
			commPort.close();
		} catch (Exception e) {
		}
	}
}
