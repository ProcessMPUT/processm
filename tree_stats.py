#!/usr/bin/python
import csv
import sys
from pm4py.evaluation.precision import evaluator as precision_evaluator
from pm4py.evaluation.replay_fitness import evaluator as replay_fitness_evaluator
from pm4py.objects.conversion.process_tree import converter as pt_converter
from pm4py.objects.log.importer.xes import importer as xes_importer
from pm4py.objects.process_tree.importer import importer as ptml_importer

_python_script_name, xes_file, tree_file, iter, alg_name, mode, window_size, step, current_pos = sys.argv

# Import log file
log = xes_importer.apply('logFile-' + mode + '.xes')

# Import tree and convert it to Petri net
tree = ptml_importer.apply(tree_file)
petri_net, initial_marking, final_marking = pt_converter.apply(tree, variant=pt_converter.Variants.TO_PETRI_NET)

# Supported 'perc_fit_traces', 'average_trace_fitness', 'log_fitness'
fitness = replay_fitness_evaluator.apply(log, petri_net, initial_marking, final_marking,
                                         variant=replay_fitness_evaluator.Variants.TOKEN_BASED)['log_fitness']

with open(iter + '-' + alg_name + '-' + mode + '-fitness-' + xes_file, 'a+') as output_file:
    content = [window_size, step, current_pos, fitness]
    csv.writer(output_file).writerow(content)

precision = precision_evaluator.apply(log, petri_net, initial_marking, final_marking,
                                      variant=precision_evaluator.Variants.ETCONFORMANCE_TOKEN)

with open(iter + '-' + alg_name + '-' + mode + '-precision-' + xes_file, 'a+') as output_file:
    content = [window_size, step, current_pos, precision]
    csv.writer(output_file).writerow(content)
