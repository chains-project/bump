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
    "createdAt": "<timestamp for pr creation>",
    "dependencyGroupID": "<group id>",
    "dependencyArtifactID": "<artifact id>",
    "versionUpdateType": "{major|minor|patch|other}",
    "type": "{human|renovate|dependabot|other}",
    "reproductionStatus": "{not_attempted|successful|unreproducible}",
    "analysis": "<json object corresponding to the specification below>"
}
```

## Workflow
The data gathering workflow is as follows: 
* Stage 1 : we look at Github metadata
* Stage 2: (WIP) we save the commit <commit_id> in a branch called "branch-<project_slug>-<commit_id>" in this repo. 
* Stage 3: (WIP) reproduce the failure locally under the assumptions documented below. This will introduce the following analysis 
  data to the breaking update JSON format:
  ```json
  {
    "labels": ["DEPENDENCY_RESOLUTION_FAILURE", "MAVEN_ENFORCER_ERROR", "COMPILATION_FAILURE", 
               "TEST_FAILURE", "UNKNOWN_FAILURE", "PRECEDING_COMMIT_DEPENDENCY_RESOLUTION_FAILURE", 
               "PRECEDING_COMMIT_MAVEN_ENFORCER_ERROR", "PRECEDING_COMMIT_COMPILATION_FAILURE", 
               "PRECEDING_COMMIT_TEST_FAILURE", "PRECEDING_COMMIT_UNKNOWN_FAILURE", "NO_FAILURE"], 
    "reproductionLogLocation": "reproduction/{successful|unreproducible}/<sha>.log",
    "javaVersionUsedForLocalReproduction": "11"
  }
  ```
  * Assumptions:
    * We run Linux (kernel version and distribution to be documented)
    * We use Maven version 3.8.6
    * We run OpenJDK
    * As a starting point, we use Java 11
  * The reproduction can result in 11 different outcomes:
    * The project build fails _before_ the dependency is updated due to unresolved dependencies. 
      This is a failure of reproduction corresponding to the label "PRECEDING_COMMIT_DEPENDENCY_RESOLUTION_FAILURE".
    * The project build fails _before_ the dependency is updated due to maven enforcer plugin errors.
      This is a failure of reproduction corresponding to the label "PRECEDING_COMMIT_MAVEN_ENFORCER_ERROR".
    * The compilation step fails _before_ the dependency is updated.
      This is a failure of reproduction corresponding to the label "PRECEDING_COMMIT_COMPILATION_FAILURE".
    * The test step fails _before_ the dependency is updated.
      This is a failure of reproduction corresponding to the label "PRECEDING_COMMIT_TEST_FAILURE".
    * The project build fails _before_ the dependency is updated due to an unknown error which cannot be categorized 
      into above other failure types.
      This is a failure of reproduction corresponding to the label "PRECEDING_COMMIT_UNKNOWN_FAILURE".
    * The project build fails _after_ the dependency is updated due to unresolved dependencies, but not before.
      This is a failure of reproduction corresponding to the label "DEPENDENCY_RESOLUTION_FAILURE".
    * The project build fails _after_ the dependency is updated due to maven enforcer plugin errors, but not before.
      This is a failure of reproduction corresponding to the label "MAVEN_ENFORCER_ERROR".
    * The compilation step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "COMPILATION_FAILURE".
    * The test step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "TEST_FAILURE".
    * The project build fails _after_ the dependency is updated due to an unknown error which cannot be categorized
      into above other failure types.
      This is a failure of reproduction corresponding to the label "UNKNOWN_FAILURE".
    * Both compilation and tests finish successfully both before and after updating the dependency.
      This is a failure of reproduction corresponding to the label "NO_FAILURE".
* Stage 4: (WIP) isolate all environment / network requests by downloading them locally.

## Tools

### The BreakingUpdateMiner
In order to gather breaking dependency updates from GitHub, a tool called the 
BreakingUpdateMiner is available.  
You can build this tool locally using `mvn package`.
You can then run the tool and print usage information with the command:
```bash
java -jar target/BreakingUpdateMiner.jar --help 
```

### The BreakingUpdateReproducer
In order to perform local reproduction once potential breaking uppdates have been found by the miner,
a tool called the BreakingUpdateReproducer is available.
You can build this tool locally using `mvn package`.
You can then run the tool and print usage information with the command:
```bash
java -jar target/BreakingUpdateReproducer.jar --help 
```

## Stats
As of Jun 12 2023:
  * The dataset consists of 11004 breaking updates from 422 different projects.
  * Reproduction has been attempted for 4750 (43.17%) of these breaking updates.
    - Of these reproductions, 488 (10.27%) fail compilation with the updated dependency.
    - 348 (7.33%) fail tests with the updated dependency.
    - The remaining 3914 (82.40%) could not be locally reproduced.
