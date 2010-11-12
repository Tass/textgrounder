from __future__ import with_statement # For chompopen(), uchompopen()
from optparse import OptionParser
import itertools
from itertools import izip, chain, cycle
import re # For regexp wrappers
import sys, codecs # For uchompopen()
import math # For float_with_commas()
import bisect # For sorted lists
import time # For status messages, resource usage
from heapq import * # For priority queue
import UserDict # For SortedList, LRUCache
import resource # For resource usage
from collections import deque # For breadth-first search

#############################################################################
#                        Regular expression functions                       #
#############################################################################


#### Some simple wrappers around basic text-processing Python functions to
#### make them easier to use.
####
#### 1. rematch() and research():
####
#### The functions 'rematch' and 'research' are wrappers around re.match()
#### and re.search(), respectively, but instead of returning a match object
#### None, they return True or False, and in the case a match object would
#### have been returned, a corresponding WreMatch object is stored in the
#### global variable m_.  Groups can be accessed from this variable using
#### m_.group() or m_.groups(), but they can also be accessed through direct
#### subscripting, i.e. m_[###] = m_.group(###).

class WreMatch(object):
  def setmatch(self, match):
    self.match = match

  def groups(self, *foo):
    return self.match.groups(*foo)

  def group(self, *foo):
    return self.match.group(*foo)

  def __getitem__(self, key):
    return self.match.group(key)

m_ = WreMatch()

def rematch(pattern, string, flags=0):
  m = re.match(pattern, string, flags)
  if m:
    m_.setmatch(m)
    return True
  return False

def research(pattern, string, flags=0):
  global m_
  m = re.search(pattern, string, flags)
  if m:
    m_.setmatch(m)
    return True
  return False

#############################################################################
#                            File reading functions                         #
#############################################################################

### NOTE NOTE NOTE: Only works on Python 2.5 and above, due to using the
### "with" statement.

#### 1. chompopen():
####
#### A generator that yields lines from a file, with any terminating newline
#### removed (but no other whitespace removed).  Ensures that the file
#### will be automatically closed under all circumstances.
####
#### 2. uchompopen():
####
#### Same as chompopen() but specifically open the file as 'utf-8' and
#### return Unicode strings.

# Open a filename with UTF-8-encoded input and yield lines converted to
# Unicode strings, but with any terminating newline removed (similar to
# "chomp" in Perl).
def uchompopen(filename, errors='strict'):
  with codecs.open(filename, encoding='utf-8', errors=errors) as f:
    for line in f:
      if line and line[-1] == '\n': line = line[:-1]
      yield line

# Open a filename and yield lines, but with any terminating newline
# removed (similar to "chomp" in Perl).
def chompopen(filename):
  with open(filename) as f:
    for line in f:
      if line and line[-1] == '\n': line = line[:-1]
      yield line

#############################################################################
#                         Other basic utility functions                     #
#############################################################################

def internasc(text):
  '''Intern a string (for more efficient memory use, potentially faster lookup.
If string is Unicode, automatically convert to UTF-8.'''
  if type(text) is unicode: text = text.encode("utf-8")
  return intern(text)

def uniprint(text, outfile=sys.stdout, nonl=False, flush=False):
  '''Print text string using 'print', converting Unicode as necessary.
If string is Unicode, automatically convert to UTF-8, so it can be output
without errors.  Send output to the file given in OUTFILE (default is
stdout).  Uses the 'print' command, and normally outputs a newline; but
this can be suppressed using NONL.  Output is not normally flushed (unless
the stream does this automatically); but this can be forced using FLUSH.'''
  
  if type(text) is unicode:
    text = text.encode("utf-8")
  if nonl:
    print >>outfile, text,
  else:
    print >>outfile, text
  if flush:
    outfile.flush()

def errprint(text, nonl=False):
  '''Print text to stderr using 'print', converting Unicode as necessary.
If string is Unicode, automatically convert to UTF-8, so it can be output
without errors.  Uses the 'print' command, and normally outputs a newline; but
this can be suppressed using NONL.'''
  uniprint(text, outfile=sys.stderr, nonl=nonl)

