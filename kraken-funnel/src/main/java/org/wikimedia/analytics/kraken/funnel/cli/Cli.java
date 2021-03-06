package org.wikimedia.analytics.kraken.funnel.cli;

import java.net.MalformedURLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;

import org.wikimedia.analytics.kraken.exceptions.MalformedFunnelException;
import org.wikimedia.analytics.kraken.funnel.Funnel;
import org.wikimedia.analytics.kraken.funnel.Node;
import org.wikimedia.analytics.kraken.utils.DateUtils;
import org.wikimedia.analytics.kraken.utils.FileUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Cli {
	private static final String USAGE = "[-input <absolute path>] [-schema <schema>] [-node <node definition>] [-funnel <funnel definition>]";
	private static final String HEADER = "Funnel - A simple tool to conduct funnel analyses, Copyright 2012-2013 Wikimedia Foundation licensed under GPL2.\n.";
	private static final String FOOTER = "\nThis program was written by Diederik van Liere <dvanliere@wikimedia.org> and Dan Andreescu <dandreescu@wikimedia.org>\n";
	private String input;
	public String schema;
	private String rawEventLoggingData;
	private String funnelDefinition;
	private String nodeDefinition;
	private final Map<String, Map<Date, JsonObject>> jsonData = new HashMap<String, Map<Date, JsonObject>>();

	/**
	 * @param args should contain the options to start the funnel analysis.
	 * @throws MalformedFunnelException
	 */
	@SuppressWarnings("static-access")
	public static void main(String[] args) throws MalformedFunnelException {
		Cli cli = new Cli();
		CommandLineParser parser = new GnuParser();

		Options options = new Options();

		Option help = new Option("help", "print this message");
		Option input = OptionBuilder
				.withArgName("input")
				.hasArg()
				.withDescription(
						"path to single input file with eventLogging data, can be gzipped.")
				.create("input");
		Option schema = OptionBuilder.withArgName("schema").hasArg()
				.withDescription("Specify the name of the EventLogging schema")
				.create("schema");

		Option funnelDefinition = OptionBuilder.withArgName("funnel").hasArg()
				.withDescription("").create("funnel");
		Option nodeDefinition = OptionBuilder.withArgName("node").hasArg()
				.withDescription("").create("node");

		input.setRequired(true);
		schema.setRequired(true);
		funnelDefinition.setRequired(true);
		nodeDefinition.setRequired(true);

		options.addOption(input);
		options.addOption(schema);
		options.addOption(nodeDefinition);
		options.addOption(funnelDefinition);
		options.addOption(help);

		// automatically generate the help statement
		CommandLine line;
		try {
			// parse the command line arguments
			line = parser.parse(options, args);
			if (line.hasOption("input")) {
				cli.input = line.getOptionValue("input");
			}
			if (line.hasOption("schema")) {
				cli.schema = line.getOptionValue("schema");
			}
			if (line.hasOption("node")) {
				cli.nodeDefinition = line.getOptionValue("node");
			}
			if (line.hasOption("funnel")) {
				cli.funnelDefinition = line.getOptionValue("funnel");
			}
			if (line.hasOption("help")) {
				printUsage(options);
				System.exit(-1);
			}
		} catch (ParseException exp) {
			// oops, something went wrong
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			printUsage(options);
			System.exit(-1);
		}

		cli.rawEventLoggingData = FileUtils.unCompressGzipFile(cli.input);
		cli.readEventLoggingJsonData();
		Funnel funnel = new Funnel(cli.nodeDefinition, cli.funnelDefinition);
		Map<String, DirectedGraph<Node, DefaultEdge>> histories = funnel
				.constructUserGraph(cli.jsonData);
		for (Entry<String, DirectedGraph<Node, DefaultEdge>> kv : histories
				.entrySet()) {
			funnel.analysis(kv.getKey(), kv.getValue());
		}
        funnel.aggregateResults();
	}

	private static void printUsage(Options options) {
		HelpFormatter helpFormatter = new HelpFormatter();
		helpFormatter.setWidth(80);
		helpFormatter.printHelp(HEADER, USAGE, options, FOOTER);
	}

	public void readEventLoggingKVData() {
		//
	}

	public void readEventLoggingJsonData() {
		String[] lines = this.rawEventLoggingData.split("\n");
		JsonParser parser = new JsonParser();
        Map<Date, JsonObject> map;
		for (String line : lines) {
			JsonObject json = parser.parse(line).getAsJsonObject();
			JsonElement key = json.get("token");
			String schema = json.get("meta").getAsJsonObject().get("schema")
					.getAsString();
			if (this.schema.equals(schema)) {
				Date date = DateUtils.convertToDate(json.get("meta")
						.getAsJsonObject().get("timestamp").getAsLong());
				if (!jsonData.containsKey(key.toString())) {
					// TODO: We don't handle the case when events have the exact
					// same timestamp
					map = new HashMap<Date, JsonObject>();
                }   else {
                    map = jsonData.get(key.toString());
                }
                map.put(date, json);
                jsonData.put(key.toString(), map);
				}
				// System.out.println("key: " + key.toString() + "value: "
				// + json.toString());
		}
	}
}
