# breaking-updates
A dataset of beaking dependency updates

We collect pull requests for which the diff modifies a single line in a pom file corresponding to the version (we exclude dependency additions and removals).

The update must be an application dependency (in tag `dependencies`) and not a build dependency in tag `build`)

The PRs might be done or not by Renovate or Dependabot, but looking for Renovate or Dependabot keywords on Github search might be very useful.

## Data format 

```json
{
  {
    "url": "<github pr url>",
    "project": "<github_project>",
    "commit": "<sha>",
    "versionUpdateType": "{major|minor|patch}",
    "type": "{human|renovate|dependabot|other}",
    "failureType": "free text", // stage 2
    "failurelog": "<CI url>", // stage 2
    "isFailureVerifiedLocally": "yes/no", // stage 2
  }
...
]
```

## Stages

* Stage 1: purely static
* Stage 2: reproduced locally
* Stage 3: isolated will all environment requests downloaded

## Stats

* Number of different projects in the dataset

