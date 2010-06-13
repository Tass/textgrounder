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
package opennlp.textgrounder.gazetteers;

import gnu.trove.*;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;

import opennlp.textgrounder.textstructs.Lexicon;
import opennlp.textgrounder.topostructs.*;

/**
 * Base class for Gazetteer objects. Links to gazetteer databases (i.e. db's
 * that contain names of places around the world and geographic information),
 * extracts information from them, and dumps them into internally accessible
 * collections. The Gazetteer class itself is hashmap from integer keys to
 * hashsets of integers. The keys are placenames in a gazetteer that have been
 * converted to dictionary indices (records for which are maintained in
 * toponymLexicon). The values of the hashmap, the hashsets are not populated
 * until corpus is processed and relevant toponyms have been identified in
 * the corpus. For each toponym in the corpus, a db query is conducted and if
 * the toponym is successfully found, all locations that have been selected from
 * the db are converted to Location objects and added to a TIntHashSet object,
 * whose hashset keys are the ids inherent to the locations in the gazetteer.
 * A separate lookup table from location ids to locations is maintained in
 * idxToLocationMap.
 */
public abstract class Gazetteer<E extends SmallLocation> extends TIntObjectHashMap<TIntHashSet> {

    /*public final static int USGS_TYPE = 0;
    public final static int US_CENSUS_TYPE = 1;
    
    public final static int DEFAULT_TYPE = USGS_TYPE;*/
    /**
     * Lookup table for Location (hash)code to Location object
     */
    protected TIntObjectHashMap<E> idxToLocationMap;
    /**
     * Flag for refreshing gazetteer from original database
     */
    public boolean gazetteerRefresh = false;
    /**
     * Internal lexicon for maintaining lookups between toponyms in gazetteer
     * and indices
     */
    protected Lexicon toponymLexicon;
    protected Connection conn;
    protected Statement stat;
    protected static Pattern allDigits = Pattern.compile("^[0-9]+$");
    protected static Pattern rawCoord = Pattern.compile("^\\-?[0-9]+$");
    /**
     * The largest location id value that has been activated. Needed for creating
     * pseudo locations later in region model
     */
    protected static int maxLocId = 0;
    /**
     *
     */
    public E genericsKludgeFactor;

    protected Gazetteer() {
    }

    /**
     * Default constructor. Allocates memory for internal collections.
     *
     * @param location empty parameter only to be overridden in derived classes
     */
    public Gazetteer(String location) {
        idxToLocationMap = new TIntObjectHashMap<E>();
        toponymLexicon = new Lexicon();
    }

    /**
     * Abstract initialization function. Generally includes db building and reading,
     * and population of collections and the gazetteer.
     *
     * @param location
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws SQLException
     */
    abstract void initialize(String location) throws FileNotFoundException,
          IOException, ClassNotFoundException, SQLException;

    /**
     * Search for a placename and return locations associated with it. This is
     * done by first looking up the index of the placename in toponymLexicon,
     * then querying the native get(int) method to see if the location hashset
     * associated with it is null or not. If it is null, it means that the
     * placename has previously been unobserved in the text and the appropriate
     * hashset is built and added to the map and returned. If it is not null, the corresponding
     * hashset is retrieved and return.
     *
     * @param placename the placename to lookup in the gazetteer
     * @return the indexes of the locations that are associated with the current
     * placename
     * @throws SQLException
     */
    public TIntHashSet get(String placename) {
        int topid = toponymLexicon.addWord(placename);
        return get(placename, topid);
    }

