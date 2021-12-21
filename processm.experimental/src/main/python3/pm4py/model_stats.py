#!/usr/bin/env python3

import concurrent.futures
import json
import re
import sys
import tarfile
import tempfile
from pathlib import Path
from collections import defaultdict

from pm4py.algo.evaluation.replay_fitness import evaluator as replay_fitness_evaluator
from pm4py.objects.log.importer.xes import importer as xes_importer
from pm4py.objects.petri.importer import importer as pnml_importer

r_model = re.compile(r'^model_(?P<log>.*)_(?P<window>\d+)_(?P<sublog>\d+)_(?P<idx>\d+)\.pnml$')
# <transition id="{01_HOOFD_020 -> [01_HOOFD_030_1, 01_HOOFD_030_2, end*]}">
r_split = re.compile(r'^\{(.*) -> \[(.*?)\]\}$')
# <transition id="{[01_HOOFD_050, 01_HOOFD_040] -> 01_HOOFD_060}">
r_join = re.compile(r'^\{\[(.*?)\] -> (.*)\}$')
binding_separator = ', '

def process2(descriptor, model_fn):
    net, initial_marking, final_marking = pnml_importer.apply(model_fn)

    n_activities = 0
    splits = defaultdict(list)
    joins = defaultdict(list)

    for t in net.transitions:
        if t.label[0] != '{':
            n_activities += 1
        else:
            m = r_split.fullmatch(t.label)
            if m is not None:
                splits[m.group(1)].append(len(m.group(2).split(binding_separator)))
            else:
                m = r_join.fullmatch(t.label)
                assert m is not None, t.label
                joins[m.group(2)].append(len(m.group(1).split(binding_separator)))

    n_deps = len([p for p in net.places if '->' in p.name])
    splits = list(splits.values())
    joins = list(joins.values())

    result = {'activities': n_activities, 'dependencies': n_deps, 'splits': splits, 'joins': joins}
    result.update(descriptor)
    return json.dumps(result)


def process_directory(input_directory, output_file, glob_pattern='*'):
    futures = []
    with concurrent.futures.ProcessPoolExecutor() as executor:
        for model in Path(input_directory).glob(glob_pattern):
            match = r_model.fullmatch(model.name)
            if match is not None:
                descriptor = match.groupdict()
                futures.append(executor.submit(process2, descriptor, str(model)))
    with open(output_file, 'wt') as out:
        for f in futures:
            assert f.done()
            e = f.exception()
            if e is not None:
                print(e)
            else:
                print(f.result(), file=out)


def main():
    process_directory(*sys.argv[1:])
#    print(process2({}, '20210222artifacts/model_Receipt_phase_of_an_environmental_permit_application_process_WABO_CoSeLoG_project.xes.gz_100_4_98.pnml'))

if __name__ == '__main__':
    main()
