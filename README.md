# pokemans

A few fun examples of how Datomic works, using the delightful and
completely-made-up-without-infringing-on-valuable-copyrights world of
pokemans.

Used in a fun introductory session at
[Ladders](http://engineering.theladders.com).

## Usage

Schema and initial data are in the [resources folder](resources/).

Start up a repl, switch to the `dev` namespace, and run `(restart)` to
load up an in-memory Datomic database with the schema and data. The
`conn` var will contain the resulting Datomic connection.

Randomly generate some pokemans with `(generate-pokemans conn n)`
where n is the number of pokemans you want to generate.

Have each traineur catch some pokemans with
`(everyone-capture-n-pokemans conn n)` which will have each traineur
randomly catch n pokemans until everyone catches n or they've caught
them all.

There are a couple other nifty things in the
[dev namespace](dev/dev.clj) that you can play with, including some
commented-out steps for setting up a test for duplicates.

## License

Copyright © 2016 Michael Gaare

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
