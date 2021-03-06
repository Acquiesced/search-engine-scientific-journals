package src.project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Eval {

	private static final String COLLECTIONSFILE = "cacm/cacm.all";
	private static final String QUERYFILE = "cacm/query.text";
	private static final String QUERYJUDGMENTFILE = "cacm/qrels.text";
	private static final String EVALFILE = "eval.txt";

	static HashMap<Integer, Query> queries = new HashMap<Integer, Query>();
	static TreeMap<Integer, Set<Integer>> qrels = new TreeMap<Integer, Set<Integer>>();

	static HashMap<Integer, DocumentRecord> documents = new HashMap<Integer, DocumentRecord>();
	static TreeMap<String, DictionaryRecord> dictionary = new TreeMap<String, DictionaryRecord>();
	static TreeMap<String, TreeMap<Integer, Posting>> postingsList = new TreeMap<String, TreeMap<Integer, Posting>>();
	static ArrayList<CitationRecord> citationList = new ArrayList<CitationRecord>();

	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		Invert inverter = new Invert(COLLECTIONSFILE);
		Options options = new Options();
		double idfThreshold = 1.0;
		int maxResults = 15;
		double w1 = 0.5;
		double w2 = 0.5;
		boolean useStemming = false;
		boolean useStopWords = false;
		options.addOption("stem", false, "Enable stemming");
		options.addOption("stopwords", false, "Enable stop word removal");
		options.addOption("create", false, "Create index input files");
		options.addOption("help", "Show this menu");
		OptionBuilder.withLongOpt("idf");
		OptionBuilder.withDescription("Idf threshold");
		OptionBuilder.withType(Number.class);
		OptionBuilder.hasArg();
		OptionBuilder.withArgName("argname");
		options.addOption(OptionBuilder.create());
		OptionBuilder.withLongOpt("maxresults");
		OptionBuilder.withDescription("Maximum number of entries to retrieve");
		OptionBuilder.withType(Number.class);
		OptionBuilder.hasArg();
		OptionBuilder.withArgName("argname");
		options.addOption(OptionBuilder.create());
		OptionBuilder.withLongOpt("w1");
		OptionBuilder.withDescription("w1 value");
		OptionBuilder.withType(Number.class);
		OptionBuilder.hasArg();
		OptionBuilder.withArgName("argname");
		options.addOption(OptionBuilder.create());
		OptionBuilder.withLongOpt("w2");
		OptionBuilder.withDescription("w2 value");
		OptionBuilder.withType(Number.class);
		OptionBuilder.hasArg();
		OptionBuilder.withArgName("argname");
		options.addOption(OptionBuilder.create());

		CommandLineParser parser = new DefaultParser();

		try {
			CommandLine cmd = parser.parse(options, args);
			if (cmd.hasOption("stem")) {
				inverter.setUseStemming(true);
				useStemming = true;
			}
			if (cmd.hasOption("stopwords")) {
				inverter.setUseStopWords(true);
				useStopWords = true;
			}
			if (cmd.hasOption("create"))
				inverter.setCreateNewIndex(true);
			if (cmd.hasOption("idf")) {
				idfThreshold = ((Number) cmd.getParsedOptionValue("idf")).doubleValue();
			}
			if (cmd.hasOption("maxresults")) {
				maxResults = ((Number) cmd.getParsedOptionValue("maxresults")).intValue();
			}
			if (cmd.hasOption("w1")) {
				w1 = ((Number) cmd.getParsedOptionValue("w1")).doubleValue();
			}
			if (cmd.hasOption("w2")) {
				w2 = ((Number) cmd.getParsedOptionValue("w2")).doubleValue();
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}

		Parse queryParser = new Parse(QUERYFILE);
		queryParser.parseQueryFile(queries);

		Parse qrelsParser = new Parse(QUERYJUDGMENTFILE);
		qrelsParser.parseqrels(qrels);

		inverter.initializeIndex(documents, dictionary, postingsList);
		documents = inverter.getDocuments();
		dictionary = inverter.getDictionary();
		postingsList = inverter.getPostingsList();
		
		// send w1 and w2
		Search search = new Search(documents, dictionary, postingsList, useStemming, useStopWords, idfThreshold,
				maxResults, w1, w2);

		File eval = new File(EVALFILE);
		FileWriter writer = null;
		try {
			writer = new FileWriter(eval);

			List<DocumentRecord> resultSet = null;
			ArrayList<Double> precisionList = null;
			ArrayList<Double> MAPList = new ArrayList<Double>();
			ArrayList<Double> RPrecisionList = new ArrayList<Double>();

			double precision = 0.0;
			double MAP = 0.0;
			double RPrecision = 0.0;
			for (Map.Entry<Integer, Set<Integer>> entry : qrels.entrySet()) {
				int id = entry.getKey();
				Query query = queries.get(id);

				System.out.println("Query #" + query.getId() + ": " + query.getQueryText());
				writer.write("Query #" + query.getId() + ": " + query.getQueryText());
				
				resultSet = search.getResultSubList(query.getQueryText());
				
				System.out.println("All results retrieved: ");
				writer.write("\nAll results retrieved: ");
				for (DocumentRecord d: resultSet) {
					System.out.println(d.displayDocumentScoreInfo());
					writer.write("\n");
					writer.write(d.displayDocumentScoreInfo());
				}

				Set<Integer> relevantDocIDs = qrels.get(query.getId());
				int relevantDocCount = 0;
				double qrelTotal = relevantDocIDs.size();

				if (!resultSet.isEmpty()) {
					precisionList = new ArrayList<Double>();
					for (int i = 0; i < resultSet.size(); i++) {
						DocumentRecord d = resultSet.get(i);
						if (relevantDocIDs.contains(d.getId())) {
							relevantDocCount++;
							System.out.println("Matching document with id#: " + d.getId() + " found.");
							writer.write("\nMatching document with id#: " + d.getId() + " found.");
						}
						precision = relevantDocCount / (i + 1.0);
						precisionList.add(precision);
					}
				} else {
					precision = 0;
				}

				double sum = 0.0;
				for (Double i : precisionList) {
					sum += i;
				}
				if (resultSet.size() > 0) {
					MAP = sum / resultSet.size();
				} else {
					MAP = 0.0;
				}
				MAPList.add(MAP);

				RPrecision = relevantDocCount / qrelTotal;
				RPrecisionList.add(RPrecision);

				System.out.println("Number of Expected CACM Matched Documents Retrieved: " + relevantDocCount);
				System.out.println("MAP: " + MAP);
				System.out.println("R-Precision: " + RPrecision);
				System.out.println("------------------------------------------");
				writer.write("\n");
				writer.write("Number of Expected CACM Matched Documents Retrieved: " + relevantDocCount);
				writer.write("\n");
				writer.write("MAP: " + MAP);
				writer.write("\n");
				writer.write("R-Precision: " + RPrecision);
				writer.write("\n");
				writer.write("------------------------------------------");
				writer.write("\n");
			}
			double RPrecisionSum = 0.0;
			for (Double i : RPrecisionList) {
				RPrecisionSum += i;
			}

			double MAPSum = 0.0;
			for (Double i : MAPList) {
				MAPSum += i;
			}

			System.out.println("Average R-Precision: " + RPrecisionSum / RPrecisionList.size());
			System.out.println("Average MAP: " + MAPSum / MAPList.size());
			System.out.println("Total Queries: " + qrels.size());

			writer.write("Average R-Precision: " + RPrecisionSum / RPrecisionList.size());
			writer.write("\n");
			writer.write("Average MAP: " + MAPSum / MAPList.size());
			writer.write("\n");
			writer.write("Total Queries: " + qrels.size());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

}