def warning(text):
  '''Output a warning, formatting into UTF-8 as necessary'''
  errprint("Warning: %s" % text)

def safe_float(x):
  '''Convert a string to floating point, but don't crash on errors;
instead, output a warning.'''
  try:
    return float(x)
  except:
    x = x.strip()
    if x:
      warning("Expected number, saw %s" % x)
    return 0.

def pluralize(word):
  '''Pluralize an English word, using a basic but effective algorithm.'''
  if word[-1] >= 'A' and word[-1] <= 'Z': upper = True
  else: upper = False
  lowerword = word.lower()
  if re.match(r'.*[b-df-hj-np-tv-z]y$', lowerword):
    if upper: return word[:-1] + 'IES'
    else: return word[:-1] + 'ies'
  elif re.match(r'.*([cs]h|[sx])$', lowerword):
    if upper: return word + 'ES'
    else: return word + 'es'
  else:
    if upper: return word + 'S'
    else: return word + 's'

def capfirst(st):
  '''Capitalize the first letter of string, leaving the remainder alone.'''
  if not st: return st
  return st[0].capitalize() + st[1:]

# From: http://stackoverflow.com/questions/1823058/how-to-print-number-with-commas-as-thousands-separators-in-python-2-x
def int_with_commas(x):
  if type(x) not in [type(0), type(0L)]:
    raise TypeError("Parameter must be an integer.")
  if x < 0:
    return '-' + int_with_commas(-x)
  result = ''
  while x >= 1000:
    x, r = divmod(x, 1000)
    result = ",%03d%s" % (r, result)
  return "%d%s" % (x, result)

# My own version
def float_with_commas(x):
  intpart = int(math.floor(x))
  fracpart = x - intpart
  return int_with_commas(intpart) + ("%.2f" % fracpart)[1:]

