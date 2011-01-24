package opennlp.textgrounder.util;

import opennlp.textgrounder.text.*;
import opennlp.textgrounder.topo.*;
import java.util.*;

public class TopoUtil {
    
    public static Lexicon<String> buildLexicon(StoredCorpus corpus) {
        Lexicon<String> lexicon = new SimpleLexicon<String>();

        addToponymsToLexicon(lexicon, corpus);

        return lexicon;
    }

    public static void addToponymsToLexicon(Lexicon<String> lexicon, StoredCorpus corpus) {
        for(Document<StoredToken> doc : corpus) {
            for(Sentence<StoredToken> sent : doc) {
                for(Toponym toponym : sent.getToponyms()) {
                    if(toponym.getAmbiguity() > 0) {
                        lexicon.getOrAdd(toponym.getForm());
                    }
                }
            }
        }
    }

    public static void buildLexicons(StoredCorpus corpus, Lexicon<String> lexicon, HashMap<Integer, String> reverseLexicon) {
        for(Document<StoredToken> doc : corpus) {
            for(Sentence<StoredToken> sent : doc) {
                for(Toponym toponym : sent.getToponyms()) {
                    if(toponym.getAmbiguity() > 0) {
                        int idx = lexicon.getOrAdd(toponym.getForm());
                        reverseLexicon.put(idx, toponym.getForm());
                    }
                }
            }
        }
    }

    public static int getRegionNumber(Location location, double dpr) {
        int x = (int) ((location.getRegion().getCenter().getLng() + 180.0) / dpr);
        int y = (int) ((location.getRegion().getCenter().getLat() + 90.0) / dpr);

        return x * 1000 + y;
    }

    public static int getRegionNumber(double lat, double lon, double dpr) {

        if(lat < 0 || lat >= 180/dpr) return -1;
        if(lon < 0) lon += 360/dpr;
        if(lon >= 360/dpr) lon -= 360/dpr;

        return (int)((int)(lat/dpr) * 1000 + (lon/dpr));
    }

    public static int getCorrectCandidateIndex(Toponym toponym, int regionNumber, double dpr) {
        if(regionNumber == -1) System.out.println("-1");
        int index = 0;
        for(Location location : toponym.getCandidates()) {
            if(getRegionNumber(location, dpr) == regionNumber)
                return index;
            index++;
        }

        return -1;
    }
}
