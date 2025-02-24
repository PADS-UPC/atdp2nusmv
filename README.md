# atdp-reasoner

The ATDP-Reasoner is a tool built to convert ATDP specifications into model
specifications that can be tested with the NuSMV model checker
http://nusmv.fbk.eu/. 


## Installation

The install scripts will only work in UNIX-like systems. To install, you will
need a recent java installation (>= 1.8)  as well as
[`maven`](https://maven.apache.org/) and the
[`leiningen`](https://leiningen.org/) build tools: . Once the dependencies are
met, run:


```
$ sh install-local-jars.sh
$ lein uberjar
```

This will generate a standalone jar file under the `./target` directory that is
ready to be run without any further dependencies other than Java. Alternatively,
please feel free to use the pre-compiled version in the `./bin` directory of
this repository.


## Usage

The generated standalone jar can be run with the following syntax:


```
java -jar target/atd-reasoner-0.1.0-SNAPSHOT-standalone.jar <atdp-path> <query> <initial-activity> <out-path>
```

Where:

- **`atdp-path`**: Is the path to an ATDP specification. There are some
  specification examples in the `./examples` directory.
- **`query`**: Is an LTL query in prefix notation using lists. LTL operators are
  preceded by a colon (`:`) and activity ids must be enclosed in double quotes
  (`""`). For example `[:G [:-> "a" [:U "b" "c"]]]` can be translated as `"G (a -> (b U c))"`. 
  Any Activity fragment in the ATDP can be used as an activity id in
  the query. Additionally, you can prefix a scope id with `"START_"` and
  `"END_"` to refer to its implicit start and end activities.
- **`initial-activity`**: The initial activity in the model. Typically set to
  the start of the topmost-level scope (in the examples, `"START_P0"`).
- **`out`**: The path and filename where the nusmv model should be generated.

Please note that this software only deals with single
interpretations (that is, atdp specifications are completely unambiguous). 
To reason on multiple ambiguous interpretations please store them as separate
specifications and run this software multiple times accordingly.

## Examples

```
java -jar target/atd-reasoner-0.1.0-SNAPSHOT-standalone.jar './examples/hosp-2-i1.clj' '[:G [:-> "T20" [:U [:! "T23"] "T19"]]]' "START_P0" "./hosp-2-i1.nusmv"
```

## The ATDP file format

ATDP are stored in [Extensible Data Notation (EDN)](https://learnxinyminutes.com/docs/edn/). The schema is as follows:

A top-level map, with four keys:

- `:spans`: Contains the activity fragment specifications. Available fragment types are: `Action`, `Entity`.
- `:relations`: Contains the activity fragment relations. Available relation types are: `Agent`, `Patient`, `Coreference`, `Precedence`, `Response`, `Sequence`, `NoCoOccurs`, `Terminating`, `Mandatory`, `LoopBack`. Note that some of them have slightly different names in the paper.
- `:scopes`: Contain the scopes definitions.
- `:scope-relations`: Contain the scope relation definitions. Available scope relation types are: `Sequential`, `Exclusive`, `Concurrent`, `Iterating`, `Mandatory`.

The rest of the fields are self-documenting and example annotations can be found in the `./examples` folder.

## License

Copyright © 2019  Universitat Politècnica de Catalunya

Distributed under the GNU General Public License version 3.
