# Sample data

| File | Content |
|---|---|
| `cdrs-small.ndjson` | 20 valid records (NDJSON) for `/cdrs/stream` or `/cdrs/async` |
| `cdrs-with-errors.ndjson` | 30 records with ~20% invalid and ~10% duplicate records |
| `batch.json` | 5 records wrapped for `/cdrs/batch` |

Start times are relative to when the files were generated, so regenerate them if they are older than
the usage range you query: `make sample-data`. For load testing generate bigger feeds:
`make gen-data N=1000000` (writes `sample-data/cdrs-generated.ndjson.gz`, git-ignored).
