#!/bin/bash

mvn install:install-file -Dfile=./local-jars/atdlib-0.1.0-jar-with-dependencies.jar -DgroupId=edu.upc -DartifactId=atdlib -Dversion=0.1.0 -Dpackaging=jar -DgeneratePom=true
