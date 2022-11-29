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
    "type": "{human|renovate|dependabot|other}",
    "date": "<timestamp>",
    "analysis": {...} /// per the analysis format below
}
```

## Workflow
The data gathering workflow is as follows: 
* Stage 1 : we look at Github metadata
* Stage 2: (WIP) we save the commit <commit_id> in a branch called "branch-<project_slug>-<commit_id>" in this repo. 
* Stage 3: (WIP) reproduce the failure locally under the some assumptions documented below. This will introduce the following data to the breaking update JSON format:
  ```json
  // analysis format
  {
    "failureLabels": ["COMPILATION_FAILURE", "TEST_FAILURE", ...], 
    "failureType": "<free text>", 
    "reproductionFailureLog": "<url>", // points to a long-lived URL, a file in that repo fits
    "isFailureVerifiedLocally": "{yes|no}",
    "javaVersionInferred": "11",
    "javaVersionUsedForLocalReproduction": "11"
    
  }
  ```
  * Assumptions:
    * We run Linux (kernel version and distribution to be documented)
    * We run OpenJDK
    * If the Java inference task fails (see `javaVersionInferred`), we assume Java 11 
* Stage 4: (WIP) isolate all environment / network requests by downloading them locally


## The GitHub repository miner
In order to gather breaking dependency updates from GitHub, a tool called the 
Breaking Update Miner is available.  
You can build this tool locally using `mvn package`.
You can then run the tool and print usage information with the command:
```bash
java -jar target/breaking-updates-1.0-SNAPSHOT-jar-with-dependencies.jar --help 
```

## Stats

* Core stats
  * As of Nov 29 2022: The dataset currently consists of 8492 breaking updates from 405 different projects.
* Other stats:
  * Ratio of breaking PR with compilation failures locally : XX%
  * Ratio of breaking PR with test failures locally: XX%
  * Distribution per Java version: XXXXXXXX
