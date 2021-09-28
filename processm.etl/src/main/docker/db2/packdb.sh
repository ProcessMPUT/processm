#!/bin/bash

cd /database
tar c data config | xz --compress -9 >/database.tar.xz
