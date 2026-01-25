# Beerouter

Beerouter is a fork of [brouter](https://brouter.de/) with the aim to modernize the code base,
improve performance and add support for Kotlin Multiplatform.

## License

Beerouter is licensed under the [Mozilla Public License Version 2.0](LICENSE). Its code is derived
from the brouter project, see [attribution](ATTRIBUTION).

## Performance

```
Benchmark                    Mode  Cnt   Score   Error  Units
RoutingBenchmark.beerouter  thrpt    5  14.987 ± 1.847  ops/s
RoutingBenchmark.brouter    thrpt    5  15.979 ± 0.815  ops/s
```

Generated with: be784db87956b17153b3dbdd85e7fcb03dc8c67c
