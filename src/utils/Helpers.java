package utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

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
			logger.warn("Could not find a prop-styled file");
		} catch (IOException e) {
			e.printStackTrace();
			logger.warn("Could not I/O a prop-styled file");
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
