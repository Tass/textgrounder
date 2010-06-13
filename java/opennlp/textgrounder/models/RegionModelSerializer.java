///////////////////////////////////////////////////////////////////////////////
//  Copyright (C) 2010 Taesun Moon, The University of Texas at Austin
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
///////////////////////////////////////////////////////////////////////////////
package opennlp.textgrounder.models;

import gnu.trove.TIntHashSet;
import gnu.trove.TIntIterator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import opennlp.textgrounder.gazetteers.Gazetteer;
import opennlp.textgrounder.gazetteers.GazetteerGenerator;
import opennlp.textgrounder.geo.*;
import opennlp.textgrounder.models.callbacks.*;
import opennlp.textgrounder.textstructs.*;
import opennlp.textgrounder.topostructs.*;
import opennlp.textgrounder.util.Constants;

/**
 * Topic model with region awareness. Toponyms are all unigrams. Multiword
 * toponyms are split into space delimited tokens.
 * 
 * @author tsmoon
 */
public class RegionModelSerializer extends RegionModel<SmallLocation> {

    /**
     * Default constructor. Take input from commandline and default options
     * and initialize class. Also, process input text and process so that
     * toponyms, stopwords and other words are identified and collected.
     *
     * @param options
     */
    public RegionModelSerializer(CommandLineOptions options) {
        regionMapperCallback = new RegionMapperCallback();
        try {
            initialize(options);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(RegionModelSerializer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(RegionModelSerializer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(RegionModelSerializer.class.getName()).log(Level.SEVERE, null, ex);
        } catch (SQLException ex) {
            Logger.getLogger(RegionModelSerializer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     *
     * @param options
     */
    @Override
    protected void initialize(CommandLineOptions options) throws
          FileNotFoundException, IOException, ClassNotFoundException,
          SQLException {
        trainInputPath = options.getTrainInputPath();
        trainInputFile = new File(trainInputPath);
        evalInputFile = new File(evalInputPath);

        degreesPerRegion = options.getDegreesPerRegion();
        lexicon = new Lexicon();
        gazetteerGenerator = new GazetteerGenerator(options);
        TextProcessor textProcessor = null;
        String fname = trainInputFile.getName();
        if (options.isPCLXML()) {
            textProcessor = new TextProcessorTEIXML(lexicon);
        } else if (trainInputFile.isDirectory() && trainInputFile.list(new PCLXMLFilter()).length != 0) {
            textProcessor = new TextProcessorTEIXML(lexicon);
        } else if (fname.startsWith("txu") && fname.endsWith(".xml")) {
            textProcessor = new TextProcessorTEIXML(lexicon);
        } else {
            textProcessor = new TextProcessor(lexicon, paragraphsAsDocs);
        }

        trainTokenArrayBuffer = new TokenArrayBuffer(lexicon, new TrainingMaterialCallback(lexicon));
        stopwordList = new StopwordList();
        processTrainInputPath(trainInputFile, textProcessor, trainTokenArrayBuffer, stopwordList);
        trainTokenArrayBuffer.convertToPrimitiveArrays();
        initializeRegionArray();
        locationSet = new TIntHashSet();
        dataSpecificLocationMap = new TIntObjectHashMap<SmallLocation>();
        dataSpecificGazetteer = new TIntObjectHashMap<TIntHashSet>();

        buildTopoTable(trainTokenArrayBuffer);

        evalTokenArrayBuffer = new EvalTokenArrayBuffer(lexicon, new TrainingMaterialCallback(lexicon));
        processEvalInputPath(evalInputFile, new TextProcessorTR(lexicon), evalTokenArrayBuffer, stopwordList);
        evalTokenArrayBuffer.convertToPrimitiveArrays();

        buildTopoTable(evalTokenArrayBuffer);
    }

    /**
     * 
     * @return
     */
    protected TIntHashSet buildTopoTable(TokenArrayBuffer _tokenArrayBuffer) {
        System.err.println();
        System.err.print("Buildng lookup tables for locations, regions and toponyms for document: ");
        int curDoc = 0, prevDoc = -1;

        Gazetteer gazetteer = gazetteerGenerator.generateGazetteer();
        for (int i = 0; i < _tokenArrayBuffer.size(); i++) {
            curDoc = _tokenArrayBuffer.documentVector[i];
            if (curDoc != prevDoc) {
                System.err.print(curDoc + ",");
            }
            prevDoc = curDoc;
            if (_tokenArrayBuffer.toponymVector[i] == 1) {
                int topid = _tokenArrayBuffer.wordVector[i];
                if (!dataSpecificGazetteer.contains(topid)) {
                    String placename = lexicon.getWordForInt(_tokenArrayBuffer.wordVector[i]);
                    if (gazetteer.contains(placename)) {
                        TIntHashSet possibleLocations = gazetteer.frugalGet(placename);
                        TIntHashSet tempLocs = new TIntHashSet();
                        for (TIntIterator it = possibleLocations.iterator();
                              it.hasNext();) {
                            int locid = it.next();
                            SmallLocation loc = gazetteer.getLocation(locid);

                            if (Math.abs(loc.getCoord().latitude) > Constants.EPSILON && Math.abs(loc.getCoord().longitude) > Constants.EPSILON) {
                                tempLocs.add(loc.getId());
                                dataSpecificLocationMap.put(loc.getId(), loc);
                            }
                        }
                        dataSpecificGazetteer.put(topid, tempLocs);

                        addLocationsToRegionArray(possibleLocations, gazetteer, regionMapperCallback);
                        regionMapperCallback.addAll(placename, lexicon);
                        locationSet.addAll(possibleLocations.toArray());
                    }
                }
            }
        }
        System.err.println();
        return null;
    }

    protected void buildTopoFilter(TIntHashSet toponymsNotInGazetteer) {
        TIntObjectHashMap<TIntHashSet> nameToRegionIndex = regionMapperCallback.getNameToRegionIndex();
        for (TIntObjectIterator<TIntHashSet> it1 = nameToRegionIndex.iterator();
              it1.hasNext();) {
            it1.advance();
            int wordoff = it1.key() * T;
            for (TIntIterator it2 = it1.value().iterator(); it2.hasNext();) {
                int j = it2.next();
                regionByToponym[wordoff + j] = 1;
            }
        }

        for (TIntIterator it = toponymsNotInGazetteer.iterator(); it.hasNext();) {
            int topid = it.next();
            int topoff = topid * T;
            for (int i = 0; i < T; ++i) {
                regionByToponym[topoff + i] = 1;
            }
        }
    }

    public void serialize() {
    }
}