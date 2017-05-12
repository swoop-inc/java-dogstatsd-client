statsd-client
=============

[![Build Status](https://travis-ci.org/swoop-inc/statsd-client.svg?branch=swoop)](https://travis-ci.org/swoop-inc/statsd-client/builds) (**`swoop` branch status**)

A statsd client library implemented in Java. Allows for Java applications to easily communicate with statsd.

This version was originally forked from [DataDog/java-dogstatsd-client](https://github.com/DataDog/java-dogstatsd-client), [java-dogstatsd-client](https://github.com/indeedeng/java-dogstatsd-client) and [java-statsd-client](https://github.com/youdevise/java-statsd-client) but it is now the canonical home for swoop's statsd client.

See [CHANGELOG.md](CHANGELOG.md) for changes.

Maven Integration
-----------------

```xml
<dependency>
    <groupId>com.swoop</groupId>
    <artifactId>statsd-client</artifactId>
    <version>${swoop.statsd-client.version}</version>
</dependency>
```

Releases can be found here: <https://github.com/Shopximity/swoop-rest/releases>

Usage
-----
```java
import com.timgroup.statsd.ServiceCheck;
import com.timgroup.statsd.StatsDClient;
import com.timgroup.statsd.NonBlockingStatsDClient;

public class Foo {

  private static final StatsDClient statsd = new DisruptorStatsDClient(
    "my.prefix",                          /* prefix to any stats; may be null or empty string */
    "statsd-host",                        /* common case: localhost */
    8125,                                 /* port */
    new String[] {"tag:value"}            /* Datadog extension: Constant tags, always applied */
  );

  public static final void main(String[] args) {
    statsd.incrementCounter("foo");
    statsd.recordGaugeValue("bar", 100);
    statsd.recordGaugeValue("baz", 0.01); /* DataDog extension: support for floating-point gauges */
    statsd.recordHistogramValue("qux", 15);     /* DataDog extension: histograms */
    statsd.recordHistogramValue("qux", 15.5);   /* ...also floating-point */

    ServiceCheck sc = ServiceCheck
          .builder()
          .withName("my.check.name")
          .withStatus(ServiceCheck.Status.OK)
          .build();
    statsd.serviceCheck(sc); /* Datadog extension: send service check status */

    /* Compatibility note: Unlike upstream statsd, DataDog expects execution times to be a
     * floating-point value in seconds, not a millisecond value. This library
     * does the conversion from ms to fractional seconds.
     */
    statsd.recordExecutionTime("bag", 25, "cluster:foo"); /* DataDog extension: cluster tag */
  }
}
```
