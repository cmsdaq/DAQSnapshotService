package tasks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.log4j.Logger;

import rcms.utilities.daqaggregator.data.DAQ;
import rcms.utilities.daqaggregator.persistence.PersistenceFormat;
import rcms.utilities.daqaggregator.persistence.StructureSerializer;
import utils.DAQSetup;
import utils.SetupManager;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch) Discovers latest available setups and sets pointer on
 *         DAQSetup objects
 */

public class GetLatestTask implements Runnable {

	SetupManager setupManager;

	private static final Logger logger = Logger.getLogger(GetLatestTask.class);

	private Map<String, String> lastKnownSnapshotsBySetupPath = new HashMap<>(20);
	private Map<String, String> knownSetupPathsBySetupName = new HashMap<>(20);
	private Set<String> knownSetups = new HashSet<>(20);

	public GetLatestTask(SetupManager setupManager) {
		this.setupManager = setupManager;
	}

	@Override
	public void run() {
		Date tic = new Date();

		// act upon a copy of setups and only call setup manager objects just to set values, once the discovery jobs
		// have finished

		List<DAQSetup> setups = setupManager.getAvailableSetups();

		Map<String, String> map = new HashMap<String, String>();

		Set<String> newSetups = new HashSet<>();
		for (DAQSetup setup : setups) {
			String setupName = setup.getName();

			newSetups.add(setupName);

			try {
				String result = findLatestSnapshot(setup.getSnapshotPath());
				if (result != null) {
					map.put(setupName, result);
				}
			} catch (Exception e) {
				logger.warn("Failed finding the latest snapshot for setup: " + setup.getName());
			}
		}

		for (String knownSetup: this.knownSetups) {
			if (!newSetups.contains(knownSetup)) {
				String path = this.knownSetupPathsBySetupName.remove(knownSetup);
				this.lastKnownSnapshotsBySetupPath.remove(path);
			}
		}
		this.knownSetups.addAll(newSetups);


		setupManager.updateLatestSnapshot(map);

		Date toc = new Date();
		logger.debug("Latest setup discovery task for " + map.size() + " setups took " + (toc.getTime() - tic.getTime())
				+ " milliseconds");
	}

	private String findLatestSnapshot(String setupSnapshotPath) {
		String path; // path to a smile file
		String ret = "";

		try {
			// implementation based on the temporal ordering of directories and files in the time-based hierarchy

			File root = new File(setupSnapshotPath);

			// case when no snapshots have been produced for this setup
			if (root.length() == 0) {
				return null;
			}

			File[] years = root.listFiles(); // dirs of year

			int maxYear = getMax(years); // position for max year

			File[] months = years[maxYear].listFiles();

			int maxMonth = getMax(months); // position for max month

			File[] days = months[maxMonth].listFiles();

			int maxDay = getMax(days); // position for max day

			File[] hours = days[maxDay].listFiles();

			int maxHour = getMax(hours); // position for max hour

			File[] snapshots = hours[maxHour].listFiles();

			// if snapshots in this hour were not found (in practice should not occur)
			if (snapshots.length == 0) {
				return null;
			}

			int maxSnapshotTimestamp = getMax(snapshots); // position for snapshot file at max unix timestamp

			// if newest snapshot in this hour was not found (first file discovered is tmp)
			if (snapshots.length == -1) {
				return null;
			}

			path = snapshots[maxSnapshotTimestamp].getAbsolutePath();

			String lastKnownSnapshotPath = this.lastKnownSnapshotsBySetupPath.get(setupSnapshotPath);
			if (lastKnownSnapshotPath != null && lastKnownSnapshotPath.equals(path)) {
				return null;
			} else {
				this.lastKnownSnapshotsBySetupPath.put(setupSnapshotPath, path);
			}

			logger.debug("Newest snapshot in " + setupSnapshotPath + " > " + snapshots[maxSnapshotTimestamp].getName());

		} catch (RuntimeException e) {
			e.printStackTrace();
			logger.error("Could not find latest snapshot under root: " + setupSnapshotPath);
			return null;
		}

		// deserialization of snapshot
		ret = deserializeSnapshot(path); // can be null or a deserialized snapshot in json string
		logger.debug("Deserialized: " + ret.substring(0, 500));
		return ret;

	}

	private String deserializeSnapshot(String path) {
		String json;

		try {
			logger.trace("Deserializing snapshot: " + path);
			DAQ result = loadSnapshot(path);

			logger.trace("Deserialized snapshot (accessing timestamp): " + result.getLastUpdate());

			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			StructureSerializer ss = new StructureSerializer();

			logger.trace("Serializing snapshot...");

			// the usual client of getLatest requests needs the most compact possible format
			ss.serialize(result, baos, PersistenceFormat.JSONREFPREFIXEDUGLY);

			logger.trace("Serialized.");

			json = baos.toString(java.nio.charset.StandardCharsets.UTF_8.toString());

		} catch (Exception e) {
			e.printStackTrace();
			logger.error("Could not deserialize snapshot");
			return null;
		}

		return json;
	}

	private DAQ loadSnapshot(String filepath) {
		DAQ ret = null;
		StructureSerializer structurePersistor = new StructureSerializer();
		ret = structurePersistor.deserialize(filepath);
		return ret;
	}

	private int getMax(File[] items) {
		int posAtMax = 0; // position of File array where the maximum value is
		long max = -1;

		// case files (snapshots)
		if (items[0].getName().contains(".")) {
			for (int i = 0; i < items.length; i++) {

				if(!items[i].getName().endsWith(".tmp")) {

					long value = Long.parseLong(items[i].getName().split("\\.")[0]);

					if (value > max) {
						max = value;
						posAtMax = i;
					}
				}else{
					logger.info("Ignoring tmp file: " + items[i].getName());
				}
			}
		}
		// case dirs (parent directories of snapshots)
		else {
			for (int i = 0; i < items.length; i++) {

				long value = Long.parseLong(items[i].getName());

				if (value > max) {
					max = value;
					posAtMax = i;
				}
			}
		}

		return posAtMax;
	}

}
