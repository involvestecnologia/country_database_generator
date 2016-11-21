import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.geonames.GeoNamesException;
import org.geonames.Style;
import org.geonames.Toponym;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;

/**
 * 
 * @author gabriel
 * 
 */
public class Generator {

	/*
	 * get this at
	 * http://api.geonames.org/countryInfo?username=[username]&country=[ISO CODE]
	 */
	private static final int COUNTRY_CODE = 6252001;

	// start id of state register
	private static final int START_STATE_ID = 1400;

	// file where the result SQL will be written
	private static final String DESTINY_FILE = "c:\\deploy\\eua.sql";

	// the level of state representation, ex: country (0) -> region (1) ->
	// state/province (2)
	private static final int STATE_LEVEL = 1;

	// the level of state representation, ex: country (0) -> region (1) ->
	// state/province (2) -> city (3)
	private static final int CITY_LEVEL = 2;

	// the language of the result file
	private static final String LANGUAGE = "en";

	// do not change this variables values
	private static int currentStateId;
	private static FileWriter writer;
	private static int requestCounter = 0;

	/**
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		WebService.setUserName(args[0]);
		
		Toponym country = getCountry(COUNTRY_CODE, LANGUAGE, Style.SHORT.name());
		System.out.println("Criando SQL de " + country.getCountryCode() + " - " + country.getCountryName());
		File file = new File(DESTINY_FILE);
		if (!file.exists())
			file.createNewFile();

		writer = new FileWriter(file);
		currentStateId = START_STATE_ID;

		generateSql(country);
		writer.flush();
		writer.close();

		System.out.println("SQL finalizado, verifique em " + file.getAbsolutePath() + " com " + requestCounter + " requests");

	}

	private static void generateSql(Toponym country) throws Exception {
		int currentLevel = 1;

		List<Toponym> states = null;
		ToponymSearchResult children = getChildren(country.getGeoNameId());
		states = getAllChildrenAtLevel(children.getToponyms(), currentLevel, STATE_LEVEL);

		for (Toponym state : states) {
			generateStateSql(state, currentStateId, STATE_LEVEL);
			currentStateId++;
		}

	}

	private static void generateStateSql(Toponym state, int stateId, int currentLevel) throws Exception {
		System.out.println("adding state " + state.getName());
		String stateCode = state.getAdminCode1ISO();
		if (stateCode == null)
			stateCode = getStateCode(state.getAlternateNames());

		String sql = "INSERT INTO ESTADO(id, nome, sigla, regiao, country_code) VALUES(" + stateId + ",'"
				+ state.getName() + "','" + stateCode + "','','" + state.getCountryCode() + "');\n";

		writer.write(sql);

		currentLevel++;
		ToponymSearchResult children = getChildren(state.getGeoNameId());
		List<Toponym> cities = getAllChildrenAtLevel(children.getToponyms(), currentLevel, CITY_LEVEL);
		for (Toponym city : cities) {

			generateCitySql(city, stateId);
		}
		currentLevel--;

	}

	private static void generateCitySql(Toponym city, int stateId) throws Exception {
		String cityName = city.getName();
		if (cityName.contains("Barrio")) {
			return;
		}

		System.out.println("adding city " + cityName + " type: " + city.getFeatureCode());
		if (cityName.contains("'")) {
			String sql = "INSERT INTO CIDADE(id_estado,nome) VALUES (" + stateId + ",\"" + cityName + "\");\n";
			writer.write(sql);
		} else {
			String sql = "INSERT INTO CIDADE(id_estado,nome) VALUES (" + stateId + ",'" + cityName + "');\n";
			writer.write(sql);
		}
	}

	private static List<Toponym> getAllChildrenAtLevel(List<Toponym> bases, int currentLevel, int childLevel)
			throws Exception {
		if (currentLevel == childLevel) {
			return bases;
		} else {
			List<Toponym> result = new ArrayList<>();
			currentLevel++;
			for (Toponym topo : bases) {
				ToponymSearchResult children = getChildren(topo.getGeoNameId());
				result.addAll(children.getToponyms());
			}

			return getAllChildrenAtLevel(result, currentLevel, childLevel);
		}

	}

	private static String getStateCode(String alternateNames) {
		String[] split = alternateNames.split(",");
		String result = null;

		for (String s : split) {
			if (result == null || result.length() > s.length()) {
				result = s;
			}
		}

		return result;
	}

	public static Toponym getCountry(int geocodeId, String language, String style) throws Exception {
		try {
			Toponym country = WebService.get(geocodeId, language, style);
			requestCounter++;
			return country;
		} catch (GeoNamesException e) {
			if (e.getMessage().contains("hourly limit of")) {
				System.err.println("API request hourly limit exceeded, waiting 10 minutes. Message: " + e.getMessage());
				Thread.currentThread().sleep(10 * 60 * 1000);

				return (getCountry(geocodeId, language, style));
			} else {

				// outra expcetion
				throw e;
			}
		}
	}

	public static ToponymSearchResult getChildren(int geonameId) throws Exception {
		try {
			ToponymSearchResult children = WebService.children(geonameId, LANGUAGE, Style.FULL);
			requestCounter++;

			return children;
		} catch (GeoNamesException e) {
			if (e.getMessage().contains("hourly limit of")) {
				System.err.println("API request hourly limit exceeded, waiting 10 minutes. Message: " + e.getMessage());
				Thread.currentThread().sleep(10 * 60 * 1000);

				return (getChildren(geonameId));
			} else {

				// outra expcetion
				throw e;
			}
		}

	}

}
