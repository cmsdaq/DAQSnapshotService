package utils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import rcms.utilities.daqaggregator.data.DAQ;
import rcms.utilities.daqaggregator.persistence.PersistenceFormat;
import rcms.utilities.daqaggregator.persistence.StructureSerializer;

/**
 * 
 * @author Michail Vougioukas (michail.vougioukas@cern.ch)
 *
 * Class containing static functions that execute trivial tasks (I/O, conversions etc.)
 */
public class Helpers {
	
	
	private static final Logger logger = Logger.getLogger(Helpers.class);

	public static String readFileAsString(String path) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		Charset encoding = Charset.defaultCharset();
		return new String(encoded, encoding);
	}
	
	public static List<String> readFileAsLines(String path) throws IOException{
		List<String> lines = new ArrayList<String>();
		
		BufferedReader br = new BufferedReader (new FileReader(path));
		
		String line;
		while ((line = br.readLine()) != null){
			lines.add(line);
		}
		
		br.close();
		
		return lines;
	}

	public static int getMax(File[] items) {
	    String[] itemNames = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            itemNames[i] = items[i].getName();
        }

        return getMax(itemNames);
    }

	public static int getMax(String[] items) {
		int posAtMax = 0; // position of array where the maximum value is
		long max = -1;

		// case files (snapshots)
		if (items[0].contains(".")) {
			for (int i = 0; i < items.length; i++) {

				if (!items[i].endsWith(".tmp")) {

					long value = Long.parseLong(items[i].split("\\.")[0]);

					if (value > max) {
						max = value;
						posAtMax = i;
					}
				} else {
					logger.info("Ignoring tmp file: " + items[i]);
				}
			}
		}
		// case dirs (parent directories of snapshots)
		else {
			for (int i = 0; i < items.length; i++) {

				long value = Long.parseLong(items[i]);

				if (value > max) {
					max = value;
					posAtMax = i;
				}
			}
		}

		return posAtMax;
	}

	public static String deserializeSnapshot(String path) {
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

	private static StructureSerializer structurePersistor = new StructureSerializer();

	private static DAQ loadSnapshot(String filepath) {
		DAQ ret = null;
		ret = structurePersistor.deserialize(filepath);
		return ret;
	}
	
	public static long convertDateToMillis(Date d){
		long time = -1;
		//missing implementation
		return time;
	}
	
	public static Properties loadProps(String propertiesFile) {

		try {
			FileInputStream propertiesInputStream = new FileInputStream(propertiesFile);
			Properties properties = new Properties();
			properties.load(propertiesInputStream);
			propertiesInputStream.close();

			return properties;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			logger.error("Could not find a prop-styled file");
		} catch (IOException e) {
			e.printStackTrace();
			logger.error("Could not I/O a prop-styled file");
		}
		return null;
	}
	
	public static String getIterableElementsString(Iterable i){
		String ret = "";
		Iterator iter = i.iterator();
		while (iter.hasNext()){
			ret+=iter.next().toString()+",";
		}
		
		return ret.substring(0,ret.length()-1);
	}
}
