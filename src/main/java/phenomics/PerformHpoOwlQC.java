package phenomics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PerformHpoOwlQC {

	private static final Pattern subclassOfPattern = Pattern.compile("SubClassOf\\(<(.+)> <(.+)>\\)");
	private static final Pattern purlPattern = Pattern.compile("^http://purl.obolibrary.org/obo/(.+)_.+$");
	private static final Pattern labelLayPattern = Pattern.compile("rdfs:label.*HP_.*layperson");

	/**
	 * @param hp
	 *            hp-edit.owl
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		System.out.println("Test: PerformHpoOwlQC");

		HashMap<String, String> namespace2namespaceWhitelist = getWhitelist();

		String hpEditOwlFile = args[0];
		BufferedReader in = new BufferedReader(new FileReader(hpEditOwlFile));
		String line = null;
		HashSet<String> subclassProblems = new HashSet<String>();
		while ((line = in.readLine()) != null) {

			// test for tabs
			if (line.contains("\\t")) {
				System.out.println("found illegal tab in line: " + line);
				System.exit(1);
			}

			// test for laysynomys at labels
			Matcher matcherLayLabel = labelLayPattern.matcher(line);
			if (matcherLayLabel.find()) {
				System.out.println("found label of HP class to be asserted as lay: " + line);
				System.exit(1);
			}

			// test the subclass axioms
			Matcher matcherSubclassOf = subclassOfPattern.matcher(line);
			if (matcherSubclassOf.find()) {
				String purl1 = matcherSubclassOf.group(1);
				String purl2 = matcherSubclassOf.group(2);

				String n1 = getNameSpace(purl1);
				String n2 = getNameSpace(purl2);

				if (n1.equals(n2))
					continue; // always ok

				if (!namespace2namespaceWhitelist.containsKey(n1))
					subclassProblems.add(line);
				else if (!(namespace2namespaceWhitelist.get(n1).equals(n2)))
					subclassProblems.add(line);
			}
		}
		in.close();
		if (subclassProblems.size() > 0) {
			System.out.println("found problematic inter-ontology subclass axioms");
			for (String problem : subclassProblems) {
				System.out.println(" - " + problem);
			}
			System.exit(1);
		}

		System.out.println("everything ok");
	}

	private static HashMap<String, String> getWhitelist() throws FileNotFoundException, IOException {
		File whilteListFile = new File("src/main/resources/subclass_whitelist.txt");
		if (!whilteListFile.exists()) {
			throw new RuntimeException("could not find subclass_whitelist.txt at " + whilteListFile.getAbsolutePath());
		}
		BufferedReader whiteListIn = new BufferedReader(new FileReader(whilteListFile));
		String line = null;
		HashMap<String, String> namespace2namespaceWhitelist = new HashMap<String, String>();
		while ((line = whiteListIn.readLine()) != null) {
			String[] elems = line.split(",");
			namespace2namespaceWhitelist.put(elems[0], elems[1]);
		}
		whiteListIn.close();
		return namespace2namespaceWhitelist;
	}

	private static String getNameSpace(String purl1) {
		Matcher m = purlPattern.matcher(purl1);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

}
