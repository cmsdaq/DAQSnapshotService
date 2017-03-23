package tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import rcms.utilities.daqaggregator.data.DAQ;
import rcms.utilities.daqaggregator.persistence.PersistenceFormat;
import rcms.utilities.daqaggregator.persistence.StructureSerializer;
import utils.DAQSetup;
import utils.SetupManager;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 * Discovers latest available setups and sets pointer on DAQSetup objects
 */

public class GetLatestTask implements Runnable{

	SetupManager setupManager;

	private static final Logger logger = Logger.getLogger(GetLatestTask.class);

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
		String path; //path to a smile file
		String ret = "";

		try{
			//implementation based on the temporal ordering of directories and files in the time-based hierarchy

			File root = new File(setupSnapshotPath);

			//case when no snapshots have been produced for this setup
			if (root.length() == 0){
				return null;
			}

			File [] years = root.listFiles(); //dirs of year

			int maxYear = getMax(years); //position for max year

			File [] months = years[maxYear].listFiles();

			int maxMonth = getMax(months); //position for max month

			File [] days = months[maxMonth].listFiles();

			int maxDay = getMax(days); //position for max day

			File [] hours = days[maxDay].listFiles();

			int maxHour = getMax(hours); //position for max hour

			File [] snapshots = hours[maxHour].listFiles();

			//if snapshots in this hour were not found (in practice should not occur)
			if (snapshots.length == 0){
				return null;
			}

			int maxSnapshotTimestamp = getMax(snapshots); //position for snapshot file at max unix timestamp

			path = snapshots[maxSnapshotTimestamp].getAbsolutePath();

			logger.debug("Newest snapshot in "+setupSnapshotPath+" > "+snapshots[maxSnapshotTimestamp].getName());
			
		}catch(RuntimeException e){
			e.printStackTrace();
			logger.error("Could not find latest snapshot under root: "+setupSnapshotPath);
			return null;
		}

		//deserialization of snapshot
		ret = deserializeSnapshot(path); //can be null or a deserialized snapshot in json string
		return ret;

	}

	private String deserializeSnapshot(String path) {
		String json;

		try{
			DAQ result = loadSnapshot(path);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			StructureSerializer ss = new StructureSerializer();
			
			//the usual client of getLatest requests needs the most compact possible format
			ss.serialize(result, baos, PersistenceFormat.JSONREFPREFIXEDUGLY);

			json = baos.toString(java.nio.charset.StandardCharsets.UTF_8.toString());

		}catch (Exception e) {
			e.printStackTrace();
			logger.error("Could not deserialize snapshot");
			return null;
		}

		return json;
	}

	private DAQ loadSnapshot(String filepath){
		DAQ ret = null;
		StructureSerializer structurePersistor = new StructureSerializer();
		ret = structurePersistor.deserialize(filepath, PersistenceFormat.SMILE);
		return ret;
	}

	private int getMax(File[] items) {
		int posAtMax = 0; //position of File array where the maximum value is
		long max = -1;

		//case files (snapshots)
		if (items[0].getName().contains(".")){
			for (int i=0;i<items.length;i++){

				long value = Long.parseLong(items[i].getName().split("\\.")[0]);

				if (value>max){
					max = value;
					posAtMax = i;
				}
			}
		}
		//case dirs (parent directories of snapshots)
		else{
			for (int i=0;i<items.length;i++){

				long value = Long.parseLong(items[i].getName());

				if (value>max){
					max = value;
					posAtMax = i;
				}
			}
		}

		return posAtMax;
	}

}
