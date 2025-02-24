# atdp-reasoner

The ATDP-Reasoner is a tool built to convert ATDP specifications into model specifications that can be tested with the NuSMV model checker [http://nusmv.fbk.eu/].

## Installation

The install scripts will only work in UNIX-like systems. To install, you will need a recent java installation (>= 1.8)  as well as [`maven`](https://maven.apache.org/) and the [`leiningen`](https://leiningen.org/) build tools: . Once the dependencies are met, run:

```
$ sh install-local-jars.sh
$ lein uberjar
```

This will generate a standalone jar file under the `./target` directory that is ready to be run without any further dependencies other than Java. Alternatively, please feel free to use the pre-compiled version in the `./bin` directory of this repository.

## Usage


## Examples

...

## License

Copyright © 2019  Universitat Politècnica de Catalunya

Distributed under the GNU General Public License version 3.
