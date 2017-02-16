package utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import rcms.utilities.daqaggregator.data.DAQ;
import rcms.utilities.daqaggregator.persistence.FileSystemConnector;
import rcms.utilities.daqaggregator.persistence.PersistenceExplorer;
import rcms.utilities.daqaggregator.persistence.PersistenceFormat;
import rcms.utilities.daqaggregator.persistence.PersistorManager;
import rcms.utilities.daqaggregator.persistence.StructureSerializer;

/**
 * 
 * @author Maciej Gladki (maciej.szymon.gladki@cern.ch)
 */
 
public class APIPersistorManager extends PersistorManager {

	private static final Logger logger = Logger.getLogger(APIPersistorManager.class);

	private final PersistenceExplorer persistenceExplorer;

	public APIPersistorManager(String persistenceDir) {
		super(persistenceDir, null, PersistenceFormat.SMILE, null);
		persistenceExplorer = new PersistenceExplorer(new FileSystemConnector());
	}

	private ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * Find snapshot which is the closest to given date
	 * 
	 * @param date
	 *            requested date to find snapshot
	 * @return DAQ snapshot found for given date
	 * @throws IOException
	 */
	public DAQ findSnapshot(Date date) throws IOException {
		List<File> candidates = new ArrayList<>();
		String candidateDir = null;

		logger.debug("Searching snapshot for date: " + date + ", base dir: " + snapshotPersistenceDir);

		candidateDir = this.getTimeDir(snapshotPersistenceDir, date);

		logger.debug("Candidates will be searched in " + candidateDir);
		
		try {
			candidates.addAll(persistenceExplorer.getFileSystemConnector().getFiles(candidateDir));
		} catch (FileNotFoundException e) {
			candidates = new ArrayList<>();
			logger.warn("Cannot access persistence dir: "+candidateDir+", ignoring...");
		}

		return findSnapshot(date, candidates);

	}
	
	public DAQ findSnapshot(Date date, String setupPath) throws IOException {
		
		List<File> candidates = new ArrayList<>();
		String candidateDir = null;
		
		logger.debug("Searching snapshot for date: " + date + ", base dir: " + setupPath);

		candidateDir = this.getTimeDir(setupPath, date);

		logger.debug("Candidates will be searched in " + candidateDir);

		try {
			candidates.addAll(persistenceExplorer.getFileSystemConnector().getFiles(candidateDir));
		} catch (FileNotFoundException e) {
			
			candidates = new ArrayList<>();
			logger.warn("Cannot access persistence dir, ignoring...");
		}

		return findSnapshot(date, candidates);

	}

	private DAQ findSnapshot(Date date, List<File> candidates) {
		StructureSerializer structurePersistor = new StructureSerializer();
		try {

			if (candidates.size() == 0) {
				logger.error("No files to process");
				return null;
			}
			Collections.sort(candidates, FileSystemConnector.FileComparator);

			long diff = Integer.MAX_VALUE;
			String bestFile = null;
			DAQ best = null;
			for (File path : candidates) {

				String currentName = path.getAbsolutePath().toString();
				String dateFromFileName = path.getName();
				if (dateFromFileName.contains(".")) {
					int indexOfDot = dateFromFileName.indexOf(".");
					dateFromFileName = dateFromFileName.substring(0, indexOfDot);
				}
				Date currentDate;
				currentDate = objectMapper.readValue(dateFromFileName, Date.class);

				logger.trace("Current file: " + currentName);

				if (bestFile == null) {
					bestFile = currentName;
					continue;
				}

				long currDiff = date.getTime() - currentDate.getTime();

				if (Math.abs(currDiff) < diff) {
					bestFile = currentName;
					diff = Math.abs(currDiff);
				}
			}

			logger.debug("Best file found: " + bestFile + " with time diff: " + diff + "ms.");
			best = structurePersistor.deserialize(bestFile, PersistenceFormat.SMILE);
			return best;

		} catch (IOException e) {
			logger.error("IO problem finding snapshot", e);
		}

		logger.warn("No snapshot found for date " + date);
		return null;
	}
	
	public String getDefaultSnapshotPersistenceDir(){
		return snapshotPersistenceDir;
	}
}
