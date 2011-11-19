///////////////////////////////////////////////////////////////////////////////
//  Copyright (C) 2011 Ben Wing, The University of Texas at Austin
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

package opennlp.textgrounder.geolocate

import math._
import collection.mutable
import util.control.Breaks._

import java.io._

import opennlp.textgrounder.util.collectionutil.DynamicArray
import opennlp.textgrounder.util.ioutil.{errprint, warning, FileHandler}
import opennlp.textgrounder.util.MeteredTask
import opennlp.textgrounder.util.osutil.output_resource_usage

import GeolocateDriver.Params
import GeolocateDriver.Debug._
import WordDist.memoizer._

/**
 * Extract out the behavior related to the pseudo Good-Turing smoother.
 */
class PGTSmoothedBigramWordDistFactory extends
    WordDistFactory {
  // Total number of types seen once
  var total_num_types_seen_once = 0

  // Estimate of number of unseen word types for all articles
  var total_num_unseen_word_types = 0

  /**
   * Overall probabilities over all articles of seeing a word in an article,
   * for all words seen at least once in any article, computed using the
   * empirical frequency of a word among all articles, adjusted by the mass
   * to be assigned to globally unseen words (words never seen at all), i.e.
   * the value in 'globally_unseen_word_prob'.  We start out by storing raw
   * counts, then adjusting them.
   */
  var overall_word_probs = create_word_double_map()
  var owp_adjusted = false

  // The total probability mass to be assigned to words not seen at all in
  // any article, estimated using Good-Turing smoothing as the unadjusted
  // empirical probability of having seen a word once.
  var globally_unseen_word_prob = 0.0

  // For articles whose word counts are not known, use an empty list to
  // look up in.
  // unknown_article_counts = ([], [])

  def finish_global_distribution() = {
    /* We do in-place conversion of counts to probabilities.  Make sure
       this isn't done twice!! */
    assert (!owp_adjusted)
    owp_adjusted = true
    // Now, adjust overall_word_probs accordingly.
    //// FIXME: A simple calculation reveals that in the scheme where we use
    //// globally_unseen_word_prob, total_num_types_seen_once cancels out and
    //// we never actually have to compute it.
    total_num_types_seen_once = overall_word_probs.values count (_ == 1.0)
    globally_unseen_word_prob =
      total_num_types_seen_once.toDouble/WordDist.total_num_word_tokens
    for ((word, count) <- overall_word_probs)
      overall_word_probs(word) = (
        count.toDouble/WordDist.total_num_word_tokens*
        (1.0 - globally_unseen_word_prob))
    // A very rough estimate, perhaps totally wrong
    total_num_unseen_word_types =
      total_num_types_seen_once max (WordDist.total_num_word_types/20)
    if (debug("bigram"))
      errprint("Total num types = %s, total num tokens = %s, total num_seen_once = %s, globally unseen word prob = %s, total mass = %s",
               WordDist.total_num_word_types, WordDist.total_num_word_tokens,
               total_num_types_seen_once,
               globally_unseen_word_prob,
               globally_unseen_word_prob + (overall_word_probs.values sum))
  }

  def create_word_dist() =
    new PGTSmoothedBigramWordDist(this, Array[Word](), Array[Int](), 0, Array[Word](), Array[Int](), 0)

  /**
   * Parse the result of a previous run of --output-counts and generate
   * a unigram distribution for Naive Bayes matching.  We do a simple version
   * of Good-Turing smoothing where we assign probability mass to unseen
   * words equal to the probability mass of all words seen once, and rescale
   * the remaining probabilities accordingly.
   */ 
  def read_word_counts(table: DistDocumentTable,
      filehand: FileHandler, filename: String, stopwords: Set[String]) {
errprint("read_word_counts")
    val initial_dynarr_size = 1000
    val keys_dynarr =
      new DynamicArray[Word](initial_alloc = initial_dynarr_size)
    val values_dynarr =
      new DynamicArray[Int](initial_alloc = initial_dynarr_size)
    val bigram_keys_dynarr =
      new DynamicArray[Word](initial_alloc = initial_dynarr_size)
    val bigram_values_dynarr =
      new DynamicArray[Int](initial_alloc = initial_dynarr_size)

    // This is basically a one-off debug statement because of the fact that
    // the experiments published in the paper used a word-count file generated
    // using an older algorithm for determining the geotagged coordinate of
    // an article.  We didn't record the corresponding article-data
    // file, so we need a way of regenerating it using the intersection of
    // articles in the article-data file we actually used for the experiments
    // and the word-count file we used.
    var stream: PrintStream = null
    var writer: GeoDocumentWriter = null
    if (debug("wordcountdocs")) {
      // Change this if you want a different file name
      val wordcountdocs_filename = "wordcountdocs-combined-document-data.txt"
      stream = filehand.openw(wordcountdocs_filename)
      // See write_document_file() in GeoDocument.scala
      writer =
        new GeoDocumentWriter(stream, GeoDocumentData.combined_document_data_outfields)
      writer.output_header()
    }

    var num_word_tokens = 0
    var num_bigram_tokens = 0
    var title = null: String

    def one_document_probs() {
      if (num_word_tokens == 0) return
      val doc = table.lookup_document(title)
      if (doc == null) {
        warning("Skipping document %s, not in table", title)
        table.num_documents_with_word_counts_but_not_in_table += 1
        return
      }
      if (debug("wordcountdocs"))
        writer.output_row(doc)
      table.num_word_count_documents_by_split(doc.split) += 1
      // If we are evaluating on the dev set, skip the test set and vice
      // versa, to save memory and avoid contaminating the results.
      if (doc.split != "training" && doc.split != Params.eval_set)
        return
      // Don't train on test set
      doc.dist =
        new PGTSmoothedBigramWordDist(this, keys_dynarr.array,
          values_dynarr.array, keys_dynarr.length,
          bigram_keys_dynarr.array, bigram_values_dynarr.array, bigram_keys_dynarr.length)
          //note_globally = (art.split == "training"))
    }

    val task = new MeteredTask("document", "reading distributions of")
    errprint("Reading word and bigram counts from %s...", filename)
    errprint("")

    // Written this way because there's another line after the for loop,
    // corresponding to the else clause of the Python for loop
    breakable {
      for (line <- filehand.openr(filename)) {
        if (line.startsWith("Article title: ")) {
          if (title != null)
            one_document_probs()
          // Stop if we've reached the maximum
          if (task.item_processed(maxtime = Params.max_time_per_stage))
            break
          if ((Params.num_training_docs > 0 &&
            task.num_processed >= Params.num_training_docs)) {
            errprint("")
            errprint("Stopping because limit of %s documents reached",
              Params.num_training_docs)
            break
          }

          // Extract title and set it
          val titlere = "Article title: (.*)$".r
          line match {
            case titlere(ti) => title = ti
            case _ => assert(false)
          }
          keys_dynarr.clear()
          values_dynarr.clear()
          num_word_tokens = 0
        } else if (line.startsWith("Article coordinates) ") ||
          line.startsWith("Article ID: "))
          ()
        else {
          val linere = "(.*) = ([0-9]+)$".r
          line match {
            case linere(xword, xcount) => {
              val count = xcount.toInt
              var words = xword

              if (!Params.preserve_case_words) words = words.toLowerCase
              
              var tokens = words.split("\\s");

              if(tokens.length == 1){

                if (!(stopwords contains words) ||
                  Params.include_stopwords_in_document_dists) {
                  num_word_tokens += count
                  keys_dynarr += memoize_word(words)
                  values_dynarr += count
	              }
              }
		          else if(tokens.length == 2) {
                  num_bigram_tokens += count
                  bigram_keys_dynarr += memoize_word(words)
                  bigram_values_dynarr += count
              }
            }
            case _ =>
              warning("Strange line, can't parse: title=%s: line=%s",
                title, line)
          }
        }
      }
      one_document_probs()
    }

    if (debug("wordcountdocs"))
      stream.close()
    task.finish()
    table.num_documents_with_word_counts += task.num_processed
    output_resource_usage()
  }
}