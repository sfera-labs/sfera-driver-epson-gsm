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

class PartialSms {

	private final String[] parts;
	private final int numOfParts;
	private boolean error = false;

	/**
	 * 
	 * @param numOfParts
	 * @throws IllegalArgumentException
	 */
	public PartialSms(int numOfParts) throws IllegalArgumentException {
		if (numOfParts <= 0) {
			throw new IllegalArgumentException("number of parts = " + numOfParts);
		}

		this.numOfParts = numOfParts;
		parts = new String[numOfParts];
	}

	/**
	 * 
	 * @param partNum
	 * @param msg
	 * @param error
	 */
	public void addPart(int partNum, String msg, boolean error) {
		if (partNum < 1 || partNum > numOfParts) {
			error = true;
		}
		this.error = error;
		parts[partNum - 1] = msg;
	}

	/**
	 * 
	 * @return
	 */
	public String getCompleteMessage() {
		StringBuilder ret = new StringBuilder();
		for (String part : parts) {
			if (part == null) {
				return null;
			}
			ret.append(part);
		}

		return ret.toString();
	}

	public boolean hasErrors() {
		return error;
	}
}
