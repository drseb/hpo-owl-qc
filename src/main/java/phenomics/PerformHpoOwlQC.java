package phenomics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PerformHpoOwlQC {

	static final Pattern p = Pattern.compile("SubClassOf\\(<(.+)> <(.+)>\\)");
	static final Pattern p2 = Pattern.compile("^http://purl.obolibrary.org/obo/(.+)_.+$");

	public static void main(String[] args) throws IOException {

		System.out.println("Test: PerformHpoOwlQC");

		File f = new File("src/main/resources/subclass_whitelist.txt");
		if (!f.exists()) {
			throw new RuntimeException("could not find subclass_whitelist.txt at " + f.getAbsolutePath());
		}
		BufferedReader whiteListIn = new BufferedReader(new FileReader(f));
		String line = null;
		HashMap<String, String> namespace2namespaceWhitelist = new HashMap<String, String>();
		while ((line = whiteListIn.readLine()) != null) {
			String[] elems = line.split(",");
			namespace2namespaceWhitelist.put(elems[0], elems[1]);
		}
		whiteListIn.close();

		String file = args[0];
		BufferedReader in = new BufferedReader(new FileReader(file));
		line = null;
		HashSet<String> problems = new HashSet<String>();
		while ((line = in.readLine()) != null) {
			Matcher m = p.matcher(line);
			if (m.find()) {
				String purl1 = m.group(1);
				String purl2 = m.group(2);

				String n1 = getNameSpace(purl1);
				String n2 = getNameSpace(purl2);

				if (n1.equals(n2))
					continue; // always ok

				if (!namespace2namespaceWhitelist.containsKey(n1))
					problems.add(line);
				else if (!(namespace2namespaceWhitelist.get(n1).equals(n2)))
					problems.add(line);
			}
		}
		in.close();
		if (problems.size() > 0) {
			System.out.println("found problematic inter-ontology subclass axioms");
			for (String problem : problems) {
				System.out.println(" - " + problem);
			}
			System.exit(1);
		}

		System.out.println("everything ok");
	}

	private static String getNameSpace(String purl1) {
		Matcher m = p2.matcher(purl1);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

}
