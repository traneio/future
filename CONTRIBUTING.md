
Pull requests are welcome! :) 

Feel free to create the pull request even before it is ready for review so the maintainers can give you early feedback. Please add the prefix [WIP] to the title if it is sill in progress.

We are looking for developers interested in becoming a maintainer. After a few contributions, please reach out to one of the maintainers to join the team if you are interested. Note that contributions are not necessarily pull requests; they can be help on the gitter channel, issues triage, documentation improvements, and others.

Building the project
==================

The pre-requisites are [Maven](maven.apache.org/) and Java 8. Go to the project directory and run this command to build the library and run the tests:

```
mvn install
```

To check the test coverage, run:

```
mvn org.pitest:pitest-maven:mutationCoverage --projects future-java
```

This target uses [pitest](http://pitest.org) and checks for both line coverage and mutation coverage.

Analyzing performance
======================

The main focus of this project is to provide a `Future` implementation with low CPU usage and memory footprint. There are a few tools that can be used to help you understand the impact of your changes:

- The [jmh benchmarks module](https://github.com/traneio/future/tree/master/future-benchmark) is an objective way to measure the impact of your change. Feel free to create new benchmarks if necessary. The benchmarks are usually enough to understand how the change behaves regarding CPU usage and memory allocation.

- If it is not clear why the benchmark produced a certain score, you can use [Yourkit](http://yourkit.com/) to profile the change. It has a 30-day trial period and free licenses for open source developers.

- Sometimes even Yourkit is not enough to understand the performance characteristics of your code. The [Intel VTune](https://software.intel.com/en-us/intel-vtune-amplifier-xe) profiler is a great tool to perform advanced profiling at the CPU level. For instance, it can show the cost of each line of a method, and how the code interacts with the CPU caches. It also has a trial period, but unfortunately, does not have free licenses for open source.

- Another important factor for performance is how your code interacts with the Just In Time Compiler (JIT). [JITWatch](https://github.com/AdoptOpenJDK/jitwatch) is the recommended tool to visualize how the JIT optimizes your code. Note that optimizing for JIT compilation is not always the best thing to do, sometimes it is better to optimize for lower memory allocation. For instance, `Future` has multiple concrete implementations to reduce memory usage, but it requires megamorphic calls.

Performance tips
================

This section uses Twitter's Future to exemplify performance optimizations since this project has it as its main inspiration.

**Reduce closure allocations**

Closures are so convenient and easy to create in Java 8 that it is common to create one without thinking about the allocation of the closure object and references to the outer scope. For instance, [this closure](https://github.com/twitter/util/blob/226f778ad5f1e0e0790512fc7f21a4094534e8d7/util-core/src/main/scala/com/twitter/util/Future.scala#L473) is instantiated for each list element and has five fields (`fsSize`, `results`, `count`, `p`, `ii`), each one using 8 bytes of memory in a 64-bit architecture. For lists of size greater than two, it is more efficient to create a [single object](https://github.com/traneio/future/blob/c83ea89b2a6fc44991d4fa04946b74e423013751/future-core/src/main/java/io/futures/Future.java#L67) with `fsSize`, `results`, `counts`, and `p`, and reuse this object as a reference of each callback closure allocation.

**Merge interfaces**

A typical pattern in the future implementation is creating a promise and other control structures to perform a transformation. It is often possible to create a new class that satisfies multiple interfaces and instantiate a single object. As an example, these [two objects](https://github.com/twitter/util/blob/226f778ad5f1e0e0790512fc7f21a4094534e8d7/util-core/src/main/scala/com/twitter/util/Future.scala#L241) (p, update) can be merged into a [single one](https://github.com/traneio/future/blob/c83ea89b2a6fc44991d4fa04946b74e423013751/future-core/src/main/java/io/futures/Future.java#L106).

**Specialize implementations**

Sometimes an abstraction can simplify the code but hurt performance. For example, Twitter’s Future trait is a nice abstraction; it requires its subclasses to implement only the `respond` and `transform` transformations. This approach comes with a performance penalty: the other methods require an [additional closure allocation](https://github.com/twitter/util/blob/226f778ad5f1e0e0790512fc7f21a4094534e8d7/util-core/src/main/scala/com/twitter/util/Future.scala#L1144) that adapts the user function to the `transform` or `respond` function. Specializing methods often enable optimizations [1](https://github.com/traneio/future/blob/c83ea89b2a6fc44991d4fa04946b74e423013751/future-core/src/main/java/io/futures/ExceptionFuture.java#L22) [2](https://github.com/traneio/future/blob/f4484f4d9eec17479317a4bfabff3c305aade4d3/src/main/java/io/futures/ValueFuture.java#L16) [3](https://github.com/traneio/future/blob/f4484f4d9eec17479317a4bfabff3c305aade4d3/src/main/java/io/futures/Promise.java#L188).

**Keep methods small**

JIT inlining is crucial to reduce CPU use. Some hot methods used by Twitter’s Future cannot be inlined because they are too large or become large after inlining its nested methods. It is import to inspect the JIT log to make sure that the hot methods are inlined. 