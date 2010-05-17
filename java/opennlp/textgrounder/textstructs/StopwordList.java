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
package opennlp.textgrounder.textstructs;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import opennlp.textgrounder.util.Constants;

/**
 * A list of stopwords populated from a fixed table
 *
 * @author tsmoon
 */
public class StopwordList {

    /**
     * The list of stopwords
     */
    protected Set<String> stopwords;

    /**
     * Default constructor
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public StopwordList() throws FileNotFoundException, IOException {
        stopwords = new HashSet<String>();

        String stopwordPath = Constants.TEXTGROUNDER_HOME + "/data/lists/stopwords.english";
        BufferedReader textIn = new BufferedReader(new FileReader(stopwordPath));
        String curLine = null;
        while ((curLine = textIn.readLine()) != null) {
            curLine = curLine.trim();
            stopwords.add(curLine);
        }
    }

    /**
     * Check if a word is a stopword or not. Returns true if it is a stopword,
     * false if not.
     *
     * @param word the word to examine
     * @return Returns true if it is a stopword, false if not.
     */
    public boolean isStopWord(String word) {
        if (stopwords.contains(word)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return size of the stopword list
     */
    public int size() {
        return stopwords.size();
    }
}