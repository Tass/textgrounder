#!/usr/bin/python

import os
from nlputil import *

dry_run = False

# Run a series of disambiguation experiments.

if 'TEXTGROUNDER_PYTHON' in os.environ:
  tgdir = os.environ['TEXTGROUNDER_PYTHON']
else:
  tgdir = '%s/python' % (os.environ['TEXTGROUNDER_DIR'])
runcmd = '%s/run-run-disambig' % tgdir

def runit(fun, id, args):
  command='%s --id %s documents %s' % (runcmd, id, args)
  errprint("Executing: %s" % command)
  if not dry_run:
    os.system("%s" % command)

def combine(*funs):
  def do_combine(fun, *args):
    for f in funs:
      f(fun, *args)
  return do_combine

def iterate(paramname, vals):
  def do_iterate(fun, id, args):
    for val in vals:
      fun('%s.%s' % (id, val), '%s %s %s' % (args, paramname, val))
  return do_iterate

def add_param(param):
  def do_add_param(fun, id, args):
    fun(id, '%s %s' % (args, param))
  return do_add_param

def recurse(funs, *args):
  if not funs:
    return
  (funs[0])(lambda *args: recurse(funs[1:], *args), *args)

def nest(*nest_funs):
  def do_nest(fun, *args):
    recurse(nest_funs + (fun,), *args)
  return do_nest

def run_exper(exper, expername):
  exper(lambda fun, *args: runit(id, *args), expername, '')

def main():
  op = OptionParser(usage="%prog [options] experiment [...]")
  op.add_option("-n", "--dry-run", action="store_true",
		  help="Don't execute anything; just output the commands that would be executed.")
  (opts, args) = op.parse_args()
  global dry_run
  if opts.dry_run:
    dry_run = True
  if not args:
    op.print_help()
  for exper in args:
    run_exper(eval(exper), exper)

##############################################################################
#                       Description of experiments                           #
##############################################################################

MTS300 = iterate('--max-time-per-stage', [300])
NonBaselineStrategies = iterate('--strategy',
    ['partial-kl-divergence', 'per-word-region-distribution', 'naive-bayes-no-baseline', 'partial-cosine-similarity'])
BaselineStrategies = iterate('--strategy baseline --baseline-strategy',
    ['internal-link', 'random', 'num-articles',
     'link-most-common-toponym', 'regdist-most-common-toponym'])
AllStrategies = combine(NonBaselineStrategies, BaselineStrategies)

CoarseDPR = iterate('--degrees-per-region',
    #[90, 30, 10, 5, 3, 2, 1, 0.5]
    #[0.5, 1, 2, 3, 5, 10, 30, 90]
    [0.5, 1, 2, 3, 5, 10])
OldFineDPR = iterate('--degrees-per-region',
    [90, 75, 60, 50, 40, 30, 25, 20, 15, 12, 10, 9, 8, 7, 6, 5, 4, 3, 2.5, 2,
     1.75, 1.5, 1.25, 1, 0.87, 0.75, 0.63, 0.5, 0.4, 0.3, 0.25, 0.2, 0.15, 0.1]
    )

DPR1 = iterate('--degrees-per-region', [0.5, 1, 3])

MinWordCount = iterate('--minimum-word-count', [1, 2, 3, 4, 5])

CoarseDisambig = nest(MTS300, AllStrategies, CoarseDPR)

# PCL experiments
PCLDPR = iterate('--degrees-per-region', [1.5, 0.5, 1, 2, 3, 5])
PCLEvalFile = add_param('-f pcl-travel -e /groups/corpora/pcl_travel/books')
PCLDisambig = nest(MTS300, PCLEvalFile, NonBaselineStrategies, PCLDPR)

# Param experiments

ParamExper = nest(MTS300, DPR1, MinWordCount, NonBaselineStrategies)

# Fine experiments

FinerDPR = iterate('--degrees-per-region', [0.3, 0.2, 0.1])
EvenFinerDPR = iterate('--degrees-per-region', [0.1, 0.05])
KLDivStrategy = iterate('--strategy', ['partial-kl-divergence'])
FullKLDivStrategy = iterate('--strategy', ['kl-divergence'])
FinerExper = nest(MTS300, FinerDPR, KLDivStrategy)
EvenFinerExper = nest(MTS300, EvenFinerDPR, KLDivStrategy)

# Missing experiments

MissingNonBaselineStrategies = iterate('--strategy',
    ['naive-bayes-no-baseline', 'partial-cosine-similarity', 'cosine-similarity'])
MissingBaselineStrategies = iterate('--strategy baseline --baseline-strategy',
    ['link-most-common-toponym'
      #, 'regdist-most-common-toponym'
      ])
NBStrategy = iterate('--strategy',
    ['naive-bayes-no-baseline'])
MissingOtherNonBaselineStrategies = iterate('--strategy',
    ['partial-cosine-similarity', 'cosine-similarity'])
MissingAllButNBStrategies = combine(MissingOtherNonBaselineStrategies,
    MissingBaselineStrategies)
#Original MissingExper failed on or didn't include all but
#regdist-most-common-toponym.
#MissingExper = nest(MTS300, CoarseDPR, MissingAllStrategies)

MissingNBExper = nest(MTS300, CoarseDPR, NBStrategy)
MissingOtherExper = nest(MTS300, CoarseDPR, MissingAllButNBStrategies)
MissingBaselineExper = nest(MTS300, CoarseDPR, MissingBaselineStrategies)
FullKLDivExper = nest(MTS300, CoarseDPR, FullKLDivStrategy)


main()
