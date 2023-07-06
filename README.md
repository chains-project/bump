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
    "previousVersion": "<label indicating the previous version of the dependency>",
    "newVersion" : "<label indicating the new version of the dependency>",
    "versionUpdateType": "{major|minor|patch|other}",
    "type": "{human|renovate|dependabot|other}",
    "reproductionStatus": "{not_attempted|successful|unreproducible}",
    "baseBuildCommand": "<The command to compile and run tests without the breaking update commit>",
    "breakingUpdateReproductionCommand": "<The command to compile and run tests with the breaking update commit>",
    "analysis": "<json object of collected analysis data as specified below>",
    "metadata": "<json object of collected metadata as specified below>"
}
```

## Workflow
The data gathering workflow is as follows: 
* Stage 1 : we look at Github metadata
* Stage 2: (WIP) we save the commit <commit_id> in a branch called "branch-<project_slug>-<commit_id>" in this repo. 
* Stage 3: (WIP) reproduce the failure locally under the assumptions documented below. This will introduce the following analysis 
  data and metadata to the breaking update JSON format:
  ```json
  {
    "analysis" : {
      "labels" : [ "<label indicating the status of the reproduction>" ],
      "javaVersionUsedForReproduction" : "<used java version>",
      "reproductionLogLocation" : "<location of the saved reproduction log file>"
    },
    "metadata" : {
      "compareLink" : "<the github comparison link for the old and new tag releases of the updated dependency if it exists>",
      "mavenSourceLinks" : [ "<maven source jar links for the old and new releases of the updated dependency if they exist>"],
      "updateType" : "{pom|jar}"
    }
  }
  ```
  * Assumptions:
    * We run Linux (kernel version and distribution to be documented)
    * We use Maven version 3.8.6
    * We run OpenJDK
    * As a starting point, we use Java 11
  * The reproduction can result in 5 different successful outcomes:
    * The project build fails _after_ the dependency is updated due to unresolved dependencies, but not before.
      This is a successful reproduction corresponding to the label "DEPENDENCY_RESOLUTION_FAILURE".
    * The project build fails _after_ the dependency is updated due to maven enforcer plugin errors, but not before.
      This is a successful reproduction corresponding to the label "MAVEN_ENFORCER_FAILURE".
    * The compilation step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "COMPILATION_FAILURE".
    * The test step fails _after_ the dependency is updated, but not before.
      This is a successful reproduction corresponding to the label "TEST_FAILURE".
    * The project build fails _after_ the dependency is updated due to an unknown error which cannot be categorized
      into above other failure types.
      This is a successful reproduction corresponding to the label "UNKNOWN_FAILURE".
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
As of Jul 6 2023:
  * The dataset consists of 0 reproducible breaking updates.
    - Of these breaking updates, 0 (0.00%) fail compilation with the updated dependency.
    - 0 (0.00%) fail tests with the updated dependency.
    - 0 (0.00%) have dependency resolution failures with the updated dependency.
    - 0 (0.00%) fail after updating the dependency due to maven enforcer failures.
    - 0 (0.00%) fail due to unknown failures after updating the dependency.
  * Reproduction has been attempted for 3 breaking updates, and 3 (100.00%) could not be locally reproduced.
  * For the remaining 1394 breaking updates in the dataset, reproduction has not been attempted yet.
