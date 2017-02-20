package utils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import rcms.utilities.daqaggregator.persistence.StructureSerializer;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 * Discovers latest available setups and sets pointer on DAQSetup objects
 */

public class GetLatestTask implements Runnable{

	SetupManager setupManager;

	private static final Logger logger = Logger.getLogger(SetupDetectionTask.class);

	public GetLatestTask(SetupManager setupManager) {
		this.setupManager = setupManager;
	}

	@Override
	public void run() {
		Date tic = new Date();

		//act upon a copy of setups and only call setup manager objects just to set values, once the discovery jobs have finished

		List<DAQSetup> setups = setupManager.getAvailableSetups();

		Map<String, String> map = new HashMap<String, String>();

		for (DAQSetup setup : setups){
			String result = findLatestSnapshot(setup.getSnapshotPath());
			map.put(setup.getName(), result);
		}

		setupManager.updateLatestSnapshot(map);

		Date toc = new Date();
		logger.debug("Latest setup discovery task for "+map.size()+" setups took "+(toc.getTime()-tic.getTime())+" milliseconds");
	}

	private String findLatestSnapshot(String setupSnapshotPath) {
		String ret = ""; //path to a smile file

		//implement, based on hierarchical directory (find dir, max etc.)


		File root = new File(setupSnapshotPath);

		File [] years = root.listFiles();
		
		System.out.println(years.length>0? years[0]:"");
		



	return ret;

}

}
