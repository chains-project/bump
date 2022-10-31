# Breaking Updates

## Overview 
A dataset of breaking dependency updates. 
These breaking updates are defined as:
* A pull request changing only a single line in a `pom.xml` file.
* This change must be to an application dependency (in a `dependencies` tag)
  rather than a build dependency (in a `build` tag).
* The pull request must be associated with a failing GitHub Action workflow.

This definition matches common dependency "bumps" as performed by bots such as 
Dependabot and Renovate, but could also correspond to an update made by a human developer.

## Data format 
Gathered data may be found as JSON files in the [`dataset`](/dataset) folder.
Each file is named according to the SHA of the pull request, and contains data of
the format:
```json
{
    "url": "<github pr url>",
    "project": "<github_project>",
    "commit": "<sha>",
    "versionUpdateType": "{major|minor|patch}",
    "type": "{human|renovate|dependabot|other}"
}
```

## Stages
This data gathering is indented to be carried out in three stages: 
* Stage 1 : purely static 
* Stage 2: reproduced locally. This will introduce the following data to the breaking update JSON format:
  ```json
  {
    "failureType": "<free text>", 
    "failureLog": "<CI url>", 
    "isFailureVerifiedLocally": "{yes|no}"
  }
  ```
* Stage 3: isolated will all environment requests downloaded

Currently, only the tooling for stage 1 is in place.

## The GitHub repository miner
In order to gather breaking dependency updates from GitHub, a tool called the 
Breaking Update Miner is available.  
You can build this tool locally using `mvn package`.
You can then run the tool and print usage information with the command:
```bash
java -jar target/breaking-updates-1.0-SNAPSHOT-jar-with-dependencies.jar --help 
```

## Stats
The dataset currently consists of 289 breaking updates from 21 different projects.