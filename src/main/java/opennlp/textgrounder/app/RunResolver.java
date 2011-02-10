/*
 * This class runs the resolvers in opennlp.textgrounder.resolver
 */

package opennlp.textgrounder.app;

import opennlp.textgrounder.resolver.*;
import opennlp.textgrounder.text.*;
import opennlp.textgrounder.text.io.*;
import opennlp.textgrounder.text.prep.*;
import opennlp.textgrounder.topo.gaz.*;
import opennlp.textgrounder.eval.*;
import opennlp.textgrounder.util.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public class RunResolver extends BaseApp {

    public static void main(String[] args) throws Exception {

        initializeOptionsFromCommandLine(args);

        Tokenizer tokenizer = new OpenNLPTokenizer();

        StoredCorpus testCorpus = Corpus.createStoredCorpus();
        System.out.print("Reading corpus from " + getInputPath() + " ...");

        testCorpus.addSource(new TrXMLDirSource(new File(getInputPath()), tokenizer));
        //Uncomment these 4 lines and comment the one line above for system NER and multipoint representations with GeoNames:
        //GeoNamesGazetteer gnGaz = new GeoNamesGazetteer(new BufferedReader(
        //        new FileReader(Constants.getGazetteersDir() + File.separator + "allCountries.txt")));
        //testCorpus.addSource(new ToponymAnnotator(new ToponymRemover(new TrXMLDirSource(new File(getInputPath()), tokenizer)),
        //        new OpenNLPRecognizer(), gnGaz));
        
        testCorpus.load();
        System.out.println("done.");

        StoredCorpus trainCorpus = Corpus.createStoredCorpus();
        if(getAdditionalInputPath() != null) {
            System.out.print("Reading additional training corpus from " + getAdditionalInputPath() + " ...");
            List<Gazetteer> gazList = new ArrayList<Gazetteer>();
            LoadableGazetteer trGaz = new InMemoryGazetteer();
            trGaz.load(new CorpusGazetteerReader(testCorpus));
            LoadableGazetteer otherGaz = new InMemoryGazetteer();
            otherGaz.load(new WorldReader(new File(Constants.getGazetteersDir() + File.separator + "dataen-fixed.txt.gz")));
            gazList.add(trGaz);
            gazList.add(otherGaz);
            Gazetteer multiGaz = new MultiGazetteer(gazList);
            /*trainCorpus.addSource(new ToponymAnnotator(new PlainTextSource(
                    new BufferedReader(new FileReader(getAdditionalInputPath())), new OpenNLPSentenceDivider(), tokenizer),
                    new OpenNLPRecognizer(),
                    multiGaz));*/
            trainCorpus.addSource(new ToponymAnnotator(new GigawordSource(
                    new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(new FileInputStream(getAdditionalInputPath())))), 10, 40000),
                    new OpenNLPRecognizer(),
                    multiGaz));
            trainCorpus.addSource(new TrXMLDirSource(new File(getInputPath()), tokenizer));
            trainCorpus.load();
            System.out.println("done.");
        }

        System.out.println("\nNumber of documents: " + testCorpus.getDocumentCount());
        System.out.println("Number of toponym types: " + testCorpus.getToponymTypeCount());
        System.out.println("Maximum ambiguity (locations per toponym): " + testCorpus.getMaxToponymAmbiguity() + "\n");

        Resolver resolver;
        if(getResolverType() == RESOLVER_TYPE.RANDOM) {
            System.out.print("Running RANDOM resolver...");
            resolver = new RandomResolver();
        }
        else if(getResolverType() == RESOLVER_TYPE.WEIGHTED_MIN_DIST) {
            System.out.print("Running WEIGHTED MINIMUM DISTANCE resolver with " + getNumIterations() + " iteration(s)...");
            resolver = new WeightedMinDistResolver(getNumIterations());
        }
        else if(getResolverType() == RESOLVER_TYPE.LABEL_PROP_DEFAULT_RULE) {
            System.out.print("Running LABEL PROP DEFAULT RULE resolver, using graph at " + getGraphInputPath() + " ...");
            resolver = new LabelProcDefaultRuleResolver(getGraphInputPath());
        }
        else if(getResolverType() == RESOLVER_TYPE.LABEL_PROP_CONTEXT_SENSITIVE) {
            System.out.print("Running LABEL PROP CONTEXT SENSITIVE resolver, using graph at " + getGraphInputPath() + " ...");
            resolver = new LabelPropContextSensitiveResolver(getGraphInputPath());
        }
        else {//if(getResolverType() == RESOLVER_TYPE.BASIC_MIN_DIST) {
            System.out.print("Running BASIC MINIMUM DISTANCE resolver...");
            resolver = new BasicMinDistResolver();
        }

        if(getAdditionalInputPath() != null)
            resolver.train(trainCorpus);
        Corpus disambiguated = resolver.disambiguate(testCorpus);

        System.out.println("done.");

        System.out.print("\nEvaluating...");
        Evaluator evaluator = new SignatureEvaluator(testCorpus);
        Report report = evaluator.evaluate(disambiguated, false);
        System.out.println("done.");

        System.out.println("\nResults:");
        System.out.println("P: " + report.getPrecision());
        System.out.println("R: " + report.getRecall());
        System.out.println("F: " + report.getFScore());
        System.out.println("A: " + report.getAccuracy() + "\n");

        if(getOutputPath() != null) {
            System.out.print("Writing resolved corpus in XML format to " + getOutputPath() + " ...");
            CorpusXMLWriter w = new CorpusXMLWriter(disambiguated);
            w.write(new File(getOutputPath()));
            System.out.println("done.");
        }

        if(getKMLOutputPath() != null) {
            System.out.print("Writing visualizable resolved corpus in KML format to " + getKMLOutputPath() + " ...");
            CorpusKMLWriter kw = new CorpusKMLWriter(disambiguated);
            kw.write(new File(getKMLOutputPath()));
            System.out.println("done.");
        }
    }
}