    /**
     * 
     * @param placename
     * @param topid
     * @return
     */
    public TIntHashSet get(String placename, int topid) {
        try {
            TIntHashSet locationsToReturn = get(topid);
            if (locationsToReturn == null) {
                locationsToReturn = new TIntHashSet();
                ResultSet rs = stat.executeQuery("select * from places where name = \"" + placename + "\";");
                while (rs.next()) {
                    E locationToAdd = generateLocation(rs, topid);
                    idxToLocationMap.put(locationToAdd.getId(), locationToAdd);
                    locationsToReturn.add(locationToAdd.getId());
                    if (locationToAdd.getId() > maxLocId) {
                        maxLocId = locationToAdd.getId();
                    }
                }
                rs.close();
                put(topid, locationsToReturn);
            }

            return locationsToReturn;
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    /**
     * Same as get(String) except does not add locationsToReturn to the
     * gazetteer
     *
     * @param placename
     * @return
     */
    public TIntHashSet frugalGet(String placename) {
        try {
            int topid = toponymLexicon.getIntForWord(placename);
            TIntHashSet locationsToReturn = new TIntHashSet();
            ResultSet rs = stat.executeQuery("select * from places where name = \"" + placename + "\";");
            while (rs.next()) {
                E locationToAdd = generateLocation(rs, topid);
                idxToLocationMap.put(locationToAdd.getId(), locationToAdd);
                locationsToReturn.add(locationToAdd.getId());
                if (locationToAdd.getId() > maxLocId) {
                    maxLocId = locationToAdd.getId();
                }
            }
            rs.close();
            return locationsToReturn;
        } catch (SQLException e) {
            e.printStackTrace();
            System.exit(1);
        }
        return null;
    }

    /**
     * 
     * @return
     * @throws Exception
     */
    public TIntHashSet getAllLocalities() throws Exception { // overridden by WGGazetteer only right now
        return new TIntHashSet();
    }

    /**
     * @return the idxToLocationMap
     */
    public TIntObjectHashMap<E> getIdxToLocationMap() {
        return idxToLocationMap;
    }

    /**
     * 
     * @param locid
     * @return
     */
    public E getLocation(int locid) {
        return idxToLocationMap.get(locid);
    }

    /**
     * Same as getLocation(int) except checks whether location exists in
     * idxToLocaationMap
     *
     * @param locid
     * @return
     */
    public E safeGetLocation(int locid) {
        if (idxToLocationMap.contains(locid)) {
            return idxToLocationMap.get(locid);
        } else {
            E loc = null;
            try {
                ResultSet rs = stat.executeQuery("select * from places where id = \"" + locid + "\";");
                while (rs.next()) {
                    loc = generateLocation(rs, locid);
                    idxToLocationMap.put(loc.getId(), loc);
                    if (loc.getId() > maxLocId) {
                        maxLocId = loc.getId();
                    }
                    rs.close();
                    /**
                     * Collect one and let go. If the id is a proper key value
                     * there will be only one record anyway
                     */
                    break;
                }
            } catch (SQLException ex) {
                Logger.getLogger(Gazetteer.class.getName()).log(Level.SEVERE, null, ex);
            }
            return loc;
        }
    }

    /**
     * Add location to idxToLocationMap. Only for pseudo-locations that 
     * must be resolved after training on corpus is complete. These locations
     * do not actually have any corresponding points in the gazetteer.
     *
     * @param loc
     * @return
     */
    public int putLocation(E loc) {
        idxToLocationMap.put(loc.getId(), loc);
        return loc.getId();
    }

    /**
     * @return the toponymLexicon
     */
    public Lexicon getToponymLexicon() {
        return toponymLexicon;
    }

    /**
     * 
     * @param placename
     * @return
     */
    public boolean contains(String placename) {
        return toponymLexicon.contains(placename);
    }

    /**
     * @return the maxLocId
     */
    public int getMaxLocId() {
        return maxLocId;
    }

    protected E generateLocation(ResultSet rs, int topid) throws SQLException {
        E locationToAdd;// = (E) new SmallLocation();

        if (genericsKludgeFactor instanceof Location) {
            locationToAdd = (E) new Location(rs.getInt("id"),
                  rs.getString("name"),
                  rs.getString("type"),
                  new Coordinate(rs.getDouble("lon"), rs.getDouble("lat")),
                  rs.getInt("pop"),
                  rs.getString("container"),
                  0);
        } else {
            locationToAdd = (E) new SmallLocation(rs.getInt("id"),
                  topid,
                  new Coordinate(rs.getDouble("lon"), rs.getDouble("lat")),
                  0);
        }
        return locationToAdd;
    }
}
