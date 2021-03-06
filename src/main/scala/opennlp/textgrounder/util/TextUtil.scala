///////////////////////////////////////////////////////////////////////////////
//  TextUtil.scala
//
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

package opennlp.textgrounder.util

import scala.util.control.Breaks._
import scala.util.matching.Regex
import math._

import printutil.warning

package object textutil {

  ////////////////////////////////////////////////////////////////////////////
  //                    String functions involving numbers                  //
  ////////////////////////////////////////////////////////////////////////////

  /**
   Convert a string to floating point, but don't crash on errors;
  instead, output a warning.
   */
  def safe_float(x: String) = {
    try {
      x.toDouble
    } catch {
      case _ => {
        val y = x.trim()
        if (y != "") warning("Expected number, saw %s", y)
        0.
      }
    }
  }

  // Originally based on code from:
  // http://stackoverflow.com/questions/1823058/how-to-print-number-with-commas-as-thousands-separators-in-python-2-x
  def long_with_commas(x: Long): String = {
    var mx = x
    if (mx < 0)
      "-" + long_with_commas(-mx)
    else {
      var result = ""
      while (mx >= 1000) {
        val r = mx % 1000
        mx /= 1000
        result = ",%03d%s" format (r, result)
      }
      "%d%s" format (mx, result)
    }
  }
  
  // My own version
  def float_with_commas(x: Double) = {
    val intpart = floor(x).toInt
    val fracpart = x - intpart
    long_with_commas(intpart) + ("%.2f" format fracpart).drop(1)
  }
 
  // Try to format something with reasonable precision.
  def format_float(x: Double) = {
    var precision = 2
    if (x != 0) {
      var xx = abs(x)
      while (xx < 0.1) {
        xx *= 10
        precision += 1
      }
    }
    val formatstr = "%%.%sf" format precision
    formatstr format x
  }

  def format_minutes_seconds(seconds: Double, hours: Boolean = true) = {
    var secs = seconds
    var mins = (secs / 60).toInt
    secs = secs % 60
    val hourstr = {
      if (!hours) ""
      else {
        val hours = (mins / 60).toInt
        mins = mins % 60
        if (hours > 0) "%s hour%s " format (hours, if (hours == 1) "" else "s")
        else ""
      }
    }
    val secstr = (if (secs.toInt == secs) "%s" else "%1.1f") format secs
    "%s%s minute%s %s second%s" format (
        hourstr,
        mins, if (mins == 1) "" else "s",
        secstr, if (secs == 1) "" else "s")
  }
  
  ////////////////////////////////////////////////////////////////////////////
  //                           Other string functions                       //
  ////////////////////////////////////////////////////////////////////////////

  // A function to make up for a missing feature in Scala.  Split a text
  // into segments but also return the delimiters.  Regex matches the
  // delimiters.  Return a list of tuples (TEXT, DELIM).  The last tuple
  // with have an empty delim.
  def re_split_with_delimiter(regex: Regex, text: String) = {
    val delim_intervals =
      for (m <- regex.findAllIn(text).matchData) yield List(m.start, m.end)
    val flattened = List(0) ++ (delim_intervals reduce (_ ++ _)) ++
      List(text.length, text.length)
    val interval_texts = flattened.iterator.sliding(2) map (
        x => {
          val Seq(y,z) = x
          text.slice(y,z)
        }
      )
    interval_texts grouped 2
  }

  // In fact, split(..., -1) works exactly like this function.
  // /* A function to make up for a bug in Scala.  The normal split() is broken
  //    in that if the delimiter occurs at the end of the line, it gets ignored;
  //    in fact, multiple such delimiters at end of line get ignored.  We hack
  //    around that by adding an extra char at the end and then removing it
  //    later. */
  // def splittext(str: String, ch: Char) = {
  //   val ch2 = if (ch == 'x') 'y' else 'x'
  //   val stradd = str + ch2
  //   val ret = stradd.split(ch)
  //   ret(ret.length - 1) = ret(ret.length - 1).dropRight(1)
  //   ret
  // }

  // A worse implementation -- it will fail if there are any NUL bytes
  // in the input.
  // def propersplit(str: String, ch: Char) = {
  //   val chs = ch.toString
  //   for (x <- str.replace(chs, chs + "\000").split(ch))
  //     yield x.replace("\000", "")
  // }

  def split_text_into_words(text: String, ignore_punc: Boolean=false,
    include_nl: Boolean=false) = {
    // This regexp splits on whitespace, but also handles the following cases:
    // 1. Any of , ; . etc. at the end of a word
    // 2. Parens or quotes in words like (foo) or "bar"
    // These punctuation characters are returned as separate words, unless
    // 'ignore_punc' is given.  Also, if 'include_nl' is given, newlines are
    // returned as their own words; otherwise, they are treated like all other
    // whitespace (i.e. ignored).
    (for (Seq(word, punc) <-
          re_split_with_delimiter("""([,;."):]*(?:\s+|$)[("]*)""".r, text)) yield
       Seq(word) ++ (
         for (p <- punc; if !(" \t\r\f\013" contains p)) yield (
           if (p == '\n') (if (include_nl) p.toString else "")
           else (if (!ignore_punc) p.toString else "")
         )
       )
    ) reduce (_ ++ _) filter (_ != "")
  }
 
  
  /**
   Pluralize an English word, using a basic but effective algorithm.
   */
  def pluralize(word: String) = {
    val upper = word.last >= 'A' && word.last <= 'Z'
    val lowerword = word.toLowerCase()
    val ies_re = """.*[b-df-hj-np-tv-z]y$""".r
    val es_re = """.*([cs]h|[sx])$""".r
    lowerword match {
      case ies_re() =>
        if (upper) word.dropRight(1) + "IES"
        else word.dropRight(1) + "ies"
      case es_re() =>
        if (upper) word + "ES"
        else word + "es"
      case _ =>
        if (upper) word + "S"
        else word + "s"
    }
  }

  /**
   Capitalize the first letter of string, leaving the remainder alone.
   */
  def capfirst(st: String) = {
    if (st == "") st else st(0).toString.capitalize + st.drop(1)
  }
  
  /*
    A simple object to make regexps a bit less awkward.  Works like this:

    ("foo (.*)", "foo bar") match {
      case Re(x) => println("matched 1 %s" format x)
      case _ => println("no match 1")
    }

    This will print out "matched 1 bar".
   */

  object Re {
    def unapplySeq(x: Tuple2[String, String]) = {
      val (re, str) = x
      re.r.unapplySeq(str)
    }
  }
}

