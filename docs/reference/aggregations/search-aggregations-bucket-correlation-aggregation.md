---
navigation_title: "Bucket correlation"
mapped_pages:
  - https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-correlation-aggregation.html
---

# Bucket correlation aggregation [search-aggregations-bucket-correlation-aggregation]


A sibling pipeline aggregation which executes a correlation function on the configured sibling multi-bucket aggregation.

## Parameters [bucket-correlation-agg-syntax]

### `buckets_path`

(Required, string) Path to the buckets that contain one set of values to correlate. For syntax, see [`buckets_path` Syntax](/reference/aggregations/pipeline.md#buckets-path-syntax).

### `function`

(Required, object) The correlation function to execute.

#### `count_correlation`

(Required*, object) The configuration to calculate a count correlation. This function is designed for determining the correlation of a term value and a given metric. Consequently, it needs to meet the following requirements.

* The `buckets_path` must point to a `_count` metric.
* The total count of all the `bucket_path` count values must be less than or equal to `indicator.doc_count`.
* When utilizing this function, an initial calculation to gather the required `indicator` values is required.

`indicator`
:   (Required, object) The indicator with which to correlate the configured `bucket_path` values.

    `doc_count`
    :   (Required, integer) The total number of documents that initially created the `expectations`. It’s required to be greater than or equal to the sum of all values in the `buckets_path` as this is the originating superset of data to which the term values are correlated.

    `expectations`
    :   (Required, array) An array of numbers with which to correlate the configured `bucket_path` values. The length of this value must always equal the number of buckets returned by the `bucket_path`.

    `fractions`
    :   (Optional, array) An array of fractions to use when averaging and calculating variance. This should be used if the pre-calculated data and the `buckets_path` have known gaps. The length of `fractions`, if provided, must equal `expectations`.

## Syntax [_syntax_8]

A `bucket_correlation` aggregation looks like this in isolation:

```js
{
  "bucket_correlation": {
    "buckets_path": "range_values>_count", <1>
    "function": {
      "count_correlation": { <2>
        "indicator": {
          "expectations": [...],
          "doc_count": 10000
        }
      }
    }
  }
}
```

1. The buckets containing the values to correlate against.
2. The correlation function definition.



## Example [bucket-correlation-agg-example]

The following snippet correlates the individual terms in the field `version` with the `latency` metric. Not shown is the pre-calculation of the `latency` indicator values, which was done utilizing the [percentiles](/reference/aggregations/search-aggregations-metrics-percentile-aggregation.md) aggregation.

This example is only using the 10s percentiles.

```console
POST correlate_latency/_search?size=0&filter_path=aggregations
{
  "aggs": {
    "buckets": {
      "terms": { <1>
        "field": "version",
        "size": 2
      },
      "aggs": {
        "latency_ranges": {
          "range": { <2>
            "field": "latency",
            "ranges": [
              { "to": 0.0 },
              { "from": 0, "to": 105 },
              { "from": 105, "to": 225 },
              { "from": 225, "to": 445 },
              { "from": 445, "to": 665 },
              { "from": 665, "to": 885 },
              { "from": 885, "to": 1115 },
              { "from": 1115, "to": 1335 },
              { "from": 1335, "to": 1555 },
              { "from": 1555, "to": 1775 },
              { "from": 1775 }
            ]
          }
        },
        "bucket_correlation": { <3>
          "bucket_correlation": {
            "buckets_path": "latency_ranges>_count",
            "function": {
              "count_correlation": {
                "indicator": {
                   "expectations": [0, 52.5, 165, 335, 555, 775, 1000, 1225, 1445, 1665, 1775],
                   "doc_count": 200
                }
              }
            }
          }
        }
      }
    }
  }
}
```

1. The term buckets containing a range aggregation and the bucket correlation aggregation. Both are utilized to calculate the correlation of the term values with the latency.
2. The range aggregation on the latency field. The ranges were created referencing the percentiles of the latency field.
3. The bucket correlation aggregation that calculates the correlation of the number of term values within each range and the previously calculated indicator values.


And the following may be the response:

```console-result
{
  "aggregations" : {
    "buckets" : {
      "doc_count_error_upper_bound" : 0,
      "sum_other_doc_count" : 0,
      "buckets" : [
        {
          "key" : "1.0",
          "doc_count" : 100,
          "latency_ranges" : {
            "buckets" : [
              {
                "key" : "*-0.0",
                "to" : 0.0,
                "doc_count" : 0
              },
              {
                "key" : "0.0-105.0",
                "from" : 0.0,
                "to" : 105.0,
                "doc_count" : 1
              },
              {
                "key" : "105.0-225.0",
                "from" : 105.0,
                "to" : 225.0,
                "doc_count" : 9
              },
              {
                "key" : "225.0-445.0",
                "from" : 225.0,
                "to" : 445.0,
                "doc_count" : 0
              },
              {
                "key" : "445.0-665.0",
                "from" : 445.0,
                "to" : 665.0,
                "doc_count" : 0
              },
              {
                "key" : "665.0-885.0",
                "from" : 665.0,
                "to" : 885.0,
                "doc_count" : 0
              },
              {
                "key" : "885.0-1115.0",
                "from" : 885.0,
                "to" : 1115.0,
                "doc_count" : 10
              },
              {
                "key" : "1115.0-1335.0",
                "from" : 1115.0,
                "to" : 1335.0,
                "doc_count" : 20
              },
              {
                "key" : "1335.0-1555.0",
                "from" : 1335.0,
                "to" : 1555.0,
                "doc_count" : 20
              },
              {
                "key" : "1555.0-1775.0",
                "from" : 1555.0,
                "to" : 1775.0,
                "doc_count" : 20
              },
              {
                "key" : "1775.0-*",
                "from" : 1775.0,
                "doc_count" : 20
              }
            ]
          },
          "bucket_correlation" : {
            "value" : 0.8402398981360937
          }
        },
        {
          "key" : "2.0",
          "doc_count" : 100,
          "latency_ranges" : {
            "buckets" : [
              {
                "key" : "*-0.0",
                "to" : 0.0,
                "doc_count" : 0
              },
              {
                "key" : "0.0-105.0",
                "from" : 0.0,
                "to" : 105.0,
                "doc_count" : 19
              },
              {
                "key" : "105.0-225.0",
                "from" : 105.0,
                "to" : 225.0,
                "doc_count" : 11
              },
              {
                "key" : "225.0-445.0",
                "from" : 225.0,
                "to" : 445.0,
                "doc_count" : 20
              },
              {
                "key" : "445.0-665.0",
                "from" : 445.0,
                "to" : 665.0,
                "doc_count" : 20
              },
              {
                "key" : "665.0-885.0",
                "from" : 665.0,
                "to" : 885.0,
                "doc_count" : 20
              },
              {
                "key" : "885.0-1115.0",
                "from" : 885.0,
                "to" : 1115.0,
                "doc_count" : 10
              },
              {
                "key" : "1115.0-1335.0",
                "from" : 1115.0,
                "to" : 1335.0,
                "doc_count" : 0
              },
              {
                "key" : "1335.0-1555.0",
                "from" : 1335.0,
                "to" : 1555.0,
                "doc_count" : 0
              },
              {
                "key" : "1555.0-1775.0",
                "from" : 1555.0,
                "to" : 1775.0,
                "doc_count" : 0
              },
              {
                "key" : "1775.0-*",
                "from" : 1775.0,
                "doc_count" : 0
              }
            ]
          },
          "bucket_correlation" : {
            "value" : -0.5759855613334943
          }
        }
      ]
    }
  }
}
```


