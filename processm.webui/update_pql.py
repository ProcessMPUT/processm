#!/usr/bin/env python3

import re
import subprocess
from pathlib import Path


def main():
    pql_file = "../docs/pql.md"
    template_file = "src/main/frontend-app/src/templates/PQLDocs.vue"
    output_file = "src/main/frontend-app/src/generated/PQLDocs.vue"
    with open(pql_file, 'rt') as f:
        pql_md = f.read()
    p = subprocess.run(["pandoc", "-f", "markdown", "-t", "html", pql_file], check=True, capture_output=True,
                       input=pql_md, universal_newlines=True)
    pql = p.stdout
    Path(output_file).parent.mkdir(parents=True, exist_ok=True)
    with open(template_file, 'rt') as f:
        template = f.read()
    m = re.search(r'<pql-doc\s*/>', template)
    assert m is not None
    final = template[:m.start()] + pql + template[m.end():]
    with open(output_file, 'wt') as g:
        g.write(final)


if __name__ == '__main__':
    main()
