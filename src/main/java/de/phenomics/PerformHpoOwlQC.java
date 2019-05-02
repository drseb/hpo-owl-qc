package de.phenomics;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Very simple text-based QC of hp-edit.owl. No real logical test, i.e. no
 * owl-api involved so far
 * 
 * @author sebastiankohler
 *
 */
public class PerformHpoOwlQC {

	private static final Pattern subclassOfPattern = Pattern.compile("SubClassOf\\(<(.+)> <(.+)>\\)");
	private static final Pattern purlPattern = Pattern.compile("^http://purl.obolibrary.org/obo/(.+)_.+$");
	private static final Pattern labelLayPattern = Pattern.compile("rdfs:label.*HP_.*layperson");
	private static final Pattern emptyAnnotationPattern = Pattern.compile("^Annotation.+\"\"");
	private static final Pattern hpoIdPattern = Pattern.compile("HP_\\d{7}");

	/**
	 * Very simple text-based QC of hp-edit.owl. No real logical test, i.e. no
	 * owl-api involved so far
	 * 
	 * @param hp
	 *            hp-edit.owl
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {

		System.out.println("Test: PerformHpoOwlQC");

		PerformHpoOwlQC qc = new PerformHpoOwlQC();

		HashMap<String, String> namespace2namespaceWhitelist = qc.getWhitelist();

		/*
		 * TODO : use proper cmd-line parser...
		 */
		String hpEditOwlFile = args[0];
		BufferedReader in = new BufferedReader(new FileReader(hpEditOwlFile));
		String line = null;
		HashSet<String> subclassProblems = new HashSet<String>();
		HashSet<String> logicalDefLines = new HashSet<String>();
		HashSet<String> logicalDefProblems = new HashSet<String>();
		HashSet<String> synonymTypeProblems = new HashSet<String>();
		HashMap<String, String> hpoid2defLine = new HashMap<>();
		HashMap<String, String> hpoid2commentLine = new HashMap<>();

		while ((line = in.readLine()) != null) {

			// test for tabs
			if (line.contains("\\t")) {
				System.out.println("found illegal tab in line: " + line);
				System.exit(1);
			}

			// test for laysynomys at labels
			Matcher matcherLayLabel = labelLayPattern.matcher(line);
			if (matcherLayLabel.find()) {
				System.out.println("found primary label of HP class to be asserted as lay: " + line);
				System.exit(1);
			}

			// test for empty annotations at labels
			Matcher matcherEmpty = emptyAnnotationPattern.matcher(line);
			if (matcherEmpty.find()) {
				System.out.println("found empty annotation in line: " + line);
				System.exit(1);
			}

			// test for usage of BFO quality
			if (line.contains("BFO_0000019")) {
				System.out.println("found illegal usage of 'BFO quality' in line: " + line);
				System.exit(1);
			}

			// no dc:creator in annotations
			if (line.contains("AnnotationAssertion") && line.contains("dc:creator")) {
				System.out.println("found illegal usage of dc:creator in annotation assertions in line: " + line);
				System.exit(1);
			}

			// duplicated definitions
			if (line.contains("AnnotationAssertion") && line.contains("IAO_0000115")) {
				Matcher hpoIdMatcher = hpoIdPattern.matcher(line);
				if (hpoIdMatcher.find()) {
					String hpoId = hpoIdMatcher.group();
					if (hpoid2defLine.containsKey(hpoId)) {
						System.out.println("found two defintions for one class! Please fix! See HPO-id: " + hpoId);
						System.exit(1);
					}
					else {
						hpoid2defLine.put(hpoId, line);
					}
				}
			}

			// duplicated comments
			if (line.contains("AnnotationAssertion") && line.contains("rdfs:comment")) {
				Matcher hpoIdMatcher = hpoIdPattern.matcher(line);
				if (hpoIdMatcher.find()) {
					String hpoId = hpoIdMatcher.group();
					if (hpoid2commentLine.containsKey(hpoId)) {
						System.out.println("found two comments for one class! Please fix! See HPO-id: " + hpoId);
						System.exit(1);
					}
					else {
						hpoid2commentLine.put(hpoId, line);
					}
				}
			}

			// test the subclass axioms
			Matcher matcherSubclassOf = subclassOfPattern.matcher(line);
			if (matcherSubclassOf.find()) {
				String purl1 = matcherSubclassOf.group(1);
				String purl2 = matcherSubclassOf.group(2);

				String n1 = qc.getNameSpace(purl1);
				String n2 = qc.getNameSpace(purl2);

				if (n1.equals(n2))
					continue; // always ok

				if (!namespace2namespaceWhitelist.containsKey(n1))
					subclassProblems.add(line);
				else if (!(namespace2namespaceWhitelist.get(n1).equals(n2)))
					subclassProblems.add(line);
			}

			// check if line is a logical def, i.e. EquivalentClasses-Axiom
			if (line.startsWith("EquivalentClasses") && line.contains("ObjectSomeValuesFrom")) {
				// have we seen this line before
				if (logicalDefLines.contains(line)) {
					// add to lines that are problematic
					logicalDefProblems.add(line);
				}
				else {
					// store that we have seen this line
					logicalDefLines.add(line);
				}
			}
			// check if oboInOwl#hasSynonymType is followed by
			// "<http://purl.obolibrary.org/obo/hp.owl#XYZ>"
			if (line.startsWith("AnnotationAssertion") && line.contains("hasSynonymType")) {
				if (!line.contains("hasSynonymType> <http://purl.obolibrary.org/obo/")) {
					synonymTypeProblems.add(line);
				}
			}
		}
		in.close();

		boolean foundProblem = false;
		// did we see problems with subclass-axioms?
		if (subclassProblems.size() > 0) {
			System.out.println("found problematic inter-ontology subclass axioms");
			for (String problem : subclassProblems) {
				System.out.println(" - " + problem);
			}
			foundProblem = true;
		}

		// did we see duplicated logical defs?
		if (logicalDefProblems.size() > 0) {
			System.out.println("found duplicated lines of logical definitions");
			for (String problem : logicalDefProblems) {
				System.out.println(" - " + problem);
			}
			foundProblem = true;
		}

		if (synonymTypeProblems.size() > 0) {
			System.out.println("found problematic synonym type definitions");
			for (String problem : synonymTypeProblems) {
				System.out.println(" - " + problem);
			}
			foundProblem = true;
		}

		if (foundProblem)
			System.exit(1);
		else
			System.out.println("everything ok");
	}

	private HashMap<String, String> getWhitelist() throws FileNotFoundException, IOException {

		BufferedReader whiteListIn = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream("/subclass_whitelist.txt")));

		String line = null;
		HashMap<String, String> namespace2namespaceWhitelist = new HashMap<String, String>();
		while ((line = whiteListIn.readLine()) != null) {
			String[] elems = line.split(",");
			namespace2namespaceWhitelist.put(elems[0], elems[1]);
		}
		whiteListIn.close();
		return namespace2namespaceWhitelist;
	}

	private String getNameSpace(String purl1) {
		Matcher m = purlPattern.matcher(purl1);
		if (m.find()) {
			return m.group(1);
		}
		return null;
	}

}
