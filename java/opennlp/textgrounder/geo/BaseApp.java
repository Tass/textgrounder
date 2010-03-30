///////////////////////////////////////////////////////////////////////////////
//  Copyright (C) 2010 Taesun Moon, The University of Texas at Austin
//
//  This library is free software; you can redistribute it and/or
//  modify it under the terms of the GNU Lesser General Public
//  License as published by the Free Software Foundation; either
//  version 3 of the License, or (at your option) any later version.
//
//  This library is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU Lesser General Public License for more details.
//
//  You should have received a copy of the GNU Lesser General Public
//  License along with this program; if not, write to the Free Software
//  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////
package opennlp.textgrounder.geo;

import org.apache.commons.cli.Options;

/**
 * Base class for setting command line options for all georeferencing classes that have a
 * "main" method.
 *
 * @author tsmoon
 */
public class BaseApp {

    /**
     * Takes an Options instance and adds/sets command line options.
     *
     * @param options Command line options
     */
    protected static void setOptions(Options options) {
        options.addOption("a", "alpha", true, "alpha value (default=50/topics)");
        options.addOption("b", "beta", true, "beta value (default=0.1)");
        options.addOption("c", "bar-scale", true, "height of bars in kml output (default=50000)");
        options.addOption("d", "degrees-per-region", true, "size of Region squares in degrees [default = 3.0]");
        options.addOption("e", "training-iterations", true, "number of training iterations (default=100)");
        options.addOption("g", "gazetteer", true, "gazetteer to use [world, census, NGA, USGS; default = world]");
        options.addOption("i", "train-input", true, "input file or directory name for training data");
        options.addOption("ie", "test-input", true, "input file or directory name for test data");
        options.addOption("ks", "samples", true, "number of samples to take (default=100)");
        options.addOption("kl", "lag", true, "number of iterations between samples (default=100)");
        options.addOption("m", "model", true, "model [default = PopBaseline]"); // nothing is done with this yet
        options.addOption("o", "output", true, "output filename [default = 'output.kml']");
        options.addOption("ot", "output-tabulated-probabilities", true, "path of tabulated probability output");
        options.addOption("p", "paragraphs-as-docs", true, "number of paragraphs to treat as a document. Set the argument to 0 if documents should be treated whole");
        options.addOption("pi", "initial-temperature", true, "initial temperature for annealing regime (default=0.1)");
        options.addOption("pd", "temperature-decrement", true, "temperature decrement steps (default=0.1)");
        options.addOption("pt", "target-temperature", true, "temperature at which to stop annealing (default=1)");
        options.addOption("r", "random-seed", true, "seed for random number generator. set argument to 0 to seed with the current time (default=1)");
        options.addOption("t", "topics", true, "number of topics for baseline topic model (default=50)");

        options.addOption("h", "help", false, "print help");
    }
}