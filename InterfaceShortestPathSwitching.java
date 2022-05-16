package edu.nyu.cs.sdn.apps.util;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface InterfaceShortestPathSwitching extends IFloodlightService
{
	/**
	 * Get the table in which this application installs rules.
	 */
	public byte getTable();
}