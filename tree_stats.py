#!/usr/bin/python
import csv
import glob
import sys
from pm4py.evaluation.precision import evaluator as precision_evaluator
from pm4py.evaluation.replay_fitness import evaluator as replay_fitness_evaluator
from pm4py.objects.conversion.process_tree import converter as pt_converter
from pm4py.objects.log.importer.xes import importer as xes_importer
from pm4py.objects.process_tree.importer import importer as ptml_importer

_python_script_name, xes_file_name, iteration = sys.argv

for test_log_file in glob.glob('logFile-test-[0-9]*-[0-9]*-[0-9]*-' + xes_file_name + '.xes'):
    print(test_log_file)
    splitted_name = test_log_file.split('-')  # ['logFile', 'test', 'window size', 'step', 'current pos', 'xes'...
    window_size = splitted_name[2]
    step = splitted_name[3]
    current_pos = splitted_name[4]

    # Import log files
    test_log = xes_importer.apply(test_log_file)
    if not test_log:
        continue
    splitted_name[1] = 'train'
    train_log = xes_importer.apply('-'.join(splitted_name))

    # Import trees
    offline_without_stats_tree = ptml_importer.apply(
        '-'.join(['offlinefalse', window_size, step, current_pos, xes_file_name]) + '.tree')
    offline_with_stats_tree = ptml_importer.apply(
        '-'.join(['offlinetrue', window_size, step, current_pos, xes_file_name]) + '.tree')
    online_tree = ptml_importer.apply('-'.join(['onlineModel', window_size, step, current_pos, xes_file_name]) + '.tree')

    for (mode, log) in [('test', test_log), ('train', train_log)]:
        for (alg_name, tree) in [('offlinefalse', offline_without_stats_tree), ('offlinetrue', offline_with_stats_tree),
                                 ('online', online_tree)]:
            # Convert tree to Petri net
            petri_net, initial_marking, final_marking = pt_converter.apply(tree,
                                                                           variant=pt_converter.Variants.TO_PETRI_NET)

            # Supported 'perc_fit_traces', 'average_trace_fitness', 'log_fitness'
            fitness = replay_fitness_evaluator.apply(log, petri_net, initial_marking, final_marking,
                                                     variant=replay_fitness_evaluator.Variants.TOKEN_BASED)[
                'log_fitness']

            with open(iteration + '-' + alg_name + '-' + mode + '-fitness-' + xes_file_name, 'a+') as output_file:
                content = [window_size, step, current_pos, fitness]
                csv.writer(output_file).writerow(content)

            precision = precision_evaluator.apply(log, petri_net, initial_marking, final_marking,
                                                  variant=precision_evaluator.Variants.ETCONFORMANCE_TOKEN)

            with open(iteration + '-' + alg_name + '-' + mode + '-precision-' + xes_file_name, 'a+') as output_file:
                content = [window_size, step, current_pos, precision]
                csv.writer(output_file).writerow(content)