def median(list):
  "Return the median value of a sorted list."
  l = len(list)
  if l % 2 == 1:
    return list[l // 2]
  else:
    l = l // 2
    return 0.5*(list[l-1] + list[l])

def mean(list):
  "Return the mean of a list."
  return sum(list) / float(len(list))

def split_text_into_words(text, ignore_punc=False, include_nl=False):
  # This regexp splits on whitespace, but also handles the following cases:
  # 1. Any of , ; . etc. at the end of a word
  # 2. Parens or quotes in words like (foo) or "bar"
  # These punctuation characters are returned as separate words, unless
  # 'ignore_punc' is given.  Also, if 'include_nl' is given, newlines are
  # returned as their own words; otherwise, they are treated like all other
  # whitespace (i.e. ignored).
  if include_nl:
    split_punc_re = r'[ \t]+'
  else:
    split_punc_re = r'\s+'
  # The use of izip and cycle will pair True with return values that come
  # from the grouping in the split re, and False with regular words.
  for (ispunc, word) in izip(cycle([False, True]),
                  re.split('([,;."):]*\s+[("]*)', text)):
    if not word: continue
    if ispunc:
      # Divide the punctuation up 
      for punc in word:
        if punc == '\n':
          if include_nl: yield punc
        elif punc in ' \t\r\f\v': continue
        elif not ignore_punc: yield punc
    else:
      yield word

#############################################################################
#                             Default dictionaries                          #
#############################################################################

# A dictionary where missing keys automatically spring into existence
# with a value of 0.  Useful for dictionaries that track counts of items.
class intdict(dict):
  def __missing__(self, key):
    return 0

# A dictionary where missing keys automatically spring into existence
# with a value of 0.0.  Useful for dictionaries that track counts of items.
class floatdict(dict):
  def __missing__(self, key):
    return 0.0

# A dictionary where missing keys automatically spring into existence
# with a value of [].  Useful for dictionaries that track lists of items.
# NOTE NOTE NOTE: It DOES NOT work to add to a non-existent key using
# append(); use += instead.  It "appears" to work but the new value gets
# swallowed.  The reason is that although asking for the value of a
# non-existent key automatically returns [], the key itself doesn't
# spring into existence until you assign assign a value to it, as for
# normal dictionaries.  NOTE: This behavior is NOT the same as when you
# use collections.defaultdict(list), where asking for the value of a
# non-existent key DOES cause the key to get added to the dictionary with
# the default value.
#
# Hence:
#
# foo = listdict()
# foo['bar'] -> []
# 'bar' in foo -> False
#
# import collections
# foo2 = collections.defaultdict(list)
# foo2['bar'] -> []
# 'bar' in foo2 -> True
#
class listdict(dict):
  def __missing__(self, key):
    return list()

# A dictionary where missing keys automatically spring into existence
# with a value of (), i.e. the empty tuple.
class tupledict(dict):
  def __missing__(self, key):
    return ()

# A dictionary where missing keys automatically spring into existence
# with a value of set(), i.e. the empty set.  Useful for dictionaries that
# track sets of items.  See above: you need to add items to the set
# using +=, not using append().
class setdict(dict):
  def __missing__(self, key):
    return set()

#############################################################################
#                                 Sorted lists                              #
#############################################################################

# Return a tuple (keys, values) of lists of items corresponding to a hash
# table.  Stored in sorted order according to the keys.  Use
# lookup_sorted_list(key) to find the corresponding value.  The purpose of
# doing this, rather than just directly using a hash table, is to save
# memory.

def make_sorted_list(table):
  items = sorted(table.items(), key=lambda x:x[0])
  keys = ['']*len(items)
  values = ['']*len(items)
  for i in xrange(len(items)):
    item = items[i]
    keys[i] = item[0]
    values[i] = item[1]
  return (keys, values)

# Given a sorted list in the tuple form (KEYS, VALUES), look up the item KEY.
# If found, return the corresponding value; else return None.

def lookup_sorted_list(sorted_list, key, default=None):
  (keys, values) = sorted_list
  i = bisect.bisect_left(keys, key)
  if i != len(keys) and keys[i] == key:
    return values[i]
  return default

# A class that provides a dictionary-compatible interface to a sorted list

class SortedList(object, UserDict.DictMixin):
  def __init__(self, table):
    self.sorted_list = make_sorted_list(table)

  def __len__(self):
    return len(self.sorted_list[0])

  def __getitem__(self, key):
    retval = lookup_sorted_list(self.sorted_list, key)
    if retval is None:
      raise KeyError(key)
    return retval

  def __contains__(self, key):
    return lookup_sorted_list(self.sorted_list, key) is not None

  def __iter__(self):
    (keys, values) = self.sorted_list
    for x in keys:
      yield x

  def keys(self):
    return self.sorted_list[0]

  def itervalues(self):
    (keys, values) = self.sorted_list
    for x in values:
      yield x

  def iteritems(self):
    (keys, values) = self.sorted_list
    for (key, value) in izip(keys, values):
      yield (key, value)

#############################################################################
#                                Table Output                               #
#############################################################################

# Given a table with values that are numbers, output the table, sorted
# on the numbers from bigger to smaller.
def output_reverse_sorted_table(table, outfile=sys.stdout, indent=""):
  for x in sorted(table.items(), key=lambda x:x[1], reverse=True):
    uniprint("%s%s = %s" % (indent, x[0], x[1]), outfile=outfile)

#############################################################################
#                             Status Messages                               #
#############################################################################

# Output status messages periodically, at some multiple of
# 'secs_between_output', measured in real time. 'item_name' is the name
# of the items being processed.  Every time an item is processed, call
# item_processed()
class StatusMessage(object):
  def __init__(self, item_name, secs_between_output=15):
    self.item_name = item_name
    self.plural_item_name = pluralize(item_name)
    self.secs_between_output = secs_between_output
    self.items_processed = 0
    self.first_time = time.time()
    self.last_time = self.first_time

  def num_processed(self):
    return self.items_processed

  def elapsed_time(self):
    return time.time() - self.first_time

  def item_processed(self):
    curtime = time.time()
    self.items_processed += 1
    total_elapsed_secs = int(curtime - self.first_time)
    last_elapsed_secs = int(curtime - self.last_time)
    if last_elapsed_secs >= self.secs_between_output:
      # Rather than directly recording the time, round it down to the nearest
      # multiple of secs_between_output; else we will eventually see something
      # like 0, 15, 45, 60, 76, 91, 107, 122, ...
      # rather than
      # like 0, 15, 45, 60, 76, 90, 106, 120, ...
      rounded_elapsed = (int(total_elapsed_secs / self.secs_between_output) *
                         self.secs_between_output)
      self.last_time = self.first_time + rounded_elapsed
      uniprint ("Elapsed time: %s minutes %s seconds, %s %s processed"
                % (int(total_elapsed_secs / 60), total_elapsed_secs % 60,
                   self.items_processed,
                   self.item_name if self.items_processed == 1
                     else self.plural_item_name), outfile=sys.stderr)
    return total_elapsed_secs
 
#############################################################################
#                               File Splitting                              #
#############################################################################

# Return the next file to output to, when the instances being output to the
# files are meant to be split according to SPLIT_FRACTIONS.  The absolute
# quantities in SPLIT_FRACTIONS don't matter, only the values relative to
# the other values, i.e. [20, 60, 10] is the same as [4, 12, 2].  This
# function implements an algorithm that is deterministic (same results
# each time it is run), and spreads out the instances as much as possible.
# For example, if all values are equal, it will cycle successively through
# the different split files; if the values are [1, 1.5, 1], the output
# will be [1, 2, 3, 2, 1, 2, 3, ...]; etc.

def next_split_set(split_fractions):

  num_splits = len(split_fractions)
  cumulative_articles = [0]*num_splits

  # Normalize so that the smallest value is 1.

  minval = min(split_fractions)
  split_fractions = [float(val)/minval for val in split_fractions]

  # The algorithm used is as follows.  We cycle through the output sets in
  # order; each time we return a set, we increment the corresponding
  # cumulative count, but before returning a set, we check to see if the
  # count has reached the corresponding fraction and skip this set if so.
  # If we have run through an entire cycle without returning any sets,
  # then for each set we subtract the fraction value from the cumulative
  # value.  This way, if the fraction value is not a whole number, then
  # any fractional quantity (e.g. 0.6 for a value of 7.6) is left over,
  # any will ensure that the total ratios still work out appropriately.

  while True:
    this_output = False
    for j in xrange(num_splits):
      #print "j=%s, this_output=%s" % (j, this_output)
      if cumulative_articles[j] < split_fractions[j]:
        yield j
        cumulative_articles[j] += 1
        this_output = True
    if not this_output:
      for j in xrange(num_splits):
        while cumulative_articles[j] >= split_fractions[j]:
          cumulative_articles[j] -= split_fractions[j]

#############################################################################
#                               NLP Programs                                #
#############################################################################

max_time_unlimited = 2**31

def output_option_parameters(opts, params=None):
  errprint("Parameter values:")
  for opt in dir(opts):
    if not opt.startswith('_') and opt not in \
       ['ensure_value', 'read_file', 'read_module']:
      errprint("%30s: %s" % (opt, getattr(opts, opt)))
  if params:
    for opt in dir(params):
      if not opt.startswith('_'):
        errprint("%30s: %s" % (opt, getattr(params, opt)))
  errprint("")

class NLPProgram(object):
  def __init__(self):
    if self.run_main_on_init():
      self.main()

  def implement_main(self, opts, params, args):
    pass

  def populate_options(self, op):
    pass

  def handle_arguments(self, opts, op, args):
    pass

  def argument_usage(self):
    return ""

  def get_usage(self):
    argusage = self.argument_usage()
    if argusage:
      argusage = ' ' + argusage
    return "%%prog [options]%s" % argusage

  def run_main_on_init(self):
    return True

  def populate_shared_options(self, op):
    op.add_option("--max-time-per-stage", "--mts", type='int',
                  default=max_time_unlimited,
                  help="""Maximum time per stage in seconds.  If 0, no limit.
Used for testing purposes.  Default %default.""")
    op.add_option("-d", "--debug", type='int', metavar="LEVEL",
                  help="Output debug info at given level")

  def need(self, arg, arg_english=None):
    if not arg_english:
      arg_english=arg.replace('_', ' ')
    if not getattr(self.opts, arg):
      self.op.error("Must specify %s using --%s" %
                    (arg_english, arg.replace('_', '-')))

  def main(self):
    errprint("Beginning operation at %s" % (time.ctime()))

    self.op = OptionParser(usage="%prog [options]")
    self.populate_shared_options(self.op)
    self.canon_options = self.populate_options(self.op)

    errprint("Arguments: %s" % ' '.join(sys.argv))

    ### Process the command-line options and set other values from them ###
    
    self.opts, self.args = self.op.parse_args()
    # If a mapper for canonicalizing options is given, apply the mappings
    if self.canon_options:
      for (opt, mapper) in self.canon_options.iteritems():
        val = getattr(self.opts, opt)
        if val in mapper:
          setattr(self.opts, opt, mapper[val])

    params = self.handle_arguments(self.opts, self.op, self.args)

    output_option_parameters(self.opts, params)

    retval = self.implement_main(self.opts, params, self.args)
    errprint("Ending operation at %s" % (time.ctime()))
    return retval

#############################################################################
#                               Priority Queues                             #
#############################################################################

# Priority queue implementation, based on Python heapq documentation.
# Note that in Python 2.6 and on, there is a priority queue implementation
# in the Queue module.
class PriorityQueue(object):
  INVALID = 0                     # mark an entry as deleted

  def __init__(self):
    self.pq = []                         # the priority queue list
    self.counter = itertools.count(1)    # unique sequence count
    self.task_finder = {}                # mapping of tasks to entries

  def add_task(self, priority, task, count=None):
    if count is None:
      count = self.counter.next()
    entry = [priority, count, task]
    self.task_finder[task] = entry
    heappush(self.pq, entry)

  def get_top_priority(self):
    while True:
      priority, count, task = heappop(self.pq)
      if count is not PriorityQueue.INVALID:
        del self.task_finder[task]
        return task

  def delete_task(self, task):
    entry = self.task_finder[task]
    entry[1] = PriorityQueue.INVALID

  def reprioritize(self, priority, task):
    entry = self.task_finder[task]
    self.add_task(priority, task, entry[1])
    entry[1] = PriorityQueue.INVALID

#############################################################################
#                      Least-recently-used (LRU) Caches                     #
#############################################################################

class LRUCache(object, UserDict.DictMixin):
  def __init__(self, maxsize=1000):
    self.cache = {}
    self.pq = PriorityQueue()
    self.maxsize = maxsize
    self.time = 0

  def __len__(self):
    return len(self.cache)

  def __getitem__(self, key):
    if key in self.cache:
      time = self.time
      self.time += 1
      self.pq.reprioritize(time, key)
    return self.cache[key]

  def __delitem__(self, key):
    del self.cache[key]
    self.pq.delete_task(key)

  def __setitem__(self, key, value):
    time = self.time
    self.time += 1
    if key in self.cache:
      self.pq.reprioritize(time, key)
    else:
      while len(self.cache) >= self.maxsize:
        delkey = self.pq.get_top_priority()
        del self.cache[delkey]
      self.pq.add_task(time, key)
    self.cache[key] = value

  def keys(self):
    return self.cache.keys()

  def __contains__(self, key):
    return key in self.cache

  def __iter__(self):
    return self.cache.iterkeys()

  def iteritems(self):
    return self.cache.iteritems()

#############################################################################
#                               Resource Usage                              #
#############################################################################

beginning_prog_time = time.time()

def get_program_time_usage():
  return time.time() - beginning_prog_time

def get_program_memory_usage():
  res = resource.getrusage(resource.RUSAGE_SELF)
  # FIXME!  This is "maximum resident set size".  There are other more useful
  # values, but on the Mac at least they show up as 0 in this structure.
  return res.ru_maxrss

#############################################################################
#                             Hash tables by range                          #
#############################################################################

# A table that groups all keys in a specific range together.  Instead of
# directly storing the values for a group of keys, we store an object (termed a
# "collector") that the user can use to keep track of the keys and values.
# This way, the user can choose to use a list of values, a set of values, a
# table of keys and values, etc.

class TableByRange(object):
  # Create a new object. 'ranges' is a sorted list of numbers, indicating the
  # boundaries of the ranges.  One range includes all keys that are
  # numerically below the first number, one range includes all keys that are
  # at or above the last number, and there is a range going from each number
  # up to, but not including, the next number.  'collector' is used to create
  # the collectors used to keep track of keys and values within each range;
  # it is either a type or a no-argument factory function.  We only create
  # ranges and collectors as needed. 'lowest_bound' is the value of the
  # lower bound of the lowest range; default is 0.  This is used only
  # it iter_ranges() when returning the lower bound of the lowest range,
  # and can be an item of any type, e.g. the number 0, the string "-infinity",
  # etc.
  def __init__(self, ranges, collector, lowest_bound=0):
    self.ranges = ranges
    self.collector = collector
    self.lowest_bound = lowest_bound
    self.items_by_range = {}

  def get_collector(self, key):
    lower_range = self.lowest_bound
    # upper_range = 'infinity'
    for i in self.ranges:
      if i <= key:
        lower_range = i
      else:
        # upper_range = i
        break
    if lower_range not in self.items_by_range:
      self.items_by_range[lower_range] = self.collector()
    return self.items_by_range[lower_range]

  def iter_ranges(self, unseen_between=True, unseen_all=False):
    """Return an iterator over ranges in the table.  Each returned value is
a tuple (LOWER, UPPER, COLLECTOR), giving the lower and upper bounds
(inclusive and exclusive, respectively), and the collector item for this
range.  The lower bound of the lowest range comes from the value of
'lowest_bound' specified during creation, and the upper bound of the range
that is higher than any numbers specified during creation in the 'ranges'
list will be the string "infinity" is such a range is returned.

The optional arguments 'unseen_between' and 'unseen_all' control the
behavior of this iterator with respect to ranges that have never been seen
(i.e. no keys in this range have been passed to 'get_collector').  If
'unseen_all' is true, all such ranges will be returned; else if
'unseen_between' is true, only ranges between the lowest and highest
actually-seen ranges will be returned."""
    highest_seen = None
    for (lower, upper) in (
        izip(chain([self.lowest_bound], self.ranges),
             chain(self.ranges, ['infinity']))):
      if lower in self.items_by_range:
        highest_seen = upper

    seen_any = False
    for (lower, upper) in (
        izip(chain([self.lowest_bound], self.ranges),
             chain(self.ranges, ['infinity']))):
      collector = self.items_by_range.get(lower, None)
      if collector is None:
        if not unseen_all:
          if not unseen_between: continue
          if not seen_any: continue
          if upper == 'infinity' or upper > highest_seen: continue
        collector = self.collector()
      else:
        seen_any = True
      yield (lower, upper, collector)


#############################################################################
#                          Depth-, breadth-first search                     #
#############################################################################

# General depth-first search.  'node' is the node to search, the top of a
# tree.  'matches' indicates whether a given node matches.  'children'
# returns a list of child nodes.
def depth_first_search(node, matches, children):
  nodelist = [node]
  while len(nodelist) > 0:
    node = nodelist.pop()
    if matches(node):
      yield node
    nodelist.extend(reversed(children(node)))

# General breadth-first search.  'node' is the node to search, the top of a
# tree.  'matches' indicates whether a given node matches.  'children'
# returns a list of child nodes.
def breadth_first_search(node, matches, children):
  nodelist = deque([node])
  while len(nodelist) > 0:
    node = nodelist.popLeft()
    if matches(node):
      yield node
    nodelist.extend(children(node))
