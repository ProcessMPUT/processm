#!/usr/bin/env python3

import concurrent.futures
import json
import re
import sys
import tarfile
import tempfile
from pathlib import Path

from pm4py.algo.evaluation.replay_fitness import evaluator as replay_fitness_evaluator
from pm4py.objects.log.importer.xes import importer as xes_importer
from pm4py.objects.petri.importer import importer as pnml_importer

r_model = re.compile(r'^model_(?P<log>.*)_(?P<window>\d+)_(?P<sublog>\d+)_(?P<idx>\d+)\.pnml$')
trainlog_pattern = 'train_{log}_{window}_{sublog}_{idx}.xes'
testlog_pattern = 'test_{log}_{window}_{sublog}_{idx}.xes'
output_pattern = 'result_{log}_{window}_{sublog}_{idx}.json'


def process(logid, i, model_fn):
    net, initial_marking, final_marking = pnml_importer.apply(model_fn)
    for t in net.transitions:
        if t.label[0] == '{' or t.label == 'start' or t.label == 'end':
            t.label = None

    train_fitness = None
    train_precision = None
    test_fitness = None
    test_precision = None

    try:
        trainlog = xes_importer.apply(f'/tmp/trainlog_{logid}_{i}.xes')

        # train_fitness = replay_fitness_evaluator.apply(trainlog, net, initial_marking, final_marking, variant=replay_fitness_evaluator.Variants.TOKEN_BASED)
        train_fitness = replay_fitness_evaluator.apply(trainlog, net, initial_marking, final_marking,
                                                       variant=replay_fitness_evaluator.Variants.ALIGNMENT_BASED)
    #        train_precision = precision_evaluator.apply(trainlog, net, initial_marking, final_marking, variant=precision_evaluator.Variants.ETCONFORMANCE_TOKEN)
    except FileNotFoundError:
        pass

    #    try:
    #        testlog = xes_importer.apply(f'/tmp/testlog_{logid}_{i}.xes')
    #
    #        test_fitness = replay_fitness_evaluator.apply(testlog, net, initial_marking, final_marking, variant=replay_fitness_evaluator.Variants.TOKEN_BASED)
    #        test_precision = precision_evaluator.apply(testlog, net, initial_marking, final_marking, variant=precision_evaluator.Variants.ETCONFORMANCE_TOKEN)
    #    except FileNotFoundError:
    #        pass
    return {'model_fn': model_fn, 'logid': logid, 'i': i, 'train_precision': train_precision,
            'train_fitness': train_fitness, 'test_precision': test_precision, 'test_fitness': test_fitness}


def process2(descriptor, model_fn, trainlog_fn, testlog_fn, output_fn):
    net, initial_marking, final_marking = pnml_importer.apply(model_fn)
    for t in net.transitions:
        if t.label[0] == '{' or t.label == 'start' or t.label == 'end':
            t.label = None

    train_fitness = None
    test_fitness = None

    if trainlog_fn is not None:
        trainlog = xes_importer.apply(trainlog_fn)
        train_fitness = replay_fitness_evaluator.apply(trainlog, net, initial_marking, final_marking,
                                                       variant=replay_fitness_evaluator.Variants.TOKEN_BASED)

    if testlog_fn is not None:
        testlog = xes_importer.apply(testlog_fn)
        test_fitness = replay_fitness_evaluator.apply(testlog, net, initial_marking, final_marking,
                                                      variant=replay_fitness_evaluator.Variants.TOKEN_BASED)

    result = {'train_fitness': train_fitness, 'test_fitness': test_fitness}
    result.update(descriptor)
    with open(output_fn, 'wt') as f:
        json.dump(result, f)
    return None


def test():
    futures = []
    with concurrent.futures.ProcessPoolExecutor() as executor:
        for _ in range(1):
            # futures.append(executor.submit(process, 1, 51, "/tmp/model_1_51.pnml"))
            futures.append(executor.submit(process, 1, 30, "/tmp/model_1_30.pnml"))
    for f in futures:
        assert f.done()
        row = f.result()
        print(json.dumps(row), flush=True)


def process_directory(input_directory, output_directory, glob_pattern='*'):
    output_directory = Path(output_directory)
    if not output_directory.exists():
        output_directory.mkdir(parents=True)
    assert output_directory.exists() and output_directory.is_dir()
    futures = []
    with concurrent.futures.ProcessPoolExecutor() as executor:
        for model in Path(input_directory).glob(glob_pattern):
            match = r_model.fullmatch(model.name)
            if match is not None:
                descriptor = match.groupdict()
                output = output_directory / output_pattern.format_map(descriptor)
                if not output.exists():
                    trainlog = Path(input_directory) / trainlog_pattern.format_map(descriptor)
                    if trainlog.exists():
                        trainlog = str(trainlog)
                    else:
                        trainlog = None
                    testlog = Path(input_directory) / testlog_pattern.format_map(descriptor)
                    if testlog.exists():
                        testlog = str(testlog)
                    else:
                        testlog = None
                    print(model, "->", model, trainlog, testlog, output)
                    futures.append(executor.submit(process2, descriptor, str(model), trainlog, testlog, output))
                else:
                    print(model, "->", output, "SKIP")
                # if len(futures) == 1:
                #     break
    for f in futures:
        assert f.done()
        e = f.exception()
        if e is not None:
            print(e)
    # return [f.result() for f in futures]


def process_archive(archive, output):
    with tarfile.open(archive, 'r:gz') as tar:
        with tempfile.TemporaryDirectory() as directory:
            tar.extractall(directory)
            process_directory(directory, output)


def main():
    process_directory(*sys.argv[1:])
#    process_archive(sys.argv[1], sys.argv[2])
    #process2({}, 'dupa/model_CoSeLoG_WABO_1.xes.gz_25_2_33.pnml', None, 'dupa/test.xes', '/tmp/output.json')

    # files = []
    # for model_fn in glob.glob("/tmp/model*.pnml"):
    #     m = r_model.fullmatch(model_fn)
    #     logid = int(m.group(1))
    #     i = int(m.group(2))
    #     files.append((logid, i, model_fn))
    # for logid, i, model_fn in sorted(files):
    #     row = process(logid, i, model_fn)
    #     print(json.dumps(row), flush=True)


if __name__ == '__main__':
    main()
